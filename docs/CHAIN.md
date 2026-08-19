# Freedom — Blockchain / Control Plane

Status: **canonical design draft**.

Normative security: [`SECURITY_INVARIANTS.md`](SECURITY_INVARIANTS.md).
Control-plane: [`CONTROL_PLANE_SECURITY.md`](CONTROL_PLANE_SECURITY.md).
Revocation: [`REVOCATION.md`](REVOCATION.md).
Schema: [`../spec/freedom.cddl`](../spec/freedom.cddl).

## 1. Ruolo

NEAR Testnet è la prima implementazione `ChainAdapter`; non è Freedom Protocol.

La chain è control-plane distribuito/verificabile per piccoli oggetti di identity/revocation/rendezvous/entitlement/release/governance. Chat, media, Gateway payload e APK restano off-chain.

## 2. Identity separation

```text
RootRecoveryKey        -> local cold recovery
RootControlCommitment  -> opaque recovery-lineage handle
DeviceAuthorization    -> endpoint authorization chain
DeviceRecordCommitment -> opaque device record lookup
DeviceControlKey       -> scoped record rotation/revocation
PairRendezvousSecret   -> pairwise write-key derivation
```

No global DeviceID network-facing.

## 3. Opaque device state V1

Il contract non deve conoscere quale RootIdentity possiede ogni record.

```text
DeviceRecordCommitment
DeviceKey
DeviceControlPublicKey
key_epoch
status
```

Peer ownership/authorization deriva da `DeviceAuthorizationDelegation -> DeviceCertificate -> DeviceKey possession` contro il contatto atteso.

Record spam è anti-abuse/storage problem; non giustifica public account→device mapping.

Device/contact quotas V1 sono product/service policy, non protocol security/interoperability rules.

## 4. ChainAdapter

Conceptual API:

```text
networkAnchor
verifyCheckpoint
verifyStateProof
verifyNonInclusionProof
verifyFinalOutcome

readRootControlStateProof
submitUserRootRotation

registerDeviceRecord
rotateDeviceRecord
revokeDeviceRecord
resolveDeviceRecordProof
resolveDeviceRevocationProof
resolveAuthorizationRevocationProof

readRendezvousProof
writeRendezvous
readRecoveryBeaconProof
writeRecoveryBeacon
pruneExpired

resolveEntitlementProof
readPurchaseIntentProof
readPaymentAttestationProof
redeemEntitlementVoucher

readSecurityPolicyProof
readReleaseManifestProof
readReleaseStatusProof
readSignerSetProof
readContractLineageProof
readMigrationProof
```

Core logic non importa SDK NEAR direttamente.

## 5. RPC non è trust

```text
NetworkAnchor
 -> VerifiedControlPlaneCheckpoint
 -> state root
 -> inclusion/non-inclusion proof
 -> canonical object
```

Un raw RPC response non è `VERIFIED_STATE`.

## 6. Verified mutation

```text
submit
 -> finality proof
 -> execution success
 -> resulting-state proof
 -> exact transition
 -> local success
```

Tx hash != success.

## 7. Bootstrap freshness

Fresh install applica `BootstrapFreshnessFloor` della propria release/verifier. State sotto minimum checkpoint/signer/policy floor viene rifiutato.

Un verifier autentico ma esso stesso molto vecchio non può dedurre magicamente state più recente; verifier freshness richiede independent bootstrap assurance.

## 8. Revocation

Adapter semantics devono essere univoche/testate per device key floor, authorization epoch floor, root-lineage transition e non-inclusion/current-state proof.

`RPC not found` non è una prova.

## 9. Rendezvous pairwise

Direction/epoch restano nella derivazione segreta del one-time write keypair.

Control-plane-visible slot:

```text
slot_id = H("Freedom/RendezvousSlot" || network_id || write_public_key)
```

Write accepted only when:

```text
slot/public-key binding valid
write signature valid
generation monotonic
size/expiry bounds valid
```

Il contract non necessita di conoscere direction/epoch e chi osserva lo slot non ottiene overwrite authority.

## 10. Active storage bounded

Temporary objects implementano overwrite/ring/prune/lease/reclaim. TTL alone non basta.

Repeated renew/expire must converge to a configured active-state bound. Chain archival history may remain observable.

## 11. Root control / recovery lineage

Compromise-recovery users possono registrare un opaque `root_control_commitment` con current root epoch/commitment, recovery-policy commitment e optional pending recovery hash.

Questo handle non è network identity ma rende correlabili gli eventi della stessa recovery lineage sul control-plane; trade-off esplicito.

## 12. Sticky UserRecoveryPolicy

Normal root rotation:

```text
same root_control_commitment
same recovery_policy_commitment
root_epoch + 1
```

La current root da sola non può rimuovere/sostituire la policy. V1 non supporta arbitrary recovery-policy mutation.

## 13. Compromise recovery race

Una valid quorum-authorized `COMPROMISE_RECOVERY` può targettare la latest current root state della stessa root-control lineage.

Quando accepted:

```text
RECOVERY_PENDING
```

fino all'activation height:

- normal root rotations blocked;
- recovery-policy mutation blocked;
- high-risk device authorization may be blocked/pending;
- current root cannot cancel alone;
- cancellation/replacement requires independent recovery authority.

Questo impedisce alla root rubata di evadere dalla recovery policy con una normal-rotation race.

## 14. Governance

Production minimum:

```text
ReleaseAuthorization   >= 3-of-5
ReleaseRevocation      >= 3-of-5
CriticalSecurityPolicy >= 3-of-5
ContractUpgrade        >= 3-of-5 + timelock
GovernanceRootRotation >= 3-of-5 + recovery
```

Threshold eliminates a unilateral single key, not quorum collusion. Custody/operator domains should be separated/audited.

## 15. Contract upgrade

Immutable security core or threshold/timelocked/code-hash-pinned upgrade. No silent contract-address swap or single Full Access production upgrade key.

## 16. Chain migration

```text
ChainMigrationManifest
+ StateMigrationProof
```

Proof binds source finalized/export state, migration program and target imported root. Governance authorizes the migration rule, not arbitrary replacement state.

## 17. Payments / quotas

Voucher/nullifier can reduce payment→entitlement linkage. Timing correlation may remain.

Contact/device commercial limits V1 do not justify public social/device graph.

## 18. Mainnet acceptance

- deterministic schema/signing-domain vectors;
- checkpoint/finality/state proofs against honest/stale/forked/malicious RPC;
- fresh-install floor;
- revocation proof/freshness;
- rendezvous overwrite/front-run/replay;
- active-state convergence;
- DeviceControlKey/recovery revocation;
- sticky recovery policy + latest-lineage race/timelock;
- signer custody/quorum tests/documentation;
- contract rollback;
- StateMigrationProof;
- no mailbox/message/media state.
