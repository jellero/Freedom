# Freedom — Architecture

## 1. Definizione

Freedom è un protocollo decentralizzato di comunicazione. L'architettura separa cinque responsabilità:

1. **identity** — chi è il device;
2. **rendezvous** — come due device online si ritrovano quando non hanno più un percorso valido;
3. **routing/transport** — come i pacchetti attraversano la rete;
4. **secure session** — come gli endpoint si autenticano e derivano chiavi;
5. **application** — messaggi, file, audio e video.

La blockchain interviene solo nei primi due punti. Il traffico applicativo rimane sempre off-chain.

## 2. Componenti

```text
                    +----------------------+
                    |      Blockchain      |
                    | Device Registry      |
                    | Fallback Rendezvous  |
                    +----------+-----------+
                               |
                      verify / rendezvous
                               |
      +------------------------+------------------------+
      |                                                 |
+-----v------+                                    +-----v------+
| Device A   |                                    | Device B   |
| Freedom    |                                    | Freedom    |
+-----+------+                                    +-----+------+
      |                                                 |
      | direct / NAT traversal / relay                 |
      +================ E2EE ===========================+
                               |
                      optional relay peers
```

## 3. Identity plane

Ogni installazione autorizzata possiede un `DeviceID` stabile e una identity key custodita localmente.

```text
DeviceRecord {
    version
    device_id
    identity_public_key
    key_epoch
    status
    updated_at
}
```

La private key non viene pubblicata né trasferita.

Il `DeviceID` non deriva direttamente dalla current public key, perché la chiave deve poter ruotare senza cambiare identità.

Gli stati minimi previsti sono:

```text
ACTIVE
REVOKED
```

Una rotazione incrementa `key_epoch`. Un client deve rifiutare prove firmate con un epoch revocato o superato quando la chain indica una chiave più recente.

## 4. Contact bootstrap

Il contatto non viene scoperto casualmente. Viene scambiato intenzionalmente tramite QR, link, NFC o altro canale esterno.

```text
FreedomContact {
    version
    network
    device_id
    rendezvous_capability
    expires_at?
}
```

La `rendezvous_capability` è un valore casuale ad alta entropia. Può essere one-shot oppure ruotare periodicamente.

Serve al primo contatto per creare un rendezvous opaco senza dover pubblicare una relazione leggibile `sender_device_id -> recipient_device_id`.

## 5. Pair rendezvous secret

Dopo il primo handshake autenticato, i due endpoint derivano e persistono localmente un segreto di coppia:

```text
PairRendezvousSecret_AB
```

Da questo vengono derivati slot on-chain opachi e rotanti.

Esempio concettuale:

```text
slot_A_to_B(epoch) = H(secret || "A->B" || epoch)
slot_B_to_A(epoch) = H(secret || "B->A" || epoch)
```

La direzionalità evita collisioni tra scritture simultanee e permette la regola semplice read-before-write.

## 6. Rendezvous rule

La blockchain non è una tabella di routing continuamente aggiornata.

Viene usata solo quando non esiste più alcun percorso Freedom valido tra due endpoint che devono comunicare.

Per A che vuole ritrovare B:

```text
1. controlla i route candidate locali già conosciuti
2. tenta direct / NAT traversal / relay già noti
3. se tutti falliscono, legge lo slot B->A
4. se trova un record valido, usa quello e NON scrive
5. se non trova niente, pubblica il proprio record nello slot A->B
6. continua a leggere lo slot remoto fino a riconnessione/scadenza
```

B applica la stessa regola.

Non serve un leader di coppia.

## 7. Rendezvous record

Il record esterno deve rivelare il minimo indispensabile.

```text
RendezvousRecord {
    version
    opaque_slot
    sequence
    expires_at
    ciphertext
}
```

Il plaintext cifrato può contenere:

```text
RendezvousPayload {
    sender_device_id
    sender_key_epoch
    transport_nonce
    route_candidates[]
    relay_candidates[]
    ephemeral_transport_key
}
```

Il record ha TTL breve e sequence monotono. Non viene cancellato dopo l'uso: scade naturalmente, evitando una scrittura aggiuntiva.

## 8. Route candidates

Un indirizzo IP da solo non rappresenta un percorso.

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

`endpoint` può includere IP e porta; `candidate_type` può distinguere local, reflexive, direct, relay o altri tipi futuri.

Freedom deve monitorare la raggiungibilità del percorso, non soltanto il cambio IP.

Sotto NAT possono cambiare:

- porta pubblica;
- mapping;
- protocollo disponibile;
- interfaccia;
- relay necessario;
- reachability pur mantenendo lo stesso IP.

## 9. Route maintenance

Dopo che A e B hanno una sessione valida, gli aggiornamenti di rete passano dentro la sessione E2EE.

```text
RouteUpdate {
    sequence
    candidates[]
    relay_candidates[]
    expires_at
}
```

Non viene effettuata alcuna scrittura blockchain per un semplice cambio IP/porta se esiste ancora almeno un percorso attraverso cui gli endpoint possono scambiarsi l'aggiornamento.

La chain torna in gioco solo dopo la perdita di tutti i percorsi conosciuti.

## 10. Path selection

Ordine iniziale preferito:

```text
1. direct candidate già verificato
2. NAT traversal / hole punching
3. relay candidate già conosciuto
4. blockchain rendezvous
```

Il path selector locale può usare:

- RTT;
- stabilità recente;
- directness;
- costo del relay;
- disponibilità del trasporto;
- durata prevista del mapping;
- preferenze di rete dell'utente.

Nessuna autorità centrale decide il percorso.

## 11. Relay architecture

Un relay Freedom è un endpoint di forwarding transitivo.

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

Requisiti:

- il payload applicativo resta E2EE;
- niente mailbox persistenti;
- niente storage indefinito;
- buffer limitati;
- TTL breve;
- quote per peer/connessione;
- possibilità di interrompere il servizio di relay localmente;
- nessuna fiducia necessaria per l'autenticità del contenuto.

Un relay può osservare metadati necessari al forwarding e può droppare o ritardare pacchetti. Per questo non viene trattato come componente fidato.

## 12. Endpoint storage

Solo gli endpoint partecipanti conservano la cronologia della conversazione.

```text
Blockchain  -> no message storage
Relay       -> transient buffer only
Device A/B  -> local conversation storage
```

Se B è offline:

```text
A -> B unavailable
```

il messaggio rimane pending sul dispositivo A. Freedom non lo replica automaticamente nella rete.

## 13. Secure session

Trovare un endpoint non significa aver autenticato il device.

A risolve il `DeviceRecord` di B sulla blockchain e ottiene la public key attesa. B fa lo stesso con A.

L'handshake deve dimostrare bilateralmente il possesso delle private key e legare:

```text
protocol_version
network_id
A_device_id
B_device_id
A_key_epoch
B_key_epoch
A_ephemeral_key
B_ephemeral_key
A_nonce
B_nonce
negotiated_suite
session_id
```

Una modifica di uno di questi campi deve invalidare il transcript.

## 14. Session lifecycle

Ogni nuova connessione genera materiale effimero nuovo.

Dopo l'handshake vengono create chiavi separate per direzione e per classi di traffico quando necessario.

La specifica deve mantenere separati almeno:

- messaging/session keys;
- route control keys;
- media keys per chiamate.

La rotazione interna delle chiavi deve poter avvenire senza blockchain finché l'identity key on-chain non cambia.

## 15. Blockchain adapter

Il core non chiama direttamente API NEAR.

```text
interface ChainAdapter {
    registerDevice(...)
    resolveDevice(...)
    rotateDeviceKey(...)
    revokeDevice(...)
    readRendezvous(...)
    writeRendezvous(...)
    verifyState(...)
}
```

La prima implementazione è `NearChainAdapter` su NEAR Testnet.

## 16. Bootstrap della rete

Freedom deve distinguere il bootstrap dalla fiducia.

Un client può usare più fonti iniziali per trovare peer/relay o RPC, ma nessuna di esse autentica un DeviceID. L'autenticità deriva dalla chain e dalle firme.

Le fonti bootstrap devono essere sostituibili e multiple, evitando che un singolo endpoint diventi requisito permanente del protocollo.

## 17. Applicazione

Sopra la sessione sicura vivono:

```text
text messages
ACK
attachments
call signaling
voice
video
route updates
session control
```

La blockchain non è nel packet hot path.

## 18. Piattaforme

### Android

Prima piattaforma. Può sperimentare più liberamente listener, NAT traversal e relay.

### iOS

Il protocollo resta identico, ma il client deve adattarsi alle limitazioni di background del sistema. Eventuali meccanismi di wake della piattaforma devono essere trattati come hint, non come trasporto o trust anchor.

## 19. Proprietà architetturali

Freedom mira a mantenere queste invarianti:

- identità indipendente dal percorso;
- percorso indipendente dalla sessione applicativa;
- sessione autenticata indipendentemente dal relay;
- blockchain non necessaria per ogni pacchetto o ogni cambio route;
- relay incapace di leggere il contenuto;
- nessun componente singolo necessario per conservare i messaggi;
- scritture on-chain proporzionali agli eventi di identità e ai casi di perdita completa del route, non al volume della comunicazione.
