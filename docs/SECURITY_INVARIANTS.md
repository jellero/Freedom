# Freedom — Security & Trust Invariants

Status: **canonical / normative design rules**

Questo documento definisce proprietà che una implementazione Freedom compatibile **MUST** rispettare. In caso di conflitto, queste invarianti prevalgono. Dettagli del control-plane: [`CONTROL_PLANE_SECURITY.md`](CONTROL_PLANE_SECURITY.md).

## 1. Trust model

Freedom separa rigidamente:

```text
RootRecoveryKey              -> cold recovery / user-root continuity
DeviceAuthorizationKey       -> delegated device authorization epoch
DeviceCertificate            -> autorizzazione offline della DeviceKey
DeviceKey                    -> autenticazione operativa del device
DeviceRecordCommitment       -> handle opaco del control-plane
PairwiseContactAlias         -> identità specifica della relazione
TransportToken               -> route/circuito temporaneo
Session keys                 -> E2EE effimera
EntitlementCommitment        -> capacità/licenza
PaymentBindingCommitment     -> binding economico separato
SponsorshipCommitment        -> sponsorship separata
VerifiedControlPlaneCheckpoint -> root verificata dello stato control-plane
```

Nessuno di questi elementi deve essere riutilizzato automaticamente come un altro.

## 2. Primitive vietate

Freedom Protocol **MUST NOT** introdurre:

- un identificatore globale user/device richiesto dal network layer;
- `RootIdentity` o `DeviceRecordCommitment` come routing identifier;
- messaggi, file, audio o video on-chain;
- mailbox on-chain;
- inbox persistente su relay;
- automatic offline delivery queue nel protocollo base;
- store-and-forward automatico per peer offline;
- social graph pubblico leggibile;
- mapping pubblico leggibile `RootIdentity -> devices[]`;
- mapping pubblico leggibile `identity -> IP/relay/route`;
- un server centrale di delivery obbligatorio;
- un singolo RPC/provider/relay/egress obbligatorio;
- una master decryption key;
- una singola production key con potere amministrativo totale;
- una singola Full Access key capace di sostituire silenziosamente il contratto/security core production;
- semantica `transaction hash == success`;
- downgrade silenzioso da una policy protetta a un path meno sicuro;
- una struttura temporanea on-chain che crea chiavi per sempre senza reclaim/overwrite bounded.

## 3. Synchronous communication invariant

```text
active authenticated session -> transmit now
no active authenticated session -> fail/discard now
```

Un endpoint può ritentare esplicitamente durante una nuova sessione, ma il protocollo non crea automaticamente una consegna futura.

## 4. Device authorization offline-verifiable

Ogni DeviceKey usata per nuovi handshake deve essere autorizzata da una catena verificabile offline:

```text
RootRecoveryKey
 -> DeviceAuthorizationDelegation
 -> DeviceCertificate
 -> DeviceKey possession proof
```

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

Requisiti:

- `RootRecoveryKey` non è la chiave operativa quotidiana;
- il peer può verificare delegation, certificate, network, epoch, expiry e binding alla relazione attesa senza una RPC nel packet hot path;
- revocation/rotation/freshness arrivano dal control-plane o da cache **crittograficamente verificata**;
- uno stato di revoca troppo vecchio può impedire nuovi handshake secondo policy;
- un relay non autentica il DeviceCertificate.

## 5. Control-plane authenticity

Una risposta JSON di un RPC non costituisce stato verificato.

Per oggetti security-sensitive:

```text
NetworkAnchor
 -> VerifiedControlPlaneCheckpoint
 -> state root
 -> inclusion/non-inclusion proof
 -> canonical object
```

Il client **MUST** verificare checkpoint/finality e prova di stato secondo il `ChainAdapter` concreto prima di trattare come autorevoli revocation, signer set, `SecurityPolicy`, `ReleaseStatus`, device state o entitlement.

Una cache conserva checkpoint/epoch highest-seen e non accetta rollback.

## 6. Device/account privacy proof

La sola domain separation non basta se una firma RootIdentity pubblica appare accanto alla DeviceKey.

Il target production per device-slot enforcement è una prova privacy-preserving:

```text
DeviceAuthorizationProof
public outputs:
  device_record_commitment
  device_public_key
  key_epoch
  slot_nullifier
  authorization_policy_epoch
```

Il proof dimostra appartenenza/autorizzazione e slot valido senza pubblicare quale RootIdentity sia.

Se una implementazione testnet pubblica una RootIdentity/root commitment/firma linkabile insieme al device, deve dichiarare esplicitamente che `RootIdentity -> device` è osservabile e non può rivendicare la privacy production.

## 7. Handshake invariant e anti-downgrade

Il transcript deve legare almeno:

```text
network_id
protocol_version
expected pairwise relationship
local/remote pairwise aliases or commitments
local/remote DeviceCertificate hash/proof
local/remote key_epoch
local_supported_versions[]
remote_supported_versions[]
local_supported_suites[]
remote_supported_suites[]
selected_version
selected_suite
ephemeral key material
nonces
session_id
```

La selezione deve essere deterministica o comunque verificabile contro una policy minima locale. Un MITM non deve poter rimuovere una suite/versione migliore e far firmare ai peer una scelta inferiore ancora formalmente valida.

L'handshake prova contemporaneamente:

1. contact identity attesa;
2. DeviceCertificate/delegation validi;
3. possesso DeviceKey;
4. materiale effimero corrente;
5. negoziazione non downgraded sotto policy.

## 8. Forward secrecy e key lifetime

Freedom Communication **MUST** offrire forward secrecy tra sessioni.

La compromise futura di una DeviceKey non deve consentire di derivare session key di sessioni concluse precedentemente, salvo compromissione contemporanea dell'endpoint/session state.

Per sessioni lunghe:

- traffic key lifetime bounded per tempo/byte/frame;
- rekey autenticato prima del limite;
- messaging/control keys separate da media keys;
- una costruzione ratchet standard/reviewata è il target per post-compromise security;
- `SESSION_REKEY_REQUIRED` è un event/failure normativo.

## 9. Transport semantic contract

Il protocollo distingue almeno:

```text
RELIABLE_ORDERED_STREAM
UNRELIABLE_DATAGRAM
```

Text, handshake, rekey, ACK applicativi e control frames richiedono un canale affidabile/ordinato oppure un reliability layer esplicito.

Media può usare datagram/stream separati. Perdita/reordering media non deve bloccare automaticamente chat/control tramite un sequence space globale unico.

Ogni `TransportAdapter` dichiara capability semantiche; il session layer non assume TCP quando usa transport differenti.

## 10. Domain separation dei commitment

Derivare commitment separati:

```text
DeviceAuthorizationCommitment
EntitlementCommitment
PaymentBindingCommitment
SponsorshipCommitment
```

Requisiti:

- non riutilizzare lo stesso commitment stabile tra domini quando evitabile;
- rendezvous deriva da `PairRendezvousSecret`;
- provider payment references non contengono identity/network/social identifiers Freedom in plaintext salvo necessità;
- domain separation impedisce riuso diretto, **non garantisce da sola unlinkability transazionale**.

Quando serve unlinkability più forte usare voucher/nullifier/blind credential/ZK appropriati.

## 11. Verified finality — tx hash != success

```text
submit signed operation
 -> wait acceptable finality
 -> inspect execution outcome
 -> reject Failure / partial failure
 -> verify resulting state proof
 -> verify exact expected transition
 -> only then commit local state transition
```

Si applica almeno a device/root changes, entitlement, sponsorship, payment redemption, policy, release/status/signer-set, contract migration/upgrade e recovery state transitions.

Label `ACTIVE`, `VERIFIED`, `REVOKED`, `PAID`, `CURRENT` non derivano dal solo transaction hash.

## 12. Bounded control-plane state

TTL logico non è sufficiente.

Ogni record temporaneo deve avere:

- size bound;
- TTL/epoch;
- rate limit;
- authorization;
- **reclaim/overwrite fisicamente implementabile**;
- upper bound dello stato attivo derivabile.

Sono ammessi overwrite, ring/bucket bounded, lease/rent, `prune_expired` permissionless e refund/bounty bounded.

È vietato creare una nuova map key per ogni rinnovo/epoch senza cancellazione/reclaim.

La storia archiviale della blockchain può restare osservabile: Freedom non la descrive come cancellata.

## 13. Verified time

Expiry/freshness non dipendono esclusivamente dal wall clock locale.

```text
VerifiedTimeAnchor {
    finalized_height
    finalized_time
    observed_monotonic_time
    max_clock_skew
}
```

Preferire policy che includano height/epoch. Clock rollback/forward locale non deve riattivare certificati/policy vecchi o causare rollback di highest-seen state.

## 14. User RootIdentity compromise / rotation

Recovery per perdita e recovery da compromissione sono distinti.

```text
LOST_DEVICE        -> revoke device / new DeviceKey
ROOT_COMPROMISE    -> UserRootRotation / new root epoch
```

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

Una root compromessa non viene riparata continuando a usarla.

## 15. Pairwise state recovery e multi-device

`PairSecret`, `PairwiseContactAlias` e `PairRendezvousSecret` non vengono pubblicati sul control-plane.

Sono consentiti due recovery path:

```text
A. authenticated device-to-device transfer
B. encrypted PairwiseRecoveryBundle protetto da RecoveryStateKey
```

```text
PairwiseRecoveryBundle {
    version
    contacts[]
    pairwise_state_ciphertext
    state_epoch
    integrity
}
```

Se nessun device sopravvive e non esiste un bundle pairwise valido, il protocollo **MUST dichiarare che l'ownership è recuperata ma i contatti richiedono re-bootstrap**. Non inventare recovery del social graph on-chain.

## 16. Pairwise privacy claim boundary

Alias pairwise riducono correlazione infrastrutturale, ma se due contatti vedono lo stesso root proof/certificate material possono colludere e riconoscere la stessa persona.

Quindi il claim base è:

> pairwise routing/rendezvous identities non sono globalmente riutilizzate.

Non dichiarare unlinkability contro contatti colludenti senza anonymous credentials/pairwise-scoped identity proof specificamente implementati.

## 17. First-contact substitution

La crittografia può autenticare perfettamente il descriptor ricevuto ma non sapere che esso appartiene umanamente a “Bob”.

Per primo contatto:

- QR/link/capability sostituito prima del bootstrap è una minaccia esplicita;
- safety code/fingerprint/out-of-band verification deve essere disponibile;
- stato UI distingue almeno `BOOTSTRAP_UNVERIFIED` da `CONTACT_VERIFIED` quando l'utente effettua verifica indipendente;
- copiare un QR valido non consente impersonation della relativa private key, ma sostituire l'intero descriptor prima del bootstrap può stabilire una relazione valida con l'attaccante.

## 18. Production governance: no super-admin

In production:

```text
ReleaseAuthorization    >= 3-of-5
ReleaseRevocation       >= 3-of-5
CriticalSecurityPolicy  >= 3-of-5
ContractUpgrade         >= 3-of-5 + timelock
GovernanceRootRotation  >= 3-of-5 + recovery procedure
Emergency advisory      separate scoped key/set + TTL
```

Payment attestors, entitlement authorities, relay/egress operators ed emergency keys non possono autorizzare da soli nuove release o contract code arbitrario.

Una singola Full Access key production capace di sostituire il contratto viola questa invariante.

## 19. Signer-set transition / anti-rollback

Un nuovo signer set richiede una transizione esplicita:

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

- `next_epoch = previous_epoch + 1`;
- previous set autorizza;
- next set accetta;
- activation height monotonic;
- client conserva highest-seen epoch;
- old set non può riattivarsi;
- quorum-loss recovery usa un recovery set/manifest pinned in anticipo, con threshold/timelock più forte, non una singola emergency key.

Lo stesso principio anti-rollback vale per `SecurityPolicy`, `ReleaseStatus`, accepted contract lineage e `ChainAdapter` migration.

## 20. Contract / ChainAdapter governance

Production sceglie:

```text
immutable security core
oppure
threshold-governed upgrade path
```

Un upgrade/migration usa code hash, migration hash, activation height, timelock, threshold signatures e rollback floor. Il client mantiene accepted contract/adapter lineage; un RPC non può ridefinire il contract address o network anchor.

Dettagli: [`CONTROL_PLANE_SECURITY.md`](CONTROL_PLANE_SECURITY.md).

## 21. Release authenticity e first-install root

Una release installabile verifica:

```text
exact artifact SHA-256
+ canonical FreedomRelease threshold signatures
+ Android package signer / authorized lineage
+ ReleaseStatus != REVOKED
+ current-enough SecurityPolicy
+ anti-downgrade policy
```

First sideload usa un `BootstrapTrustAnchor` pinned indipendente dalla source dei byte.

## 22. Release schema single source of truth

```text
FreedomRelease {
    manifest_version
    release_id
    version_code
    version_name
    package_id
    artifact_sha256
    artifact_size
    signing_cert_fingerprint
    signing_lineage_commitment?
    min_supported_version
    min_secure_version
    criticality
    release_locator_hash
    issued_at
    signer_set_epoch
    signatures[]
}
```

Nessuna variante incompatibile tra documenti.

## 23. Payment privacy boundary

Il pagamento non contiene RootIdentity/DeviceRecordCommitment/pairwise alias in plaintext.

Per ridurre linkage pubblico payment→entitlement, il flow preferito è:

```text
verified payment
 -> one-time EntitlementVoucher / blind credential
 -> redemption transaction with nullifier
 -> entitlement transition
```

`PaymentAttestation` non deve necessariamente contenere `EntitlementCommitment` direttamente.

Timing correlation può restare possibile e va dichiarata.

## 24. Contact-slot policy

Il limite commerciale di contatti **non è una proprietà di interoperabilità/security del Freedom Protocol**.

V1:

- il client ufficiale può applicare localmente `base_contact_slots` come product policy;
- un peer remoto non rifiuta una sessione perché il client mittente ha modificato la propria quota contatti;
- il control-plane non pubblica social graph per far rispettare tale limite.

Un futuro enforcement resistente a client modificati richiede una costruzione privacy-preserving separata (nullifier/ZK/credential) prima di diventare normativo.

## 25. Relay/Shield diversity

Più `relay_id` non significano automaticamente più operatori indipendenti.

Relay descriptor/candidate distingue:

```text
self-declared metadata
observed metadata
verified/provenance metadata
```

Il selector non usa geografia/provider/operator self-declared come prova di diversity. Shield deve usare un vero circuit protocol con chiavi per-hop/layered forwarding prima di poter sostenere claim multi-hop forti.

## 26. Security labels are derived state

`VERIFIED`, `E2EE`, `ACTIVE`, `SHIELDED`, `SUSPECTED`, `REVOKED` derivano da verifiche/osservazioni implementate.

`SUSPECTED` è inferenza di rete, non prova di censura/sorveglianza.

## 27. Fail closed / fail explicit

Failure di autenticazione, proof verification, release verification, downgrade, revocation, policy rollback o governance transition non diventa successo silenzioso.

Reachability può degradare solo entro policy autorizzata. `strict/Shield/kill-switch` vieta fallback direct silenzioso.

## 28. Review gates

Prima dell'interoperabilità pubblica devono esistere:

- encoding canonici e test vector;
- negative handshake + offer-stripping/downgrade tests;
- replay/rekey tests;
- reliable-stream/datagram semantic tests;
- parser/resource-limit tests;
- control-plane checkpoint/state-proof/finality tests;
- stale/forked/malicious RPC tests;
- storage reclaim/bounded-state stress tests;
- RootRecoveryKey/delegation/UserRootRotation tests;
- pairwise backup/re-bootstrap tests;
- first-contact substitution tests;
- signer-set/contract-upgrade/rollback tests;
- release/first-install verification tests;
- payment voucher/nullifier privacy tests se abilitati;
- independent cryptographic/security review delle primitive normative;
- threat-model review separata per Communication, Shield e Gateway.
