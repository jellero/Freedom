# Freedom — Blockchain Layer

## 1. Scelta iniziale

La prima implementazione blockchain di Freedom usa **NEAR Testnet** attraverso `ChainAdapter`.

NEAR non è parte del wire protocol applicativo e deve poter essere sostituito senza cambiare DeviceID, session format o semantica della comunicazione.

La chain è un **control-plane distribuito e verificabile**. Non trasporta messaggi, media, chiamate o APK.

Funzioni previste:

- identity registry;
- key rotation/revocation;
- fallback rendezvous e recovery beacon;
- RootIdentity/account ownership commitment;
- entitlement/licenze e limiti device;
- PurchaseIntent/PaymentAttestation minimali;
- emergency/security bulletin;
- signed release manifest/policy;
- stato economico bounded necessario alla sponsorship.

## 2. Separazione RootIdentity / DeviceIdentity

```text
RootIdentity   -> recovery, ownership, entitlement, device authorization
DeviceIdentity -> autenticazione operativa del singolo endpoint
```

Il `DeviceID` continua a identificare un device nel protocollo di sessione. La RootIdentity non viene usata come chiave di chat.

Dettagli: [`ACCOUNT_RECOVERY_LICENSES.md`](ACCOUNT_RECOVERY_LICENSES.md).

## 3. Installazione locale senza write obbligatoria

Il semplice install non deve generare automaticamente costo/stato on-chain:

```text
install
 -> generate RootIdentity locally
 -> generate DeviceIdentity locally
 -> generate Recovery Kit
 -> 0 mandatory chain writes
```

La prima registrazione avviene quando l'identità deve diventare verificabile per l'uso effettivo del protocollo e può essere sponsorizzata secondo la policy anti-abuso.

Questo evita di pagare storage per installazioni mai utilizzate.

## 4. ChainAdapter

Interfaccia concettuale estesa:

```text
interface ChainAdapter {
    registerRoot(...)
    registerDevice(...)
    resolveDevice(...)
    rotateDeviceKey(...)
    revokeDevice(...)
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

    verifyState(...)
}
```

La logica core non deve importare SDK NEAR direttamente.

## 5. Device registry

Stato concettuale minimale:

```text
devices[DeviceID] = DeviceRecord

DeviceRecord {
    version
    identity_public_key
    key_epoch
    status
    protocol_version
}
```

Il mapping deve essere compatto. Non memorizzare PII, contatti, route persistenti o history.

## 6. Sponsored registration / anti-Sybil

Freedom può sponsorizzare la prima registrazione Free tramite fee relayer/treasury, ma non deve offrire write illimitate a chiunque.

Pipeline:

```text
new RootIdentity
 -> valid signature
 -> adaptive anti-abuse proof
 -> sponsorship not already consumed
 -> relayer rate limit
 -> global sponsorship budget
 -> register
```

La policy iniziale prevede:

- installazione locale gratuita senza write;
- una prima RootIdentity sponsorizzabile;
- proof-of-work leggero/adattivo o primitive equivalente;
- fee relayer multipli con rate limit indipendenti;
- budget globale bounded;
- nessuna dipendenza obbligatoria da SMS, PayPal o numero di telefono per l'anti-Sybil.

Un attacco alle nuove registrazioni non deve interrompere gli utenti già registrati.

Dettagli: [`REGISTRATION_ECONOMICS.md`](REGISTRATION_ECONOMICS.md).

## 7. Key rotation e revocation

```text
epoch 1 -> PK1
epoch 2 -> PK2
...
```

Una rotazione incrementa `key_epoch` senza cambiare DeviceID. Un device revocato deve risultare non valido per nuovi handshake.

La RootIdentity recuperata può autorizzare la sostituzione/revoca di device secondo la policy di recovery.

## 8. Entitlement e limiti device

La licenza appartiene alla RootIdentity/account commitment.

```text
FreedomEntitlement {
    account_commitment
    tier
    entitlement_epoch
    max_devices
    expires_at?
    status
}
```

La chain deve far rispettare:

```text
active_devices <= max_devices
```

Policy iniziale: Free = 1 device attivo; i tier pagati possono avere più slot secondo il piano.

Gli slot devono essere progettati con commitment opachi, evitando un elenco pubblico `Account -> DeviceID[]`.

## 9. Contatti Free e privacy

Il piano Free prevede 10 contatti attivi, ma la lista contatti **non deve essere pubblicata in chiaro sulla chain**.

La rubrica resta locale/cifrata. Se è necessario enforcement resistente a client modificati, usare slot/commitment opachi che permettano il conteggio senza pubblicare il social graph.

## 10. Rendezvous

Freedom non scrive continuamente IP o route on-chain.

Ordine generale:

```text
known route -> try
NAT candidate -> try
relay/shielded candidate -> try
all fail -> chain rendezvous/recovery
```

Il primo contatto usa una `rendezvous_capability` casuale; dopo handshake i peer derivano `PairRendezvousSecret` e slot opachi/direzionali.

Ogni `RendezvousRecord` è autosufficiente:

```text
RendezvousRecord {
    version
    expires_at
    ciphertext
}
```

Il payload cifrato può contenere route/relay candidate e materiale effimero. Non esiste una revisione storica obbligatoria.

## 11. Read-before-write

```text
remote = READ(remote_slot)
if remote usable -> CONNECT, DO_NOT_WRITE

local = READ(local_slot)
if local usable -> WAIT/POLL
else -> WRITE(new independent record)
```

TTL e backoff limitano write inutili.

## 12. Recovery beacon / Adaptive Defense

Quando tutte le route falliscono, peer già autenticati possono pubblicare `RecoveryBeacon` pairwise, cifrati, opachi e a TTL breve.

Il beacon indica attività recente, non presenza globale continua.

Se entrambi i peer sono recentemente attivi sul control-plane ma il data-plane non funziona, il client può classificare probabile route failure/interferenza e tentare path/relay/transport alternativi.

Dettagli: [`ADAPTIVE_DEFENSE.md`](ADAPTIVE_DEFENSE.md).

## 13. Payment state

La chain non processa necessariamente il pagamento fiat; conserva solo stato minimo/verificabile necessario a collegare un acquisto all'entitlement.

```text
PurchaseIntent {
    purchase_ref_hash
    account_commitment
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

PayPal può essere verificato da worker privati outbound-only senza API pubblica Freedom. Crypto verificabile on-chain può attivare direttamente l'entitlement.

Nessun merchant secret deve stare nell'APK.

Dettagli: [`PAYMENTS.md`](PAYMENTS.md).

## 14. Emergency bulletins e release manifest

La chain può distribuire piccoli oggetti firmati:

```text
EmergencyBulletin
SecurityPolicy
FreedomRelease
```

Per bulletin geografici, la posizione utente resta locale e viene confrontata localmente con lo scope.

L'APK non viene memorizzato on-chain. La chain contiene hash, versione, signing fingerprint e source descriptors; il file può arrivare da store, mirror temporanei, peer/relay o futuri transport.

Dettagli: [`EMERGENCY_UPDATES.md`](EMERGENCY_UPDATES.md).

## 15. Security governance

Release manifest e policy critiche production dovrebbero supportare firme threshold/multi-key e rotazione/revoca delle signing key.

Una security policy non deve diventare un kill-switch commerciale: preferire la disabilitazione selettiva della superficie vulnerabile mantenendo recovery/update disponibili quando sicuro.

## 16. Metadata minimization

On-chain non devono comparire in chiaro, salvo necessità non evitabile:

- IP/porta associati a DeviceID;
- lista contatti/social graph;
- posizione precisa utente;
- conversation/message ID;
- presence globale continua;
- email/telefono;
- dati PayPal identificativi;
- payload applicativo;
- APK binary.

## 17. RPC strategy

`NearChainAdapter` deve supportare più provider, fallback, timeout, confronto/verifica di stato e progressiva proof/light-client verification dove appropriata.

Nessun RPC è un trust anchor dell'identità.

## 18. Fee/gas/storage model

L'utente compra servizi Freedom, non NEAR.

Le operazioni essenziali possono essere sponsorizzate da treasury/fee relayer. L'utente non deve possedere obbligatoriamente wallet/saldo NEAR.

Il costo deve crescere con identità realmente usate ed eventi rari, non con il traffico applicativo:

```text
message            -> 0 chain writes
media/call frames  -> 0 chain writes
active session     -> 0 heartbeat writes
```

Scritture possibili: registrazione, rotation/revocation, device activation, rendezvous/recovery, entitlement/payment state, policy/release publishing.

Lo storage permanente deve essere minimale; stato temporaneo deve essere bounded e reclaimable/riutilizzabile quando possibile.

## 19. Contract scope

Il contratto non implementa:

- chat/inbox;
- media/file storage;
- social graph pubblico;
- presenza continua;
- relay payload;
- APK storage.

Ogni endpoint di write deve avere bounds, autorizzazione e protezioni contro storage exhaustion.

## 20. Testnet acceptance criteria aggiornati

Prima della mainnet devono essere testati almeno:

- RootIdentity/DeviceIdentity separate;
- install senza write automatica;
- sponsored registration con anti-abuse e rate limit;
- device rotation/revocation;
- recovery su nuovo device senza clonare la DeviceKey;
- enforcement `max_devices`;
- rendezvous read-before-write;
- RecoveryBeacon bounded;
- PurchaseIntent/attestation idempotenti;
- entitlement ripristinabile dalla RootIdentity;
- bulletin/security/release manifest verificabili;
- nessun message body, contatto leggibile, posizione utente o APK nello stato chain.
