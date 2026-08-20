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
    "spec/crypto-domains.txt",
    "docs/SECURITY_INVARIANTS.md",
    "docs/CONTROL_PLANE_SECURITY.md",
    "docs/NETWORK_ANCHORS.md",
    "docs/REVOCATION.md",
    "docs/PAIRWISE_RECOVERY.md",
    "docs/IDENTITY_MODEL.md",
    "docs/PROTOCOL.md",
    "docs/THREAT_MODEL.md",
    "docs/ADVANCED_DEVELOPMENT.md",
    "docs/REPOSITORY_GOVERNANCE.md",
    "sim/README.md",
    "sim/scenarios/relay-block-nat-rebind.yaml",
    "sim/scenarios/pairwise-backup-rollback.yaml",
    "sim/scenarios/network-anchor-rollback.yaml",
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
    "pairwise-recovery-anchor",
    "relay-descriptor",
    "provenance-attestation",
    "handshake-offer",
    "rekey-init",
    "rekey-commit",
    "rekey-ack",
    "encrypted-control-frame",
    "encrypted-media-frame",
    "network-anchor",
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

REQUIRED_CRYPTO_DOMAINS = [
    "SIGN FREEDOM/DEVICE_AUTHORIZATION_DELEGATION",
    "SIGN FREEDOM/DEVICE_CERTIFICATE",
    "SIGN FREEDOM/DEVICE_REVOCATION",
    "SIGN FREEDOM/AUTHORIZATION_REVOCATION",
    "SIGN FREEDOM/USER_RECOVERY_POLICY",
    "SIGN FREEDOM/USER_ROOT_ROTATION",
    "SIGN FREEDOM/RENDEZVOUS_RECORD",
    "SIGN FREEDOM/RECOVERY_BEACON",
    "SIGN FREEDOM/RELAY_DESCRIPTOR",
    "SIGN FREEDOM/FREEDOM_RELEASE",
    "SIGN FREEDOM/RELEASE_STATUS",
    "SIGN FREEDOM/SECURITY_POLICY",
    "SIGN FREEDOM/SIGNER_SET_TRANSITION",
    "SIGN FREEDOM/CONTRACT_UPGRADE",
    "SIGN FREEDOM/CHAIN_MIGRATION",
    "SIGN FREEDOM/PAIRWISE_RECOVERY_ANCHOR",
    "SIGN FREEDOM/NETWORK_ANCHOR",
    "MAC FREEDOM/HANDSHAKE_TRANSCRIPT",
    "MAC FREEDOM/REKEY_INIT",
    "MAC FREEDOM/REKEY_COMMIT",
    "MAC FREEDOM/REKEY_ACK",
    "AEAD FREEDOM/CONTROL_FRAME",
    "AEAD FREEDOM/MEDIA_FRAME",
    "AEAD FREEDOM/RENDEZVOUS_PAYLOAD",
    "AEAD FREEDOM/RECOVERY_BEACON_PAYLOAD",
    "AEAD FREEDOM/PAIRWISE_RECOVERY_BUNDLE",
    "HASH FREEDOM/RENDEZVOUS_SLOT",
    "HASH FREEDOM/PAIRWISE_RECOVERY_BUNDLE_ID",
    "KDF FREEDOM/PAIR_RENDEZVOUS_SECRET",
    "KDF FREEDOM/RENDEZVOUS_WRITE_KEY",
    "KDF FREEDOM/SESSION_TRAFFIC",
]

NORMATIVE_DOCS_REQUIRING_SCHEMA_LINK = [
    "docs/SECURITY_INVARIANTS.md",
    "docs/CONTROL_PLANE_SECURITY.md",
    "docs/NETWORK_ANCHORS.md",
    "docs/REVOCATION.md",
    "docs/PAIRWISE_RECOVERY.md",
    "docs/IDENTITY_MODEL.md",
    "docs/PROTOCOL.md",
]

NORMATIVE_DOCS_REQUIRING_DOMAIN_LINK = [
    "docs/SECURITY_INVARIANTS.md",
    "docs/CONTROL_PLANE_SECURITY.md",
    "docs/NETWORK_ANCHORS.md",
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

    domains_text = read("spec/crypto-domains.txt")
    domain_lines = {
        line.strip()
        for line in domains_text.splitlines()
        if line.strip() and not line.lstrip().startswith("#")
    }
    for domain in REQUIRED_CRYPTO_DOMAINS:
        if domain not in domain_lines:
            fail(errors, f"required crypto domain missing: {domain}")

    allowed_classes = {"SIGN", "MAC", "AEAD", "HASH", "KDF"}
    seen_domain_names: dict[str, str] = {}
    for line in sorted(domain_lines):
        parts = line.split()
        if len(parts) != 2:
            fail(errors, f"invalid crypto-domain registry line: {line!r}")
            continue
        domain_class, domain_name = parts
        if domain_class not in allowed_classes:
            fail(errors, f"invalid crypto-domain class {domain_class!r}: {line!r}")
        if not domain_name.startswith("FREEDOM/"):
            fail(errors, f"crypto-domain must start with FREEDOM/: {line!r}")
        previous = seen_domain_names.get(domain_name)
        if previous is not None and previous != domain_class:
            fail(errors, f"same crypto domain reused across classes: {domain_name} ({previous}, {domain_class})")
        seen_domain_names[domain_name] = domain_class

    for rel in NORMATIVE_DOCS_REQUIRING_SCHEMA_LINK:
        text = read(rel)
        if "spec/freedom.cddl" not in text and "../spec/freedom.cddl" not in text:
            fail(errors, f"normative document does not link canonical CDDL: {rel}")

    for rel in NORMATIVE_DOCS_REQUIRING_DOMAIN_LINK:
        text = read(rel)
        if "spec/crypto-domains.txt" not in text and "../spec/crypto-domains.txt" not in text:
            fail(errors, f"normative document does not link crypto domain registry: {rel}")

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

    # Pairwise recovery semantics must preserve the integrity-vs-freshness distinction.
    pairwise = read("docs/PAIRWISE_RECOVERY.md")
    for required_phrase in (
        "integrity != freshness",
        "PairwiseRecoveryAnchor",
        "PAIRWISE_BACKUP_ROLLBACK_OR_MISMATCH",
    ):
        if required_phrase not in pairwise:
            fail(errors, f"PAIRWISE_RECOVERY.md missing required semantic marker: {required_phrase}")

    # Keep the first simulator fixtures aligned with the security semantics they were
    # introduced to exercise, without requiring a YAML dependency in this drift checker.
    pairwise_scenario = read("sim/scenarios/pairwise-backup-rollback.yaml")
    for required_marker in (
        "PAIRWISE_BACKUP_ROLLBACK_OR_MISMATCH",
        "future_rendezvous_state_rotated",
        "old_backup_not_future_authority",
    ):
        if required_marker not in pairwise_scenario:
            fail(errors, f"pairwise simulator fixture missing marker: {required_marker}")

    network_anchor_doc = read("docs/NETWORK_ANCHORS.md")
    for required_marker in (
        "NetworkAnchorCommitmentV1",
        "NETWORK_ANCHOR",
        "governance authorization never substitutes chain consensus",
        "NEAR-NEP25-PRE-SPICE-BORSH-V1",
    ):
        if required_marker not in network_anchor_doc:
            fail(errors, f"NETWORK_ANCHORS.md missing semantic marker: {required_marker}")

    network_anchor_scenario = read("sim/scenarios/network-anchor-rollback.yaml")
    for required_marker in (
        "NETWORK_ANCHOR_INVALID",
        "CONTROL_PLANE_PROOF_INVALID",
        "GOVERNANCE_TRANSITION_INVALID",
        "CONTROL_PLANE_ROLLBACK",
        "consensus_continuity_valid: false",
    ):
        if required_marker not in network_anchor_scenario:
            fail(errors, f"NetworkAnchor simulator fixture missing marker: {required_marker}")

    # SVGs are source-controlled documentation and should remain XML-well-formed.
    for svg in (ROOT / "docs" / "assets").glob("*.svg"):
        try:
            ET.parse(svg)
        except ET.ParseError as exc:
            fail(errors, f"invalid SVG/XML {svg.relative_to(ROOT)}: {exc}")

    # Ensure public README points at canonical public documents without internal lab details.
    for required_reference in (
        "spec/freedom.cddl",
        "spec/crypto-domains.txt",
        "docs/SECURITY_INVARIANTS.md",
        "docs/CONTROL_PLANE_SECURITY.md",
        "docs/NETWORK_ANCHORS.md",
        "docs/REVOCATION.md",
        "docs/PAIRWISE_RECOVERY.md",
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
