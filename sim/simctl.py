#!/usr/bin/env python3
"""Deterministic Freedom L1 scenario runner.

Python owns the restricted YAML DSL, virtual clock, fault orchestration and evidence.
Security-relevant transition state is executed by the shared pure-Java Freedom core
through a persistent local bridge process. This prevents simctl from becoming a
second implementation of the same protocol state machines.
"""

from __future__ import annotations

import argparse
import heapq
import json
import subprocess
import sys
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any, Callable

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "tools"))
from build_core import build  # noqa: E402


class ScenarioError(RuntimeError):
    pass


def _scalar(raw: str) -> Any:
    raw = raw.strip()
    if not raw:
        return ""
    if raw == "true": return True
    if raw == "false": return False
    if raw in ("null", "~"): return None
    if (raw.startswith('"') and raw.endswith('"')) or (raw.startswith("'") and raw.endswith("'")):
        return raw[1:-1]
    try:
        return int(raw)
    except ValueError:
        return raw


def load_scenario(path: Path) -> dict[str, Any]:
    data: dict[str, Any] = {}
    nodes: list[str] = []
    steps: list[dict[str, Any]] = []
    section: str | None = None
    current_step: dict[str, Any] | None = None
    nested_key: str | None = None

    for line_no, original in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
        if not original.strip() or original.lstrip().startswith("#"):
            continue
        indent = len(original) - len(original.lstrip(" "))
        if "\t" in original[:indent]:
            raise ScenarioError(f"{path}:{line_no}: tabs are forbidden")
        text = original.strip()
        if indent == 0:
            nested_key = None
            current_step = None
            if text.endswith(":"):
                section = text[:-1]
                if section not in ("nodes", "steps"):
                    raise ScenarioError(f"{path}:{line_no}: unsupported section {section!r}")
                continue
            if ":" not in text:
                raise ScenarioError(f"{path}:{line_no}: expected key: value")
            key, raw = text.split(":", 1)
            data[key.strip()] = _scalar(raw)
            section = None
            continue
        if section == "nodes":
            if indent != 2 or not text.startswith("- "):
                raise ScenarioError(f"{path}:{line_no}: invalid nodes list item")
            nodes.append(str(_scalar(text[2:])))
            continue
        if section == "steps":
            if indent == 2 and text.startswith("- "):
                current_step = {}
                steps.append(current_step)
                nested_key = None
                item = text[2:]
                if ":" not in item:
                    raise ScenarioError(f"{path}:{line_no}: step must start with key: value")
                key, raw = item.split(":", 1)
                current_step[key.strip()] = _scalar(raw)
                continue
            if current_step is None:
                raise ScenarioError(f"{path}:{line_no}: step field without step")
            if indent == 4:
                if ":" not in text:
                    raise ScenarioError(f"{path}:{line_no}: invalid step field")
                key, raw = text.split(":", 1)
                key = key.strip()
                if raw.strip() == "":
                    current_step[key] = {}
                    nested_key = key
                else:
                    current_step[key] = _scalar(raw)
                    nested_key = None
                continue
            if indent == 6 and nested_key:
                if ":" not in text:
                    raise ScenarioError(f"{path}:{line_no}: invalid nested step field")
                key, raw = text.split(":", 1)
                current_step[nested_key][key.strip()] = _scalar(raw)
                continue
            raise ScenarioError(f"{path}:{line_no}: unsupported indentation/shape")
        raise ScenarioError(f"{path}:{line_no}: indented data outside a section")

    data["nodes"] = nodes
    data["steps"] = steps
    if data.get("version") != 1:
        raise ScenarioError(f"{path}: only scenario version 1 is supported")
    if data.get("clock") != "virtual":
        raise ScenarioError(f"{path}: L1 accepts only virtual clock scenarios")
    if not data.get("name"):
        raise ScenarioError(f"{path}: missing scenario name")
    return data


def parse_time_ms(value: Any) -> int:
    if isinstance(value, int): return value
    if not isinstance(value, str): raise ScenarioError(f"invalid time {value!r}")
    if value.endswith("ms"): return int(value[:-2])
    if value.endswith("s"): return int(value[:-1]) * 1000
    raise ScenarioError(f"unsupported virtual time {value!r}")


def _bool(value: str | None) -> bool:
    return value == "true"


def _nullable_int(value: str | None) -> int | None:
    if value in (None, "null"): return None
    return int(value)


class CoreBridge:
    def __init__(self, classes: Path):
        self.proc = subprocess.Popen(
            ["java", "-cp", str(classes), "dev.freedom.sim.CoreStateServer"],
            cwd=ROOT,
            stdin=subprocess.PIPE,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True,
            encoding="utf-8",
            bufsize=1,
        )
        if self.proc.stdin is None or self.proc.stdout is None:
            raise ScenarioError("failed to create shared-core bridge")

    def command(self, *parts: Any) -> dict[str, str]:
        assert self.proc.stdin is not None and self.proc.stdout is not None
        self.proc.stdin.write("\t".join(str(p).lower() if isinstance(p, bool) else str(p) for p in parts) + "\n")
        self.proc.stdin.flush()
        line = self.proc.stdout.readline().rstrip("\n")
        if not line:
            stderr = self.proc.stderr.read() if self.proc.stderr else ""
            raise ScenarioError(f"shared core terminated unexpectedly: {stderr}")
        fields = line.split("\t")
        if fields[0] == "ERR":
            code = fields[1] if len(fields) > 1 else "CORE_ERROR"
            msg = fields[2] if len(fields) > 2 else ""
            raise ScenarioError(f"{code}: {msg}")
        if fields[0] != "OK":
            raise ScenarioError(f"invalid core bridge response: {line!r}")
        result: dict[str, str] = {}
        for item in fields[1:]:
            if "=" in item:
                key, value = item.split("=", 1)
                result[key] = value
        return result

    def snapshot(self) -> dict[str, str]:
        return self.command("SNAPSHOT")

    def close(self) -> None:
        if self.proc.stdin:
            self.proc.stdin.close()
        try:
            self.proc.wait(timeout=2)
        except subprocess.TimeoutExpired:
            self.proc.kill()


@dataclass(order=True)
class Scheduled:
    at_ms: int
    order: int
    callback: Callable[[], None] = field(compare=False)
    label: str = field(compare=False)


class Engine:
    def __init__(self, scenario: dict[str, Any], source: Path, classes: Path):
        self.scenario = scenario
        self.source = source
        self.now_ms = 0
        self.queue: list[Scheduled] = []
        self.order = 0
        self.core = CoreBridge(classes)
        self.events: list[dict[str, Any]] = []
        self.assertions: list[dict[str, Any]] = []
        for node in scenario["nodes"]:
            self.core.command("NODE", node)

    def trace(self, kind: str, **fields: Any) -> None:
        self.events.append({"at_ms": self.now_ms, "kind": kind, **fields})

    def schedule(self, delay_ms: int, label: str, callback: Callable[[], None]) -> None:
        self.order += 1
        heapq.heappush(self.queue, Scheduled(self.now_ms + delay_ms, self.order, callback, label))

    def drain_until(self, at_ms: int) -> None:
        while self.queue and self.queue[0].at_ms <= at_ms:
            scheduled = heapq.heappop(self.queue)
            self.now_ms = scheduled.at_ms
            self.trace("internal", event=scheduled.label)
            scheduled.callback()
        self.now_ms = at_ms

    def _recover_route(self) -> None:
        state = self.core.command("RECOVER_ROUTE")
        self.trace("route_recovery", recovered=_bool(state.get("recovered")), via=state.get("route"))

    def _rotate_pairwise_future(self) -> None:
        self.core.command("ROTATE_PAIRWISE")
        self.trace("pairwise_future_rotated")

    def action(self, step: dict[str, Any]) -> None:
        name = str(step["action"])
        self.trace("action", action=name)
        if name == "connect":
            self.core.command("CONNECT", step.get("from"), step.get("to"), step.get("via", "direct")); return
        if name == "block": self.core.command("BLOCK", step["target"]); return
        if name == "unblock": self.core.command("UNBLOCK", step["target"]); return
        if name == "nat_rebind":
            state = self.core.command("NAT_REBIND", step["target"])
            if _bool(state.get("route_blocked")):
                self.schedule(1000, "route-recovery", self._recover_route)
            return
        if name == "restore_root_identity": self.core.command("RESTORE_ROOT"); return
        if name == "fetch_verified_pairwise_recovery_anchor":
            generation = step.get("expect", {}).get("latest_backup_generation")
            if not isinstance(generation, int): raise ScenarioError("anchor action needs expect.latest_backup_generation")
            self.core.command("SET_RECOVERY_ANCHOR", generation); return
        if name == "fetch_pairwise_backup":
            generation = int(step["returns_generation"])
            integrity = step.get("integrity") == "valid"
            hash_ok = bool(step.get("hash_matches_anchor", False))
            state_ok = bool(step.get("state_commitment_matches_anchor", False))
            self.core.command("EVALUATE_BACKUP", generation, integrity, hash_ok, state_ok); return
        if name == "reauthenticate_peer":
            snap = self.core.snapshot()
            if _nullable_int(snap.get("accepted_generation")) is None:
                raise ScenarioError("cannot reauthenticate before pairwise backup acceptance")
            self.schedule(500, "rotate-future-pairwise-state", self._rotate_pairwise_future); return
        if name == "set_bootstrap_freshness_floor":
            self.core.command("SET_BOOTSTRAP_FLOOR", int(step["minimum_height"])); return
        if name == "fetch_control_plane_checkpoint":
            self.core.command("VERIFY_CHECKPOINT", int(step["height"]), step.get("proof") == "valid"); return
        if name == "configure_network_anchor":
            self.core.command(
                "CONFIGURE_NETWORK_ANCHOR",
                step["network_id"], step["chain_adapter_id"], step["chain_network_id"],
                step["verifier_profile"], int(step["verifier_policy_version"]),
                step["pinned_commitment"], int(step["minimum_checkpoint_height"])); return
        if name == "evaluate_network_anchor":
            previous = step.get("previous_commitment")
            self.core.command(
                "VERIFY_NETWORK_ANCHOR",
                step["network_id"], step["chain_adapter_id"], step["chain_network_id"],
                step["verifier_profile"], int(step["verifier_policy_version"]),
                step["commitment"], "null" if previous is None else previous,
                int(step["anchor_epoch"]), int(step["trusted_checkpoint_height"]),
                int(step["signer_set_epoch"]), int(step["issued_at_height"]),
                int(step["activation_height"]), bool(step.get("payload_binding_valid", False)),
                bool(step.get("threshold_signatures_valid", False)),
                bool(step.get("signer_set_transition_valid", False)),
                bool(step.get("consensus_continuity_valid", False))); return
        if name == "begin_rekey": self.core.command("BEGIN_REKEY", int(step["next_epoch"])); return
        if name == "receive_rekey_commit": self.core.command("REKEY_COMMIT", int(step["next_epoch"])); return
        if name == "drop_rekey_ack": self.trace("packet_dropped", packet="rekey_ack"); return
        if name == "send_rekey_ack": self.core.command("REKEY_ACK", int(step["next_epoch"])); return
        raise ScenarioError(f"unsupported action: {name}")

    def assertion(self, step: dict[str, Any]) -> None:
        name = str(step["assert"])
        reason = step.get("reason")
        equals = step.get("equals")
        s = self.core.snapshot()
        passed = False
        detail: Any = None
        if name == "session_recovered": passed = _bool(s.get("route_recovered"))
        elif name in ("peer_identity_unchanged", "route_is_not_authentication_authority"):
            passed = _bool(s.get("peer_identity_unchanged"))
        elif name == "no_mailbox_write": passed = int(s.get("mailbox_writes", "-1")) == 0
        elif name == "pairwise_backup_rejected":
            detail = None if s.get("backup_last_reason") == "null" else s.get("backup_last_reason")
            passed = _bool(s.get("backup_last_rejected")) and (reason is None or detail == reason)
        elif name == "pairwise_backup_accepted": passed = _nullable_int(s.get("accepted_generation")) is not None
        elif name == "future_rendezvous_state_rotated": passed = _bool(s.get("future_rotated"))
        elif name == "old_backup_not_future_authority": passed = not _bool(s.get("old_backup_future_authority"))
        elif name == "control_plane_checkpoint_rejected":
            detail = None if s.get("control_last_reason") == "null" else s.get("control_last_reason")
            passed = _bool(s.get("control_last_rejected")) and (reason is None or detail == reason)
        elif name == "control_plane_checkpoint_accepted": passed = _nullable_int(s.get("verified_height")) is not None
        elif name == "verified_checkpoint_height":
            detail = _nullable_int(s.get("verified_height")); passed = detail == equals
        elif name == "network_anchor_rejected":
            detail = None if s.get("network_anchor_last_reason") == "null" else s.get("network_anchor_last_reason")
            passed = not _bool(s.get("network_anchor_last_accepted")) and (reason is None or detail == reason)
        elif name == "network_anchor_accepted": passed = _bool(s.get("network_anchor_last_accepted"))
        elif name == "network_anchor_commitment":
            detail = None if s.get("network_anchor_commitment") == "null" else s.get("network_anchor_commitment"); passed = detail == equals
        elif name == "network_anchor_epoch": detail = int(s.get("network_anchor_epoch", "-1")); passed = detail == equals
        elif name == "network_anchor_checkpoint_height":
            detail = int(s.get("network_anchor_checkpoint_height", "-1")); passed = detail == equals
        elif name == "network_anchor_signer_set_epoch":
            detail = int(s.get("network_anchor_signer_set_epoch", "-1")); passed = detail == equals
        elif name == "session_key_epoch": detail = int(s.get("key_epoch", "-1")); passed = detail == equals
        elif name == "no_split_brain": passed = _bool(s.get("no_split_brain"))
        elif name == "old_send_key_erased": passed = _bool(s.get("old_send_key_erased"))
        else: raise ScenarioError(f"unsupported assertion: {name}")
        record = {"at_ms": self.now_ms, "assert": name, "passed": passed}
        if detail is not None: record["detail"] = detail
        self.assertions.append(record)
        self.trace("assert", assertion=name, passed=passed, detail=detail)
        if not passed: raise ScenarioError(f"assertion failed: {name}; detail={detail!r}")

    def run(self) -> dict[str, Any]:
        try:
            last_time = -1
            for step in self.scenario["steps"]:
                if "at" not in step: raise ScenarioError("every step needs virtual time 'at'")
                at_ms = parse_time_ms(step["at"])
                if at_ms < last_time: raise ScenarioError("scenario steps must be non-decreasing in virtual time")
                self.drain_until(at_ms); last_time = at_ms
                if "action" in step: self.action(step)
                elif "assert" in step: self.assertion(step)
                else: raise ScenarioError("step needs action or assert")
            self.drain_until(max(last_time, self.now_ms))
            return {
                "scenario": self.scenario["name"], "source": str(self.source.relative_to(ROOT)),
                "seed": int(self.scenario.get("seed", 0)), "clock": "virtual",
                "virtual_time_ms": self.now_ms, "core": "shared-java-17",
                "assertions": self.assertions, "events": self.events, "result": "PASS",
            }
        finally:
            self.core.close()


def run_path(path: Path, classes: Path) -> dict[str, Any]:
    return Engine(load_scenario(path), path, classes).run()


def main() -> int:
    parser = argparse.ArgumentParser()
    group = parser.add_mutually_exclusive_group(required=True)
    group.add_argument("--scenario", type=Path)
    group.add_argument("--all", action="store_true")
    parser.add_argument("--evidence-dir", type=Path)
    parser.add_argument("--quiet", action="store_true")
    parser.add_argument("--no-build", action="store_true")
    args = parser.parse_args()
    classes = ROOT / "build" / "core-classes" if args.no_build else build()
    if not classes.exists(): raise SystemExit("shared core classes missing; omit --no-build")
    paths = sorted((ROOT / "sim" / "scenarios").glob("*.yaml")) if args.all else [args.scenario if args.scenario.is_absolute() else ROOT / args.scenario]
    failures: list[str] = []
    reports: list[dict[str, Any]] = []
    for path in paths:
        try:
            report = run_path(path, classes); reports.append(report)
            if not args.quiet: print(f"PASS {report['scenario']} ({len(report['assertions'])} assertions, shared core)")
        except (ScenarioError, KeyError, ValueError, subprocess.SubprocessError) as exc:
            failures.append(f"{path}: {exc}")
            if not args.quiet: print(f"FAIL {path}: {exc}", file=sys.stderr)
    if args.evidence_dir:
        args.evidence_dir.mkdir(parents=True, exist_ok=True)
        for report in reports:
            (args.evidence_dir / f"{report['scenario']}.json").write_text(json.dumps(report, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    if failures:
        for failure in failures: print(f"ERROR: {failure}", file=sys.stderr)
        return 1
    if args.quiet: print(f"Freedom simulator passed {len(reports)} scenario(s) using shared Java core.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
