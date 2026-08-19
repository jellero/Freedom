# Freedom — Architecture

Status: **canonical design draft**.

Normative security: [`SECURITY_INVARIANTS.md`](SECURITY_INVARIANTS.md).
Control-plane: [`CONTROL_PLANE_SECURITY.md`](CONTROL_PLANE_SECURITY.md).
Revocation: [`REVOCATION.md`](REVOCATION.md).
Pairwise recovery: [`PAIRWISE_RECOVERY.md`](PAIRWISE_RECOVERY.md).
Shield: [`SHIELD.md`](SHIELD.md).
Schema: [`../spec/freedom.cddl`](../spec/freedom.cddl).
Crypto domains: [`../spec/crypto-domains.txt`](../spec/crypto-domains.txt).

## 1. System layers

Freedom separa:

1. user-root recovery (`RootRecoveryKey`, optional independent `UserRecoveryPolicy`);
2. device authorization (`DeviceAuthorizationDelegation`, `DeviceCertificate`);
3. opaque device control-plane state (`DeviceRecordCommitment`, `DeviceControlKey`);
4. verifiable control-plane checkpoint/proofs/revocation;
5. pairwise identity/rendezvous;
6. pairwise recovery state (`RecoveryStateKey`, encrypted bundle, optional monotonic anchor);
7. routing/transport fabric;
8. Freedom Communication;
9. Freedom Shield;
10. Freedom Gateway;
11. verified distribution/release governance.

## 2. Overview

```text
                 VERIFIABLE CONTROL PLANE
      +--------------------------------------------------+
      | checkpoint/state proofs                         |
      | opaque device revocation/rotation               |
      | pairwise rendezvous                             |
      | optional pairwise recovery freshness anchor     |
      | entitlement/payment                             |
      | release/security/contract governance            |
      +----------------------+---------------------------+
                             |
                    only when needed
                             |

 Alice endpoint                              Bob endpoint
 RootIdentity                                RootIdentity
 DeviceCertificate                          DeviceCertificate
 DeviceKey                                  DeviceKey
 PairwiseContactAlias                       PairwiseContactAlias
        |                                         |
        +--------- authenticated relation --------+
                             |
                   path / transport selector
                             |
             +---------------+---------------+
             |                               |
      COMMUNICATION                       GATEWAY
      authenticated E2EE                 explicit egress
```

NEAR is the first `ChainAdapter`; it is not the protocol identity.

## 3. Identity / recovery

```text
RootRecoveryKey
 -> RootIdentity(root_epoch)
 -> DeviceAuthorizationDelegation
 -> DeviceCertificate
 -> DeviceKey
```

Root-compromise recovery is a separate path:

```text
precommitted UserRecoveryPolicy
 -> independent recovery quorum
 -> delay
 -> UserRootRotation
```

Without an independent recovery authority, complete root-secret compromise cannot be distinguished from legitimate root use.

## 4. Device record privacy

V1 device control-plane state is opaque and does not require a public RootIdentity→device mapping.

Peer authorization comes from certificate/delegation verification, not from the contract knowing the user's public identity.

`max_devices` V1 is product/service policy; future hard enforcement privacy proof is optional evolution, not core blocker.

## 5. Communication boundary

![Freedom Communication architecture](assets/freedom-communication.svg)

```text
Freedom endpoint A
      <==== authenticated E2EE ====>
Freedom endpoint B
```

Session keys stay at endpoints. Relay/bridge/RPC/path selector do not authenticate the peer and do not own conversation keys.

## 6. Control-plane authenticity

```text
NetworkAnchor
 -> VerifiedControlPlaneCheckpoint
 -> state root
 -> inclusion/non-inclusion proof
 -> canonical object
```

Fresh install additionally enforces `BootstrapFreshnessFloor`.

## 7. Revocation

Device key, authorization epoch and root epoch are separate revocation/transition surfaces.

`RPC not found` is not non-revocation proof. Freshness classes and highest-seen state determine whether a new handshake may proceed.

## 8. Pairwise identity / contact assurance

```text
PairSecret
PairwiseContactAlias
PairRendezvousSecret
```

First-contact descriptor substitution remains possible before independent verification; UI distinguishes `BOOTSTRAP_UNVERIFIED` and `CONTACT_VERIFIED`.

## 9. Rendezvous authority

Each direction/epoch derives a one-time write keypair. Slot observation does not grant overwrite authority.

Rendezvous/recovery writes are signed, generation-monotonic, bounded and reclaimable.

Fixed cryptographic purpose labels are not ad-hoc strings in implementations; they come from `spec/crypto-domains.txt`.

## 10. Pairwise recovery freshness

Pairwise state is recovered either from a surviving authorized device or from an encrypted `PairwiseRecoveryBundle` stored on an untrusted source.

**Integrity does not prove freshness after total device loss.**

The rollback-detectable profile uses a small verified `PairwiseRecoveryAnchor` that commits to the latest backup generation/hash/state commitment:

```text
root_control_commitment
anchor_epoch
latest_backup_generation
latest_bundle_hash
latest_state_commitment
recovery_key_epoch
```

The full contact/pairwise backup remains encrypted and off-chain.

The anchor leaks only recovery-lineage backup-update timing, not the social graph/plaintext; that timing correlation is an explicit privacy trade-off.

Without a surviving device or verified anchor, Freedom can validate bundle integrity but cannot claim `LATEST_VERIFIED_BACKUP` freshness.

After restore, peers are re-authenticated and future rendezvous/recovery/session state is rotated.

## 11. Transport fabric

```text
DIRECT
NAT_TRAVERSAL
RELAY
BRIDGE
SHIELDED
MULTI_HOP
PLUGGABLE / OBFUSCATED TRANSPORT
```

Adapters declare reliable-stream/datagram semantics. Media loss does not block control/text through a shared sequence space.

## 12. Relay

Forward not store, bounded resources, no implicit egress, no session keys, no global identity headers.

`N relay IDs != N independent operators`.

## 13. Shield

A production Shield path requires true circuit setup, per-hop independent keys, layered forwarding, provenance-aware selection and no silent direct fallback.

Two chained proxies are not Shield.

## 14. Session security

```text
both-offer anti-downgrade
+ fresh ephemeral exchange
+ forward secrecy
+ bounded traffic-key lifetime
+ RekeyInit / RekeyCommit / RekeyAck
+ key confirmation
+ separate media/control key spaces
```

Rekey simultaneous/loss/duplicate behavior is state-machine defined.

## 15. Synchronous semantics

```text
active authenticated session -> transmit now
no active authenticated session -> fail/discard
```

No mailbox/offline queue/store-and-forward.

## 16. Governance

Production critical actions use threshold governance with explicit quorum trust assumption and separated custody/operator domains where practical.

```text
ReleaseAuthorization   >= 3-of-5
ReleaseRevocation      >= 3-of-5
CriticalSecurityPolicy >= 3-of-5
ContractUpgrade        >= 3-of-5 + timelock
GovernanceRootRotation >= 3-of-5 + recovery
```

This means no single technical credential is unilateral; quorum collusion remains a trust assumption.

## 17. Contract/chain migration

Contract upgrade is immutable-core or threshold/timelocked/code-hash pinned.

Chain migration requires a `StateMigrationProof` linking source finalized state, migration program hash and target imported state root.

## 18. Gateway boundary

```text
external app
 -> local Gateway tunnel
 -> Freedom path
 -> explicit Egress
 -> Internet
```

Gateway protects/diversifies the path; it does not convert generic external protocols into Freedom E2EE.

## 19. Distribution

```text
untrusted artifact bytes
 -> exact hash
 -> threshold release authorization
 -> verified ReleaseStatus/SecurityPolicy
 -> Android signer lineage
 -> BootstrapTrustAnchor + FreshnessFloor
 -> install
```

## 20. Primitive prohibitions

No global network DeviceID, on-chain mailbox/messages, public social graph, mandatory single infrastructure, master decrypt key, single-key production super-admin, tx-hash-is-success, silent Shield downgrade, unbounded temporary active state or unregistered protocol cryptographic domains.

## 21. Principle

> **Nessun server centrale. Nessun super-admin. Niente di opaco. Fiducia nel protocollo. Sicurezza nell'architettura.**

“Nessun super-admin” means no single unilateral production credential; governance-quorum trust assumptions are explicit rather than hidden.
