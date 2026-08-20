# Freedom Protocol schema

Status: **canonical schema draft with frozen V1 byte-encoding profile**.

`spec/freedom.cddl` is the single source of truth for field names/object shapes that are frozen enough to be referenced normatively.

`spec/ENCODING_PROFILE.md` freezes the byte-level `Freedom-DCBOR-1` representation and standalone signature-preimage envelope used by V1 implementations.

`spec/vectors/dcbor-v1.json` contains executable positive/negative byte vectors.

`spec/crypto-domains.txt` is the single source of truth for fixed cryptographic domain constants used by signatures, transcript/session authentication, AEAD associated-data contexts, hashes and KDF purposes.

If an object is not present in the CDDL, its wire shape is **not frozen for public interoperability**.

Security semantics remain normative in `SECURITY_INVARIANTS.md`, `CONTROL_PLANE_SECURITY.md`, `NETWORK_ANCHORS.md`, `REVOCATION.md`, `PAIRWISE_RECOVERY.md`, `IDENTITY_MODEL.md`, `PROTOCOL.md` and subsystem docs.

If Markdown and CDDL disagree on a frozen field/object shape, CDDL wins and Markdown must be corrected. If Markdown and `crypto-domains.txt` disagree on a domain constant, `crypto-domains.txt` wins. If code conflicts with a MUST/MUST NOT security invariant, the invariant wins.

## 1. Canonical encoding

V1 uses the frozen profile:

```text
Freedom-DCBOR-1
```

Full rules: [`ENCODING_PROFILE.md`](ENCODING_PROFILE.md).

The profile uses RFC 8949 Core Deterministic CBOR with additional Freedom restrictions:

- preferred/minimal integer and length encodings;
- definite lengths only;
- bytewise deterministic map-key ordering;
- text-string map keys only;
- duplicate-key rejection;
- no floats, tags, undefined or other simple values;
- exact UTF-8 bytes with no hidden normalization;
- strict canonical decode/re-encode equality for security objects.

Do not sign ad-hoc JSON, language-specific dumps or unordered map serialization.

Executable vectors live in [`vectors/dcbor-v1.json`](vectors/dcbor-v1.json) and are checked with:

```text
python tools/check_vectors.py
```

## 2. Signature-domain separation

Standalone signature input is no longer only conceptual. `Freedom-DCBOR-1` freezes the envelope:

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

Rules:

- `network_id` comes from the object when present or from the verifier's explicitly configured network context;
- fixed object type/domain is protocol-defined, never user display text;
- schema/object version is bound;
- a signature valid for one object type is invalid for another;
- Testnet/Mainnet/future network replay fails because network context differs;
- nested signed objects are verified using their own domains;
- domain-registry changes are normative schema/security changes.

The exact enabled constants are **not repeated normatively in Markdown**. They are listed in `spec/crypto-domains.txt` and frozen with vectors.

The cryptographic suite still defines the signature algorithm and whether it consumes this preimage directly or a suite-defined digest. That algorithm/suite choice is separate from deterministic byte canonicalization.

## 3. NetworkAnchor commitment

`network-anchor` is a frozen security-critical object family. Its signature domain is registered as `FREEDOM/NETWORK_ANCHOR`; authoritative semantics live in `docs/NETWORK_ANCHORS.md`.

For V1:

```text
unsigned_anchor_body = network-anchor with signatures removed
preimage = FreedomSigningInputV1(
    network_id,
    FREEDOM/NETWORK_ANCHOR,
    1,
    unsigned_anchor_body
)

NetworkAnchorCommitmentV1 = SHA-256(preimage)
```

This commitment intentionally excludes the `signatures` array while binding every other canonical body field. It is therefore stable across equivalent threshold-signature ordering/collection and can be pinned exactly by `BootstrapTrustAnchor.accepted_contract_or_controlplane_anchor`.

`spec/vectors/dcbor-v1.json` freezes a representative `network-anchor-v1` encoding and `network-anchor-signing` preimage/digest. Changing those expected V1 bytes silently is forbidden.

The concrete production signature algorithm/suite is **not** implied by `NetworkAnchorCommitmentV1`; it remains a separate explicit production cryptographic-suite decision.

## 4. MAC / transcript-authentication domains

Handshake/rekey messages that are authenticated inside an existing cryptographic context are not mislabeled as standalone signatures.

Their fixed `MAC`/transcript domains live in `spec/crypto-domains.txt`.

The authenticated transcript MUST bind the domain, network/session context, object version, epochs and canonical fields required by `PROTOCOL.md`.

## 5. AEAD associated-data domains

Encrypted frames/records require type/context separation even when they are not separately signed.

Conceptually AEAD associated data binds:

```text
Freedom protocol domain
network/session or pairwise context
fixed encrypted-object/frame type
object/frame version
key epoch
sequence/generation where applicable
non-secret routing/session hint where applicable
```

Examples remain semantically distinct:

```text
CONTROL_FRAME != MEDIA_FRAME
RENDEZVOUS_PAYLOAD != RECOVERY_BEACON_PAYLOAD
PAIRWISE_RECOVERY_BUNDLE != SESSION_TRAFFIC
```

A ciphertext valid in one context must not be accepted as another Freedom encrypted object simply because key bytes were accidentally reused. Purpose-separated keys are still required; AEAD associated data is an additional binding.

## 6. HASH/KDF purpose separation

Stable hash/KDF labels are also part of the registry.

Examples include rendezvous-slot derivation, pairwise backup IDs/state commitments, pairwise contact/rendezvous derivation, session control/media schedules and Shield hop keys.

Do not create a new `H("some string" || ...)` or KDF label in code without registering/versioning the purpose when it affects protocol interoperability or a security boundary.

## 7. Capability narrowing

```text
DeviceCertificate.capabilities
    subset-of
DeviceAuthorizationDelegation.capabilities

certificate.expires_after_height <= delegation.expires_after_height
certificate.root_epoch            == delegation.root_epoch
certificate.authorization_epoch   == delegation.authorization_epoch
```

Child authority cannot outlive or exceed parent authority.

## 8. User recovery-policy validity

For a `user-recovery-policy` to be valid:

```text
number of recovery_key_commitments >= 2
all commitments are distinct
1 <= threshold <= number of distinct recovery keys
```

For a profile claiming **independent root-compromise recovery**, production policy SHOULD require threshold >= 2 and recovery shares/custody domains that are operationally independent from the active RootRecoveryKey/device environment.

Multiple files/keys held by one compromised operator do not provide independent recovery merely because the CDDL contains multiple entries.

For V1 `user-root-rotation` carries the current recovery-policy commitment.

A `NORMAL` rotation preserves it. A `COMPROMISE_RECOVERY` transition verifies the precommitted independent recovery quorum and current root-control lineage.

Arbitrary recovery-policy mutation is not a frozen V1 operation.

## 9. Pairwise backup freshness

`pairwise-recovery-bundle` provides encrypted integrity-protected pairwise state.

Integrity alone does not prove that an untrusted backup source returned the newest bundle after all devices are lost.

The rollback-detectable profile uses `pairwise-recovery-anchor` according to `docs/PAIRWISE_RECOVERY.md`.

The anchor commits only to monotonic backup generation/hash/state commitment; it does not publish contacts or pairwise plaintext.

## 10. Vector discipline

The first frozen vector set covers representative security objects and canonicalization failures. It is a baseline, not permission to freeze future objects without vectors.

Every newly frozen security-critical object family MUST add the relevant positive/negative/domain fixtures before interoperability is claimed.

A security-relevant schema/domain/encoding change requires:

1. explicit human review;
2. version bump when parse/sign/MAC/AEAD semantics change;
3. positive/negative vectors;
4. downgrade/rollback analysis;
5. persistent-state migration rule/proof when relevant;
6. aligned normative documentation;
7. update to `spec/crypto-domains.txt` when a cryptographic purpose changes;
8. a new encoding profile identifier if existing `Freedom-DCBOR-1` bytes would change.

Codex/agents may propose changes but MUST NOT silently weaken/redefine canonical schema, cryptographic domains, deterministic bytes or security state machines merely to satisfy implementation/tests.
