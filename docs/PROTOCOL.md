# Freedom — Protocol Specification

Status: **design draft**

Questa specifica descrive gli oggetti logici e i flussi minimi del protocollo. Gli encoding binari definitivi e le primitive crittografiche concrete verranno fissati prima dell'interoperabilità pubblica.

## 1. Principi

- ogni oggetto parsabile è versionato;
- gli oggetti di identità vengono verificati contro la blockchain;
- le informazioni di routing non autenticano l'identità;
- la blockchain non trasporta contenuti applicativi;
- i relay inoltrano, non archiviano;
- i contenuti applicativi vengono inviati soltanto dentro una sessione autenticata attiva;
- se il destinatario non è raggiungibile o non esiste una sessione attiva, il messaggio non viene accodato per consegna futura e viene scartato;
- read-before-write è obbligatorio per il rendezvous;
- ogni rendezvous on-chain è autosufficiente e non dipende da revisioni precedenti;
- una sessione attiva gestisce direttamente i propri route update.

## 2. Header

```text
Header {
    protocol_version
    object_type
}
```

Versioni obbligatorie sconosciute devono fallire closed.

## 3. DeviceID

`DeviceID` è un identificatore stabile a 256 bit generato con entropia crittografica.

Non contiene PII e non è derivato direttamente dalla chiave pubblica corrente.

```text
DeviceRecord {
    version
    device_id
    identity_public_key
    key_epoch
    status
    protocol_version
    updated_at
}
```

## 4. Key rotation

```text
RotateDeviceKey {
    device_id
    old_epoch
    new_epoch
    new_public_key
    authorization_proof
}
```

Una rotazione valida incrementa l'epoch. La specifica della prova dipende dal modello del contratto chain, ma il client deve sempre poter determinare la chiave corrente e lo stato di revoca.

`key_epoch` appartiene all'identità del device; non è una revisione del rendezvous.

## 5. Contact descriptor

```text
FreedomContact {
    version
    network_id
    device_id
    rendezvous_capability
    expires_at?
}
```

La capability non è una identity key e non consente impersonation. È una capability di bootstrap/rendezvous.

Il QR è solo una rappresentazione del descriptor.

## 6. Pair rendezvous state

Dopo il primo handshake i peer derivano:

```text
PairRendezvousState {
    peer_device_id
    rendezvous_secret
}
```

Il segreto non viene scritto on-chain.

Gli slot possono essere derivati in modo direzionale e rotante:

```text
slot_local(context)
slot_remote(context)
```

La derivazione deve essere deterministica per entrambe le parti nel contesto corrente e non deve richiedere la conoscenza di un record precedente.

Il formato concreto della KDF viene fissato con la crypto suite.

## 7. Rendezvous record

Ogni record on-chain è indipendente. Per il lettore è sempre equivalente a una nuova **rev 0**.

```text
RendezvousRecord {
    version
    expires_at
    ciphertext
}
```

Il key/slot blockchain è opaco e derivato da capability o pair secret.

Il ciphertext protegge:

```text
RendezvousPayload {
    sender_device_id
    sender_key_epoch
    rendezvous_nonce
    route_candidates[]
    relay_candidates[]
    ephemeral_transport_public_key
}
```

Il payload deve essere autenticato e cifrato.

Non esiste `sequence`, `revision`, `previous_record_hash` o altro requisito che obblighi il destinatario a conoscere il rendezvous precedente.

## 8. Read-before-write

Algoritmo normativo per A che deve raggiungere B dopo aver perso tutti i route:

```text
remote = chain.readRendezvous(remote_slot)

if remote.valid && !remote.expired:
    try(remote.route_candidates)
    DO_NOT_WRITE
else:
    local = chain.readRendezvous(local_slot)

    if local.valid && !local.expired:
        WAIT_AND_POLL(remote_slot)
    else:
        chain.writeRendezvous(local_slot, new_independent_record)
```

Il client non deve riscrivere un record soltanto perché non ha ancora ricevuto risposta. Deve rispettare TTL e backoff.

Quando serve un nuovo rendezvous, questo viene generato da zero con nuovo nonce/materiale effimero e deve poter essere interpretato senza stato storico del rendezvous precedente.

## 9. Route candidate

```text
RouteCandidate {
    transport
    endpoint
    candidate_type
    priority
    observed_at
    expires_at
}
```

L'endpoint è una struttura trasporto-specifica. Per UDP/TCP può comprendere IP e porta.

Candidate type iniziali:

```text
LOCAL
OBSERVED
DIRECT
RELAY
```

## 10. Relay candidate

```text
RelayCandidate {
    relay_id
    endpoint
    transport
    capability_token?
    expires_at
}
```

Un relay candidate non implica fiducia nel relay.

## 11. Route update in-session

```text
RouteUpdate {
    sequence
    candidates[]
    relay_candidates[]
    issued_at
    expires_at
}
```

Questo oggetto viaggia dentro la sessione autenticata.

Il `sequence` di `RouteUpdate` appartiene alla sessione attiva e serve a ordinamento/anti-replay. Non è una revisione blockchain.

Finché almeno un percorso valido permette di scambiare `RouteUpdate`, non si usa la blockchain per aggiornare il routing della coppia.

## 12. Handshake

L'handshake produce:

```text
SessionContext {
    session_id
    local_device_id
    remote_device_id
    local_key_epoch
    remote_key_epoch
    negotiated_version
    negotiated_crypto_suite
    tx_keys
    rx_keys
    created_at
}
```

Il transcript deve includere:

```text
network_id
protocol_version
A_device_id
B_device_id
A_key_epoch
B_key_epoch
A_ephemeral_key
B_ephemeral_key
A_nonce
B_nonce
crypto_suite
session_id
```

Entrambi gli endpoint verificano la controparte usando la current public key risolta tramite `ChainAdapter`.

## 13. Blockchain freshness durante l'handshake

Prima del primo handshake con un DeviceID sconosciuto, il client deve risolvere il record chain.

Per contatti già noti può usare cache verificata entro una policy di freshness, ma deve rivalidare quando:

- l'epoch remoto cambia;
- una firma non verifica;
- riceve un'indicazione di key rotation;
- la cache supera il limite previsto;
- la sessione viene ristabilita dopo un periodo lungo.

La policy concreta verrà definita con il chain adapter.

## 14. Encrypted frame

```text
EncryptedFrame {
    version
    session_hint
    sequence
    ciphertext
}
```

L'inner frame autenticato contiene:

```text
InnerFrame {
    frame_type
    session_id
    sequence
    payload
}
```

Il sequence è monotono per direzione e protetto dall'AEAD.

Questo sequence appartiene esclusivamente alla sessione crittografica e non ha alcuna relazione con i rendezvous on-chain.

## 15. Replay protection

Requisiti:

- reject dei sequence già accettati nella sessione;
- finestra limitata se il trasporto ammette reorder;
- memoria bounded;
- nessun reset senza transizione autenticata di sessione.

Il rendezvous on-chain non usa sequence storico: freshness e validità derivano dallo stato chain verificato, dallo slot atteso, da `expires_at` e dall'autenticazione del payload.

## 16. Text message

```text
ChatMessage {
    message_id
    conversation_id
    sender_device_id
    logical_sequence
    sent_at
    body
    reply_to?
}
```

Il messaggio è valido solo dentro una sessione autenticata attiva. Un tentativo di invio senza sessione attiva deve fallire e il payload non deve essere inserito in una coda di retry/offline delivery.

## 17. Delivery ACK

```text
MessageAck {
    message_id
    ack_type
    receiver_device_id
    logical_time
}
```

ACK iniziali:

```text
RECEIVED
READ   // opzionale
```

`RECEIVED` conferma la ricezione durante la sessione attiva; non implica persistenza su disco.

## 18. Synchronous / offline behavior

Freedom è sincrono by design e non implementa un global offline store né una mailbox locale di consegna futura.

Se non esiste una sessione autenticata attiva o il remote device non è raggiungibile:

```text
SEND
  |
  +-- active authenticated session? -- no --> DISCARD
  |
  +-- yes --> transmit --> ACK / session result
```

Il messaggio non viene depositato sulla blockchain, sui relay o in una coda locale in attesa che il peer torni online.

La perdita della sessione durante un invio può causare la perdita del messaggio in-flight. Un eventuale nuovo invio richiede un'azione esplicita dell'utente o una semantica applicativa definita sopra il protocollo base; non esiste retry asincrono implicito.

### 18.1 Live / ephemeral mode

I client Freedom possono offrire una modalità **Live** in cui la conversazione locale esiste soltanto durante la presenza attiva dell'utente nella chat/sessione.

In modalità Live:

- i messaggi della sessione non vengono aggiunti alla cronologia persistente;
- nessun contenuto della conversazione viene incluso in backup automatici;
- uscendo dalla chat, chiudendo l'app o terminando la sessione, il client elimina lo stato locale della conversazione Live;
- le chiavi di sessione effimere vengono distrutte quando la sessione termina;
- notifiche e preview non devono introdurre copie persistenti del plaintext.

Questa proprietà riguarda il comportamento del client Freedom locale. Non può impedire a un peer remoto, a un sistema operativo compromesso o a un dispositivo di acquisire autonomamente screenshot, registrazioni o copie del contenuto ricevuto.

## 19. Relay packet

```text
RelayPacket {
    version
    packet_id
    next_hop_token
    hop_limit
    expires_at
    ciphertext
}
```

Il relay deve rigettare:

- packet troppo grandi;
- TTL scaduti;
- hop limit esaurito;
- quota superata;
- token/capability non validi quando richiesti.

## 20. Relay storage semantics

Un relay può mantenere soltanto buffer necessari al forwarding immediato.

Non esiste una `StoreRequest` nel protocollo base.

La perdita del relay può causare perdita dei pacchetti in volo; il livello endpoint può segnalare il fallimento all'interno della sessione, ma non crea una coda di consegna asincrona.

## 21. Attachment

```text
AttachmentManifest {
    attachment_id
    media_type
    plaintext_size
    chunk_size
    chunks[]
    integrity
}
```

Gli attachment vengono trasferiti soltanto quando esiste una sessione/route attiva. Nessun chunk viene depositato automaticamente sulla blockchain o su relay persistenti.

## 22. Call signaling

```text
CallInvite {
    call_id
    media_capabilities
    transport_capabilities
}

CallAccept {
    call_id
    selected_capabilities
    candidates[]
}

CallCandidate {
    call_id
    candidate
}

CallEnd {
    call_id
    reason
}
```

Il signaling viaggia E2EE. Le media key sono separate dalle message key.

## 23. Presence

Presence è off-chain e opportunistica.

```text
Presence {
    state
    capabilities
    expires_at
}
```

Non deve generare scritture chain continue.

## 24. Errors

Classi minime:

```text
MALFORMED
UNSUPPORTED_VERSION
CHAIN_IDENTITY_NOT_FOUND
CHAIN_IDENTITY_REVOKED
KEY_EPOCH_MISMATCH
AUTHENTICATION_FAILED
REPLAY_DETECTED
ROUTE_UNAVAILABLE
RENDEZVOUS_EXPIRED
RELAY_REFUSED
PEER_OFFLINE
SESSION_REKEY_REQUIRED
```

## 25. Resource limits

Ogni implementazione deve avere limiti espliciti per:

- frame size;
- handshake object size;
- route candidates per record;
- relay buffer;
- connessioni simultanee;
- write frequency on-chain;
- retry/backoff del rendezvous e della creazione route;
- buffer bounded dei soli messaggi in-flight durante una sessione attiva.

## 26. Chain abstraction

Interfaccia concettuale:

```text
ChainAdapter {
    registerDevice
    resolveDevice
    rotateDeviceKey
    revokeDevice
    readRendezvous
    writeRendezvous
    verifyState
}
```

La prima implementazione usa NEAR Testnet.

## 27. First executable milestones

### M1 — identity

- generazione DeviceID;
- identity key nel keystore;
- registrazione su NEAR Testnet;
- resolve DeviceID;
- QR contact descriptor;
- key rotation/revocation di test.

### M2 — rendezvous

- capability bootstrap;
- opaque slots;
- read-before-write;
- TTL;
- rendezvous indipendenti sempre interpretabili come rev 0;
- route payload cifrato.

### M3 — secure communication

- mutual authentication;
- direct debug route;
- encrypted text;
- ACK;
- replay rejection;
- nessuna coda offline;
- fresh session on reconnect.

### M4 — network paths

- candidate observation;
- NAT traversal;
- route update in-session;
- relay forward-only.
