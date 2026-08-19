# Freedom — Security & Trust Invariants

Status: **canonical / normative design rules**.

Queste proprietà sono MUST/MUST NOT per una implementazione Freedom compatibile.

Canonical schema: [`../spec/freedom.cddl`](../spec/freedom.cddl).
Canonical cryptographic domains: [`../spec/crypto-domains.txt`](../spec/crypto-domains.txt).
Canonical encoding/signing: [`../spec/README.md`](../spec/README.md).
Control-plane: [`CONTROL_PLANE_SECURITY.md`](CONTROL_PLANE_SECURITY.md).
Revocation/freshness: [`REVOCATION.md`](REVOCATION.md).
Pairwise recovery: [`PAIRWISE_RECOVERY.md`](PAIRWISE_RECOVERY.md).

## 1. Trust separation

```text
RootRecoveryKey                 -> cold user recovery
UserRecoveryPolicy              -> independent compromise-recovery authority
RootIdentity                    -> ownership/root epoch
RootControlCommitment           -> opaque recovery-lineage control-plane handle
DeviceAuthorizationKey          -> delegated device authorization
DeviceCertificate               -> offline DeviceKey authorization
DeviceKey                       -> operational endpoint authentication
DeviceRecordCommitment          -> opaque device-state handle
DeviceControlKey                -> scoped device-record rotation/revocation
PairwiseContactAlias            -> relationship identity
PairRendezvousSecret            -> pairwise rendezvous authority
RecoveryStateKey                -> encrypted pairwise-backup authority
TransportToken                  -> temporary route/circuit identity
Session keys                    -> ephemeral E2EE
VerifiedControlPlaneCheckpoint  -> verified control-plane state root
```

Nessun elemento viene automaticamente riutilizzato come un altro.

## 2. Primitive vietate

Freedom Protocol MUST NOT introdurre:

- global user/device identifier richiesto dal network layer;
- RootIdentity/RootControlCommitment/DeviceRecordCommitment come routing IDs;
- messages/files/audio/video on-chain;
- mailbox on-chain;
- persistent relay inbox;
- automatic offline delivery queue/store-and-forward nel protocollo base;
- public readable social graph;
- public RootIdentity→device mapping come requirement V1;
- mandatory central delivery server;
- mandatory single RPC/provider/relay/egress;
- master decryption key;
- single production credential con unilateral security-core authority;
- single Full Access key che può sostituire silenziosamente il production security core;
- `transaction hash == success`;
- `RPC not found == non-revoked`;
- silent downgrade da strict/Shield policy;
- temporary active state che cresce senza reclaim;
- ad-hoc/undomain-separated signatures, MAC, AEAD contexts, protocol hashes o KDF labels per security objects;
- claim `LATEST_VERIFIED_BACKUP` quando la freshness di un pairwise backup non è dimostrabile.

## 3. Synchronous Communication

```text
active authenticated session -> transmit now
no active authenticated session -> fail/discard now
```

No automatic future delivery.

## 4. Canonical objects / cryptographic domains

Object fields/shapes congelati vengono da `spec/freedom.cddl`.

I cryptographic purpose/domain constants vengono da `spec/crypto-domains.txt`.

Ogni signed security object lega almeno:

```text
Freedom protocol domain
network_id where applicable
fixed object_type
schema/object version
canonical deterministic bytes
```

Handshake/rekey authentication, AEAD associated data, protocol hashes e KDF purpose labels usano domain constants differenti e registrati. No JSON dump/language object serialization ad-hoc.

Child authority cannot exceed parent:

```text
certificate capabilities subset-of delegation capabilities
certificate expiry <= delegation expiry
same root_epoch
authorization_epoch matches parent
```

## 5. Endpoint device authorization

V1 peer authentication:

```text
expected RootIdentity/contact
 -> DeviceAuthorizationDelegation
 -> DeviceCertificate
 -> DeviceKey possession
 -> revocation/freshness proof
```

The control-plane need not publish which RootIdentity owns an opaque DeviceRecord.

Device-count commercial enforcement V1 is product/service policy, not security/interoperability invariant. Future hard enforcement may use privacy-preserving credentials/nullifiers/ZK after separate review.

## 6. DeviceControlKey

DeviceControlKey is scoped to one opaque device record. It cannot authenticate conversations or authorize unrelated devices.

Revocation may use the scoped control key or a canonical recovery/successor proof when that key is unavailable.

## 7. Revocation

Revocation/non-revocation semantics are normative in `REVOCATION.md`.

Rules:

- device-key revocation floors are monotonic;
- authorization-epoch revocation floors are monotonic;
- root epochs never roll back;
- `RPC not found` is not proof;
- stale revocation state is explicit, never silently `VERIFIED`.

## 8. Control-plane authenticity

```text
NetworkAnchor
 -> VerifiedControlPlaneCheckpoint
 -> state root
 -> inclusion/non-inclusion proof
 -> canonical object
```

A raw RPC response is not authoritative security state.

## 9. Fresh-install freshness

A fresh client uses `BootstrapFreshnessFloor` from its current verifier/release.

It MUST reject state below the embedded checkpoint/signer/policy floor.

Limit: an authentic but itself-obsolete verifier obtained only from attacker-controlled channels cannot know that newer state exists. Freshness of the verifier itself requires an independent bootstrap/update channel.

## 10. Verified mutation

```text
submit
 -> finality proof
 -> execution success
 -> resulting-state proof
 -> exact expected transition
 -> local success
```

Tx hash alone never produces `ACTIVE`, `PAID`, `REVOKED`, `CURRENT` or `VERIFIED`.

## 11. Bounded active state

TTL alone is insufficient.

Temporary state requires concrete overwrite/ring/prune/lease/reclaim and a derivable active-state upper bound.

Chain archival history may remain observable; Freedom does not claim deletion of blockchain history.

## 12. Rendezvous write authorization

For each pairwise direction/epoch, `PairRendezvousSecret` derives a fresh write keypair off-chain.

Public slot:

```text
slot_id = H("Freedom/RendezvousSlot" || network_id || write_public_key)
```

The concrete hash purpose is fixed by `spec/crypto-domains.txt`.

The control-plane verifies slot binding, write signature, monotonic generation and bounds. Observing a slot/public key does not grant overwrite authority.

## 13. Verified time

Certificate/policy/release/recovery freshness uses verified checkpoint time/height/epoch plus monotonic local time. Wall clock alone cannot reactivate old state or roll back highest-seen state.

## 14. Root compromise requires independent precommitment

A stolen sole RootRecoveryKey makes owner and attacker cryptographically indistinguishable.

Freedom only claims `ROOT_COMPROMISE` recovery when a `UserRecoveryPolicy` with independent recovery authority was committed **before** the incident.

A valid recovery policy has distinct recovery-key commitments and:

```text
1 <= threshold <= number of distinct recovery keys
```

A production profile claiming independent compromise recovery SHOULD use threshold >= 2 with recovery shares/custody domains separated from the active root/device environment. Multiple key files under one compromised operator are not independent recovery.

Without independent precommitment, Freedom can recover ordinary device loss/backup loss but cannot prove which holder of the stolen root secret is legitimate.

## 15. Opaque root recovery lineage

Users enabling control-plane compromise recovery use an opaque `root_control_commitment`.

It is not a network/contact ID, but it can correlate recovery-lineage events on the public control-plane. This metadata trade-off is explicit.

## 16. Sticky UserRecoveryPolicy

V1 recovery policy is sticky:

```text
NORMAL root rotation
 -> same root_control_commitment
 -> same recovery_policy_commitment
```

The current RootRecoveryKey alone MUST NOT remove or replace the recovery policy.

V1 does not support arbitrary policy mutation. A future policy-transition protocol must be separately specified and require at least the current independent recovery authority.

## 17. Compromise-recovery race semantics

A valid `COMPROMISE_RECOVERY` request from the independent recovery quorum may recover the latest current root state of the same `root_control_commitment` lineage, including after an attacker has already made a normal rotation using a stolen root.

Once accepted:

```text
RECOVERY_PENDING
```

until activation:

- normal root rotations are blocked;
- recovery-policy mutation is blocked;
- high-risk new device authorization may be blocked/pending by policy;
- current/compromised root cannot cancel the request alone;
- cancellation/replacement requires the independent recovery authority.

This prevents a compromised root from escaping its precommitted recovery policy by racing normal rotations.

## 18. Pairwise recovery / backup freshness

Pairwise state is not reconstructed from public social-graph data.

Recovery paths:

```text
surviving-device authenticated transfer
or
encrypted PairwiseRecoveryBundle from user-chosen untrusted storage
```

**Integrity is not freshness.** After total loss of all devices, an untrusted mirror can serve an older but valid encrypted bundle unless the client has an independent monotonic freshness source.

The rollback-detectable profile therefore uses `PairwiseRecoveryAnchor`:

```text
root_control_commitment
anchor_epoch
latest_backup_generation
latest_bundle_hash
latest_state_commitment
recovery_key_epoch
```

The anchor contains no contact list or pairwise plaintext, but its updates can correlate backup activity with the opaque recovery lineage. This privacy trade-off is explicit.

With anchor enabled, a candidate backup MUST match the latest verified anchor or fail closed. Without surviving device/anchor, the client may verify bundle integrity but MUST NOT claim the bundle is the latest state.

After backup restore, re-authenticate peers and rotate/re-derive future rendezvous/recovery/session state. An old backup must not remain indefinite future authority.

If no device/backup survives, ownership may recover but contacts require re-bootstrap.

Full lifecycle: `PAIRWISE_RECOVERY.md`.

## 19. First-contact substitution

Cryptography authenticates the descriptor received, not the human name “Bob”.

UI distinguishes:

```text
BOOTSTRAP_UNVERIFIED
CONTACT_VERIFIED
```

Safety code/fingerprint/out-of-band verification supplies stronger human assurance.

## 20. Pairwise privacy claim

Pairwise aliases/rendezvous reduce infrastructure correlation. Freedom does not claim unlinkability against colluding contacts that compare shared root/certificate material unless dedicated anonymous/pairwise credentials are implemented.

## 21. Handshake anti-downgrade

Transcript binds both peers' offer sets, expected relationship, certificate/delegation proof, epochs, selected version/suite/transport semantics, ephemeral keys, nonces and session ID.

Offer stripping below local policy fails.

## 22. Forward secrecy / rekey

Every session uses fresh ephemeral key exchange and forward secrecy between completed sessions.

Long sessions use bounded traffic-key lifetime and canonical `RekeyInit / RekeyCommit / RekeyAck` with:

- exact `N -> N+1` epochs;
- deterministic simultaneous-init resolution;
- new-key confirmation;
- old-send-key erase after confirmed transition;
- bounded old-receive grace for in-flight traffic only;
- replay/duplicate handling;
- route switch independent from key epoch;
- termination on mismatch/timeout before lifetime exhaustion.

No silent split-brain epochs.

## 23. Transport semantics

Adapters declare reliable ordered stream and/or unreliable datagram semantics.

Handshake/control/text/rekey require reliable ordering or an explicit reliability layer. Media uses separate sequence/replay spaces so media loss does not block control/text.

## 24. Shield / relay provenance

`N relay IDs != N independent operators`.

Self-declared metadata is not diversity proof. Provenance attestations are scoped/expiring signed claims; multiple attestations from one issuer/custody domain do not automatically imply independent observers.

`SHIELDED` requires real circuit setup, independent per-hop keys, layered forwarding, current policy satisfaction and no silent direct fallback.

## 25. Governance — precise no-super-admin claim

Production minimum:

```text
ReleaseAuthorization   >= 3-of-5
ReleaseRevocation      >= 3-of-5
CriticalSecurityPolicy >= 3-of-5
ContractUpgrade        >= 3-of-5 + timelock
GovernanceRootRotation >= 3-of-5 + recovery
```

This means **no single production credential** is unilateral.

It does not eliminate quorum collusion. Custody/operator domains should be separated and auditable. If one actor controls enough signer credentials for quorum, Freedom must not claim absence of a single administrative actor merely because multiple key files exist.

## 26. Signer / contract anti-rollback

Signer-set transitions are previous-threshold-authorized, next-set-accepted and monotonic. Highest-seen state prevents old signer/policy/status reactivation.

Contract security core is immutable or threshold/timelocked/code-hash-pinned. Single Full Access production upgrade authority is forbidden.

## 27. Chain migration

Migration requires `ChainMigrationManifest + StateMigrationProof`.

The proof binds source finalized state/export root, migration program hash/input and target imported state root. Governance chooses the migration rule; it cannot simply sign arbitrary replacement state and call it migration.

## 28. Release / first install

Install verification requires exact artifact hash, threshold release authorization, Android signer lineage, verified ReleaseStatus/SecurityPolicy, anti-rollback, BootstrapTrustAnchor and BootstrapFreshnessFloor.

Byte source is not trust.

## 29. Payment / product quota boundary

Payment→entitlement can use one-time voucher/blind credential + nullifier to reduce linkage; timing correlation may remain.

Contact/device V1 quotas and Relay Contributor bonuses are product/service policy, not protocol security/interoperability invariants. Business viability must not depend exclusively on local quotas that a modified open-source client can bypass.

## 30. Security labels

`VERIFIED`, `E2EE`, `ACTIVE`, `REVOKED`, `SHIELDED`, `LATEST_VERIFIED_BACKUP` derive from implemented verification/state.

`SUSPECTED` is a network inference, not proof of censorship/surveillance.

## 31. Normative-spec human gate

Agents may propose changes to security invariants, control-plane/revocation/pairwise-recovery/identity/protocol docs and canonical schema/domain registry, but they MUST NOT autonomously weaken MUST/MUST NOT rules, trust assumptions, cryptographic domains, signed schemas or security state machines simply to make tests pass.

Such changes require explicit human review before becoming canonical/main-ready.

## 32. Public interoperability gates

Before public interoperability:

- deterministic encoding vectors;
- complete `spec/crypto-domains.txt` registry vectors including cross-object/network negative cases;
- delegation scope/expiry negative vectors;
- recovery-policy threshold/distinct-key/custody validation;
- revocation/non-revocation/freshness vectors;
- fresh-install stale-checkpoint tests;
- rendezvous overwrite/front-run tests;
- handshake offer-stripping tests;
- complete rekey loss/race/duplicate/route-switch tests;
- stream/datagram semantics tests;
- control-plane finality/state-proof tests;
- storage convergence tests;
- sticky recovery-policy/race/timelock tests;
- pairwise backup anchor rollback/mismatch/post-restore rotation tests;
- signer quorum/custody tests/documentation;
- contract/migration rollback proof tests;
- release/bootstrap freshness tests;
- relay provenance/Sybil/Shield tests;
- independent cryptographic/security review.
