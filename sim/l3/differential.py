#!/usr/bin/env python3
"""Freedom L3 differential harness.

The canonical side is the shared Java core. A real ChainAdapter is supplied as a
persistent JSON-lines command. Without --adapter-cmd the harness can validate the
canonical vector/oracle only; this is not considered real L3 acceptance.
"""
from __future__ import annotations

import argparse
import json
import shlex
import subprocess
import sys
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "tools"))
sys.path.insert(0, str(ROOT / "sim"))
from build_core import build  # noqa: E402
from simctl import CoreBridge  # noqa: E402

VECTORS = ROOT / "sim" / "l3" / "vectors.json"


def nullable_int(value: str | None) -> int | None:
    if value in (None, "null"):
        return None
    return int(value)


class CanonicalOracle:
    def __init__(self) -> None:
        self.bridge = CoreBridge(build())

    def close(self) -> None:
        self.bridge.close()

    def request(self, req: dict[str, Any]) -> dict[str, Any]:
        op = req["op"]
        if op == "set_bootstrap_floor":
            state = self.bridge.command("SET_BOOTSTRAP_FLOOR", int(req["minimum_height"]))
            return {"accepted": True, "minimum_height": nullable_int(state.get("bootstrap_floor"))}
        if op == "verify_checkpoint":
            state = self.bridge.command(
                "VERIFY_CHECKPOINT", int(req["height"]), bool(req["proof_valid"])
            )
            accepted = state.get("accepted") == "true"
            reason = state.get("control_last_reason")
            return {
                "accepted": accepted,
                "failure": None if reason in (None, "null") else reason,
                "verified_height": nullable_int(state.get("verified_height")),
            }
        if op == "verify_mutation":
            state = self.bridge.command(
                "VERIFY_MUTATION",
                bool(req["finality_proof_valid"]),
                bool(req["execution_succeeded"]),
                bool(req["resulting_state_proof_valid"]),
                bool(req["exact_transition_matched"]),
                int(req["resulting_version"]),
            )
            accepted = state.get("accepted") == "true"
            reason = state.get("mutation_last_reason")
            return {
                "accepted": accepted,
                "failure": None if reason in (None, "null") else reason,
                "committed_version": int(state.get("mutation_committed_version", "0")),
            }
        raise RuntimeError(f"unsupported L3 vector operation: {op}")


class ExternalAdapter:
    def __init__(self, command: str) -> None:
        self.proc = subprocess.Popen(
            shlex.split(command), cwd=ROOT, text=True, encoding="utf-8",
            stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE, bufsize=1,
        )
        if self.proc.stdin is None or self.proc.stdout is None:
            raise RuntimeError("failed to start ChainAdapter command")

    def request(self, req: dict[str, Any]) -> dict[str, Any]:
        assert self.proc.stdin is not None and self.proc.stdout is not None
        self.proc.stdin.write(json.dumps(req, sort_keys=True) + "\n")
        self.proc.stdin.flush()
        line = self.proc.stdout.readline()
        if not line:
            stderr = self.proc.stderr.read() if self.proc.stderr else ""
            raise RuntimeError(f"ChainAdapter terminated: {stderr}")
        value = json.loads(line)
        if not isinstance(value, dict):
            raise RuntimeError("ChainAdapter response must be a JSON object")
        return value

    def close(self) -> None:
        if self.proc.stdin:
            self.proc.stdin.close()
        try:
            self.proc.wait(timeout=2)
        except subprocess.TimeoutExpired:
            self.proc.kill()


def compare_expected(name: str, actual: dict[str, Any], expected: dict[str, Any], side: str) -> None:
    for key, value in expected.items():
        if actual.get(key) != value:
            raise RuntimeError(
                f"{name}: {side} mismatch for {key}: expected {value!r}, got {actual.get(key)!r}; response={actual}"
            )


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--adapter-cmd", help="persistent JSONL NearChainAdapter command")
    parser.add_argument("--oracle-only", action="store_true", help="validate canonical vectors only; not real L3 acceptance")
    args = parser.parse_args()
    if not args.adapter_cmd and not args.oracle_only:
        print(
            "L3 not executed: supply --adapter-cmd with a real NearChainAdapter, "
            "or use --oracle-only only to validate the canonical vector side.",
            file=sys.stderr,
        )
        return 2

    document = json.loads(VECTORS.read_text(encoding="utf-8"))
    if document.get("version") != 1 or not isinstance(document.get("steps"), list):
        raise SystemExit("invalid sim/l3/vectors.json")

    oracle = CanonicalOracle()
    adapter = ExternalAdapter(args.adapter_cmd) if args.adapter_cmd else None
    try:
        for step in document["steps"]:
            name = step["name"]
            request = step["request"]
            expected = step["expect"]
            canonical = oracle.request(request)
            compare_expected(name, canonical, expected, "canonical oracle")
            if adapter:
                external = adapter.request(request)
                compare_expected(name, external, expected, "external adapter")
                # Compare the fields that the canonical oracle actually returns too.
                for key in set(canonical) & set(external):
                    if canonical[key] != external[key]:
                        raise RuntimeError(
                            f"{name}: differential mismatch for {key}: core={canonical[key]!r} adapter={external[key]!r}"
                        )
        if adapter:
            print(f"Freedom L3 differential passed {len(document['steps'])} transition step(s).")
        else:
            print(
                f"Freedom L3 canonical oracle passed {len(document['steps'])} step(s); "
                "real ChainAdapter differential NOT executed."
            )
        return 0
    finally:
        oracle.close()
        if adapter:
            adapter.close()


if __name__ == "__main__":
    raise SystemExit(main())
