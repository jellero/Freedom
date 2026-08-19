# Freedom — Blockchain / Control Plane

Status: **canonical design draft**

Normative security rules: [`SECURITY_INVARIANTS.md`](SECURITY_INVARIANTS.md).

## 1. Ruolo

La prima implementazione usa **NEAR Testnet** tramite `ChainAdapter`.

NEAR non è parte del wire protocol e deve poter essere sostituito senza cambiare semantica della comunicazione, RootIdentity, DeviceCertificate o session format.

La chain è un **control-plane distribuito e verificabile**. Non trasporta messaggi, file, audio, video, Gateway payload o APK.

Funzioni previste:

- RootIdentity / ownership commitment;
- device authorization e record opachi;
- key rotation/revocation;
- rendezvous/recovery pairwise bounded;
- entitlement e limiti device;
- sponsorship/anti-abuse bounded;
- PurchaseIntent/PaymentAttestation minimali;
- emergency/security policy;
- release manifest/status e signer-set state.

## 2. Identity separation

Freedom non richiede un `DeviceID` globale.

```text
RootIdentity                    -> ownership / recovery
DeviceCertificate               -> autorizzazione offline DeviceKey
DeviceKey                       -> autenticazione operativa
DeviceRecordCommitment          -> handle opaco control-plane
PairwiseContactAlias            -> relazione specifica
TransportToken                  -> route/circuito temporaneo
```

Il control-plane non deve trasformare un commitment tecnico in username/contact/network endpoint.

## 3. Commitment domain-separated

Il control-plane **MUST NOT** usare un singolo commitment account-global per ogni funzione quando evitabile.

```text
DeviceAuthorizationCommitment
EntitlementCommitment
PaymentBindingCommitment
SponsorshipCommitment
```

I commitment devono essere derivati con domain separation esplicita.

Rendezvous e RecoveryBeacon derivano da `PairRendezvousSecret`, non da commitment account-global.

## 4. Installazione locale

```text
install
 -> generate RootIdentity locally
 -> generate DeviceKey locally
 -> generate DeviceRecordCommitment locally
 -> generate Recovery Kit
 -> 0 mandatory chain writes
```

La prima registrazione avviene quando serve stato verificabile del control-plane e può essere sponsorizzata secondo policy anti-abuso.

## 5. ChainAdapter

```text
interface ChainAdapter {
    registerRoot(...)
    registerDeviceRecord(...)
    resolveDeviceRecord(...)
    rotateDeviceKey(...)
    revokeDeviceRecord(...)
    activateDeviceSlot(...)
    revokeDeviceSlot(...)
    resolveEntitlement(...)

    readRendezvous(...)
    writeRendezvous(...)
    readRecoveryBeacon(...)
    writeRecoveryBeacon(...)

    readPurchaseIntent(...)
    readPaymentAttestation(...)

    readEmergencyBulletins(...)
    readSecurityPolicy(...)
    readReleaseManifest(...)
    readReleaseStatus(...)
    readSignerSet(...)

    verifyState(...)
    verifyFinalOutcome(...)
}
```

La logica core non importa SDK NEAR direttamente.

## 6. Verified finality — tx hash != success

Un transaction hash dimostra soltanto che una richiesta è stata sottoposta.

Per ogni write security-sensitive:

```text
submit signed operation
 -> wait acceptable finality
 -> inspect execution outcome
 -> reject Failure / partial failure
 -> read resulting state
 -> verify exact expected transition
 -> only then persist/display local success
```

Obbligatorio almeno per:

- root/device activation;
- rotation/revocation;
- device-slot changes;
- entitlement state;
- sponsorship consumption;
- payment effects;
- SecurityPolicy;
- release manifest/status/signer-set update;
- rendezvous/recovery write quando influenza la state machine.

Il client **MUST NOT** mostrare `ACTIVE`, `REVOKED`, `PAID`, `VERIFIED` o equivalente basandosi solo sull'hash della transazione.

## 7. Device registry

```text
device_records[DeviceRecordCommitment] = DeviceRecord

DeviceRecord {
    version
    device_public_key
    key_epoch
    status
    protocol_version
    authorization_proof
}
```

Il commitment è opaco e non viene usato come identificatore di rete.

## 8. DeviceCertificate e handshake offline

Il control-plane mantiene stato di autorizzazione/revoca, ma il peer non deve interrogare obbligatoriamente la chain durante ogni handshake.

La RootIdentity autorizza la DeviceKey tramite:

```text
DeviceCertificate {
    version
    network_id
    root_identity_commitment_or_proof
    device_public_key
    key_epoch
    protocol_version
    capabilities?
    issued_at
    expires_at
    certificate_id
    root_authorization_signature
}
```

Il peer verifica il certificato offline e usa chain/cache verificata per revocation/freshness secondo policy.

## 9. Sponsored registration / anti-Sybil

```text
new RootIdentity
 -> valid signature
 -> adaptive anti-abuse proof
 -> sponsorship commitment unused
 -> relayer rate limit
 -> global sponsorship budget
 -> verified finalized registration
```

Policy:

- install locale gratuita;
- sponsorship bounded;
- adaptive PoW o primitive equivalente;
- più fee relayer;
- nessun SMS/PayPal/telefono obbligatorio per identità Free.

Un attacco alle nuove registrazioni non interrompe gli utenti già registrati.

## 10. Rotation e revocation

```text
DeviceRecordCommitment X
  epoch 1 -> PK1
  epoch 2 -> PK2
```

Una rotazione valida incrementa `key_epoch`; una revoca rende il record non valido per nuovi handshake dopo freshness verification.

Dopo rotation/revocation il client accetta il nuovo stato solo dopo finalità + state verification.

## 11. Entitlement e limiti device

```text
FreedomEntitlement {
    entitlement_commitment
    tier
    entitlement_epoch
    max_devices
    base_contact_slots
    expires_at?
    status
}
```

`EntitlementCommitment` è domain-separated dalla identity/network state.

Il control-plane fa rispettare:

```text
active_devices <= max_devices
```

senza elenco pubblico leggibile `RootIdentity -> devices[]`.

## 12. Contatti e privacy

La rubrica resta locale/cifrata. Il piano Free può limitare slot attivi, ma il social graph non viene pubblicato.

Se serve enforcement resistente a client modificati, usare commitment/slot/nullifier opachi e bounded. Non pubblicare identità dei contatti.

## 13. Rendezvous pairwise

```text
known route -> try
NAT candidate -> try
relay/shielded candidate -> try
all fail -> pairwise rendezvous/recovery
```

Il primo contatto usa una `contact_capability`; dopo handshake si deriva `PairRendezvousSecret`.

```text
RendezvousRecord {
    version
    expires_at
    ciphertext
}
```

Il record pubblico non espone RootIdentity, DeviceRecordCommitment, IP o social edge leggibili.

## 14. Read-before-write

```text
remote = READ(remote_slot)
if remote usable -> CONNECT, DO_NOT_WRITE

local = READ(local_slot)
if local usable -> WAIT/POLL
else -> WRITE(new independent bounded record)
```

TTL/backoff/size bounds riducono write e storage.

## 15. RecoveryBeacon

RecoveryBeacon è pairwise, cifrato, opaco e TTL breve.

Serve a segnalare attività recente e route generation durante recovery; non è presence globale.

## 16. Payment state

```text
PurchaseIntent {
    purchase_ref_hash
    payment_binding_commitment
    product
    amount
    provider
    expires_at
}

PaymentAttestation {
    provider_transaction_commitment
    purchase_ref_hash
    amount
    status
    worker_id
    signature
}
```

`PaymentBindingCommitment` è domain-separated. Merchant/provider reference non contiene RootIdentity, DeviceRecordCommitment o pairwise alias in plaintext.

## 17. Release / policy state

Il control-plane contiene piccoli oggetti verificabili:

```text
EmergencyBulletin
SecurityPolicy
FreedomRelease
FreedomReleaseLocator
ReleaseStatus
SignerSet
```

L'APK resta off-chain.

Lo schema canonico `FreedomRelease` è definito in `SECURITY_INVARIANTS.md`, `PROTOCOL.md` e `EMERGENCY_UPDATES.md`; non devono esistere varianti incompatibili.

## 18. Governance production

“Nessun super-admin” è una proprietà crittografica.

Production **MUST** usare threshold/multi-key per:

```text
ReleaseAuthorization   >= 3-of-5
ReleaseRevocation      >= 3-of-5
CriticalSecurityPolicy >= 3-of-5
RootRotation           >= 3-of-5 + recovery procedure
```

Emergency signer separati possono avere scope ridotto e TTL breve, ma non autorizzano da soli nuove release arbitrarie.

Payment attestor, entitlement authority, release signer e emergency signer sono ruoli separati.

## 19. Metadata minimization

On-chain non devono comparire in chiaro, salvo necessità non evitabile:

- IP/porta associati a RootIdentity/device commitment;
- mapping RootIdentity -> device list;
- lista contatti/social graph;
- alias pairwise globalmente correlabili;
- posizione precisa;
- conversation/message ID;
- presence globale continua;
- email/telefono;
- dati payment identificativi;
- payload applicativo;
- APK binary.

Un commitment opaco non elimina correlazione temporale: activation/revocation/payment/rendezvous vanno analizzati anche come pattern osservabili.

## 20. RPC strategy

`ChainAdapter` supporta provider multipli, fallback, timeout, finality awareness e proof/light-client verification progressiva quando appropriata.

Nessun RPC è trust anchor.

Una singola RPC indisponibile non deve impedire la verifica offline di un `DeviceCertificate` valido quando la revocation freshness policy consente l'uso della cache.

## 21. Fee/gas/storage model

```text
message            -> 0 chain writes
media/call frames  -> 0 chain writes
active session     -> 0 heartbeat writes
```

Scritture possibili: eventi rari e bounded di identity/device, rendezvous/recovery, entitlement/payment, policy/release e sponsorship.

Ogni record temporaneo ha size bound + TTL/epoch + rate limit + overwrite/reclaim strategy quando possibile.

## 22. Contract scope vietato

Il contratto **MUST NOT** implementare:

- chat/inbox/mailbox;
- message/media storage;
- social graph pubblico;
- presence continua;
- relay payload;
- APK storage;
- generic Internet proxy state;
- una master key amministrativa/decryption.

## 23. Testnet acceptance criteria

Prima della mainnet:

- RootIdentity / DeviceKey / DeviceCertificate / DeviceRecordCommitment separati;
- nessun global DeviceID nel wire protocol;
- install senza write automatica;
- sponsored registration bounded;
- rotation/revocation e freshness testate;
- handshake possibile con certificato offline + cache verificata;
- enforcement `max_devices` privacy-preserving;
- rendezvous read-before-write;
- RecoveryBeacon bounded;
- nessuna mailbox/message storage;
- transaction Failure rilevata correttamente;
- state mismatch rilevato dopo finality;
- release/policy threshold governance testata;
- signer-set rotation/recovery testata;
- storage exhaustion e write-spam testati.
