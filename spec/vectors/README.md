# Freedom protocol vectors

Status: **normative byte-level fixtures for frozen profiles/objects**.

Encoding profile: [`../ENCODING_PROFILE.md`](../ENCODING_PROFILE.md).
Canonical schema: [`../freedom.cddl`](../freedom.cddl).
Crypto domains: [`../crypto-domains.txt`](../crypto-domains.txt).

## Current vector set

`dcbor-v1.json` freezes the first `Freedom-DCBOR-1` byte vectors.

It contains:

- canonical object encodings;
- standalone signature-preimage bytes;
- SHA-256 evidence digests for easy cross-language comparison;
- malformed/non-canonical encodings that strict decoders MUST reject.

JSON source values use:

```json
{"$bytes": "001122..."}
```

for CBOR byte strings.

`expected_cbor_hex` and `expected_preimage_hex` are the normative outputs. Implementations in Kotlin/Rust/Go/etc. should ingest the same fixture rather than transcribing expected bytes into separate language-specific files.

Run:

```bash
python tools/check_vectors.py
```

The Python codec is intentionally dependency-free and minimal. It is a reference/vector oracle, not the production serializer requirement.

Every newly frozen security object should extend these fixtures rather than creating a second vector format.
