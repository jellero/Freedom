# Freedom — Protocol Specification

Status: **canonical design draft**.

Normative security: [`SECURITY_INVARIANTS.md`](SECURITY_INVARIANTS.md).
Identity: [`IDENTITY_MODEL.md`](IDENTITY_MODEL.md).
Control-plane: [`CONTROL_PLANE_SECURITY.md`](CONTROL_PLANE_SECURITY.md).
NetworkAnchor bootstrap/rotation: [`NETWORK_ANCHORS.md`](NETWORK_ANCHORS.md).
Revocation: [`REVOCATION.md`](REVOCATION.md).
Pairwise recovery: [`PAIRWISE_RECOVERY.md`](PAIRWISE_RECOVERY.md).
Shield: [`SHIELD.md`](SHIELD.md).
Canonical schema: [`../spec/freedom.cddl`](../spec/freedom.cddl).
Crypto domains: [`../spec/crypto-domains.txt`](../spec/crypto-domains.txt).

Markdown definisce semantica/state machine. `spec/freedom.cddl` definisce field names/object shapes congelati. `spec/crypto-domains.txt` definisce fixed cryptographic purpose/domain constants.

## 1. Core invariants

- deterministic canonical encoding + registered domain-separated crypto contexts;
- no global DeviceID network-facing;
- no message/media/APK on-chain;
- no mailbox/offline queue;
- identity/authorization/device-control/pairwise/routing/traffic/recovery keys separated;
- RPC not trust;
- exact independently authenticated initial NetworkAnchor;
- NetworkAnchor governance authorization != underlying chain consensus;
- tx hash != success;
- explicit revocation proof/freshness;
- both-offer anti-downgrade;
- forward secrecy + complete bounded rekey;
- explicit transport semantics;
- critical production governance not 1-of-1.

## 2. Root / device hierarchy

```text
RootRecoveryKey
 -> RootIdentity(root_epoch)
 -> DeviceAuthorizationDelegation
 -> DeviceCertificate
 -> DeviceKey
```

Optional compromise-recovery continuity:

```text
RootControlCommitment
 + precommitted UserRecoveryPolicy
```

`RootControlCommitment` is an opaque control-plane recovery-lineage handle, not a network/contact ID.

## 3. Sticky compromise recovery

V1 normal root rotation inherits the current recovery-policy commitment.

A current/compromised root cannot remove the policy unilaterally.

A valid policy requires distinct recovery-key commitments and a threshold inside the number of distinct keys. A production profile claiming independent compromise recovery should use a genuinely independent threshold quorum rather than multiple files controlled by the same compromised environment.

`COMPROMISE_RECOVERY` is quorum-authorized and delayed. Once accepted as `RECOVERY_PENDING`, normal root rotations and recovery-policy mutation are blocked until resolution. The current root cannot cancel the request alone.

The recovery quorum may recover the latest current state of the same opaque root-control lineage even if a stolen root performed a normal rotation before the recovery request opened.

## 4. Opaque device record

V1 control-plane record:

```text
DeviceRecordCommitment
DeviceKey
DeviceControlPublicKey
key_epoch
status
```

The record does not prove account ownership. Peer authorization comes from the expected RootIdentity/contact + delegation + DeviceCertificate + DeviceKey possession.

`DeviceControlKey` only controls the opaque record. V1 device-count/contact quotas are product/service policy, not peer interoperability/security rules.

## 5. DeviceCertificate validation

```text
canonical parse + crypto domain
 -> expected contact/root proof
 -> delegation signature/scope
 -> child capabilities/expiry constrained by parent
 -> device-record commitment binding
 -> DeviceKey possession
 -> highest-seen epoch checks
 -> current-enough revocation proof
 -> AUTHENTICATED
```

## 6. Revocation

Canonical objects cover device-key floor, authorization-epoch floor and root-lineage transition.

`RPC not found` is not non-revocation proof. Semantics: `REVOCATION.md`.

## 7. Contact assurance / pairwise identity

Bootstrap uses `freedom-contact`.

UI trust state:

```text
BOOTSTRAP_UNVERIFIED
CONTACT_VERIFIED
```

After authenticated handshake:

```text
PairSecret
PairwiseContactAlias
PairRendezvousSecret
```

## 8. Rendezvous write authorization

Each pairwise direction/epoch derives a fresh write keypair **off-chain**:

```text
PairRendezvousSecret
 -> RendezvousWriteKeypair(direction, epoch)
 -> write_public_key
```

Public slot:

```text
slot_id = H("Freedom/RendezvousSlot" || network_id || write_public_key)
```

The exact HASH/KDF purposes are fixed by `spec/crypto-domains.txt`.

The control-plane can verify slot binding without learning direction/epoch.

Write acceptance requires valid slot/public-key binding, write signature, monotonic generation and size/expiry bounds.

Observing the slot does not grant overwrite authority.

## 9. Read-before-write / RecoveryBeacon

```text
read remote slot
 -> usable: connect, no write
 -> empty: inspect own slot
      -> valid: wait/poll
      -> empty: signed bounded own-slot write
```

RecoveryBeacon follows the same one-time write-key authority and remains pairwise/ciphertext/bounded.

## 10. Pairwise recovery / freshness

`pairwise-recovery-bundle` is ciphertext-only state from a surviving-device transfer or user-chosen untrusted backup store.

Canonical bundle state includes:

```text
bundle_id
state_epoch
backup_generation
previous_bundle_hash
recovery_key_epoch
state_commitment
```

Integrity alone does not prove freshness after total device loss.

Rollback-detectable profile uses canonical `pairwise-recovery-anchor`:

```text
root_control_commitment
anchor_epoch
latest_backup_generation
latest_bundle_hash
latest_state_commitment
recovery_key_epoch
updated_at_height
authorization_proof
```

Restore with anchor:

```text
obtain verified latest anchor
 -> require exact bundle generation/hash/state commitment match
 -> decrypt/validate
 -> re-authenticate peer
 -> rotate/re-derive future rendezvous/recovery state
 -> establish fresh session keys
```

Mismatch -> `PAIRWISE_BACKUP_ROLLBACK_OR_MISMATCH`.

Without surviving device or independent anchor, integrity may be verified but the client MUST NOT claim `LATEST_VERIFIED_BACKUP` freshness.

No surviving device/backup -> ownership may return, contacts re-bootstrap.

## 11. Routing / relay / Shield

Canonical schema includes route update, relay descriptors/candidates/packets, provenance attestations and Shield descriptors.

Self-declared relay metadata is not independence proof. `N relay IDs != N independent operators`.

`SHIELDED` requires the true circuit gate in `SHIELD.md`.

## 12. Transport semantics

Adapters declare:

```text
RELIABLE_ORDERED_STREAM
UNRELIABLE_DATAGRAM
```

Handshake/control/text/rekey require reliable ordering or explicit reliability. Media uses separate frame/sequence/replay spaces.

## 13. Handshake

Canonical `handshake-offer` advertises supported versions/suites/transport semantics and fresh ephemeral material.

Authenticated transcript binds:

```text
network_id
expected relationship
local/remote offer hashes
certificate/delegation hashes/proofs
root/authorization/key epochs
selected version/suite/transport semantics
ephemeral material
nonces
session_id
```

Selection is deterministic/strongest-allowed under local policy. Offer stripping below policy -> `NEGOTIATION_DOWNGRADE`.

Handshake transcript authentication uses the fixed `MAC FREEDOM/HANDSHAKE_TRANSCRIPT` domain.

## 14. Session establishment

```text
validate expected contact/certificate
 -> validate revocation freshness
 -> validate both offers
 -> fresh ephemeral exchange
 -> derive traffic schedule
 -> key confirmation
 -> E2EE ACTIVE
```

Relay/path is not authentication authority.

## 15. Forward secrecy / rekey

Fresh ephemeral exchange per session. Static root/device compromise later does not reconstruct completed-session traffic keys, absent endpoint/session-state compromise.

Canonical objects:

```text
rekey-init
rekey-commit
rekey-ack
```

State machine:

```text
STABLE(N)
 -> INIT_SENT / INIT_RECEIVED
 -> COMMIT_ESTABLISHED
 -> NEW_KEY_PENDING_ACK
 -> STABLE(N+1)
```

Rules:

- exact `next_epoch = current_epoch + 1`;
- simultaneous Init resolved deterministically from session role/session_id ordering;
- next schedule derived before old keys are erased;
- Ack carries confirmation under the new schedule;
- after valid Ack, no new send under old key;
- old receive key only bounded in-flight grace;
- duplicate/replay idempotently rejected/ignored by state/hash;
- wrong epoch/transcript/timeout terminates before lifetime exhaustion;
- route switch does not reset key epoch;
- reconnect creates a new session schedule;
- rekey authentication uses fixed domains from `spec/crypto-domains.txt`.

No silent split-brain.

## 16. Wire/application objects

Canonical CDDL includes:

```text
encrypted-control-frame
encrypted-media-frame
chat-message
message-ack
attachment-manifest
call-signal
relay-packet
```

No alternate Markdown struct is a second source of truth.

## 17. Synchronous semantics

```text
active authenticated session -> transmit now
no active authenticated session -> FAIL / DISCARD
```

No StoreRequest/offline mailbox in base protocol.

## 18. NetworkAnchor / verified control-plane bootstrap

The canonical `network-anchor` object is the trust bridge between Freedom's independently authenticated bootstrap/update path and an adapter-specific consensus verifier.

Fresh install:

```text
authentic BootstrapTrustAnchor
 -> exact NetworkAnchorCommitmentV1 pin
 -> strict canonical NetworkAnchor parse
 -> NETWORK_ANCHOR signer-set authorization
 -> adapter payload/profile/checkpoint binding
 -> BootstrapFreshnessFloor
 -> initialize ChainAdapter consensus verifier
```

The same RPC that will later provide blocks/proofs MUST NOT be the sole trust source for the initial anchor.

Ordinary post-bootstrap rotation:

```text
current trusted NetworkAnchor
 -> candidate previous_anchor_commitment exact match
 -> anchor_epoch + 1
 -> same network/adapter/chain/profile/policy context
 -> monotonic checkpoint/floor
 -> active NETWORK_ANCHOR threshold authorization
 -> valid signer-set transition when signer epoch changes
 -> independent chain consensus/finality continuity from current trusted state
 -> atomically accept candidate
```

A candidate that fails any check leaves the previous trusted anchor unchanged.

Threshold authorization alone is insufficient post-bootstrap. Governance is not chain consensus.

The first NEAR adapter payload profile is `NEAR-NEP25-PRE-SPICE-BORSH-V1`; unsupported commitment semantics fail closed. Full rules: `NETWORK_ANCHORS.md`.

## 19. Verified control-plane state / bootstrap freshness

After the NetworkAnchor is authenticated, security-sensitive state requires independently verified checkpoint + state proof.

Fresh install additionally enforces `bootstrap-freshness-floor` from its verifier/release.

An authentic but itself-obsolete verifier obtained only through attacker-controlled channels cannot infer newer state from nothing; independent verifier freshness remains an explicit bootstrap assumption.

## 20. Verified mutation

```text
submit
 -> finality proof
 -> execution success
 -> resulting-state proof
 -> exact transition
 -> local commit
```

## 21. Release / governance / migration

Release/security objects use canonical CDDL and registered signature domains.

Signer transitions are cross-authorized/monotonic; contract core is immutable or threshold/timelocked/code-hash pinned.

Threshold governance removes unilateral single-key authority, not quorum-collusion risk.

The `NETWORK_ANCHOR` signer role is scoped and uses its own signature domain. It authorizes adoption of an anchor package but cannot override chain consensus/finality or silently switch adapter/chain/profile.

Chain migration requires:

```text
chain-migration-manifest
+ state-migration-proof
```

The proof binds source finalized/export state, migration program and target imported state.

## 22. Product/payment boundary

Payment may use one-time voucher/nullifier to reduce linkage.

Device/contact counts and Relay Contributor bonuses V1 are product/service policies and do not change remote peer session acceptance.

## 23. Error classes

```text
MALFORMED
UNSUPPORTED_VERSION
NEGOTIATION_DOWNGRADE
DEVICE_CERTIFICATE_INVALID
DEVICE_CERTIFICATE_EXPIRED
REVOCATION_STATE_STALE
REVOCATION_PROOF_INVALID
NETWORK_ANCHOR_INVALID
NETWORK_ANCHOR_NOT_ACTIVE
CONTROL_PLANE_PROOF_INVALID
CONTROL_PLANE_ROLLBACK
CONTROL_PLANE_EXECUTION_FAILED
CONTROL_PLANE_STATE_MISMATCH
KEY_EPOCH_MISMATCH
AUTHENTICATION_FAILED
REPLAY_DETECTED
RENDEZVOUS_WRITE_UNAUTHORIZED
RENDEZVOUS_GENERATION_ROLLBACK
ROOT_RECOVERY_PENDING
PAIRWISE_BACKUP_ROLLBACK_OR_MISMATCH
PAIRWISE_BACKUP_FRESHNESS_UNPROVEN
ROUTE_UNAVAILABLE
PEER_OFFLINE
SESSION_REKEY_REQUIRED
SESSION_REKEY_FAILED
ENTITLEMENT_INVALID
PAYMENT_PENDING
SECURITY_UPDATE_REQUIRED
GOVERNANCE_TRANSITION_INVALID
BOOTSTRAP_STATE_TOO_OLD
```

`NETWORK_ANCHOR_INVALID` covers malformed/non-canonical anchor, wrong independent bootstrap pin, wrong network/adapter/chain/profile context, bad payload/checkpoint binding or invalid anchor authorization.

`NETWORK_ANCHOR_NOT_ACTIVE` covers invalid activation ordering/current activation state. Consensus-continuity failure remains `CONTROL_PLANE_PROOF_INVALID`; monotonic lineage rollback remains `CONTROL_PLANE_ROLLBACK`; signer-set transition failure remains `GOVERNANCE_TRANSITION_INVALID`.

## 24. Interoperability gates

- deterministic encoding vectors;
- NetworkAnchor canonical encoding/signing-input/commitment vectors;
- initial NetworkAnchor pin/context/payload negative cases;
- NetworkAnchor governance-valid / consensus-invalid rotation rejection;
- NetworkAnchor signer transition + rollback tests;
- crypto-domain cross-type/network/purpose negative vectors;
- delegation scope/expiry negative cases;
- recovery-policy threshold/distinct-key validation;
- revocation/non-revocation/freshness vectors;
- fresh-install stale checkpoint tests;
- rendezvous overwrite/front-run/replay;
- pairwise recovery anchor rollback/mismatch/no-anchor semantics;
- both-offer downgrade tests;
- full rekey race/loss/duplicate/route-switch matrix;
- control/media semantic tests;
- control-plane proof/finality/storage convergence;
- sticky compromise-recovery race/timelock;
- relay provenance/Sybil/Shield tests;
- signer/contract/migration rollback tests;
- independent security review.
