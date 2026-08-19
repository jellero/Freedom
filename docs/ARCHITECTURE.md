# Freedom — Architecture

Status: **canonical design draft**.

Normative security: [`SECURITY_INVARIANTS.md`](SECURITY_INVARIANTS.md).
Control-plane: [`CONTROL_PLANE_SECURITY.md`](CONTROL_PLANE_SECURITY.md).
Revocation: [`REVOCATION.md`](REVOCATION.md).
Shield: [`SHIELD.md`](SHIELD.md).
Schema: [`../spec/freedom.cddl`](../spec/freedom.cddl).

## 1. System layers

Freedom separa:

1. user-root recovery (`RootRecoveryKey`, optional independent `UserRecoveryPolicy`);
2. device authorization (`DeviceAuthorizationDelegation`, `DeviceCertificate`);
3. opaque device control-plane state (`DeviceRecordCommitment`, `DeviceControlKey`);
4. verifiable control-plane checkpoint/proofs/revocation;
5. pairwise identity/rendezvous;
6. routing/transport fabric;
7. Freedom Communication;
8. Freedom Shield;
9. Freedom Gateway;
10. verified distribution/release governance.

## 2. Overview

```text
                 VERIFIABLE CONTROL PLANE
      +----------------------------------------------+
      | checkpoint/state proofs                     |
      | opaque device revocation/rotation           |
      | pairwise rendezvous/recovery                |
      | entitlement/payment                         |
      | release/security/contract governance        |
      +----------------------+-----------------------+
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

## 10. Transport fabric

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

## 11. Relay

Forward not store, bounded resources, no implicit egress, no session keys, no global identity headers.

`N relay IDs != N independent operators`.

## 12. Shield

A production Shield path requires true circuit setup, per-hop independent keys, layered forwarding, provenance-aware selection and no silent direct fallback.

Two chained proxies are not Shield.

## 13. Session security

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

## 14. Synchronous semantics

```text
active authenticated session -> transmit now
no active authenticated session -> fail/discard
```

No mailbox/offline queue/store-and-forward.

## 15. Governance

Production critical actions use threshold governance with explicit quorum trust assumption and separated custody/operator domains where practical.

```text
ReleaseAuthorization   >= 3-of-5
ReleaseRevocation      >= 3-of-5
CriticalSecurityPolicy >= 3-of-5
ContractUpgrade        >= 3-of-5 + timelock
GovernanceRootRotation >= 3-of-5 + recovery
```

This means no single technical credential is unilateral; quorum collusion remains a trust assumption.

## 16. Contract/chain migration

Contract upgrade is immutable-core or threshold/timelocked/code-hash pinned.

Chain migration requires a `StateMigrationProof` linking source finalized state, migration program hash and target imported state root.

## 17. Gateway boundary

```text
external app
 -> local Gateway tunnel
 -> Freedom path
 -> explicit Egress
 -> Internet
```

Gateway protects/diversifies the path; it does not convert generic external protocols into Freedom E2EE.

## 18. Distribution

```text
untrusted artifact bytes
 -> exact hash
 -> threshold release authorization
 -> verified ReleaseStatus/SecurityPolicy
 -> Android signer lineage
 -> BootstrapTrustAnchor + FreshnessFloor
 -> install
```

## 19. Primitive prohibitions

No global network DeviceID, on-chain mailbox/messages, public social graph, mandatory single infrastructure, master decrypt key, single-key production super-admin, tx-hash-is-success, silent Shield downgrade or unbounded temporary active state.

## 20. Principle

> **Nessun server centrale. Nessun super-admin. Niente di opaco. Fiducia nel protocollo. Sicurezza nell'architettura.**

“Nessun super-admin” means no single unilateral production credential; governance-quorum trust assumptions are explicit rather than hidden.
