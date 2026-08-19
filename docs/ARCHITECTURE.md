# Freedom — Architecture

## 1. Definizione

Freedom è un protocollo decentralizzato di comunicazione sincrona. L'architettura separa sette responsabilità:

1. **ownership identity** — chi possiede e recupera l'identità Freedom;
2. **device authorization** — quali DeviceKey sono attualmente autorizzate;
3. **verifiable control-plane** — come vengono verificati rotation/revocation, entitlement, recovery e policy;
4. **pairwise rendezvous** — come due contatti online si ritrovano quando non hanno più un percorso valido;
5. **routing/transport** — come i pacchetti attraversano la rete;
6. **secure session** — come gli endpoint si autenticano e derivano chiavi;
7. **application** — messaggi, file, audio e video.

La prima implementazione del control-plane usa una blockchain, ma il traffico applicativo rimane sempre off-chain.

## 2. Vista d'insieme

```text
                  DISTRIBUTED / VERIFIABLE CONTROL PLANE
      +---------------------------------------------------------+
      | Root identity / device authorization                    |
      | key rotation / revocation                               |
      | pairwise rendezvous / recovery                          |
      | entitlement / sponsorship                               |
      | emergency / security / release manifests                |
      +----------------------------+----------------------------+
                                   |
                           only when needed
                                   |

+---------------------+                               +---------------------+
| Alice               |                               | Bob                 |
| RootIdentity        |                               | RootIdentity        |
| DeviceKey           |                               | DeviceKey           |
| device commitment   |                               | device commitment   |
+----------+----------+                               +----------+----------+
           |                                                     |
           +----------- pairwise authenticated state ------------+
                                   |
                              route selector
                                   |
            +----------------------+----------------------+
            |                      |                      |
         DIRECT                  RELAY                 SHIELDED
                              /         \
                         VPS/server   device/community
                                   |
                       authenticated E2EE live session
                                   |
                          text / file / voice / video
```

Il registro/control-plane è fondamentale come **funzione distribuita e verificabile** del trust model attuale. NEAR è soltanto la prima implementazione tramite `ChainAdapter` e deve essere sostituibile.

## 3. Identity plane

Freedom non usa un `DeviceID` globale come identità pubblica o identificatore di trasporto.

```text
RootIdentity             -> ownership / recovery / entitlement
DeviceKey                -> chiave operativa del device
DeviceRecordCommitment   -> handle opaco del control-plane
PairwiseContactAlias     -> alias specifico della relazione
TransportToken           -> token temporaneo di route/circuito
Session keys             -> materiale effimero E2EE
```

Il `DeviceRecordCommitment` serve per lookup, key rotation e revocation ma non è username, contact ID o indirizzo di rete.

Dettagli: [`IDENTITY_MODEL.md`](IDENTITY_MODEL.md).

## 4. Device authorization

Ogni device genera localmente una DeviceKey e un commitment opaco.

```text
DeviceRecord {
    version
    device_record_commitment
    device_public_key
    key_epoch
    status
    protocol_version
    authorization_proof
}
```

La RootIdentity autorizza l'attivazione:

```text
ActivateDevice {
    root_commitment
    device_record_commitment
    device_public_key
    entitlement_epoch
    nonce
    root_signature
}
```

Gli stati minimi sono `ACTIVE` e `REVOKED`.

Una rotazione incrementa `key_epoch`. Il commitment tecnico può restare stabile durante la rotazione, ma non viene esposto come global network identity.

## 5. Contact bootstrap

La rubrica rappresenta persone/RootIdentity, non singoli telefoni.

```text
Bob / RootIdentity
  |- Phone
  |- Tablet
  `- Desktop
```

Il contatto viene scambiato intenzionalmente tramite QR, link, NFC o altro canale esterno.

```text
FreedomContact {
    version
    network_id
    root_identity_proof
    contact_capability
    bootstrap_device_certificate?
    bootstrap_route_hints[]?
    expires_at?
}
```

La `contact_capability` è casuale ad alta entropia e può essere one-shot o temporanea.

Il bootstrap non pubblica una relazione leggibile tra identità, device e route.

## 6. Pairwise identity / rendezvous secret

Dopo il primo handshake autenticato, i due contatti derivano e persistono localmente:

```text
PairSecret_AB
PairwiseContactAlias_AB
PairRendezvousSecret_AB
```

Gli alias sono specifici della relazione:

```text
Alice <-> Bob     alias X
Alice <-> Carol   alias Y
```

Da `PairRendezvousSecret` vengono derivati slot opachi e rotanti. Il secret non viene pubblicato.

## 7. Rendezvous rule

Il control-plane non è una tabella di routing continuamente aggiornata.

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

## 8. Rendezvous record

```text
RendezvousRecord {
    version
    expires_at
    ciphertext
}
```

Payload cifrato:

```text
RendezvousPayload {
    sender_pairwise_alias
    sender_device_proof
    sender_key_epoch
    rendezvous_nonce
    route_candidates[]
    relay_candidates[]
    ephemeral_transport_public_key
}
```

Il record pubblico non espone una relazione leggibile RootIdentity/device/route. Ha TTL breve e non richiede uno storico di revisioni.

## 9. Route candidates

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

Un IP non rappresenta un'identità Freedom. `candidate_type` può distinguere local, observed, direct, relay o tipi futuri.

## 10. Route maintenance

Dopo che A e B hanno una sessione valida, gli aggiornamenti di rete passano dentro la sessione E2EE:

```text
RouteUpdate {
    sequence
    candidates[]
    relay_candidates[]
    expires_at
}
```

Non viene effettuata alcuna write blockchain per un semplice cambio IP/porta se esiste ancora almeno un percorso valido.

## 11. Path selection e privacy

Possibili classi:

```text
DIRECT
NAT_TRAVERSAL
RELAY
SHIELDED / MULTI-HOP
future / obfuscated transport
```

Il direct path è efficiente ma espone gli endpoint di rete ai peer. Non deve essere obbligatorio.

Il path selector può usare:

- policy privacy;
- RTT;
- stabilità recente;
- costo/capacità del relay;
- disponibilità del trasporto;
- durata prevista del mapping;
- rischio di censura/blocco;
- preferenze dell'utente.

Nessuna autorità centrale decide il percorso.

## 12. Transport identity

Routing e identità restano separati.

Il network layer usa capability temporanee:

```text
TransportToken
RelayCircuitToken
NextHopToken
RouteCapability
```

Un relay non dovrebbe ricevere RootIdentity o DeviceRecordCommitment quando gli basta un token di circuito.

## 13. Path diversity e censorship resistance

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

## 14. Adaptive recovery control-plane

Dopo la perdita completa del path, A e B possono pubblicare negli slot pairwise opachi un `RecoveryBeacon` cifrato e a TTL breve:

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

```text
A control-plane reachable        yes
B beacon recent                  yes
current A<->B data path          fail
        |
        v
INTERFERENCE_OR_ROUTE_FAILURE_SUSPECTED
        |
        v
alternate route / relay / transport
```

Vincoli:

- niente heartbeat on-chain continui;
- slot pairwise opachi e rotanti;
- payload cifrato;
- TTL breve;
- backoff/rate limit;
- stop delle write appena la sessione è ristabilita;
- nessun claim di rilevamento della sorveglianza passiva.

Dettagli: [`ADAPTIVE_DEFENSE.md`](ADAPTIVE_DEFENSE.md).

## 15. Relay architecture

Un relay Freedom è una macchina o un dispositivo che esegue software di forwarding.

```text
VPS / VM
server dedicato
mini PC / Raspberry Pi
community node
managed node
private organization node
telefono / tablet / desktop Freedom opt-in
```

Un normale dispositivo Freedom può svolgere due ruoli separati:

```text
ENDPOINT  -> sessioni del proprio utente
RELAY     -> inoltro ciphertext di altri circuiti
```

Il ruolo relay non concede accesso alle chiavi E2EE.

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

Un `DEVICE` relay non richiede necessariamente una porta pubblica permanente. Può essere utile tramite NAT mapping, transport compatibili o connessioni outbound/circuiti già stabiliti.

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

- payload applicativo E2EE;
- niente mailbox persistenti;
- buffer/TTL bounded;
- quote per peer/connessione;
- possibilità di interrompere il servizio localmente;
- nessuna fiducia necessaria per autenticità del contenuto;
- `DEVICE_RELAY` opt-in e bounded da policy batteria/rete/CPU/RAM/banda;
- nessun arbitrary Internet proxy nel protocollo relay base.

Dettagli: [`RELAYS.md`](RELAYS.md).

## 16. Relay Contributor

```text
Free                     10 contatti attivi
Free + Relay Contributor 20 contatti attivi
```

Il bonus di +10 è temporaneo e richiede contributo relay utile. La prova deve essere privacy-preserving e non pubblicare peer serviti, social graph o contenuto inoltrato.

## 17. Synchronous delivery

```text
active authenticated session?
  yes -> transmit
  no  -> discard / fail locally
```

Il protocollo base non crea mailbox di consegna futura e non replica automaticamente messaggi su blockchain o relay.

## 18. Live / ephemeral client mode

Un client può offrire una modalità Live che evita persistenza locale della cronologia, backup/preview plaintext e distrugge session state/key al termine.

Questa proprietà riguarda il client locale e non può impedire al peer remoto o a un dispositivo compromesso di conservare ciò che ha ricevuto.

## 19. Secure session

Trovare un endpoint non significa aver autenticato il peer.

L'handshake verifica:

1. RootIdentity/contact identity attesa;
2. autorizzazione della DeviceKey corrente;
3. possesso della DeviceKey;
4. transcript effimero della sessione.

Il transcript deve legare almeno:

```text
protocol_version
network_id
pairwise aliases
current device authorization proofs
key epochs
ephemeral keys
nonces
negotiated_suite
session_id
```

Una modifica deve invalidare il transcript.

## 20. Session lifecycle

Ogni nuova connessione genera materiale effimero nuovo.

Separare almeno:

- messaging/session keys;
- route control keys;
- media keys.

La rotazione interna delle session keys non richiede blockchain.

## 21. Chain adapter

```text
interface ChainAdapter {
    registerRoot(...)
    registerDeviceRecord(...)
    resolveDeviceRecord(...)
    rotateDeviceKey(...)
    revokeDeviceRecord(...)
    readRendezvous(...)
    writeRendezvous(...)
    verifyState(...)
}
```

La prima implementazione è `NearChainAdapter` su NEAR Testnet.

## 22. Gas e fee relayer

Un fee relayer:

- paga il gas;
- non possiede RootIdentity o DeviceKey;
- non può firmare come endpoint;
- non deve essere unico o obbligatorio;
- può essere sostituito senza cambiare identità o wire protocol.

Recovery beacon e coordinamento anti-failure possono produrre write solo quando il data-plane è perso o una policy di resilienza le richiede.

## 23. Bootstrap della rete

Un client può usare più fonti iniziali per trovare peer, relay o RPC, ma nessuna di esse autentica l'identità.

L'autenticità deriva dalle firme, dall'identità pairwise attesa e dallo stato verificabile del control-plane.

## 24. Applicazione

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

I frame applicativi evitano identificatori globali stabili quando il session context basta a identificare il mittente/destinatario.

## 25. Monetizzazione e indipendenza

I servizi commerciali possono offrire capacità relay gestita, Shield/Maximum Resilience, SDK, deployment e supporto Business.

I device/community relay possono contribuire capacità best-effort; un Free qualificato come Relay Contributor riceve +10 slot contatto.

Questi servizi non devono diventare requisiti del protocollo.

## 26. Share Freedom / distribution

La distribuzione dell'app è separata dalla comunicazione applicativa.

```text
peer / relay / mirror / store
        -> artifact bytes
        -> verify FreedomRelease/hash/signer
        -> install via platform
```

La sorgente dei byte non è un trust anchor. Dettagli: [`APP_DISTRIBUTION.md`](APP_DISTRIBUTION.md).

## 27. Proprietà architetturali

Freedom mira a mantenere queste invarianti:

- RootIdentity indipendente dal percorso;
- nessun `DeviceID` globale necessario al network layer;
- DeviceRecordCommitment opaco e solo control-plane;
- contatto logico = persona/RootIdentity, non singolo device;
- alias pairwise tra contatti;
- token di trasporto temporanei;
- sessione autenticata indipendentemente dal relay;
- comunicazione sincrona senza mailbox di rete;
- registro distribuito non necessario per ogni pacchetto o cambio route;
- relay incapace di leggere il contenuto;
- direct path non obbligatorio;
- bootstrap, RPC, relay e fee relayer sostituibili;
- recovery beacon pairwise/temporanei;
- scritture on-chain proporzionali a identity/control events e recovery, non al volume della comunicazione.
