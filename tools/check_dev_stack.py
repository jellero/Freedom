#!/usr/bin/env python3
from __future__ import annotations

import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

REQUIRED = [
    "core/README.md",
    "core/src/main/java/dev/freedom/core/FreedomCore.java",
    "core/src/test/java/dev/freedom/core/CoreSelfTest.java",
    "sim/jvm/dev/freedom/sim/CoreStateServer.java",
    "sim/l2/relay_server.py",
    "sim/l2/probe.py",
    "sim/l2/run_docker.py",
    "sim/l3/README.md",
    "sim/l3/differential.py",
    "sim/l3/vectors.json",
    "tools/build_core.py",
    "tools/run_core_tests.py",
]


def main() -> int:
    errors: list[str] = []
    for rel in REQUIRED:
        if not (ROOT / rel).is_file():
            errors.append(f"missing development-stack file: {rel}")

    app_gradle = (ROOT / "app" / "build.gradle.kts").read_text(encoding="utf-8")
    if 'java.srcDir("../core/src/main/java")' not in app_gradle:
        errors.append("Android source set is not compiling the shared core")

    simctl = (ROOT / "sim" / "simctl.py").read_text(encoding="utf-8")
    for marker in ("from build_core import build", "CoreStateServer", '"core": "shared-java-17"'):
        if marker not in simctl:
            errors.append(f"simctl is not wired to shared core marker: {marker}")

    core = (ROOT / "core" / "src" / "main" / "java" / "dev" / "freedom" / "core" / "FreedomCore.java").read_text(encoding="utf-8")
    for state_machine in (
        "class RouteState",
        "class PairwiseRecoveryState",
        "class BootstrapFreshnessState",
        "class MutationVerificationState",
        "class RekeyState",
    ):
        if state_machine not in core:
            errors.append(f"shared core missing state machine: {state_machine}")

    try:
        l3 = json.loads((ROOT / "sim" / "l3" / "vectors.json").read_text(encoding="utf-8"))
        if l3.get("version") != 1 or not isinstance(l3.get("steps"), list) or len(l3["steps"]) < 5:
            errors.append("L3 vectors are missing or too small")
    except (ValueError, OSError) as exc:
        errors.append(f"invalid L3 vectors: {exc}")

    l3_readme = (ROOT / "sim" / "l3" / "README.md").read_text(encoding="utf-8")
    if "not real L3 acceptance" not in l3_readme and "not** real L3 acceptance" not in l3_readme:
        errors.append("L3 documentation must distinguish oracle-only from real adapter acceptance")

    if errors:
        for error in errors:
            print(f"ERROR: {error}", file=sys.stderr)
        return 1
    print("Freedom development-stack consistency checks passed.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
