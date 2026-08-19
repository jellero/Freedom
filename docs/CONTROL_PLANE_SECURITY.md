# Freedom — Control-Plane Security & State Verification

Status: **canonical / normative design rules**.

Normative baseline: [`SECURITY_INVARIANTS.md`](SECURITY_INVARIANTS.md).
Revocation/freshness: [`REVOCATION.md`](REVOCATION.md).
Schema: [`../spec/freedom.cddl`](../spec/freedom.cddl).

## 1. RPC non è trust

Un provider RPC può mentire, servire stato stale, omettere record o censurare richieste. Per stato security-sensitive il client **MUST NOT** trasformare una risposta RPC non provata in verità.

```text
NetworkAnchor
 -> finalized checkpoint
 -> state root
 -> inclusion/non-inclusion proof
 -> canonical object
 -> local state transition
```

## 2. VerifiedControlPlaneCheckpoint

Schema canonico: `verified-control-plane-checkpoint` in `spec/freedom.cddl`.

Il `ChainAdapter` definisce come verificare finality/consensus proof per la chain concreta. Per NEAR significa usare primitive coerenti col suo modello di finalità/stato, non fidarsi del solo JSON RPC.

## 3. VerifiedStateProof

Per ogni oggetto security-sensitive il verifier controlla:

```text
checkpoint valid
state-root binding valid
inclusion/non-inclusion proof valid
canonical deterministic encoding valid
object signing domain valid
object epoch/policy valid
```

Solo allora il risultato è `VERIFIED_STATE`.

## 4. Cache verificata / highest-seen

Persistire almeno:

```text
object
verified checkpoint
highest_seen_height
highest_seen_object_epoch
freshness class
monotonic observation time
```

```text
new_verified_height < highest_seen_height -> reject rollback
new_object_epoch < highest_seen_epoch     -> reject rollback
```

Il rollback check è per namespace/oggetto rilevante, non un singolo contatore ambiguo globale.

## 5. Bootstrap freshness per fresh install

Un client appena installato non ha highest-seen locale. Per questo il verifier/release incorpora `BootstrapFreshnessFloor`:

```text
minimum_checkpoint_height
minimum_checkpoint_hash?
minimum_signer_set_epoch
minimum_policy_epoch
issued_in_release_id
```

Un fresh install **MUST NOT** accettare stato inferiore al floor della propria release/verifier.

Questo impedisce a un RPC/peer di congelare un verifier recente su uno stato precedente al floor.

Limite inevitabile: un verifier autentico ma obsoleto, ottenuto esso stesso solo da canali controllati dall'attaccante, non può sapere magicamente che esiste una versione/floor più recente. L'assurance della freshness del verifier deriva da un canale/bootstrap anchor indipendente.

## 6. Verified time

```text
VerifiedTimeAnchor {
    finalized_height
    finalized_time
    observed_monotonic_time
    max_clock_skew
}
```

Preferire validity in height/epoch. Wall clock locale è ausilio UX, non authority esclusiva.

## 7. Device record privacy — V1 semplificato

V1 **non richiede** che il contratto provi pubblicamente quale RootIdentity possiede un device record.

Il record opaco contiene soltanto stato necessario al lookup/revocation:

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

La legittimità del device per un peer deriva da `DeviceAuthorizationDelegation -> DeviceCertificate -> DeviceKey possession`, verificata endpoint-to-endpoint.

Il control-plane limita spam/creazione tramite fee/sponsorship/anti-abuse ma non deve introdurre per forza un mapping pubblico RootIdentity→device.

## 8. Device quota

`max_devices` V1 può essere product/service policy del client ufficiale. Non è security/interoperability invariant del protocollo.

Un futuro hard enforcement privacy-preserving può usare credential/nullifier/ZK reviewati, ma non è blocker del core V1 finché il progetto non sostiene che la quota sia anti-tamper.

## 9. DeviceControlKey

Ogni `DeviceRecord` possiede una control key scoped e non usata come network identity.

La private control key deve restare nell'authorization/recovery state, non essere confusa con la DeviceKey operativa.

Azioni consentite:

```text
rotate record key epoch
revoke record
update narrowly scoped record state
```

La control key non firma sessioni/chat e non autorizza altri device.

## 10. Revocation state

La semantica canonica è in [`REVOCATION.md`](REVOCATION.md).

RPC `not found` non equivale a non-revoca. Il `ChainAdapter` deve fornire inclusion/non-inclusion semantics univoche e test vector per device, authorization epoch e root transition state.

## 11. Rendezvous write authorization

TTL/slot secrecy non impediscono overwrite dopo che uno slot viene osservato.

Per ogni pairwise direction/epoch:

```text
PairRendezvousSecret
 -> deterministic RendezvousWriteKeypair
 -> write_public_key
 -> slot_id = H(domain || write_public_key || epoch || direction)
```

Il contratto accetta `RendezvousRecord`/`RecoveryBeacon` solo quando:

```text
slot derivation matches
write_signature valid
generation monotonic
expiry/size bounds valid
```

Una osservazione pubblica dello slot non concede la private write key.

## 12. Active state bounded — TTL non basta

Temporary state deve implementare almeno uno tra:

- overwrite dello stesso slot;
- bounded epoch ring/bucket;
- permissionless `prune_expired`;
- storage rent/lease;
- explicit delete/refund/bounded bounty.

Una nuova map key infinita per epoch/rinnovo è vietata.

Acceptance test:

```text
simulate N renewals/expiries
 -> active state converges to configured bound
 -> expired keys reclaimable/removed
 -> payer/refund behavior bounded
```

La chain history archiviale resta osservabile.

## 13. User Root key hierarchy

```text
RootRecoveryKey
 -> DeviceAuthorizationDelegation
 -> DeviceCertificate
 -> DeviceKey
```

La RootRecoveryKey non è daily operational key.

## 14. UserRecoveryPolicy / compromise recovery

Per poter recuperare da **compromissione** della root, non soltanto da perdita, deve esistere una independent recovery authority precommitted prima dell'incidente.

Schema canonico: `user-recovery-policy`.

Esempio:

```text
recovery key/share commitments
threshold
recovery delay blocks
policy commitment
```

Se non esiste questa seconda authority, possedere la stessa RootRecoveryKey rende proprietario e ladro crittograficamente indistinguibili. In quel profilo Freedom non rivendica compromise recovery.

## 15. UserRootRotation

Schema canonico: `user-root-rotation`.

Due modalità:

```text
NORMAL
  old-root continuity proof

COMPROMISE_RECOVERY
  independent recovery quorum proof
  + recovery delay
```

Race resolution è definita dalla policy/activation height; una old root compromessa non può annullare unilateralmente una recovery quorum transition valida solo perché possiede ancora la vecchia key.

## 16. Contract / adapter governance

Production sceglie:

```text
A. immutable verification/security core
B. threshold-governed upgrade path
```

Se upgradeabile:

```text
ContractUpgradeManifest
current_code_hash
new_code_hash
migration_hash
activation_height
rollback_floor
threshold signatures
```

Requisiti:

- threshold almeno equivalente a CriticalSecurityPolicy;
- public timelock non-emergency;
- code hash verificabile;
- deterministic/versioned migration;
- accepted contract lineage client-side;
- no silent contract-address swap;
- rollback floor;
- emergency authority non installa codice arbitrario da sola.

## 17. Governance quorum trust assumption

`3-of-5` elimina una singola chiave unilaterale, ma non elimina collusione/compromissione del quorum.

Production deve usare per quanto praticabile:

- signer custoditi in operator/custody domains differenti;
- hardware/offline custody separata;
- nessun singolo secret manager/account capace di estrarre un quorum;
- public signer-set transition/transparency records;
- periodic custody audit.

Se un singolo soggetto possiede unilateralmente abbastanza credenziali per raggiungere il quorum, il progetto non può tradurre `3-of-5` in “nessun singolo attore amministrativo”.

## 18. Signer-set transition

```text
next_epoch == previous_epoch + 1
previous set threshold authorizes
next set accepts
activation height monotonic
highest accepted epoch persisted
old set cannot reactivate itself
```

Quorum-loss recovery usa recovery set/manifest pinned in anticipo con threshold/timelock più forte.

## 19. SecurityPolicy / ReleaseStatus anti-rollback

Persistire almeno:

```text
highest_signer_set_epoch
highest_policy_epoch
highest_release_status_epoch
highest_verified_checkpoint
accepted_contract_lineage
```

Un oggetto validamente firmato ma inferiore al floor/highest-seen rilevante non sovrascrive stato recente.

## 20. ChainAdapter migration

Un `ChainMigrationManifest` firmato da solo **non basta** a trasformare stato arbitrario in stato legittimo.

La migration richiede `StateMigrationProof`:

```text
source finalized checkpoint
source export root
migration program hash
migration input commitment
target imported state root
verification artifact
```

Il verifier deve poter controllare che il target root derivi dalla source secondo la migration rule deterministicamente definita o tramite una prova verificabile appropriata.

Il quorum può autorizzare **quale migration rule/version usare**, non sostituire arbitrariamente lo stato senza verification artifact.

## 21. Acceptance gates

Prima della mainnet/interoperabilità production:

- fresh-install stale-checkpoint/bootstrap-floor tests;
- finality/checkpoint/state-proof vectors con RPC honest/stale/forked/malicious;
- revocation inclusion/non-inclusion/freshness vectors;
- rendezvous overwrite/front-run/replay tests;
- active-state storage convergence;
- RootRecoveryKey/DeviceControlKey separation;
- `UserRecoveryPolicy` + compromise recovery race/timelock tests;
- contract upgrade threshold/timelock oppure immutable-core verification;
- signer-set transition + quorum-loss recovery;
- signer custody/operator assumptions documentate;
- verified time rollback/forward tests;
- deterministic `StateMigrationProof` verification.
