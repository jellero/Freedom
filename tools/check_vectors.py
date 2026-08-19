#!/usr/bin/env python3
"""Validate Freedom-DCBOR-1 and domain-separated signing test vectors."""

from __future__ import annotations

import hashlib
import json
import sys
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "tools"))

from canonical_cbor import CBORError, decode_strict, encode, signing_preimage  # noqa: E402


def materialize(value: Any) -> Any:
    if isinstance(value, list):
        return [materialize(item) for item in value]
    if isinstance(value, dict):
        if set(value) == {"$bytes"}:
            return bytes.fromhex(value["$bytes"])
        return {key: materialize(item) for key, item in value.items()}
    return value


def main() -> int:
    vector_path = ROOT / "spec" / "vectors" / "dcbor-v1.json"
    data = json.loads(vector_path.read_text(encoding="utf-8"))
    errors: list[str] = []

    if data.get("profile") != "Freedom-DCBOR-1":
        errors.append("unexpected/missing profile identifier")

    for vector in data.get("encoding_vectors", []):
        name = vector["name"]
        value = materialize(vector["value"])
        expected = bytes.fromhex(vector["expected_cbor_hex"])
        actual = encode(value)
        if actual != expected:
            errors.append(
                f"{name}: encoder mismatch\n expected={expected.hex()}\n actual  ={actual.hex()}"
            )
            continue
        try:
            decoded = decode_strict(expected)
        except CBORError as exc:
            errors.append(f"{name}: strict decoder rejected positive vector: {exc}")
            continue
        if decoded != value:
            errors.append(f"{name}: decoded value mismatch")

    for vector in data.get("signing_preimage_vectors", []):
        name = vector["name"]
        body = materialize(vector["body"])
        actual = signing_preimage(
            network_id=vector["network_id"],
            domain=vector["domain"],
            schema_version=vector["schema_version"],
            body=body,
        )
        expected = bytes.fromhex(vector["expected_preimage_hex"])
        if actual != expected:
            errors.append(f"{name}: signing preimage mismatch")
        digest = hashlib.sha256(actual).hexdigest()
        if digest != vector["expected_sha256"]:
            errors.append(
                f"{name}: SHA-256 evidence mismatch expected={vector['expected_sha256']} actual={digest}"
            )

    for vector in data.get("negative_vectors", []):
        name = vector["name"]
        raw = bytes.fromhex(vector["cbor_hex"])
        try:
            decode_strict(raw)
        except CBORError as exc:
            marker = vector["error_contains"]
            if marker not in str(exc):
                errors.append(
                    f"{name}: rejected for unexpected reason: {exc!s} (wanted {marker!r})"
                )
        else:
            errors.append(f"{name}: invalid/non-canonical CBOR was accepted")

    if errors:
        for error in errors:
            print(f"ERROR: {error}", file=sys.stderr)
        print(f"\n{len(errors)} vector error(s).", file=sys.stderr)
        return 1

    print(
        f"Freedom vectors passed: "
        f"{len(data.get('encoding_vectors', []))} encoding, "
        f"{len(data.get('signing_preimage_vectors', []))} signing, "
        f"{len(data.get('negative_vectors', []))} negative."
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
