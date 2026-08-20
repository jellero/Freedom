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
    "near/README.md",
    "near/Cargo.toml",
    "near/control-plane-contract/Cargo.toml",
    "near/control-plane-contract/src/lib.rs",
    "near/l3-adapter/Cargo.toml",
    "near/l3-adapter/src/main.rs",
    "near/proof-verifier/Cargo.toml",
    "near/proof-verifier/src/lib.rs",
    "near/proof-verifier/tests/sandbox_proofs.rs",
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
    for marker in (
        "real NEAR Sandbox adapter implemented",
        "production light-client verification still separate",
        "light-client",
        "near/l3-adapter/Cargo.toml",
    ):
        if marker not in l3_readme:
            errors.append(f"L3 documentation missing boundary/implementation marker: {marker}")

    adapter = (ROOT / "near" / "l3-adapter" / "src" / "main.rs").read_text(encoding="utf-8")
    for marker in (
        "near_workspaces::sandbox()",
        "compile_project",
        "set_bootstrap_floor",
        "apply_mutation",
        "FREEDOM_L3",
        "is_failure()",
        "client_committed_version",
        "read_chain_version",
    ):
        if marker not in adapter:
            errors.append(f"NEAR L3 adapter missing executable marker: {marker}")

    contract = (ROOT / "near" / "control-plane-contract" / "src" / "lib.rs").read_text(encoding="utf-8")
    for marker in (
        "#[near(contract_state)]",
        "set_bootstrap_floor",
        "apply_mutation",
        "CONTROL_PLANE_ROLLBACK",
        "CONTROL_PLANE_EXECUTION_FAILED",
    ):
        if marker not in contract:
            errors.append(f"NEAR control-plane kernel missing marker: {marker}")

    proof_verifier = (ROOT / "near" / "proof-verifier" / "src" / "lib.rs").read_text(encoding="utf-8")
    for marker in (
        "NearNetworkAnchor",
        "NearLightClientVerifier",
        "verify_and_advance",
        "InsufficientApprovedStake",
        "verify_execution_proof",
        "BlockMerkleProofInvalid",
        "verify_contract_state_value",
        "StateProofMissingNode",
    ):
        if marker not in proof_verifier:
            errors.append(f"NEAR proof verifier missing executable marker: {marker}")

    proof_test = (ROOT / "near" / "proof-verifier" / "tests" / "sandbox_proofs.rs").read_text(encoding="utf-8")
    for marker in (
        "malicious_rpc_objects_cannot_be_promoted_to_verified_state",
        "include_proof: true",
        "light_client_proof",
        "FREEDOM_ABSENT_KEY",
    ):
        if marker not in proof_test:
            errors.append(f"NEAR proof integration gate missing marker: {marker}")

    workflow = (ROOT / ".github" / "workflows" / "spec-consistency.yml").read_text(encoding="utf-8")
    for marker in (
        "l3-near-sandbox:",
        "Run real NEAR Sandbox differential",
        "l4-near-proof-verifier:",
        "Verify NEAR finality, execution and state proofs against NetworkAnchor",
        '"near/**"',
    ):
        if marker not in workflow:
            errors.append(f"CI does not gate NEAR development stack marker: {marker}")

    if errors:
        for error in errors:
            print(f"ERROR: {error}", file=sys.stderr)
        return 1
    print("Freedom development-stack consistency checks passed.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
