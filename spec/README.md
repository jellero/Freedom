# Freedom Protocol schema

Status: **canonical schema draft**.

`spec/freedom.cddl` is the single source of truth for **object field names and object shapes** used by the Freedom Protocol specification.

Security semantics remain normative in:

1. `docs/SECURITY_INVARIANTS.md`
2. `docs/CONTROL_PLANE_SECURITY.md`
3. `docs/IDENTITY_MODEL.md`
4. `docs/PROTOCOL.md`
5. subsystem-specific documents.

If a Markdown snippet disagrees with `spec/freedom.cddl` on a field name or object shape, the CDDL schema wins and the Markdown must be corrected. If an implementation disagrees with a MUST/MUST NOT security invariant, the security invariant wins.

## Canonical encoding

The interoperability target is deterministic CBOR over the CDDL schema. The exact deterministic-CBOR profile, integer/string constraints and test vectors must be frozen before public interoperability.

Implementations MUST NOT sign ad-hoc JSON serialization, language-specific object dumps or unordered maps.

## Domain-separated signing input

Every signed object uses an explicit signing domain. Conceptually:

```text
FreedomSigningInput =
    "Freedom" || 0x00 ||
    network_id || 0x00 ||
    object_type || 0x00 ||
    schema_version || 0x00 ||
    canonical_object_bytes_without_signatures
```

The corresponding digest is computed with the suite-approved cryptographic hash.

Requirements:

- `network_id` is always bound where the object can cross networks;
- `object_type` is a fixed protocol constant, never a user-controlled display string;
- schema/object version is bound;
- signatures cannot be replayed as a different Freedom object type;
- signatures cannot be replayed between Testnet/Mainnet or future network domains;
- nested signatures use the domain of the nested object being authenticated.

Recommended fixed object-type domains include:

```text
FREEDOM/DEVICE_AUTHORIZATION_DELEGATION
FREEDOM/DEVICE_CERTIFICATE
FREEDOM/DEVICE_REVOCATION
FREEDOM/AUTHORIZATION_REVOCATION
FREEDOM/USER_ROOT_ROTATION
FREEDOM/RENDEZVOUS_RECORD
FREEDOM/RECOVERY_BEACON
FREEDOM/RELAY_DESCRIPTOR
FREEDOM/PROVENANCE_ATTESTATION
FREEDOM/HANDSHAKE_OFFER
FREEDOM/REKEY_INIT
FREEDOM/REKEY_COMMIT
FREEDOM/REKEY_ACK
FREEDOM/FREEDOM_RELEASE
FREEDOM/RELEASE_STATUS
FREEDOM/SECURITY_POLICY
FREEDOM/SIGNER_SET_TRANSITION
FREEDOM/CONTRACT_UPGRADE
FREEDOM/CHAIN_MIGRATION
```

The final registry of object-type constants must be frozen with test vectors.

## Capability narrowing

Delegation/certificate chains are monotonic in privilege:

```text
DeviceCertificate.capabilities
    subset-of
DeviceAuthorizationDelegation.capabilities
```

A child object MUST NOT outlive or exceed its parent authority:

```text
certificate.expires_after_height <= delegation.expires_after_height
certificate.root_epoch            == delegation.root_epoch
certificate.authorization_epoch   == delegation.authorization_epoch
```

Equivalent subset/expiry rules apply to future scoped delegations.

## Schema-change discipline

A security-relevant schema change requires:

1. human review of the normative change;
2. version bump when parsing or signing semantics change;
3. updated positive/negative test vectors;
4. downgrade/rollback analysis;
5. migration rule if persistent state changes;
6. corresponding documentation update.

Codex/agents may propose schema changes but MUST NOT silently weaken or redefine the canonical schema merely to satisfy an implementation or failing test.
