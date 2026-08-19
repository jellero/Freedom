# Freedom Messenger

**Powered by Freedom Protocol**

> **Synchronous. Ephemeral. Endpoint-to-endpoint.**

Freedom è un progetto di comunicazione privata e resilienza di rete costruito attorno a una scelta precisa: **la conversazione Freedom esiste quando le persone sono presenti nello stesso momento**.

```text
peer raggiungibile + sessione autenticata -> comunica adesso
peer non raggiungibile                    -> non accodare per dopo
```

Il protocollo base non crea una mailbox offline, non deposita messaggi sulla blockchain e non usa i relay come storage persistente.

La seconda direzione del progetto, separata dal core messenger, è **Freedom Gateway**: usare lo stesso fabric di route/relay/Shield e transport adattivi come percorso di rete opzionale per browser e altre app, soprattutto in reti filtrate o degradate.

## Due superfici, due garanzie

Freedom deve essere descritto distinguendo sempre questi due livelli.

### 1. Freedom Communication — sicurezza della conversazione

```text
Alice
  |
  | authenticated E2EE live session
  | keys only at Freedom endpoints
  v
Bob
```

Proprietà target:

- autenticazione endpoint-to-endpoint;
- session keys possedute dagli endpoint;
- nessuna offline mailbox nel protocollo base;
- relay non fidati e forward-only;
- route sostituibili;
- identity, routing e transport separati;
- alias pairwise invece di un `DeviceID` globale nel network layer;
- Adaptive Defense e recovery distribuito;
- modalità Shield/multi-hop quando la policy lo richiede.

Questa è la superficie con le **garanzie di comunicazione più forti di Freedom**.

### 2. Freedom Gateway — resilienza del percorso di rete

```text
Chrome / Firefox / altra app
            |
            v
      Freedom Gateway
            |
      encrypted tunnel
            |
 relay / bridge / Shield / transport adattivo
            |
            v
       Freedom Egress
            |
            v
          Internet
```

Il Gateway protegge e diversifica **il percorso verso Internet**. Non trasforma automaticamente un protocollo esterno in Freedom E2EE.

Se un'app usa HTTPS, la sua cifratura continua oltre l'egress. Se usa un protocollo plaintext, un egress può teoricamente osservare quel plaintext. Per questo **Freedom Communication e Freedom Gateway hanno trust model differenti**.

Dettagli: [`docs/GATEWAY.md`](docs/GATEWAY.md).

## Per chi è

Freedom è pensato per utenti e organizzazioni che vogliono controllare non soltanto **chi può leggere una comunicazione**, ma anche **da quali infrastrutture dipende la possibilità di stabilirla**.

Esempi:

- giornalisti, ricercatori e operatori in reti soggette a filtraggio o blocchi;
- attivisti e comunità che vogliono ridurre i single points of control;
- professionisti che vogliono comunicazioni live senza mailbox o consegna differita;
- organizzazioni che vogliono poter usare relay, gateway o infrastruttura compatibile propri;
- utenti che preferiscono contatti espliciti via QR/link invece di un numero telefonico obbligatorio;
- utenti che vogliono poter condividere anche il client Freedom da persona a persona tramite artifact verificati;
- utenti che, in una fase successiva, vogliono proteggere o rendere più resiliente il traffico di altre app tramite Freedom Gateway.

Freedom **non** promette anonimato assoluto, invisibilità contro un osservatore globale, comunicazione dopo la perdita totale di ogni connettività o rilevamento certo della sorveglianza passiva.

## Censorship resistance: obiettivo corretto

Freedom non deve avere un singolo IP, dominio, protocollo, relay, RPC, provider o transport la cui interdizione blocchi l'intero sistema.

```text
direct blocked       -> relay / altro path
relay A blocked      -> relay B / bridge
RPC A blocked        -> RPC B
transport A filtered -> transport B
public nodes blocked -> non-public / pairwise bridge
normal path fails    -> Shield / alternate strategy
```

Obiettivo:

> **Quando esiste almeno un carrier di rete ancora utilizzabile, Freedom deve poter cercare automaticamente transport e percorsi indipendenti progettati per evitare la primitive bloccata o confondersi con traffico consentito.**

Non è tecnicamente serio promettere di attraversare **ogni firewall**. Una rete può limitarsi a una allowlist strettissima, bloccare tutti i bridge scoperti o spegnere completamente Internet. Nessun protocollo IP può garantire il passaggio in quelle condizioni.

Freedom deve invece puntare a una **Maximum Reachability** misurabile tramite:

- transport adapter sostituibili;
- bridge non pubblici / difficili da enumerare;
- active-probing resistance quando supportata;
- HTTPS/WebSocket/WebTunnel-like carrier dove appropriato;
- transport offuscati/pluggable;
- provider e geografia differenti;
- failover automatico;
- candidate pre-warmed bounded;
- test reali contro DPI/firewall e reti restrittive.

Dettagli: [`docs/GATEWAY.md`](docs/GATEWAY.md) e [`docs/ADAPTIVE_DEFENSE.md`](docs/ADAPTIVE_DEFENSE.md).

## In cosa differisce

La combinazione Freedom è:

```text
RootIdentity / ownership
        |
        v
DeviceKey + opaque device record
        |
        v
pairwise contact identity
        |
        v
distributed rendezvous / recovery
        |
        v
replaceable route / pluggable transport
 direct / NAT / relay / device relay / bridge / shielded
        |
        +-----------------------------+
        |                             |
        v                             v
Freedom Communication          Freedom Gateway
E2EE live session              optional device traffic
text/media/voice/video         -> explicit Internet egress
```

Principi:

- ownership identity separata dal percorso di rete;
- nessun `DeviceID` globale necessario al network layer;
- device authorization tramite commitment opachi del control-plane;
- alias pairwise specifici per ogni relazione;
- token temporanei per route/circuiti relay;
- comunicazione Freedom applicativa off-chain;
- nessuna offline mailbox nel protocollo base;
- relay forward-only, non fidati e sostituibili;
- `DEVICE_RELAY` **non** è un Internet exit node;
- direct path non obbligatorio;
- più RPC/provider/bootstrap/relay/egress/transport possibili;
- recovery pairwise tramite control-plane solo quando serve;
- stato di rete visibile quando qualcosa degrada;
- nessun provider commerciale necessario al trust crittografico;
- distribuzione del client verificabile indipendentemente dalla sorgente dei byte.

## Confronto oggettivo

Freedom non viene presentato come "più sicuro" in assoluto. Sistemi differenti ottimizzano problemi differenti.

| Sistema | Benchmark principale | Differenza rispetto a Freedom |
|---|---|---|
| **Signal** | UX, E2EE production e affidabilità operativa | delivery asincrono tramite servizio; Freedom Communication è live-only e senza mailbox base |
| **SimpleX** | metadata privacy / assenza di user identifier globale | queue pairwise temporanee; Freedom usa RootIdentity + commitment opachi + alias pairwise e non accoda offline |
| **Session** | rete decentralizzata e onion routing | Session Nodes/swarm con storage per delivery; relay Freedom sono forward-only |
| **Briar** | resilienza Tor/Bluetooth/Wi-Fi | supporta sincronizzazione successiva; Freedom mantiene semantica sincrona |
| **Tor** | anonimato e pluggable transports anti-censura | Freedom non vuole ricreare Tor: Tor è benchmark per bridge/PT e anti-enumeration |
| **Psiphon** | circumvention adattiva in reti molto filtrate | Psiphon è benchmark diretto per Gateway/transport diversity; Freedom aggiunge il proprio communication core, identity/recovery e relay fabric |
| **Tailscale** | overlay networking / exit nodes | dimostra il valore del device come exit node; Freedom separa rigorosamente relay community da egress Internet |
| **VPN multi-hop** | tunnel device-wide e server multipli | Freedom Gateway punta in più su transport/path diversity e Adaptive Defense, non sulla sola catena di server VPN |

La differenziazione credibile di Freedom è quindi **la combinazione**, non il claim che nessuno abbia mai realizzato le singole primitive.

Analisi: [`docs/COMPETITIVE_POSITIONING.md`](docs/COMPETITIVE_POSITIONING.md).

## Architettura

![Architettura del sistema Freedom](docs/assets/freedom-architecture.svg)

```text
RootIdentity
 -> authorized DeviceKey / opaque device record
 -> pairwise identity
 -> verifiable control-plane
 -> rendezvous/recovery
 -> path + transport selector
      |- Freedom Communication -> authenticated E2EE live session
      `- Freedom Gateway       -> explicit egress -> Internet
```

La blockchain/control-plane **non trasporta** chat, file, audio, video, Gateway payload o APK. Serve per ownership/device authorization, key rotation/revocation, rendezvous/recovery, entitlement e piccoli manifest/policy firmati.

**NEAR non è Freedom Protocol.** È la prima implementazione del registro tramite `ChainAdapter` e deve poter essere sostituita.

## Identity model

```text
RootIdentity             -> recovery / entitlement / device authorization
DeviceKey                -> autenticazione operativa del device
DeviceRecordCommitment   -> handle opaco del control-plane
PairwiseContactAlias     -> alias specifico Alice<->Bob
TransportToken           -> route/circuito temporaneo
Session keys             -> comunicazione effimera
```

Un contatto è una persona/RootIdentity, non un telefono. Più device autorizzati della stessa persona non diventano automaticamente più contatti.

Dettagli: [`docs/IDENTITY_MODEL.md`](docs/IDENTITY_MODEL.md).

## RootIdentity e Recovery Kit

La prima installazione genera localmente RootIdentity, DeviceKey, device commitment e Recovery Kit e **non richiede automaticamente una write blockchain**.

```text
recover RootIdentity
 -> generate NEW DeviceKey
 -> generate NEW DeviceRecordCommitment
 -> activate device
 -> restore entitlement
```

La licenza segue la RootIdentity; la chain può far rispettare `max_devices` senza pubblicare un mapping leggibile tra persona e device.

Dettagli: [`docs/ACCOUNT_RECOVERY_LICENSES.md`](docs/ACCOUNT_RECOVERY_LICENSES.md).

## Registrazione Free e anti-abuse

```text
install locale
 -> €0 / 0 chain writes
 -> identity realmente necessaria
 -> adaptive proof / PoW anti-abuse
 -> relayer rate limit
 -> bounded sponsorship budget
 -> registration
```

Il costo chain dipende da eventi rari del control-plane, **mai dal numero di messaggi, chiamate o frame media**.

Dettagli: [`docs/REGISTRATION_ECONOMICS.md`](docs/REGISTRATION_ECONOMICS.md).

## Relay e Relay Contributor

Un relay Freedom può essere:

```text
VPS / VM
server dedicato
mini PC / Raspberry Pi
community node
managed/private relay
telefono / tablet / desktop Freedom
```

Un normale device può essere contemporaneamente:

```text
ENDPOINT -> comunica per il proprio utente
RELAY    -> inoltra ciphertext per circuiti altrui
```

Il relay non possiede le session key, non crea mailbox e non è un trust anchor.

### Importante: relay != egress

```text
DEVICE_RELAY
  Freedom circuit -> Freedom circuit
  NO arbitrary Internet access

FREEDOM_EGRESS
  explicit managed/private/business node
  Gateway traffic -> Internet
```

Un Relay Contributor non trasforma il proprio telefono in un open proxy Internet.

Policy Free iniziale:

```text
FREE                      1 device / 10 contatti attivi
FREE + RELAY CONTRIBUTOR  1 device / 20 contatti attivi
```

Il bonus +10 richiede contributo relay utile e verificabile; il semplice toggle non basta.

Dettagli: [`docs/RELAYS.md`](docs/RELAYS.md).

## Routing, Shield e Adaptive Defense

```text
Pairwise identity -> chi è il contatto in questa relazione
RouteCandidate    -> come posso raggiungerlo ora
TransportToken    -> come inoltro questo circuito
```

Path possibili:

```text
DIRECT
NAT_TRAVERSAL
RELAY
BRIDGE
SHIELDED
MULTI_HOP
PLUGGABLE / OBFUSCATED TRANSPORT
```

Dopo failure selettive il motore può distinguere, per quanto possibile, normale route failure da filtraggio/interferenza sospetta e tentare un'altra strategia.

```text
peer recentemente attivo       yes
control-plane                  reachable
current data path               fail
        |
        v
INTERFERENCE_OR_ROUTE_FAILURE_SUSPECTED
        |
        v
alternate route / relay / bridge / transport
```

Questo non prova sorveglianza né attribuisce il blocco a un attore specifico.

Dettagli: [`docs/ADAPTIVE_DEFENSE.md`](docs/ADAPTIVE_DEFENSE.md).

## Freedom Network Indicator

```text
NORMAL       percorso funzionante
SHIELDED     percorso protetto attivo
DEGRADED     degradazione/fallback
SUSPECTED    probabile filtraggio/interferenza o failure selettiva
UNAVAILABLE  nessun path valido trovato
```

Il pannello distingue fatti osservati, inferenza, route/transport falliti e contromisure attive.

> **Semplice quando tutto funziona. Trasparente quando qualcosa cerca di impedirti di comunicare.**

Dettagli: [`docs/NETWORK_STATUS_UI.md`](docs/NETWORK_STATUS_UI.md).

## Messaggistica sincrona

```text
SEND
  |
  +-- active authenticated session? -- no --> DISCARD / FAIL
  |
  `-- yes --> transmit --> ACK/session result
```

Nessun deposito automatico su blockchain, relay o queue per recapito futuro.

La modalità **Live** può inoltre evitare cronologia persistente locale; è una proprietà del client, non il motivo centrale della sincronicità.

## Share Freedom

```text
Alice -> Share Freedom -> QR
Bob   -> fotocamera/browser
      -> peer / relay / mirror / store
      -> download artifact
      -> verify release/hash/signer
      -> installer Android
```

La sorgente dei byte non è fidata. Il primo sideload richiede un trust anchor indipendente dal peer che distribuisce il file.

Dettagli: [`docs/APP_DISTRIBUTION.md`](docs/APP_DISTRIBUTION.md).

## Emergency bulletin e secure updates

Il control-plane può pubblicare piccoli oggetti firmati:

```text
EmergencyBulletin
SecurityPolicy
FreedomRelease
```

L'APK resta off-chain e può arrivare da store, peer, relay/update node o mirror sostituibili.

Dettagli: [`docs/EMERGENCY_UPDATES.md`](docs/EMERGENCY_UPDATES.md).

## Pagamenti ed entitlement

Freedom è payment-provider agnostic:

```text
PaymentAdapter
|- PayPal
|- crypto native
|- stablecoin
`- future providers
```

L'utente compra servizi Freedom, **non NEAR**. Nessun merchant secret deve stare nell'APK.

Dettagli: [`docs/PAYMENTS.md`](docs/PAYMENTS.md).

## Monetizzazione

> **monetizzare capacità, comodità e servizi professionali; non la conversazione.**

> **la censura non deve diventare un paywall.**

### Free

- 1 device attivo;
- 10 contatti attivi;
- +10 con Relay Contributor qualificato;
- core Freedom Communication E2EE/live;
- route/RPC fallback;
- Network Indicator;
- community/device relay;
- quota bounded Emergency Shield;
- recovery base;
- sponsorship chain essenziale secondo anti-abuse.

### Freedom Plus / Shield

- contatti/device superiori;
- maggiore capacità relay gestita;
- Always-Shielded;
- multi-hop;
- provider/path diversity;
- candidate pre-warmed;
- parallel failover;
- transport rotation più aggressiva;
- Maximum Resilience.

Pro non compra una cifratura del messenger "più forte" né una diagnosi più onesta.

### Gateway

La policy commerciale del Gateway verrà definita solo dopo misure reali di egress bandwidth, abuso, geografia e costi. Il core anti-censura della **Freedom Communication** non deve essere indebolito per creare un paywall Gateway.

### Business

- SDK/integrations;
- deployment privati;
- relay/Shield pool dedicati;
- private/business egress;
- supporto/SLA;
- infrastruttura compatibile gestita.

Dettagli: [`docs/MONETIZATION.md`](docs/MONETIZATION.md).

## Scope prodotto

V1 resta focalizzata sulla comunicazione 1:1:

```text
identity + recovery
QR/link contact
text / media / file
voice messages
1:1 audio/video
Live mode
relay/device relay
Adaptive Defense base
Network Indicator
Share Freedom
payment/entitlement foundation
```

**Freedom Gateway non deve ritardare il messenger V1.** Viene costruito sopra le primitive riutilizzabili di routing, relay, Shield e transport diversity.

Dettagli: [`docs/PRODUCT_SCOPE.md`](docs/PRODUCT_SCOPE.md) e [`docs/GATEWAY.md`](docs/GATEWAY.md).

## Stato del codice Android

Il codice Android presente nel repository è ancora uno **transport/crypto spike precedente alla specifica corrente**. Dimostra socket diretto, handshake cifrato e comunicazione E2EE di test, ma non rappresenta l'M1 canonico.

Le proprietà documentate devono essere dimostrate con implementazione, test su device/reti reali, fuzzing, test contro firewall/DPI, review crittografica e security review indipendente.

## Documentazione

- [`docs/IDENTITY_MODEL.md`](docs/IDENTITY_MODEL.md) — RootIdentity, device commitment, alias pairwise e transport token.
- [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) — architettura completa.
- [`docs/PROTOCOL.md`](docs/PROTOCOL.md) — oggetti e flussi normativi.
- [`docs/CHAIN.md`](docs/CHAIN.md) — control-plane blockchain, identity, recovery, entitlement e manifest.
- [`docs/THREAT_MODEL.md`](docs/THREAT_MODEL.md) — minacce, limiti e mitigazioni.
- [`docs/RELAYS.md`](docs/RELAYS.md) — relay fisici, device relay, resource bounds e Relay Contributor.
- [`docs/GATEWAY.md`](docs/GATEWAY.md) — device Gateway, egress, anti-censura e pluggable transports.
- [`docs/ADAPTIVE_DEFENSE.md`](docs/ADAPTIVE_DEFENSE.md) — recovery pairwise e rilevamento interferenza.
- [`docs/NETWORK_STATUS_UI.md`](docs/NETWORK_STATUS_UI.md) — Network Indicator ed Emergency Shield.
- [`docs/ACCOUNT_RECOVERY_LICENSES.md`](docs/ACCOUNT_RECOVERY_LICENSES.md) — RootIdentity, Recovery Kit e multi-device.
- [`docs/PAYMENTS.md`](docs/PAYMENTS.md) — PayPal outbound-worker, crypto e PaymentAttestation.
- [`docs/REGISTRATION_ECONOMICS.md`](docs/REGISTRATION_ECONOMICS.md) — sponsorship Free, anti-Sybil e storage bounded.
- [`docs/EMERGENCY_UPDATES.md`](docs/EMERGENCY_UPDATES.md) — bulletin, SecurityPolicy e release firmate.
- [`docs/APP_DISTRIBUTION.md`](docs/APP_DISTRIBUTION.md) — Share Freedom, peer APK transfer e trust del primo install.
- [`docs/COMPETITIVE_POSITIONING.md`](docs/COMPETITIVE_POSITIONING.md) — confronto e benchmark esterni.
- [`docs/PRODUCT_SCOPE.md`](docs/PRODUCT_SCOPE.md) — V1, Live Groups/Rooms e roadmap.
- [`docs/MONETIZATION.md`](docs/MONETIZATION.md) — modello Free/Shield/Business.
- [`docs/LAUNCH_PLAN.md`](docs/LAUNCH_PLAN.md) — validazione e lancio.
- [`docs/STORE_COMPLIANCE.md`](docs/STORE_COMPLIANCE.md) — separazione protocollo/client e vincoli store.
- [`ANDROID.md`](ANDROID.md) — roadmap Android.

## Roadmap sintetica

```text
M0  specification / threat model
M1  RootIdentity + DeviceKey + opaque device record + Recovery Kit
M2  registry + pairwise QR/contact + rendezvous read-before-write
M3  authenticated secure session without global DeviceID
M4  NAT traversal + route updates
M5  relay forward-only + community/device relay + Relay Contributor
M6  V1 1:1 text/media/voice/video + Live + Network Indicator
M7  Share Freedom + verified peer/direct distribution
M8  entitlement + max_devices + Free contact policy + payment adapters
M9  emergency bulletin + signed secure update plane
M10 Live Groups / Live Rooms
M11 group voice/video + scalable media forwarding
M12 Adaptive Defense + Emergency Shield + Shield hardening
M13 transport diversity / bridge hardening
M14 iOS/platform integration
M15 hardening, fuzzing, censorship tests, interoperability, independent review

POST-V1 GATEWAY
G1  explicit managed/private egress
G2  selected-app Android Gateway
G3  whole-device Gateway + DNS/leak controls
G4  egress diversity / multi-hop
G5  pluggable anti-censorship transports
G6  bridge anti-enumeration / DPI lab
G7  Maximum Reachability
```

## Principio finale

Freedom non è definito da una blockchain, da un relay specifico, da una VPN o da un'app Android.

> **Freedom Communication punta a proteggere la comunicazione endpoint-to-endpoint. Freedom Gateway punta a mantenere utilizzabile il percorso di rete quando l'ambiente prova a limitarlo. Le due proprietà condividono il fabric, ma non vanno mai confuse nelle garanzie di sicurezza.**
