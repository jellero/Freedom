# Freedom — Deterministic Encoding Profile

Status: **Freedom-DCBOR-1 frozen implementation profile**.

Canonical object shapes: [`freedom.cddl`](freedom.cddl).
Cryptographic purpose/domain constants: [`crypto-domains.txt`](crypto-domains.txt).
Executable vectors: [`vectors/dcbor-v1.json`](vectors/dcbor-v1.json).
Reference checker: [`../tools/check_vectors.py`](../tools/check_vectors.py).

This profile freezes the byte-level representation required by V1 implementations. A future security/interoperability review may introduce a **new profile/version**; it MUST NOT silently redefine `Freedom-DCBOR-1`.

## 1. Profile identifier

```text
Freedom-DCBOR-1
```

The base serialization is CBOR using the **Core Deterministic Encoding Requirements** of RFC 8949, with additional Freedom restrictions below.

## 2. Allowed V1 data model

Freedom-DCBOR-1 admits only:

```text
unsigned integers
negative integers
byte strings
UTF-8 text strings
arrays
maps with text-string keys
false
true
null
```

V1 protocol integers MUST fit the ordinary CBOR major-type 0/1 64-bit argument range.

Freedom-DCBOR-1 forbids:

```text
floating point
CBOR tags
undefined / other simple values
indefinite-length strings/arrays/maps
bignum tags
non-text map keys
```

If a future protocol object needs one of those types, it requires a new reviewed encoding profile/schema version.

## 3. Preferred/minimal representation

Encoders MUST use the shortest legal CBOR argument representation for integers and lengths.

Examples:

```text
23  -> 17
24  -> 1818
255 -> 18ff
256 -> 190100
```

A value encoded using a longer representation than required is non-canonical and MUST be rejected for signed/security-sensitive protocol objects.

Indefinite lengths are always rejected.

## 4. Map ordering

Every map key is a text string.

Map entries MUST be sorted by **bytewise lexicographic order of the deterministic CBOR encoding of each key**.

Example:

```text
{"z": 1, "aa": 2}
```

encodes as:

```text
a2 617a 01 626161 02
```

because encoded key `617a` sorts before `626161`.

Duplicate map keys are invalid and MUST be rejected before application semantics are evaluated.

## 5. UTF-8 semantics

Text strings are encoded as their exact valid UTF-8 byte sequence.

The encoder MUST NOT perform hidden Unicode normalization, case folding or whitespace rewriting before hashing/signing. If a field requires ASCII/token/NFC/case restrictions, that constraint belongs to the CDDL/field semantics and is validated before canonical encoding.

Visually similar strings with different Unicode scalar sequences are therefore different protocol values unless the field definition explicitly normalizes them before object creation.

## 6. Optional field semantics

Omission and explicit `null` are different values.

```text
field absent != field: null
```

CDDL decides whether omission, `null`, or both are legal. Implementations MUST NOT inject implicit defaults into the canonical byte representation unless the object specification explicitly defines that transformation.

## 7. Strict decode rule

For security-sensitive objects the receiving implementation MUST either use a strict Freedom-DCBOR-1 decoder or perform an equivalent check:

```text
decode
 -> validate allowed data model
 -> reject duplicate/unsorted keys
 -> reject non-minimal/indefinite/tag/float forms
 -> deterministic re-encode
 -> require exact byte equality
```

This prevents two byte strings from representing the same accepted signed object under different encodings.

## 8. Standalone signature preimage V1

For an object protected by a standalone signature, the object-specific signature/authentication fields are removed from the signed body first.

The exact preimage is:

```text
body_bytes = FreedomDCBOR1(object_without_signature_fields)

FreedomSigningInputV1 = FreedomDCBOR1([
    "FreedomSigningInput",
    1,
    network_id,
    fixed_domain,
    schema_version,
    body_bytes
])
```

Where:

- `network_id` is the object's network when present, otherwise the verifier's explicitly configured network context;
- `fixed_domain` MUST come from the `SIGN` class in `spec/crypto-domains.txt`;
- `schema_version` is the signed object's version/manifest version as defined by its schema;
- `body_bytes` is a CBOR byte string containing the already-canonical object body.

The cryptographic suite decides whether its signature primitive signs this preimage directly or a suite-defined digest of it. That choice is separate from byte canonicalization and MUST be frozen by the suite specification/test vectors.

A signature for one network/domain/version therefore cannot be accepted as another simply because object fields happen to match.

## 9. MAC / AEAD / HASH / KDF purposes

`spec/crypto-domains.txt` remains the source of truth for the fixed purpose constants.

This document does not collapse MAC/AEAD/HASH/KDF inputs into standalone signature semantics. Each subsystem binds the registered domain plus the session/pairwise/epoch/sequence context required by its state machine.

Unregistered ad-hoc purpose strings are forbidden for protocol/security boundaries.

## 10. Frozen vectors

`spec/vectors/dcbor-v1.json` contains exact bytes for:

- map ordering;
- `device-certificate`;
- `rendezvous-record`;
- `pairwise-recovery-anchor`;
- standalone signing preimages for the same security objects;
- negative representations that MUST be rejected.

The JSON notation uses:

```json
{"$bytes": "001122..."}
```

to represent a CBOR byte string in the source value.

`expected_cbor_hex` and `expected_preimage_hex` are normative vector outputs.

## 11. Vector extension rule

Every newly frozen security-critical object family MUST add at least:

1. one positive canonical object vector;
2. relevant optional/null boundary vectors;
3. one signature/MAC/AEAD/domain vector when cryptographically protected;
4. negative vectors for parse/canonicalization conditions specific to that object when applicable.

Changing existing expected bytes is a normative compatibility change and requires explicit human review plus a profile/schema version decision.

## 12. CI gate

The repository gate runs:

```text
python tools/check_vectors.py
```

It validates encoder output, strict decoding, signing-preimage bytes and negative rejection vectors without third-party dependencies.

A green vector check demonstrates deterministic agreement with the frozen reference vectors; it does **not** substitute for cryptographic review of the actual signature/KDF/AEAD suites.
