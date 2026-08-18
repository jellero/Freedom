# Freedom — Blockchain Layer

## 1. Scelta iniziale

La prima implementazione blockchain di Freedom usa **NEAR Testnet**.

La scelta non rende NEAR parte del protocollo applicativo. Tutto l'accesso alla chain passa attraverso `ChainAdapter`.

Obiettivi del layer:

- registrare l'identità crittografica dei device;
- permettere key rotation e revocation;
- fornire rendezvous di fallback;
- minimizzare numero e frequenza delle scritture;
- non memorizzare messaggi, media o cronologie;
- non diventare necessario durante una sessione attiva.

## 2. ChainAdapter

```text
interface ChainAdapter {
    registerDevice(record)
    resolveDevice(deviceId)
    rotateDeviceKey(deviceId, newKey, proof)
    revokeDevice(deviceId, proof)

    readRendezvous(slot)
    writeRendezvous(slot, record)

    verifyState(proofOrResponse)
}
```

La logica core non deve importare SDK NEAR direttamente.

## 3. Device registry

Stato concettuale:

```text
devices[DeviceID] = DeviceRecord
```

```text
DeviceRecord {
    version
    identity_public_key
    key_epoch
    status
    protocol_version
    updated_at
}
```

Il key del mapping è il `DeviceID`; non è necessario ripeterlo nel value on-chain se l'encoding del contratto non lo richiede.

## 4. Device registration

Al primo avvio:

```text
1. generate DeviceID
2. generate identity key pair
3. private key -> platform secure storage
4. public key + DeviceID -> NEAR Testnet registry
5. wait for confirmed state
6. app becomes READY
```

La private key non deve essere esportata per eseguire la registrazione.

## 5. Key rotation

Il record è stabile per DeviceID.

```text
epoch 1 -> PK1
epoch 2 -> PK2
epoch 3 -> PK3
```

Una rotazione non cambia DeviceID.

Il contratto deve impedire rollback non autorizzati a epoch precedenti.

## 6. Revocation

Un device revocato deve risultare inequivocabilmente non valido per nuovi handshake.

```text
status = REVOKED
```

La policy di recovery/ownership dell'identità verrà definita separatamente; non va inventata dentro il transport protocol.

## 7. Rendezvous non è routing persistente

Freedom non scrive continuamente IP o route on-chain.

La blockchain è l'ultima fase del recovery di percorso:

```text
known direct route?
  yes -> use it
  no

known NAT candidate?
  yes -> try it
  no

known relay candidate?
  yes -> try it
  no

chain rendezvous
```

## 8. Primo rendezvous

Il QR può fornire una capability casuale:

```text
rendezvous_capability = random(256 bit)
```

Lo slot del primo contatto può essere derivato da capability + direzione + epoch.

Il formato deve evitare un mapping pubblico semplice del tipo:

```text
AliceDeviceID -> BobDeviceID
```

La capability può essere:

- one-time;
- temporanea;
- rigenerabile dall'utente.

## 9. Rendezvous successivi

Dopo una sessione autenticata, A e B derivano un pair secret.

```text
PairRendezvousSecret = KDF(authenticated_session_material, context)
```

Il secret viene memorizzato localmente e non pubblicato.

Gli slot cambiano per epoch temporale/protocollare:

```text
slot_A(epoch)
slot_B(epoch)
```

Questo evita un identificatore on-chain statico facilmente correlabile nel tempo.

## 10. Record

```text
RendezvousRecord {
    version
    sequence
    expires_at
    ciphertext
}
```

Il contratto non ha bisogno di interpretare `ciphertext`.

Il payload cifrato contiene i route candidate attuali e materiale effimero necessario a provare la connessione.

## 11. Read-before-write

Questa è una regola core del progetto.

```text
remote = READ(remote_slot)

if remote usable:
    CONNECT(remote)
    return

local = READ(local_slot)

if local usable:
    // abbiamo già annunciato: non riscrivere
    WAIT_AND_POLL(remote_slot)
    return

WRITE(local_slot, current_offer)
WAIT_AND_POLL(remote_slot)
```

Un timeout di connessione non autorizza una nuova write immediata. Il client applica backoff e aggiorna soltanto quando il record locale è scaduto o le informazioni sono materialmente cambiate secondo policy.

## 12. Concorrenza

Gli slot sono direzionali. Se A e B perdono il route nello stesso istante possono entrambi pubblicare una sola offerta nei rispettivi slot senza collisione.

Al polling successivo ciascuno vede il record dell'altro e tenta il percorso.

Non serve eleggere un leader.

## 13. Expiration

Un rendezvous è temporaneo.

```text
expires_at = short TTL
```

Dopo la riconnessione non viene cancellato esplicitamente: la cancellazione costerebbe una scrittura aggiuntiva senza beneficio necessario.

I client ignorano record scaduti.

## 14. Sequence

Per ogni direzione viene mantenuto un sequence monotono locale.

Un client rifiuta:

```text
record.sequence <= lastAcceptedSequence
```

salvo regole esplicite di recovery ancora da definire.

## 15. Metadata minimization

On-chain non devono comparire in chiaro, salvo necessità tecnica non evitabile:

- IP associato a DeviceID;
- porta associata a DeviceID;
- lista contatti;
- conversation ID;
- message ID;
- stato online globale;
- nome/email/telefono;
- payload applicativo.

Gli endpoint di rete vivono nel ciphertext del rendezvous.

## 16. RPC strategy

Un client non deve codificare un unico endpoint RPC come requisito permanente.

`NearChainAdapter` deve supportare:

- lista di provider configurabile;
- rotazione/fallback;
- timeout;
- confronto di risposte quando necessario;
- progressiva integrazione di verifica light-client/proof dove disponibile e appropriata.

Gli RPC trasportano dati chain; non costituiscono l'identità dell'utente.

## 17. Fee model

Su testnet vengono usate risorse di test.

Per mainnet la policy economica è una decisione separata. Possibili modelli:

- piccola riserva nativa per device;
- meta-transazioni pagate da relayer indipendenti;
- combinazione dei due.

Un eventuale relayer di fee non deve poter firmare al posto del DeviceID né diventare obbligatorio per il protocollo.

Il client store non deve essere trasformato inutilmente in wallet/trading application.

## 18. Contract scope

Il primo contratto Freedom deve essere piccolo.

API concettuale:

```text
register_device
get_device
rotate_device_key
revoke_device
get_rendezvous
put_rendezvous
```

Non implementare nel contratto:

- chat;
- inbox;
- file storage;
- social graph;
- presence continua;
- relay payload;
- content moderation.

## 19. Storage bounds

Il rendezvous deve avere una strategia bounded.

Gli slot vengono sovrascritti/aggiornati e i record scaduti non devono produrre una crescita infinita dello stato logico per la stessa coppia/capability.

La gestione fisica dello storage e dei depositi NEAR sarà definita nel contratto in modo da non permettere scritture gratuite illimitate.

## 20. Testnet milestone

Acceptance criteria del primo chain milestone:

- due Android generano DeviceID distinti;
- entrambi registrano la public key su NEAR Testnet;
- A risolve B e viceversa;
- key rotation cambia epoch senza cambiare DeviceID;
- revocation viene osservata dal client;
- QR produce una rendezvous capability;
- A può pubblicare un record cifrato;
- B può trovarlo e decifrarlo;
- un secondo tentativo legge il record esistente e non genera una write inutile;
- nessun message body compare in stato chain.
