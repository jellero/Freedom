# Freedom Protocol schema

Status: **canonical schema draft**.

`spec/freedom.cddl` is the single source of truth for field names/object shapes that are frozen enough to be referenced normatively.

If an object is not present in the CDDL, its wire shape is **not frozen for public interoperability**.

Security semantics remain normative in `SECURITY_INVARIANTS.md`, `CONTROL_PLANE_SECURITY.md`, `REVOCATION.md`, `IDENTITY_MODEL.md`, `PROTOCOL.md` and subsystem docs.

If Markdown and CDDL disagree on a frozen field/object shape, CDDL wins and Markdown must be corrected. If code conflicts with a MUST/MUST NOT security invariant, the invariant wins.

## 1. Canonical encoding

Interoperability target: deterministic CBOR over CDDL.

Before public interoperability the exact deterministic-CBOR profile, numeric/string constraints and positive/negative vectors MUST be frozen.

Do not sign ad-hoc JSON, language-specific dumps or unordered map serialization.

## 2. Signed-object domain separation

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
- signing-domain registry changes are normative schema/security changes.

Signed-domain registry MUST include every signed CDDL object actually enabled by a protocol version, including identity/delegation/revocation/recovery, rendezvous, relay/provenance, release/policy/governance, payment/attestation/voucher and migration/upgrade objects.

Representative constants:

```text
FREEDOM/DEVICE_AUTHORIZATION_DELEGATION
FREEDOM/DEVICE_CERTIFICATE
FREEDOM/DEVICE_REVOCATION
FREEDOM/AUTHORIZATION_REVOCATION
FREEDOM/USER_RECOVERY_POLICY
FREEDOM/USER_ROOT_ROTATION
FREEDOM/RENDEZVOUS_RECORD
FREEDOM/RECOVERY_BEACON
FREEDOM/RELAY_DESCRIPTOR
FREEDOM/PROVENANCE_ATTESTATION
FREEDOM/HANDSHAKE_OFFER
FREEDOM/REKEY_INIT
FREEDOM/REKEY_COMMIT
FREEDOM/REKEY_ACK
FREEDOM/PAYMENT_ATTESTATION
FREEDOM/ENTITLEMENT_VOUCHER
FREEDOM/FREEDOM_RELEASE
FREEDOM/RELEASE_STATUS
FREEDOM/SECURITY_POLICY
FREEDOM/SIGNER_SET_TRANSITION
FREEDOM/GOVERNANCE_RECOVERY
FREEDOM/CONTRACT_UPGRADE
FREEDOM/CHAIN_MIGRATION
```

The final registry is frozen with test vectors.

## 3. AEAD associated-data domains

Encrypted frames/records also require type/context separation even when they are not separately signed.

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

Examples:

```text
CONTROL_FRAME != MEDIA_FRAME
RENDEZVOUS_PAYLOAD != RECOVERY_BEACON_PAYLOAD
PAIRWISE_BACKUP != SESSION_TRAFFIC
```

A ciphertext valid in one context must not be accepted as a different Freedom encrypted object simply because key bytes were accidentally reused. Keys SHOULD already be domain-separated by purpose; AEAD associated data is an additional binding, not a substitute for key separation.

## 4. Capability narrowing

```text
DeviceCertificate.capabilities
    subset-of
DeviceAuthorizationDelegation.capabilities

certificate.expires_after_height <= delegation.expires_after_height
certificate.root_epoch            == delegation.root_epoch
certificate.authorization_epoch   == delegation.authorization_epoch
```

Child authority cannot outlive or exceed parent authority.

## 5. Root recovery policy stability

For V1 `user-root-rotation` carries the current recovery-policy commitment.

A `NORMAL` rotation must preserve it. A `COMPROMISE_RECOVERY` transition must verify the precommitted independent recovery quorum and current root-control lineage.

Arbitrary recovery-policy mutation is not a frozen V1 operation.

## 6. Schema-change discipline

A security-relevant schema/domain change requires:

1. explicit human review;
2. version bump when parse/sign/AEAD semantics change;
3. positive/negative vectors;
4. downgrade/rollback analysis;
5. persistent-state migration rule/proof when relevant;
6. aligned normative documentation.

Codex/agents may propose changes but MUST NOT silently weaken/redefine canonical schema, signing domains, AEAD domains or security state machines just to satisfy implementation/tests.
