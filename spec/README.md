# Freedom Protocol schema

Status: **canonical schema draft**.

`spec/freedom.cddl` is the single source of truth for field names/object shapes that are frozen enough to be referenced normatively.

`spec/crypto-domains.txt` is the single source of truth for fixed cryptographic domain constants used by signatures, transcript/session authentication, AEAD associated-data contexts, hashes and KDF purposes.

If an object is not present in the CDDL, its wire shape is **not frozen for public interoperability**.

Security semantics remain normative in `SECURITY_INVARIANTS.md`, `CONTROL_PLANE_SECURITY.md`, `REVOCATION.md`, `PAIRWISE_RECOVERY.md`, `IDENTITY_MODEL.md`, `PROTOCOL.md` and subsystem docs.

If Markdown and CDDL disagree on a frozen field/object shape, CDDL wins and Markdown must be corrected. If Markdown and `crypto-domains.txt` disagree on a domain constant, `crypto-domains.txt` wins. If code conflicts with a MUST/MUST NOT security invariant, the invariant wins.

## 1. Canonical encoding

Interoperability target: deterministic CBOR over CDDL.

Before public interoperability the exact deterministic-CBOR profile, numeric/string constraints and positive/negative vectors MUST be frozen.

Do not sign ad-hoc JSON, language-specific dumps or unordered map serialization.

## 2. Signature-domain separation

Conceptual signing input:

```text
FreedomSigningInput =
    "Freedom" || 0x00 ||
    network_id || 0x00 ||
    fixed_object_type || 0x00 ||
    schema_version || 0x00 ||
    canonical_object_bytes_without_signature_fields
```

The suite-approved cryptographic hash/signature algorithm operates on this domain-separated input.

Rules:

- `network_id` comes from the object when present or from the verifier's explicitly configured network context;
- fixed object type is protocol-defined, never user display text;
- schema/object version is bound;
- a signature valid for one object type is invalid for another;
- Testnet/Mainnet/future network replay fails because network context differs;
- nested signed objects are verified using their own domains;
- domain-registry changes are normative schema/security changes.

The exact enabled constants are **not repeated normatively in Markdown**. They are listed in `spec/crypto-domains.txt` and frozen with vectors.

## 3. MAC / transcript-authentication domains

Handshake/rekey messages that are authenticated inside an existing cryptographic context are not mislabeled as standalone signatures.

Their fixed `MAC`/transcript domains live in `spec/crypto-domains.txt`.

The authenticated transcript MUST bind the domain, network/session context, object version, epochs and canonical fields required by `PROTOCOL.md`.

## 4. AEAD associated-data domains

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

## 5. HASH/KDF purpose separation

Stable hash/KDF labels are also part of the registry.

Examples include rendezvous-slot derivation, pairwise backup IDs/state commitments, pairwise contact/rendezvous derivation, session control/media schedules and Shield hop keys.

Do not create a new `H("some string" || ...)` or KDF label in code without registering/versioning the purpose when it affects protocol interoperability or a security boundary.

## 6. Capability narrowing

```text
DeviceCertificate.capabilities
    subset-of
DeviceAuthorizationDelegation.capabilities

certificate.expires_after_height <= delegation.expires_after_height
certificate.root_epoch            == delegation.root_epoch
certificate.authorization_epoch   == delegation.authorization_epoch
```

Child authority cannot outlive or exceed parent authority.

## 7. User recovery-policy validity

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

## 8. Pairwise backup freshness

`pairwise-recovery-bundle` provides encrypted integrity-protected pairwise state.

Integrity alone does not prove that an untrusted backup source returned the newest bundle after all devices are lost.

The rollback-detectable profile uses `pairwise-recovery-anchor` according to `docs/PAIRWISE_RECOVERY.md`.

The anchor commits only to monotonic backup generation/hash/state commitment; it does not publish contacts or pairwise plaintext.

## 9. Schema-change discipline

A security-relevant schema/domain change requires:

1. explicit human review;
2. version bump when parse/sign/MAC/AEAD semantics change;
3. positive/negative vectors;
4. downgrade/rollback analysis;
5. persistent-state migration rule/proof when relevant;
6. aligned normative documentation;
7. update to `spec/crypto-domains.txt` when a cryptographic purpose changes.

Codex/agents may propose changes but MUST NOT silently weaken/redefine canonical schema, cryptographic domains or security state machines merely to satisfy implementation/tests.
