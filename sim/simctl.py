#!/usr/bin/env python3
"""Deterministic host-side Freedom scenario runner.

The runner executes a deliberately small YAML subset used by sim/scenarios/*.yaml.
It provides a virtual clock, deterministic internal scheduling, fault injection
and security assertions without depending on Android or third-party packages.

It is an engineering simulator, not an independent implementation of Freedom
cryptography. As production core modules appear, scenario actions should call the
same state machines/serialization rather than re-implement them here.
"""

from __future__ import annotations

import argparse
import heapq
import json
import random
import sys
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any, Callable

ROOT = Path(__file__).resolve().parents[1]


class ScenarioError(RuntimeError):
    pass


def _scalar(raw: str) -> Any:
    raw = raw.strip()
    if not raw:
        return ""
    if raw == "true":
        return True
    if raw == "false":
        return False
    if raw in ("null", "~"):
        return None
    if (raw.startswith('"') and raw.endswith('"')) or (
        raw.startswith("'") and raw.endswith("'")
    ):
        return raw[1:-1]
    try:
        return int(raw)
    except ValueError:
        return raw


def load_scenario(path: Path) -> dict[str, Any]:
    """Parse the Freedom Scenario YAML subset v1."""
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
        raise ScenarioError(f"{path}: only virtual clock scenarios are accepted by L1 simctl")
    if not data.get("name"):
        raise ScenarioError(f"{path}: missing scenario name")
    return data


def parse_time_ms(value: Any) -> int:
    if isinstance(value, int):
        return value
    if not isinstance(value, str):
        raise ScenarioError(f"invalid time {value!r}")
    if value.endswith("ms"):
        return int(value[:-2])
    if value.endswith("s"):
        return int(value[:-1]) * 1000
    raise ScenarioError(f"unsupported virtual time {value!r}")


@dataclass(order=True)
class Scheduled:
    at_ms: int
    order: int
    callback: Callable[[], None] = field(compare=False)
    label: str = field(compare=False)


class Engine:
    def __init__(self, scenario: dict[str, Any], source: Path):
        self.scenario = scenario
        self.source = source
        self.now_ms = 0
        self.rng = random.Random(int(scenario.get("seed", 0)))
        self.queue: list[Scheduled] = []
        self.order = 0
        self.nodes = {name: {"nat_generation": 0} for name in scenario["nodes"]}
        self.blocked: set[str] = set()
        self.session: dict[str, Any] = {
            "active": False,
            "from": None,
            "to": None,
            "route": None,
            "original_pair": None,
            "recovered": False,
            "mailbox_writes": 0,
            "key_epoch": 1,
            "rekey_state": "STABLE",
            "pending_key_epoch": None,
            "old_send_key_erased": False,
        }
        self.backup: dict[str, Any] = {
            "root_restored": False,
            "anchor_generation": None,
            "accepted_generation": None,
            "last_rejected": False,
            "last_reason": None,
            "future_rotated": False,
            "old_backup_future_authority": False,
        }
        self.control: dict[str, Any] = {
            "bootstrap_floor": None,
            "verified_height": None,
            "last_rejected": False,
            "last_reason": None,
        }
        self.events: list[dict[str, Any]] = []
        self.assertions: list[dict[str, Any]] = []

    def trace(self, kind: str, **fields: Any) -> None:
        self.events.append({"at_ms": self.now_ms, "kind": kind, **fields})

    def schedule(self, delay_ms: int, label: str, callback: Callable[[], None]) -> None:
        self.order += 1
        heapq.heappush(
            self.queue,
            Scheduled(self.now_ms + delay_ms, self.order, callback, label),
        )

    def drain_until(self, at_ms: int) -> None:
        while self.queue and self.queue[0].at_ms <= at_ms:
            scheduled = heapq.heappop(self.queue)
            self.now_ms = scheduled.at_ms
            self.trace("internal", event=scheduled.label)
            scheduled.callback()
        self.now_ms = at_ms

    def _relay_candidates(self) -> list[str]:
        return sorted(
            name
            for name in self.nodes
            if name.startswith("relay_") and name not in self.blocked
        )

    def _recover_route(self) -> None:
        candidates = [
            relay for relay in self._relay_candidates()
            if relay != self.session.get("route")
        ]
        if not candidates:
            self.trace("route_recovery_failed")
            return
        new_route = candidates[self.rng.randrange(len(candidates))]
        self.session["route"] = new_route
        self.session["recovered"] = True
        self.trace("route_recovered", via=new_route)

    def action(self, step: dict[str, Any]) -> None:
        name = step["action"]
        self.trace("action", action=name)

        if name == "connect":
            source, target = step.get("from"), step.get("to")
            if source not in self.nodes or target not in self.nodes:
                raise ScenarioError("connect references unknown endpoint")
            self.session.update(
                active=True,
                to=target,
                route=step.get("via", "direct"),
                original_pair=(source, target),
                recovered=False,
            )
            self.session["from"] = source
            return

        if name == "block":
            target = str(step["target"])
            self.blocked.add(target)
            if self.session["active"] and self.session["route"] == target:
                self.trace("route_degraded", blocked=target)
            return

        if name == "unblock":
            self.blocked.discard(str(step["target"]))
            return

        if name == "nat_rebind":
            target = str(step["target"])
            if target not in self.nodes:
                raise ScenarioError("nat_rebind references unknown node")
            self.nodes[target]["nat_generation"] += 1
            if self.session["active"] and self.session["route"] in self.blocked:
                self.schedule(1000, "route-recovery", self._recover_route)
            return

        if name == "restore_root_identity":
            self.backup["root_restored"] = True
            return

        if name == "fetch_verified_pairwise_recovery_anchor":
            expected = step.get("expect", {})
            generation = expected.get("latest_backup_generation")
            if not isinstance(generation, int):
                raise ScenarioError("anchor action needs expect.latest_backup_generation")
            self.backup["anchor_generation"] = generation
            return

        if name == "fetch_pairwise_backup":
            generation = step.get("returns_generation")
            integrity_ok = step.get("integrity") == "valid"
            anchor = self.backup["anchor_generation"]
            hash_ok = step.get("hash_matches_anchor", generation == anchor)
            state_ok = step.get("state_commitment_matches_anchor", generation == anchor)
            accepted = (
                integrity_ok
                and anchor is not None
                and generation == anchor
                and bool(hash_ok)
                and bool(state_ok)
            )
            self.backup["last_rejected"] = not accepted
            self.backup["last_reason"] = (
                None if accepted else "PAIRWISE_BACKUP_ROLLBACK_OR_MISMATCH"
            )
            if accepted:
                self.backup["accepted_generation"] = generation
            return

        if name == "reauthenticate_peer":
            if self.backup["accepted_generation"] is None:
                raise ScenarioError("cannot reauthenticate recovered peer before backup acceptance")
            self.schedule(500, "rotate-future-pairwise-state", self._rotate_pairwise_future)
            return

        if name == "set_bootstrap_freshness_floor":
            self.control["bootstrap_floor"] = int(step["minimum_height"])
            return

        if name == "fetch_control_plane_checkpoint":
            height = int(step["height"])
            proof_ok = step.get("proof") == "valid"
            floor = self.control["bootstrap_floor"]
            accepted = proof_ok and (floor is None or height >= floor)
            self.control["last_rejected"] = not accepted
            if not proof_ok:
                self.control["last_reason"] = "CONTROL_PLANE_PROOF_INVALID"
            elif floor is not None and height < floor:
                self.control["last_reason"] = "BOOTSTRAP_STATE_TOO_OLD"
            else:
                self.control["last_reason"] = None
                self.control["verified_height"] = height
            return

        if name == "begin_rekey":
            next_epoch = int(step["next_epoch"])
            if self.session["rekey_state"] != "STABLE":
                raise ScenarioError("rekey already in progress")
            if next_epoch != self.session["key_epoch"] + 1:
                raise ScenarioError("rekey epoch must increment exactly by one")
            self.session["pending_key_epoch"] = next_epoch
            self.session["rekey_state"] = "INIT_SENT"
            return

        if name == "receive_rekey_commit":
            next_epoch = int(step["next_epoch"])
            if (
                self.session["rekey_state"] != "INIT_SENT"
                or next_epoch != self.session["pending_key_epoch"]
            ):
                raise ScenarioError("unexpected rekey commit")
            self.session["rekey_state"] = "NEW_KEY_PENDING_ACK"
            return

        if name == "drop_rekey_ack":
            if self.session["rekey_state"] != "NEW_KEY_PENDING_ACK":
                raise ScenarioError("no pending rekey ack to drop")
            self.trace("packet_dropped", packet="rekey_ack")
            return

        if name == "send_rekey_ack":
            next_epoch = int(step["next_epoch"])
            if (
                self.session["rekey_state"] != "NEW_KEY_PENDING_ACK"
                or next_epoch != self.session["pending_key_epoch"]
            ):
                raise ScenarioError("unexpected rekey ack")
            self.session["key_epoch"] = next_epoch
            self.session["pending_key_epoch"] = None
            self.session["rekey_state"] = "STABLE"
            self.session["old_send_key_erased"] = True
            return

        raise ScenarioError(f"unsupported action: {name}")

    def _rotate_pairwise_future(self) -> None:
        self.backup["future_rotated"] = True
        self.backup["old_backup_future_authority"] = False

    def assertion(self, step: dict[str, Any]) -> None:
        name = str(step["assert"])
        reason = step.get("reason")
        equals = step.get("equals")
        passed = False
        detail: Any = None

        if name == "session_recovered":
            passed = bool(self.session["recovered"])
        elif name == "peer_identity_unchanged":
            passed = self.session["original_pair"] == (
                self.session["from"], self.session["to"]
            )
        elif name == "no_mailbox_write":
            passed = self.session["mailbox_writes"] == 0
        elif name == "route_is_not_authentication_authority":
            passed = self.session["original_pair"] == (
                self.session["from"], self.session["to"]
            )
        elif name == "pairwise_backup_rejected":
            passed = self.backup["last_rejected"] and (
                reason is None or self.backup["last_reason"] == reason
            )
            detail = self.backup["last_reason"]
        elif name == "pairwise_backup_accepted":
            passed = self.backup["accepted_generation"] is not None
        elif name == "future_rendezvous_state_rotated":
            passed = bool(self.backup["future_rotated"])
        elif name == "old_backup_not_future_authority":
            passed = not self.backup["old_backup_future_authority"]
        elif name == "control_plane_checkpoint_rejected":
            passed = self.control["last_rejected"] and (
                reason is None or self.control["last_reason"] == reason
            )
            detail = self.control["last_reason"]
        elif name == "control_plane_checkpoint_accepted":
            passed = self.control["verified_height"] is not None
        elif name == "verified_checkpoint_height":
            detail = self.control["verified_height"]
            passed = detail == equals
        elif name == "session_key_epoch":
            detail = self.session["key_epoch"]
            passed = detail == equals
        elif name == "no_split_brain":
            passed = (
                self.session["rekey_state"] == "STABLE"
                and self.session["pending_key_epoch"] is None
            )
        elif name == "old_send_key_erased":
            passed = bool(self.session["old_send_key_erased"])
        else:
            raise ScenarioError(f"unsupported assertion: {name}")

        record = {"at_ms": self.now_ms, "assert": name, "passed": passed}
        if detail is not None:
            record["detail"] = detail
        self.assertions.append(record)
        self.trace("assert", assertion=name, passed=passed, detail=detail)
        if not passed:
            raise ScenarioError(f"assertion failed: {name}; detail={detail!r}")

    def run(self) -> dict[str, Any]:
        last_time = -1
        for step in self.scenario["steps"]:
            if "at" not in step:
                raise ScenarioError("every step needs virtual time 'at'")
            at_ms = parse_time_ms(step["at"])
            if at_ms < last_time:
                raise ScenarioError("scenario steps must be non-decreasing in virtual time")
            self.drain_until(at_ms)
            last_time = at_ms
            if "action" in step:
                self.action(step)
            elif "assert" in step:
                self.assertion(step)
            else:
                raise ScenarioError("step needs action or assert")
        self.drain_until(max(last_time, self.now_ms))
        return {
            "scenario": self.scenario["name"],
            "source": str(self.source.relative_to(ROOT)),
            "seed": int(self.scenario.get("seed", 0)),
            "clock": "virtual",
            "virtual_time_ms": self.now_ms,
            "assertions": self.assertions,
            "events": self.events,
            "result": "PASS",
        }


def run_path(path: Path) -> dict[str, Any]:
    scenario = load_scenario(path)
    return Engine(scenario, path).run()


def main() -> int:
    parser = argparse.ArgumentParser()
    group = parser.add_mutually_exclusive_group(required=True)
    group.add_argument("--scenario", type=Path)
    group.add_argument("--all", action="store_true")
    parser.add_argument("--evidence-dir", type=Path)
    parser.add_argument("--quiet", action="store_true")
    args = parser.parse_args()

    paths = (
        sorted((ROOT / "sim" / "scenarios").glob("*.yaml"))
        if args.all
        else [args.scenario if args.scenario.is_absolute() else ROOT / args.scenario]
    )
    failures: list[str] = []
    reports: list[dict[str, Any]] = []

    for path in paths:
        try:
            report = run_path(path)
            reports.append(report)
            if not args.quiet:
                print(f"PASS {report['scenario']} ({len(report['assertions'])} assertions)")
        except (ScenarioError, KeyError, ValueError) as exc:
            failures.append(f"{path}: {exc}")
            if not args.quiet:
                print(f"FAIL {path}: {exc}", file=sys.stderr)

    if args.evidence_dir:
        args.evidence_dir.mkdir(parents=True, exist_ok=True)
        for report in reports:
            target = args.evidence_dir / f"{report['scenario']}.json"
            target.write_text(
                json.dumps(report, indent=2, sort_keys=True) + "\n", encoding="utf-8"
            )

    if failures:
        for failure in failures:
            print(f"ERROR: {failure}", file=sys.stderr)
        return 1
    if args.quiet:
        print(f"Freedom simulator passed {len(reports)} scenario(s).")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
