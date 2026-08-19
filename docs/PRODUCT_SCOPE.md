# Freedom — Product Scope

Status: **canonical product scope**.

Normative security rules: [`SECURITY_INVARIANTS.md`](SECURITY_INVARIANTS.md).
Control-plane: [`CONTROL_PLANE_SECURITY.md`](CONTROL_PLANE_SECURITY.md).
Revocation: [`REVOCATION.md`](REVOCATION.md).
Pairwise recovery: [`PAIRWISE_RECOVERY.md`](PAIRWISE_RECOVERY.md).
Advanced development: [`ADVANCED_DEVELOPMENT.md`](ADVANCED_DEVELOPMENT.md).
Schema: [`../spec/freedom.cddl`](../spec/freedom.cddl).
Crypto domains: [`../spec/crypto-domains.txt`](../spec/crypto-domains.txt).

## 1. Obiettivo

Freedom Communication deve dimostrare comunicazione privata live, autenticata E2EE, sincrona, senza mailbox centrale e senza dipendenza permanente da un singolo percorso/provider.

## 2. V1 core

La prima release pubblica è 1:1 e richiede:

- RootRecoveryKey/RootIdentity;
- DeviceAuthorizationDelegation;
- DeviceCertificate verificabile offline;
- opaque DeviceRecord + scoped DeviceControlKey;
- canonical deterministic encoding + registered crypto domains;
- verified control-plane checkpoint/state proof;
- canonical revocation/freshness semantics;
- BootstrapFreshnessFloor per fresh install;
- contact bootstrap + `BOOTSTRAP_UNVERIFIED`/`CONTACT_VERIFIED`;
- pairwise identity/rendezvous con write authorization;
- pairwise encrypted backup/device transfer;
- explicit pairwise-backup freshness semantics (`PairwiseRecoveryAnchor` when rollback-detectable total-device-loss recovery is claimed);
- expected-contact handshake;
- both-offer-set anti-downgrade;
- forward secrecy;
- bounded traffic-key lifetime;
- complete RekeyInit/Commit/Ack state machine;
- stream/datagram semantic separation;
- synchronous text/file/media/voice/video;
- no mailbox/offline queue;
- forward-only relay;
- device relay opt-in;
- Adaptive Defense base;
- Share Freedom with threshold release verification;
- contract/signer governance anti-rollback;
- stable Recovery Kit cryptographic envelope.

## 3. V1 commercial quotas are not protocol security

Targets such as:

```text
FREE 1 device
FREE 10 contacts
Relay Contributor +10 contacts
```

are client/service product policy in V1.

A modified open-source client may bypass a purely local quota. Therefore:

- peers do not reject a valid E2EE session because a remote client exceeded a local commercial quota;
- the control-plane does not publish social/device graph merely to enforce monetization;
- V1 does not require an unfinished ZK device-slot construction as a core protocol blocker;
- future anti-tamper device/contact quota enforcement requires a separate privacy-preserving credential/nullifier/ZK design and review.

Managed Gateway/Shield/egress capacity remains a more enforceable commercial surface.

## 4. Root compromise profile

Normal Recovery Kit restore handles device loss. Claiming recovery after complete RootRecoveryKey compromise requires a precommitted `UserRecoveryPolicy` with independent recovery quorum + delay.

Policy validation rejects duplicate recovery keys and invalid thresholds. A production independent-compromise-recovery profile should not keep the complete recovery quorum in the same custody domain as the active root/device environment.

If such a policy is not configured, the client/documentation must not claim compromise recovery.

## 5. Session security gate

Before public V1:

```text
expected-contact auth
canonical encoding + crypto domains
DeviceCertificate parent-scope validation
revocation proof/freshness
fresh-install bootstrap floor
forward secrecy
rekey loss/duplicate/simultaneous-init behavior
replay protection
both-offer anti-downgrade
control/media sequence separation
```

## 6. Rendezvous/storage gate

Rendezvous/recovery uses derived one-time write keys, signed generation-monotonic records and bounded storage reclaim.

Blockers:

- public slot overwrite possible without secret write authority;
- map state grows forever despite TTL;
- write generation rollback/replay accepted.

## 7. Pairwise recovery freshness gate

After total device loss, an untrusted backup source may serve a valid but stale bundle.

If Freedom claims rollback-detectable recovery, it requires either:

```text
surviving authorized device state
or
verified monotonic PairwiseRecoveryAnchor
```

With anchor enabled, bundle generation/hash/state commitment must match the latest verified anchor. Without surviving state/anchor, integrity may be verified but `LATEST_VERIFIED_BACKUP` freshness is not claimed.

Post-restore peer re-authentication and future rendezvous/recovery-state rotation are mandatory.

## 8. Control-plane gate

Security read:

```text
NetworkAnchor
 -> VerifiedControlPlaneCheckpoint
 -> state proof
 -> canonical object
```

Security write:

```text
submit
 -> finality proof
 -> execution success
 -> resulting-state proof
 -> expected transition
 -> local success
```

RPC response/tx hash alone never enough.

## 9. Governance gate

Production:

```text
ReleaseAuthorization   >= 3-of-5
ReleaseRevocation      >= 3-of-5
CriticalSecurityPolicy >= 3-of-5
ContractUpgrade        >= 3-of-5 + timelock
GovernanceRootRotation >= 3-of-5 + recovery
```

Signer custody/operator independence is an explicit trust assumption. A single organization controlling a quorum cannot be marketed as absence of a single administrative actor merely because there are five key files.

## 10. Shield

True Freedom Shield remains post-core until `SHIELD.md` circuit setup/per-hop/layered-forwarding/provenance gates pass.

`SHIELDED` label is unavailable before that.

## 11. Gateway

Post-V1 capability:

```text
app -> local Gateway -> Freedom path -> explicit Egress -> Internet
```

Target managed Free capacity can remain 100 MB/day as a product target, separate from Communication.

## 12. Release / first install

```text
untrusted bytes
 -> exact hash
 -> threshold FreedomRelease
 -> signer epoch/transition
 -> verified ReleaseStatus / SecurityPolicy
 -> Android signer lineage
 -> BootstrapTrustAnchor
 -> BootstrapFreshnessFloor
 -> install
```

## 13. Development gates

Protocol/control-plane/routing development is simulator-first according to `ADVANCED_DEVELOPMENT.md`.

Before public V1, at least L0/L1/L2/L3 coverage exists for core protocol/control-plane behavior; Android gates are added for platform-specific properties.

## 14. Non-blockers for V1

- groups;
- group media;
- cloud history;
- production Shield multi-hop;
- full Gateway;
- advanced padding;
- hard anti-tamper contact/device quota;
- tokenized relay economy;
- embedded browser.

## 15. Launch blockers

- canonical schema/domain drift between docs/code;
- ad-hoc non-domain-separated signatures/MAC/AEAD/HASH/KDF purposes;
- stale fresh-install checkpoint accepted below floor;
- RPC `not found` interpreted as non-revoked;
- rendezvous overwrite/front-run without write key;
- active state not reclaimable;
- root-compromise claim without independent recovery policy;
- invalid/duplicate recovery quorum accepted;
- pairwise total-device-loss recovery called latest without surviving state/verified anchor;
- pairwise restore does not rotate future rendezvous/recovery authority;
- rekey split-brain/loss behavior undefined;
- first-contact substitution ignored;
- signer quorum operationally centralized while marketed as no single actor;
- chain migration without StateMigrationProof;
- global DeviceID or public social/device graph reintroduced;
- mailbox/offline queue reintroduced;
- release first-install without pinned trust/freshness anchor;
- `SHIELDED` claim before real circuit protocol.
