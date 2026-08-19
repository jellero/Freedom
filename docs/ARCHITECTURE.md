# Freedom — Architecture

## 1. Definizione

Freedom è un protocollo decentralizzato di comunicazione sincrona. L'architettura separa sei responsabilità:

1. **identity** — chi è il device;
2. **verifiable registry** — come viene risolta e aggiornata l'identità;
3. **rendezvous** — come due device online si ritrovano quando non hanno più un percorso valido;
4. **routing/transport** — come i pacchetti attraversano la rete;
5. **secure session** — come gli endpoint si autenticano e derivano chiavi;
6. **application** — messaggi, file, audio e video.

La prima implementazione del registro usa una blockchain, ma il traffico applicativo rimane sempre off-chain.

## 2. Componenti

```text
                    +----------------------+
                    | Verifiable Registry  |
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
      | direct / NAT / relay / shielded path           |
      +================ E2EE ===========================+
                               |
                     optional relay nodes
            VPS / server / VM / community / device
```

Il registro è fondamentale come **funzione distribuita e verificabile** del trust model attuale. NEAR è soltanto la prima implementazione tramite `ChainAdapter` e deve essere sostituibile.

## 3. Identity plane

Ogni installazione possiede un `DeviceID` stabile e una identity key custodita localmente.

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

Una rotazione incrementa `key_epoch`. Un client deve rifiutare prove firmate con un epoch revocato o superato quando il registro indica una chiave più recente.

Il `DeviceID` è un'identità protocollare, non deve essere trattato come indirizzo di rete né esposto inutilmente ai trasporti.

## 4. Contact bootstrap

Il contatto viene scambiato intenzionalmente tramite QR, link, NFC o altro canale esterno.

```text
FreedomContact {
    version
    network
    device_id
    rendezvous_capability
    expires_at?
}
```

La `rendezvous_capability` è un valore casuale ad alta entropia. Può essere one-shot oppure temporanea.

Serve al primo contatto per creare un rendezvous opaco senza dover pubblicare una relazione leggibile `sender_device_id -> recipient_device_id`.

## 5. Pair rendezvous secret

Dopo il primo handshake autenticato, i due endpoint derivano e persistono localmente:

```text
PairRendezvousSecret_AB
```

Da questo vengono derivati slot opachi e rotanti. Il secret non viene pubblicato.

Gli slot sono direzionali, così A e B possono pubblicare offerte indipendenti senza collisione.

## 6. Rendezvous rule

Il registro non è una tabella di routing continuamente aggiornata.

Viene usato solo quando non esiste più alcun percorso Freedom valido tra due endpoint che devono comunicare.

Per A che vuole ritrovare B:

```text
1. controlla route candidate locali già conosciuti
2. tenta i percorsi consentiti dalla policy locale
3. se tutti falliscono, legge lo slot B->A
4. se trova un record valido, usa quello e NON scrive
5. se non trova niente, legge il proprio slot A->B
6. se esiste già un'offerta locale valida, non riscrive
7. altrimenti pubblica un nuovo record indipendente
8. continua a leggere lo slot remoto fino a riconnessione/scadenza
```

La regola è **read-before-write**.

## 7. Rendezvous record

Ogni rendezvous è autosufficiente e non richiede uno storico di revisioni precedenti.

```text
RendezvousRecord {
    version
    expires_at
    ciphertext
}
```

Il payload cifrato può contenere:

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

Il record ha TTL breve. Freshness e validità dipendono dallo slot atteso, dallo stato verificato del registro, da `expires_at`, dal nonce e dall'autenticazione del payload; non esiste una `sequence` storica del rendezvous.

## 8. Route candidates

Un indirizzo IP da solo non rappresenta un'identità né un percorso completo.

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

`endpoint` può includere IP e porta; `candidate_type` può distinguere local, observed, direct, relay o tipi futuri.

Freedom deve monitorare la raggiungibilità del percorso, non soltanto il cambio IP.

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

Il registro torna in gioco solo dopo la perdita di tutti i percorsi conosciuti.

## 10. Path selection e privacy

La selezione del path è locale e dipende dalla policy dell'utente/client.

Possibili classi:

```text
DIRECT
NAT_TRAVERSAL
RELAY
SHIELDED / MULTI-HOP
```

Il direct path è efficiente ma espone gli endpoint di rete ai peer. Per questo non deve essere obbligatorio: un client deve poter preferire relay o percorsi schermati quando la privacy di rete è prioritaria.

Il path selector può usare:

- policy privacy;
- RTT;
- stabilità recente;
- costo del relay;
- disponibilità del trasporto;
- durata prevista del mapping;
- rischio di censura/blocco;
- preferenze dell'utente.

Nessuna autorità centrale decide il percorso.

## 11. Path diversity e censorship resistance

Freedom mira a evitare single points of control.

```text
direct blocked      -> altro route
relay A blocked     -> relay B / altro path
RPC A blocked       -> RPC B
transport filtrato  -> transport alternativo
fee relayer down    -> altro relayer / pagamento compatibile
```

Bootstrap, RPC, relay, fee relayer e provider devono essere multipli e sostituibili.

Un IP, dominio o endpoint specifico non deve essere requisito permanente del protocollo.

Freedom non può garantire disponibilità se il dispositivo perde ogni forma di connettività, né anonimato assoluto contro un avversario globale capace di osservare l'intera rete.

### 11.1 Adaptive recovery control-plane

Il registro/rendezvous può essere usato come **control-plane di emergenza** quando il data-plane non passa.

Freedom non pubblica una presenza globale continua. Dopo la perdita completa del path, A e B possono pubblicare negli slot pairwise opachi un `RecoveryBeacon` cifrato e a TTL breve:

```text
RecoveryBeacon {
    version
    issued_at
    expires_at
    recovery_nonce
    route_generation
    state
    candidate_hints[]?
}
```

Un beacon valido prova attività recente, non presenza assoluta in tempo reale.

Se:

```text
A control-plane reachable        yes
B beacon recent                  yes
current A<->B data path          fail
```

A può classificare il caso come `INTERFERENCE_OR_ROUTE_FAILURE_SUSPECTED` e attivare route/relay/transport alternativi. B applica la stessa logica.

Il recovery può anche coordinare candidate alternativi attraverso il payload cifrato del rendezvous.

Vincoli:

- nessun heartbeat on-chain continuo durante una sessione valida;
- slot pairwise opachi e rotanti;
- payload cifrato;
- TTL breve;
- backoff e rate limit;
- stop delle write appena una sessione viene ristabilita;
- nessun claim che il sistema abbia rilevato sorveglianza passiva.

Dettagli: [`ADAPTIVE_DEFENSE.md`](ADAPTIVE_DEFENSE.md).

## 12. Relay architecture

Un relay Freedom è **una macchina o un dispositivo che esegue software di forwarding Freedom**.

Può essere fisicamente:

```text
VPS / VM
server dedicato
mini PC / Raspberry Pi
community node
managed node
private organization node
telefono / tablet / desktop Freedom opt-in
```

Un normale dispositivo Freedom può quindi svolgere due ruoli contemporanei ma logicamente separati:

```text
ENDPOINT  -> sessioni del proprio utente
RELAY     -> inoltro ciphertext di altri circuiti
```

Il ruolo relay non concede accesso alle chiavi E2EE delle sessioni inoltrate.

```text
RelayCandidate {
    relay_id
    relay_class
    endpoint
    transport
    capability_token?
    capacity_hint?
    expires_at
}
```

Classi iniziali: `DEDICATED`, `COMMUNITY`, `DEVICE`, `PRIVATE`, `MANAGED`.

Un `DEVICE` relay non richiede necessariamente una porta pubblica permanente. Può essere utile tramite NAT mapping, trasporti compatibili o connessioni outbound/circuiti già stabiliti.

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
- possibilità di interrompere il servizio localmente;
- nessuna fiducia necessaria per l'autenticità del contenuto;
- `DEVICE_RELAY` opt-in e bounded da policy batteria/rete/CPU/RAM/banda.

Un relay può osservare metadati necessari al forwarding e può droppare o ritardare pacchetti. Per questo non viene trattato come componente fidato.

### 12.1 Relay Contributor

Policy iniziale Free:

```text
Free                    10 contatti attivi
Free + Relay Contributor 20 contatti attivi
```

Il bonus di +10 contatti è temporaneo e richiede contributo relay utile secondo policy verificabile; il semplice toggle non è sufficiente.

La prova deve essere privacy-preserving e non pubblicare peer serviti, social graph o contenuto inoltrato. La scadenza del bonus non cancella automaticamente i contatti sopra quota; impedisce nuove aggiunte finché la quota effettiva non torna sufficiente.

Dettagli: [`RELAYS.md`](RELAYS.md).

## 13. Synchronous delivery

Freedom è sincrono by design.

```text
active authenticated session?
  yes -> transmit
  no  -> discard / fail locally
```

Il protocollo base non crea una mailbox locale di consegna futura e non replica automaticamente messaggi su blockchain o relay.

Un messaggio perso perché la sessione cade durante l'invio non viene trasformato implicitamente in un messaggio asincrono.

## 14. Live / ephemeral client mode

Un client può offrire una modalità Live in cui:

- i messaggi non entrano nella cronologia persistente;
- i contenuti non vengono inclusi nei backup automatici;
- uscita dalla chat/chiusura app/termine sessione elimina lo stato locale previsto dalla policy;
- le chiavi effimere di sessione vengono distrutte al termine;
- notifiche e preview non devono introdurre copie persistenti del plaintext.

Questa proprietà non può impedire a un peer remoto o a un dispositivo compromesso di conservare autonomamente ciò che ha ricevuto.

## 15. Secure session

Trovare un endpoint non significa aver autenticato il device.

A risolve il `DeviceRecord` di B tramite `ChainAdapter` e ottiene la public key attesa. B fa lo stesso con A.

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

## 16. Session lifecycle

Ogni nuova connessione genera materiale effimero nuovo.

La specifica deve mantenere separati almeno:

- messaging/session keys;
- route control keys;
- media keys per chiamate.

La rotazione interna delle chiavi deve poter avvenire senza blockchain finché l'identity key del registro non cambia.

## 17. Chain adapter

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

La funzione di registro/rendezvous è parte del trust model; l'implementazione concreta deve poter cambiare.

## 18. Gas e fee relayer

Le operazioni on-chain rare possono essere sponsorizzate da fee relayer indipendenti.

Un fee relayer:

- paga il gas;
- non possiede la identity key del device;
- non può firmare come DeviceID;
- non deve essere unico o obbligatorio;
- può essere sostituito senza cambiare identità o wire protocol.

La private key di un fee relayer non deve mai essere incorporata nel client distribuito.

Recovery beacon e coordinamento anti-failure possono produrre write aggiuntive solo quando il data-plane è perso o una policy di resilienza le richiede; non devono diventare heartbeat continui.

## 19. Bootstrap della rete

Freedom distingue bootstrap dalla fiducia.

Un client può usare più fonti iniziali per trovare peer, relay o RPC, ma nessuna di esse autentica un DeviceID. L'autenticità deriva dal registro verificato e dalle firme.

Le fonti bootstrap devono essere multiple e sostituibili.

## 20. Applicazione

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

Il registro distribuito non è nel packet hot path.

## 21. Monetizzazione e indipendenza

I servizi commerciali ufficiali possono offrire capacità relay gestita, percorsi privacy, Freedom Shield/Maximum Resilience, funzionalità Plus, SDK, deployment e supporto Business.

I device/community relay possono contribuire capacità best-effort alla rete; un utente Free qualificato come Relay Contributor riceve +10 slot contatto senza diventare Pro.

Questi servizi e incentivi non devono diventare requisiti del protocollo. Un client compatibile deve poter continuare a stabilire e recuperare sessioni Freedom anche se l'infrastruttura commerciale ufficiale non è disponibile, quando esiste un percorso compatibile.

Vedi [`MONETIZATION.md`](MONETIZATION.md) e [`RELAYS.md`](RELAYS.md).

## 22. Proprietà architetturali

Freedom mira a mantenere queste invarianti:

- identità indipendente dal percorso;
- nessun IP come identità stabile;
- percorso indipendente dalla sessione applicativa;
- sessione autenticata indipendentemente dal relay;
- comunicazione sincrona senza mailbox di rete;
- registro distribuito non necessario per ogni pacchetto o ogni cambio route;
- relay incapace di leggere il contenuto;
- relay eseguibile anche da dispositivi Freedom senza ottenere autorità sull'identità;
- direct path non obbligatorio;
- componenti di bootstrap, RPC, relay e fee relayer sostituibili;
- recovery beacon pairwise e temporanei, non presenza globale continua;
- reward relay privacy-preserving e non farmabile tramite semplice toggle;
- scritture on-chain proporzionali agli eventi di identità e ai casi di perdita completa del route/recovery, non al volume della comunicazione.
