#!/usr/bin/env python3
"""Repository-level drift checks for the Freedom canonical specification.

This does not prove protocol or cryptographic correctness. It catches a small set
of structural regressions that should never silently re-enter the repository.
"""

from __future__ import annotations

import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

REQUIRED_FILES = [
    "AGENTS.md",
    "README.md",
    "spec/README.md",
    "spec/freedom.cddl",
    "docs/SECURITY_INVARIANTS.md",
    "docs/CONTROL_PLANE_SECURITY.md",
    "docs/REVOCATION.md",
    "docs/IDENTITY_MODEL.md",
    "docs/PROTOCOL.md",
    "docs/THREAT_MODEL.md",
    "docs/ADVANCED_DEVELOPMENT.md",
    "docs/REPOSITORY_GOVERNANCE.md",
    ".github/CODEOWNERS",
]

REQUIRED_CDDL_OBJECTS = [
    "root-control-state",
    "device-authorization-delegation",
    "device-certificate",
    "device-record",
    "device-revocation-record",
    "authorization-revocation-record",
    "user-recovery-policy",
    "user-root-rotation",
    "rendezvous-record",
    "recovery-beacon",
    "pairwise-recovery-bundle",
    "relay-descriptor",
    "provenance-attestation",
    "handshake-offer",
    "rekey-init",
    "rekey-commit",
    "rekey-ack",
    "encrypted-control-frame",
    "encrypted-media-frame",
    "verified-control-plane-checkpoint",
    "bootstrap-freshness-floor",
    "state-migration-proof",
    "chain-migration-manifest",
    "contract-upgrade-manifest",
    "freedom-release",
    "release-status",
    "security-policy",
    "signer-set-transition",
    "bootstrap-trust-anchor",
]

NORMATIVE_DOCS_REQUIRING_SCHEMA_LINK = [
    "docs/SECURITY_INVARIANTS.md",
    "docs/CONTROL_PLANE_SECURITY.md",
    "docs/REVOCATION.md",
    "docs/IDENTITY_MODEL.md",
    "docs/PROTOCOL.md",
]

# Stale strings/formulas that contradict the current canonical model.
FORBIDDEN_TEXT = {
    "root_authorization_signature": "old DeviceCertificate field; use canonical CDDL",
    "H(domain || write_public_key || epoch || direction)": (
        "old rendezvous slot formula; direction/epoch stay in secret key derivation"
    ),
    "proof/light-client verification progressiva quando appropriata": (
        "old optional proof language; security-sensitive state requires proofs"
    ),
}


def fail(errors: list[str], message: str) -> None:
    errors.append(message)


def read(rel: str) -> str:
    return (ROOT / rel).read_text(encoding="utf-8")


def main() -> int:
    errors: list[str] = []

    for rel in REQUIRED_FILES:
        if not (ROOT / rel).is_file():
            fail(errors, f"missing required file: {rel}")

    if errors:
        for error in errors:
            print(f"ERROR: {error}", file=sys.stderr)
        return 1

    readme = read("README.md")
    for internal_term in ("ADVANCED_DEVELOPMENT", "Codex", "Docker", "/var/run/docker.sock"):
        if internal_term in readme:
            fail(errors, f"README contains internal development-method term: {internal_term}")

    cddl = read("spec/freedom.cddl")
    for name in REQUIRED_CDDL_OBJECTS:
        if not re.search(rf"(?m)^\s*{re.escape(name)}\s*=\s*\{{", cddl):
            fail(errors, f"canonical CDDL object missing: {name}")

    for rel in NORMATIVE_DOCS_REQUIRING_SCHEMA_LINK:
        text = read(rel)
        if "spec/freedom.cddl" not in text and "../spec/freedom.cddl" not in text:
            fail(errors, f"normative document does not link canonical CDDL: {rel}")

    # Scan specification/documentation only; legacy implementation may intentionally
    # still contain spike-era terminology until it is replaced by the canonical core.
    scan_paths = [ROOT / "README.md", ROOT / "AGENTS.md"]
    scan_paths += list((ROOT / "docs").glob("*.md"))
    scan_paths += list((ROOT / "spec").glob("*.md"))
    for path in scan_paths:
        text = path.read_text(encoding="utf-8")
        for needle, reason in FORBIDDEN_TEXT.items():
            if needle in text:
                fail(errors, f"{path.relative_to(ROOT)} contains forbidden stale text {needle!r}: {reason}")

    # No old RecoveryBundle plaintext contacts[] schema should be reintroduced.
    for path in (ROOT / "docs").glob("*.md"):
        text = path.read_text(encoding="utf-8")
        if "PairwiseRecoveryBundle" in text and re.search(r"(?m)^\s*contacts\[\]\s*$", text):
            fail(errors, f"{path.relative_to(ROOT)} reintroduces plaintext contacts[] recovery schema")

    # SVGs are source-controlled documentation and should remain XML-well-formed.
    for svg in (ROOT / "docs" / "assets").glob("*.svg"):
        try:
            ET.parse(svg)
        except ET.ParseError as exc:
            fail(errors, f"invalid SVG/XML {svg.relative_to(ROOT)}: {exc}")

    # Ensure public README points at the new canonical documents.
    for required_reference in (
        "spec/freedom.cddl",
        "docs/SECURITY_INVARIANTS.md",
        "docs/CONTROL_PLANE_SECURITY.md",
        "docs/REVOCATION.md",
        "docs/SHIELD.md",
    ):
        if required_reference not in readme:
            fail(errors, f"README missing canonical reference: {required_reference}")

    if errors:
        for error in errors:
            print(f"ERROR: {error}", file=sys.stderr)
        print(f"\n{len(errors)} specification consistency error(s).", file=sys.stderr)
        return 1

    print("Freedom specification consistency checks passed.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
