# Freedom — Blockchain / Control Plane

Status: **canonical design draft**.

Normative security rules: [`SECURITY_INVARIANTS.md`](SECURITY_INVARIANTS.md).
Control-plane verification: [`CONTROL_PLANE_SECURITY.md`](CONTROL_PLANE_SECURITY.md).
Revocation: [`REVOCATION.md`](REVOCATION.md).
Schema: [`../spec/freedom.cddl`](../spec/freedom.cddl).

## 1. Ruolo

NEAR Testnet è la prima implementazione `ChainAdapter`; non è Freedom Protocol.

La chain è control-plane distribuito/verificabile per piccoli oggetti di identity/revocation/rendezvous/entitlement/release/governance. Non trasporta chat, media, Gateway payload o APK.

## 2. Identity separation

```text
RootRecoveryKey       -> local cold recovery
DeviceAuthorization   -> endpoint authorization chain
DeviceRecordCommitment-> opaque record lookup
DeviceControlKey      -> scoped record rotation/revocation
PairRendezvousSecret  -> pairwise slot authority
```

No global DeviceID network-facing.

## 3. V1 device record

Il control-plane V1 **non richiede** un mapping pubblico RootIdentity→device e non richiede una ZK device-slot proof come blocker del core.

```text
DeviceRecord {
    device_record_commitment
    device_public_key
    device_control_public_key
    key_epoch
    status
    protocol_version
}
```

Un peer decide se il record appartiene al contatto atteso verificando `DeviceAuthorizationDelegation -> DeviceCertificate -> DeviceKey possession`.

Record spam viene limitato da sponsorship/fee/anti-abuse. `max_devices` hard enforcement privacy-preserving è estensione futura; V1 quota commerciale è product/service policy.

## 4. ChainAdapter

Interfaccia concettuale:

```text
networkAnchor
verifyCheckpoint
verifyStateProof
verifyNonInclusionProof
verifyFinalOutcome

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

Security state da una risposta RPC non provata non è `VERIFIED`.

## 6. Verified mutation

```text
submit
 -> finality proof
 -> execution success
 -> resulting state proof
 -> exact expected transition
 -> local success
```

Tx hash != success.

## 7. Bootstrap freshness

Fresh install usa il `BootstrapFreshnessFloor` della propria release/verifier:

```text
minimum_checkpoint_height
minimum_checkpoint_hash?
minimum_signer_set_epoch
minimum_policy_epoch
```

State sotto il floor viene rifiutato.

Un verifier autentico ma molto vecchio non può sapere da solo che esiste stato più recente; la freshness del verifier stesso richiede un canale/bootstrap anchor indipendente.

## 8. Revocation

Semantica canonica in `REVOCATION.md`.

Il `ChainAdapter` deve offrire proof semantics univoche per:

- device record/key revocation;
- authorization epoch revocation;
- root epoch transition;
- non-revocation/current-state proof.

`RPC not found` non è una prova.

## 9. Rendezvous pairwise

Ogni direction/epoch deriva un one-time write keypair.

```text
write_public_key
 -> slot_id = H(domain || write_public_key || epoch || direction)
```

Write valida soltanto con firma del relativo write key + generation monotonic + size/expiry bounds.

Questo impedisce a chi osserva lo slot dopo la prima write di sovrascriverlo liberamente.

## 10. Active storage bounded

TTL non basta.

Ogni temporary object usa overwrite/ring/prune/lease/reclaim. Una nuova map key infinita per epoch/rinnovo è vietata.

La storia archiviale della chain può restare visibile; l'invariante riguarda active state bounded.

## 11. User root recovery

Normal device loss e root compromise sono distinti.

Compromise recovery richiede `UserRecoveryPolicy` precommitted indipendente dalla singola RootRecoveryKey.

`UserRootRotation` in modalità compromise richiede recovery quorum proof + delay secondo policy.

## 12. Governance

Production:

```text
ReleaseAuthorization   >= 3-of-5
ReleaseRevocation      >= 3-of-5
CriticalSecurityPolicy >= 3-of-5
ContractUpgrade        >= 3-of-5 + timelock
GovernanceRootRotation >= 3-of-5 + recovery
```

Il quorum è una trust assumption: signer custody/operator domains devono essere separati per quanto praticabile. `3-of-5` non significa automaticamente “nessun singolo soggetto” se uno stesso soggetto controlla tre chiavi.

## 13. Contract upgrade

Immutable security core oppure threshold-governed upgrade con code hash, migration hash, activation height, timelock, accepted lineage e rollback floor.

No silent contract-address swap.

## 14. Chain migration

Una migration richiede:

```text
ChainMigrationManifest
+ StateMigrationProof
```

Il proof lega source finalized checkpoint/export root, migration program hash e target imported root. Governance autorizza la migration rule; non può semplicemente firmare uno state rewrite arbitrario.

## 15. Payments / quotas

Payment può usare voucher/nullifier per ridurre linkage payment→entitlement.

Contact/device commercial limits V1 non sono protocol interoperability invariants e non giustificano un social/device graph pubblico.

## 16. Mainnet acceptance criteria

- deterministic schema/signing-domain vectors;
- checkpoint/finality/state-proof verification contro RPC honest/stale/forked/malicious;
- fresh-install bootstrap-floor tests;
- revocation proof/freshness tests;
- rendezvous overwrite/front-run tests;
- active-state storage convergence;
- device control-key rotation/revocation;
- compromise-recovery policy tests;
- contract/signer governance rollback tests;
- StateMigrationProof verification;
- no mailbox/message/media state.
