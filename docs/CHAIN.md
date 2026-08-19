# Freedom — Blockchain / Control Plane

Status: **canonical design draft**

Normative security rules: [`SECURITY_INVARIANTS.md`](SECURITY_INVARIANTS.md).
Control-plane proof/governance details: [`CONTROL_PLANE_SECURITY.md`](CONTROL_PLANE_SECURITY.md).

## 1. Ruolo

La prima implementazione usa **NEAR Testnet** tramite `ChainAdapter`.

NEAR non è parte del wire protocol e deve poter essere sostituito senza cambiare semantica della comunicazione, identity model o session format.

La chain è un **control-plane distribuito e verificabile**. Non trasporta messaggi, file, audio, video, Gateway payload o APK.

## 2. Identity separation

```text
RootRecoveryKey              -> cold user recovery
DeviceAuthorizationKey       -> delegated device authorization
DeviceCertificate            -> offline authorization proof
DeviceKey                    -> operational device authentication
DeviceRecordCommitment       -> opaque control-plane handle
PairwiseContactAlias         -> relationship-specific identity
TransportToken               -> temporary route/circuit identity
```

Nessun global `DeviceID` è richiesto dal network layer.

## 3. Commitment separation

```text
DeviceAuthorizationCommitment
EntitlementCommitment
PaymentBindingCommitment
SponsorshipCommitment
```

sono domain-separated. Rendezvous/recovery deriva da `PairRendezvousSecret`.

Domain separation riduce riuso/linkage diretto ma non equivale automaticamente a unlinkability transazionale.

## 4. Installazione locale

```text
install
 -> generate RootRecoveryKey / RootIdentity locally
 -> generate DeviceAuthorizationKey delegation as needed
 -> generate DeviceKey locally
 -> generate DeviceRecordCommitment locally
 -> generate Recovery Kit
 -> 0 mandatory chain writes
```

## 5. ChainAdapter

```text
interface ChainAdapter {
    networkAnchor(...)
    verifyCheckpoint(...)
    verifyStateProof(...)
    verifyNonInclusionProof(...)
    verifyFinalOutcome(...)

    registerOwnership(...)
    registerDeviceRecord(...)
    rotateDeviceKey(...)
    revokeDeviceRecord(...)
    resolveDeviceRecordProof(...)
    activateDeviceSlot(...)
    revokeDeviceSlot(...)

    resolveEntitlementProof(...)
    readRendezvousProof(...)
    writeRendezvous(...)
    pruneExpired(...)
    readRecoveryBeaconProof(...)
    writeRecoveryBeacon(...)

    readPurchaseIntentProof(...)
    readPaymentAttestationProof(...)
    redeemEntitlementVoucher(...)

    readEmergencyBulletinsProof(...)
    readSecurityPolicyProof(...)
    readReleaseManifestProof(...)
    readReleaseStatusProof(...)
    readSignerSetProof(...)
    readContractLineageProof(...)
}
```

La logica core non importa SDK NEAR direttamente.

## 6. RPC non è trust anchor

Un RPC può mentire, omettere o servire stato stale.

Stato security-sensitive è accettato solo tramite:

```text
NetworkAnchor
 -> VerifiedControlPlaneCheckpoint
 -> state root
 -> inclusion/non-inclusion proof
 -> canonical object
```

`proof/light-client verification` non è più una ottimizzazione opzionale per revocation, signer set, policy, release status, device authorization ed entitlement: è il target normativo production.

Implementazioni testnet che non verificano ancora prove end-to-end devono essere marcate come tali e non sostenere il claim production.

## 7. Verified finality — tx hash != success

```text
submit signed operation
 -> wait acceptable finality
 -> verify execution outcome
 -> reject Failure / partial failure
 -> verify resulting state proof
 -> verify expected transition
 -> persist/display success
```

Un transaction hash dimostra submission, non successo.

## 8. Verified time / anti-rollback

Il client conserva:

```text
highest_verified_height
highest_signer_set_epoch
highest_policy_epoch
highest_seen_security_state
```

Expiry/freshness usa `VerifiedTimeAnchor`, height/epoch e monotonic local time; wall clock locale non può riattivare stato vecchio.

Una risposta valida ma più vecchia del highest-seen state viene rifiutata come rollback.

## 9. Device authorization privacy

Pubblicare una RootIdentity/root signature accanto a una DeviceKey renderebbe leggibile `RootIdentity -> device`.

Production target:

```text
DeviceAuthorizationProof
public outputs:
  device_record_commitment
  device_public_key
  key_epoch
  slot_nullifier
  authorization_policy_epoch
```

Il proof dimostra ownership/authorization + slot valido senza rivelare la RootIdentity pubblica. La costruzione concreta deve usare anonymous credential/ZK membership/authorization proof reviewata.

Se Testnet usa una prova linkabile, la documentazione/UI tecnica deve dichiararlo esplicitamente.

## 10. DeviceCertificate

```text
DeviceAuthorizationDelegation {
    root_epoch
    authorization_public_key
    authorization_epoch
    capabilities
    valid_from
    expires_at
    root_recovery_signature
}

DeviceCertificate {
    version
    network_id
    root_identity_commitment_or_proof
    authorization_epoch
    device_public_key
    key_epoch
    protocol_version
    capabilities?
    issued_at
    expires_at
    certificate_id
    authorization_signature
}
```

Il peer verifica offline delegation/certificate/DeviceKey possession e usa proof/cache control-plane per revocation/freshness.

## 11. Rotation / revocation / UserRootRotation

Device rotation incrementa `key_epoch`.

Compromissione RootIdentity richiede una transizione distinta:

```text
UserRootRotation {
    old_root_epoch
    new_root_public_key
    new_root_commitment
    continuity_proof
    recovery_policy_proof
    issued_at
}
```

Perdita di device e root compromise non sono lo stesso evento.

## 12. Multi-device enforcement

```text
active_devices <= max_devices
```

Production target usa `DeviceSlotProof`/nullifier privacy-preserving. Non pubblicare una device list leggibile.

## 13. Contact slots

`base_contact_slots` è **product policy del client ufficiale**, non requisito di interoperabilità/security del protocollo V1.

Il control-plane non pubblica social graph per far rispettare 10 contatti. Un futuro enforcement anti-tampering richiede una costruzione privacy-preserving separata prima di diventare normativo.

## 14. Rendezvous pairwise

```text
known route -> try
relay/bridge/Shield -> try
pairwise rendezvous -> fallback/recovery
```

`RendezvousRecord` e `RecoveryBeacon` sono pairwise, cifrati, opachi, size-bounded e read-before-write.

## 15. TTL non basta: active storage bounded

Ogni record temporaneo deve avere reclaim concreto:

- overwrite dello stesso slot;
- ring/bucket bounded;
- `prune_expired` permissionless;
- lease/storage rent;
- refund/bounty bounded.

È vietata una mappa che aggiunge una nuova chiave per ogni epoch/rinnovo senza cancellazione.

Acceptance invariant:

```text
repeated create/expire/prune
 -> active state converges to configured upper bound
```

La storia archiviale della chain resta osservabile; il claim riguarda lo stato attivo.

## 16. Sponsorship

```text
new ownership
 -> valid proof
 -> SponsorshipCommitment unused
 -> adaptive anti-abuse proof
 -> relayer/budget bounds
 -> submit
 -> finalized verified state
```

Nessun SMS/PayPal/telefono obbligatorio per identità Free.

## 17. Payments

`PaymentAttestation` non deve collegare direttamente payment provider e `EntitlementCommitment` quando evitabile.

Flow privacy-preferred:

```text
verified payment
 -> one-time EntitlementVoucher / blind credential
 -> redeem with nullifier
 -> verified entitlement transition
```

Timing correlation può restare possibile.

## 18. Release / policy / signer state

Il control-plane contiene piccoli oggetti verificabili:

```text
EmergencyBulletin
SecurityPolicy
FreedomRelease
FreedomReleaseLocator
ReleaseStatus
SignerSet
SignerSetTransition
ContractUpgradeManifest
ChainMigrationManifest
```

APK resta off-chain.

## 19. Signer-set transitions

```text
next_epoch = previous_epoch + 1
previous threshold set authorizes
next threshold set accepts
activation height monotonic
highest-seen epoch persisted
```

Un vecchio signer set non può riattivarsi perché ancora crittograficamente valido.

Quorum-loss recovery usa un recovery set/manifest pinned in anticipo, con threshold/timelock più forte.

## 20. Contract governance

Production sceglie:

```text
immutable security core
oppure
threshold-governed upgrade path
```

Se upgradeabile:

```text
ContractUpgradeManifest {
    governance_epoch
    current_code_hash
    new_code_hash
    migration_hash
    activation_height
    rollback_floor
    signatures[]
}
```

Requisiti: `>= 3-of-5`, timelock non-emergency, code hash verificabile, accepted contract lineage client-side, no silent contract-address swap, rollback protection.

Una Full Access key singola capace di sostituire il contratto production viola “nessun super-admin”.

## 21. ChainAdapter migration

```text
ChainMigrationManifest {
    from_adapter
    to_adapter
    from_final_checkpoint
    imported_state_commitment
    new_network_anchor
    activation_epoch
    governance_signatures[]
}
```

Migrare da NEAR non significa accettare una nuova root of trust non autenticata.

## 22. Metadata minimization

On-chain non devono comparire in chiaro, salvo necessità non evitabile:

- IP/porta associati a identity/device;
- RootIdentity -> device list;
- social graph;
- pairwise alias globalmente correlabili;
- posizione precisa;
- conversation/message IDs;
- continuous presence;
- email/telefono;
- plaintext payment identity;
- application payload;
- APK binary.

Timing/transaction correlation resta un rischio da misurare.

## 23. Contract scope vietato

Il contratto **MUST NOT** implementare:

- chat/inbox/mailbox;
- message/media storage;
- social graph pubblico;
- continuous presence;
- relay payload;
- APK storage;
- generic Internet proxy state;
- master decryption/admin key.

## 24. Mainnet acceptance criteria

Prima della mainnet:

- checkpoint/finality/state-proof verifier testato contro RPC honest/stale/malicious/forked;
- non-inclusion proof testata;
- highest-seen rollback rejection testata;
- device authorization privacy proof implementata o claim privacy ridimensionato;
- multi-device slot nullifier testato;
- active storage convergente a bound dopo stress;
- `prune_expired`/refund/bounty testati;
- DeviceAuthorizationDelegation + DeviceCertificate testati;
- `UserRootRotation` testata;
- contract upgrade threshold/timelock o immutability verificata;
- signer-set transition/quorum-loss recovery testati;
- ChainAdapter migration anti-rollback testata;
- payment voucher/nullifier testato se abilitato;
- nessuna mailbox/message storage.
