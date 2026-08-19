# Freedom — Control-Plane Security & State Verification

Status: **canonical / normative design rules**

Questo documento chiude le proprietà di sicurezza del control-plane che non possono restare dipendenti dalla buona fede di RPC, operatori o chiavi amministrative.

Normative baseline: [`SECURITY_INVARIANTS.md`](SECURITY_INVARIANTS.md).

## 1. RPC non è trust

Un provider RPC può mentire, servire stato stale, omettere record o censurare richieste. Per stato security-sensitive il client **MUST NOT** trasformare una risposta RPC non provata in verità.

La catena di verifica concettuale è:

```text
NetworkAnchor
 -> finalized checkpoint
 -> state root
 -> inclusion/non-inclusion proof
 -> canonical object
 -> local state transition
```

Oggetti come revocation state, signer set, `SecurityPolicy`, `ReleaseStatus`, device authorization ed entitlement richiedono stato verificabile riconducibile a un checkpoint finalizzato.

## 2. VerifiedControlPlaneCheckpoint

```text
VerifiedControlPlaneCheckpoint {
    network_id
    chain_adapter_id
    finalized_height
    finalized_block_hash
    state_root
    finalized_time
    consensus_or_finality_proof
    verifier_version
}
```

Il `ChainAdapter` deve definire come verificare `consensus_or_finality_proof` per l'implementazione concreta.

Per NEAR questo significa usare primitive/prove coerenti con il modello di finalità e stato della chain, non fidarsi del solo JSON restituito da un RPC.

## 3. VerifiedStateProof

```text
VerifiedStateProof<T> {
    checkpoint
    key
    value_or_absence
    inclusion_or_non_inclusion_proof
    object_hash
}
```

Il verifier controlla:

```text
checkpoint valid
state root binding valid
proof valid
object canonical encoding valid
object policy/epoch valid
```

Solo dopo il risultato diventa `VERIFIED_STATE`.

## 4. Cache verificata

La cache locale conserva:

```text
object
verified checkpoint
highest_seen_epoch/height
freshness class
monotonic observation time
```

Una cache può sostenere funzionamento offline/degradato, ma non può essere retrocessa da una risposta RPC più vecchia.

Regola:

```text
new_verified_height < highest_seen_height -> reject rollback
new_object_epoch < highest_seen_epoch     -> reject rollback
```

## 5. Verified time

Expiry e freshness non dipendono esclusivamente dall'orologio modificabile del device.

```text
VerifiedTimeAnchor {
    finalized_height
    finalized_time
    observed_monotonic_time
    max_clock_skew
}
```

Preferire quando possibile vincoli espressi anche in height/epoch:

```text
valid_from_height
expires_after_height
policy_epoch
```

Il wall clock locale è un ausilio UX. Il tempo monotono locale impedisce rollback dell'orologio durante la stessa installazione; un nuovo checkpoint finalizzato rinnova l'anchor temporale.

## 6. Device authorization privacy

Una firma RootIdentity pubblicata direttamente accanto alla DeviceKey ricreerebbe un mapping leggibile `RootIdentity -> device`.

Production privacy target:

```text
registered ownership set
       |
       | anonymous membership / authorization proof
       v
DeviceAuthorizationProof
       |
public outputs:
  device_record_commitment
  device_public_key
  key_epoch
  slot_nullifier
  authorization_policy_epoch
```

Il proof dimostra che una ownership valida ha autorizzato il device e possiede uno slot disponibile senza pubblicare quale RootIdentity sia.

La costruzione concreta può usare anonymous credentials / ZK membership + authorization proof standard e reviewato. Finché una implementazione testnet pubblica un root commitment o una firma linkabile insieme al device, **MUST dichiarare esplicitamente che il mapping è osservabile** e non può rivendicare la proprietà privacy production.

`DeviceAuthorizationCommitment` e `slot_nullifier` non sono network/contact identifiers.

## 7. Multi-device slot enforcement

Obiettivo:

```text
active_devices <= max_devices
```

senza una lista pubblica dei device di una persona.

Modello normativo target:

```text
DeviceSlotProof {
    authorization_policy_epoch
    slot_nullifier
    device_record_commitment
    proof
}
```

Un nullifier impedisce il riuso dello stesso slot; il proof lega lo slot a una ownership/entitlement valido senza rivelarne l'identità pubblica.

## 8. Active state bounded — TTL non basta

TTL logico non equivale a rimozione fisica dello stato attivo.

Ogni record temporaneo del contratto deve avere una strategia concreta di reclaim:

```text
RendezvousRecord
RecoveryBeacon
PurchaseIntent
temporary route/recovery state
```

Sono consentiti:

- overwrite dello stesso slot quando sicuro;
- ring/bucket di epoch bounded;
- `prune_expired` permissionless;
- refund/storage-credit al proprietario o bounty bounded al pruner;
- cancellazione esplicita dopo expiry;
- storage rent/lease bounded.

Una struttura che crea una nuova map key per ogni epoch senza cancellazione è **vietata**.

Il requisito riguarda lo **stato attivo** del protocollo; la storia archiviale della blockchain può restare osservabile e non viene descritta come cancellata.

## 9. Storage invariant

Per ogni tipo temporaneo deve esistere un upper bound derivabile:

```text
max record size
x max active slots per subject/domain
x max retained epochs
```

Acceptance test obbligatorio:

```text
simulate N renewals/expiries
 -> active state converges to configured bound
 -> expired keys are reclaimable/removed
 -> storage payer/refund behavior remains bounded
```

## 10. User RootIdentity key hierarchy

La chiave di recovery dell'utente non deve essere la chiave operativa quotidiana.

```text
RootRecoveryKey        -> cold recovery / root continuity
DeviceAuthorizationKey -> delegated authorization epoch
DeviceKey              -> per-device operational authentication
Session keys           -> ephemeral traffic
```

`RootRecoveryKey` dovrebbe restare offline/non-residente nell'uso normale quando la piattaforma e UX lo consentono.

## 11. DeviceAuthorizationDelegation

```text
DeviceAuthorizationDelegation {
    root_epoch
    authorization_public_key
    authorization_epoch
    capabilities = DEVICE_AUTHORIZE | DEVICE_REVOKE
    valid_from
    expires_at
    root_recovery_signature
}
```

I `DeviceCertificate` possono essere firmati dalla chiave di autorizzazione delegata, con transcript che include il delegation proof.

Compromettere la chiave delegata non equivale automaticamente a compromettere la RootRecoveryKey.

## 12. UserRootRotation

Per compromissione della RootIdentity serve una transizione distinta dal semplice restore:

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

La policy deve distinguere:

```text
LOST_DEVICE          -> revoke device / issue new DeviceKey
LOST_RECOVERY_COPY   -> regenerate backup if root still controlled
ROOT_COMPROMISE      -> rotate RootIdentity epoch
```

Una root compromessa non viene “riparata” continuando a usare la stessa chiave.

## 13. Contract / adapter governance

“Nessun super-admin” comprende anche il codice del control-plane.

Production **MUST** scegliere uno dei due modelli:

```text
A. immutable verification contract / immutable security core
B. threshold-governed upgrade path
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

Requisiti:

- threshold governance almeno equivalente alla `CriticalSecurityPolicy`;
- timelock pubblico per upgrade non-emergency;
- code hash verificabile;
- migrazione deterministica/versionata;
- client con contract lineage/accepted anchor;
- nessun cambio silenzioso di contract address;
- rollback sotto `rollback_floor` rifiutato;
- emergency authority non può installare codice arbitrario da sola.

Una Full Access key singola capace di sostituire il contratto production viola l'invariante “nessun super-admin”.

## 14. Governance signer-set transition

Un nuovo signer set non è valido solo perché auto-firmato.

```text
SignerSetTransition {
    role
    previous_epoch
    next_epoch
    previous_set_commitment
    next_set_commitment
    activation_height
    previous_set_threshold_signatures[]
    next_set_acceptance_signatures[]
}
```

Regole:

```text
next_epoch == previous_epoch + 1
previous set authorizes transition
next set accepts transition
activation height monotonic
client stores highest accepted epoch
old set cannot reactivate itself
```

## 15. Signer-set recovery

Per perdita di quorum deve esistere un recovery path separato, pinned prima dell'incidente, per esempio:

```text
GovernanceRecoverySet commitment
+ longer timelock
+ stronger threshold
+ public recovery manifest
```

Il recovery path non può equivalere a una singola emergency key.

## 16. SecurityPolicy / ReleaseStatus anti-rollback

Client/verifier conserva:

```text
highest_signer_set_epoch
highest_policy_epoch
highest_release_status_epoch/version
highest_verified_checkpoint
```

Una policy/revoca validamente firmata ma più vecchia non sovrascrive stato più recente già osservato.

Per un device appena installato, il bootstrap anchor + checkpoint verification impediscono l'accettazione arbitraria di una storia alternativa servita da un singolo peer/RPC.

## 17. Control-plane migration

Migrare da NEAR a un altro `ChainAdapter` non significa accettare un nuovo trust root senza prova.

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

La migrazione è governance-sensitive, threshold, versionata e anti-rollback.

## 18. Acceptance gates

Prima della mainnet:

- finality/checkpoint verifier testato con RPC onesto, stale, forked e malevolo;
- inclusion/non-inclusion proof test vector;
- rollback di checkpoint/policy/signer set rifiutato;
- device authorization privacy proof o claim privacy ridimensionato esplicitamente;
- active-state storage converge a bound dopo stress test;
- RootRecoveryKey/DeviceAuthorizationKey separation testata;
- `UserRootRotation` simulata dopo compromissione;
- contract upgrade governance threshold/timelock testata oppure core dichiarato immutabile;
- signer-set transition + quorum-loss recovery testati;
- verified time con clock rollback/forward testato.
