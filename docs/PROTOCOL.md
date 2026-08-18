# Freedom — Protocol Specification

Status: **design draft**

Questa specifica descrive gli oggetti logici e i flussi minimi del protocollo. Gli encoding binari definitivi e le primitive crittografiche concrete verranno fissati prima dell'interoperabilità pubblica.

## 1. Principi

- ogni oggetto parsabile è versionato;
- gli oggetti di identità vengono verificati contro la blockchain;
- le informazioni di routing non autenticano l'identità;
- la blockchain non trasporta contenuti applicativi;
- i relay inoltrano, non archiviano;
- se il destinatario è offline non esiste consegna distribuita automatica;
- read-before-write è obbligatorio per il rendezvous;
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
    local_sequence
    remote_sequence
}
```

Il segreto non viene scritto on-chain.

Gli slot sono derivati in modo direzionale e rotante:

```text
slot_local(epoch)
slot_remote(epoch)
```

Il formato concreto della KDF viene fissato con la crypto suite.

## 7. Rendezvous record

```text
RendezvousRecord {
    version
    sequence
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

## 8. Read-before-write

Algoritmo normativo per A che deve raggiungere B dopo aver perso tutti i route:

```text
remote = chain.readRendezvous(remote_slot)

if remote.valid && !remote.expired && remote.sequence > stored_remote_sequence:
    try(remote.route_candidates)
    DO_NOT_WRITE
else:
    if local slot does not already contain a valid current record:
        chain.writeRendezvous(local_slot, local_record)
```

Il client non deve riscrivere un record soltanto perché non ha ancora ricevuto risposta. Deve rispettare TTL, sequence e backoff.

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

## 15. Replay protection

Requisiti:

- reject dei sequence già accettati;
- finestra limitata se il trasporto ammette reorder;
- memoria bounded;
- nessun reset senza transizione autenticata di sessione.

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

Il messaggio è valido solo dentro una sessione autenticata.

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
PERSISTED
READ   // opzionale
```

## 18. Offline behavior

Freedom non implementa un global offline store.

Se non esiste alcuna sessione e il remote device non è raggiungibile:

```text
OutgoingMessage.status = WAITING_FOR_PEER
```

Il plaintext o ciphertext resta esclusivamente nello storage locale del mittente finché il peer torna raggiungibile o l'utente lo elimina.

Relay e blockchain non ricevono il messaggio applicativo.

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

La perdita del relay può causare perdita dei pacchetti in volo; il livello endpoint gestisce retry all'interno dei limiti della sessione.

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
- retry/backoff;
- message local queue.

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
- TTL/sequence;
- route payload cifrato.

### M3 — secure communication

- mutual authentication;
- direct debug route;
- encrypted text;
- ACK;
- replay rejection;
- fresh session on reconnect.

### M4 — network paths

- candidate observation;
- NAT traversal;
- route update in-session;
- relay forward-only.
