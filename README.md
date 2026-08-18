# Freedom

## Protocollo decentralizzato di comunicazione

Freedom è un protocollo decentralizzato di comunicazione che permette a dispositivi identificati crittograficamente di stabilire sessioni sicure senza dipendere da un server centrale di messaggistica.

La blockchain non trasporta messaggi, file, audio o video. Viene usata come prima implementazione di un **registro distribuito e verificabile** per identità, key rotation/revocation e rendezvous di fallback quando due dispositivi online non dispongono più di un percorso valido per raggiungersi.

Il traffico applicativo viaggia fuori dalla blockchain, direttamente tra endpoint oppure attraverso relay transitivi che inoltrano ciphertext senza possedere le chiavi della conversazione.

> Principio operativo: **verifiable identity, off-chain communication, distributed rendezvous only when needed.**

## Obiettivi

- identità dei dispositivi verificabili crittograficamente;
- cifratura end-to-end obbligatoria;
- nessun server centrale di messaggistica necessario;
- comunicazione **sincrona by design**: nessuna mailbox o coda di consegna offline nel protocollo base;
- modalità Live/effimera nei client compatibili;
- comunicazione diretta quando il percorso di rete e la policy privacy lo consentono;
- NAT traversal, relay e percorsi alternativi;
- aggiornamenti di route scambiati direttamente durante una sessione attiva;
- scritture blockchain ridotte al minimo tramite read-before-write;
- nessun messaggio persistito nella rete Freedom;
- nessun messaggio o media memorizzato sulla blockchain;
- relay `forward-only`, con buffer limitati e temporanei;
- nessun singolo server, relay, RPC, provider, IP o percorso deve essere requisito permanente per il funzionamento del protocollo;
- rilevamento di route failure/interferenza probabile attraverso segnali indipendenti, incluso il caso **peer recentemente attivo sul control-plane ma data-plane indisponibile**;
- recovery automatico attraverso route/relay/transport alternativi quando disponibili;
- indicatore di rete visibile e cliccabile che spiega agli utenti Free e Pro cosa Freedom ha osservato e quali contromisure sta applicando;
- quota limitata di Emergency Shield anche per il tier Free quando l'infrastruttura gestita è necessaria e disponibile;
- protocollo indipendente da Android, iOS, store e servizi commerciali ufficiali;
- possibilità di sostituire la blockchain tramite un adapter senza cambiare il protocollo applicativo.

## Architettura

![Architettura del sistema Freedom](docs/assets/freedom-architecture.svg)

Per la consegna di un messaggio applicativo deve esistere una sessione autenticata attiva tra gli endpoint. Se il destinatario non è raggiungibile, il protocollo base **non accoda il messaggio per una consegna futura** e non lo dissemina nella rete.

## Architettura in una frase

```text
DeviceID -> verifiable registry -> rendezvous fallback -> route -> authenticated E2EE live session -> messages/media
```

## Registro distribuito e blockchain

Freedom necessita della **funzione** di un registro distribuito e verificabile per:

- risolvere `DeviceID -> identity_public_key`;
- verificare key rotation e revocation;
- pubblicare/leggere rendezvous di fallback quando tutte le route note sono perse;
- coordinare recovery pairwise temporaneo quando il data-plane è perso e serve distinguere peer offline da peer recentemente attivo ma non raggiungibile sul percorso corrente.

Questa funzione è fondamentale per il trust model attuale. **NEAR non è fondamentale come tecnologia specifica.**

La prima implementazione usa **NEAR Testnet** attraverso un'interfaccia `ChainAdapter`.

NEAR non fa parte del wire protocol Freedom: è la prima implementazione del registro decentralizzato. Il core deve poter supportare adapter differenti senza cambiare DeviceID, session protocol o formato dei messaggi.

```text
ChainAdapter
  |- NearChainAdapter      <- prima implementazione
  |- ...                   <- future implementazioni
```

Durante una sessione attiva il registro non è nel packet hot path e non viene interrogato o scritto per ogni messaggio.

## Ruolo del registro

### Device identity

```text
DeviceRecord {
    device_id
    identity_public_key
    key_epoch
    status
    protocol_version
}
```

Il `DeviceID` è stabile e non coincide con l'hash della chiave pubblica, così una chiave può essere ruotata o revocata senza cambiare identità.

### Rendezvous

Il registro viene usato per ristabilire un percorso solo quando non esiste più alcuna route Freedom valida tra due endpoint online.

Regola fondamentale:

```text
1. READ
2. se esiste un rendezvous valido -> usa i dati, NON scrivere
3. se non esiste -> WRITE del proprio rendezvous
```

Appena viene ristabilita una sessione, gli aggiornamenti di endpoint, NAT candidate e relay candidate passano direttamente nel canale E2EE. La chain non viene più aggiornata finché esiste almeno un percorso valido.

### Recovery beacon

Freedom non pubblica una presenza globale continua. Dopo la perdita completa del data path, due peer già autenticati possono usare slot pairwise opachi per pubblicare un `RecoveryBeacon` cifrato e a TTL breve.

Un beacon indica **attività recente**, non presenza assoluta in tempo reale.

Se entrambi i peer risultano recentemente attivi sul control-plane ma la route corrente non trasporta pacchetti, il client può classificare il caso come probabile interferenza/route failure e attivare contromisure di routing.

```text
peer recently active       yes
current data path           fail
alternate control-plane     reachable
        |
        v
INTERFERENCE_OR_ROUTE_FAILURE_SUSPECTED
        |
        v
alternate route / relay / transport
```

Il sistema non deve dichiarare di aver rilevato sorveglianza passiva: un osservatore può monitorare senza lasciare segnali osservabili.

Dettagli: [`docs/ADAPTIVE_DEFENSE.md`](docs/ADAPTIVE_DEFENSE.md).

## Network status UX

Freedom non deve nascondere completamente lo stato della rete come un messenger generalista.

Principio UX:

> **Semplice quando tutto funziona. Trasparente quando qualcosa cerca di impedirti di comunicare.**

Il client ufficiale prevede un **Freedom Network Indicator** piccolo, sempre accessibile e cliccabile. Una possibile rappresentazione usa un pallino multicolore accompagnato sempre da testo/icona accessibili.

Stati concettuali:

```text
NORMAL       percorso funzionante
SHIELDED     percorso protetto attivo
DEGRADED     degradazione o fallback
SUSPECTED    probabile filtraggio/interferenza o route failure selettiva
UNAVAILABLE  peer recentemente attivo ma nessun percorso valido trovato
```

In caso di `SUSPECTED` o `UNAVAILABLE` il pannello può aprirsi automaticamente una volta per incidente e mostrare fatti osservati, inferenza, route fallita, fallback e livello di protezione.

Free e Pro devono ricevere la stessa diagnosi tecnica. Freedom non deve usare falsi allarmi o paura per vendere Shield.

Dettagli: [`docs/NETWORK_STATUS_UI.md`](docs/NETWORK_STATUS_UI.md).

## Primo contatto

Un contatto Freedom viene scambiato intenzionalmente tramite QR, link, NFC o copia/incolla.

```text
FreedomContact {
    network
    device_id
    rendezvous_capability
    expires_at?
}
```

`rendezvous_capability` è casuale e può essere monouso o temporanea. Serve a evitare che il primo rendezvous debba esporre pubblicamente una relazione leggibile tra due DeviceID.

Dopo il primo handshake autenticato, i due endpoint derivano un `PairRendezvousSecret` locale usato per generare slot on-chain opachi e rotanti per le riconnessioni successive.

## Routing, privacy di rete e censura

Freedom distingue identità e raggiungibilità.

```text
DeviceID -> chi sei
RouteCandidate -> come posso raggiungerti adesso
```

Un route candidate può contenere:

```text
RouteCandidate {
    transport
    endpoint
    nat_mapping
    relay_hint
    priority
    expires_at
}
```

Un indirizzo IP non è un'identità Freedom e non deve diventare un identificatore stabile del device.

La comunicazione diretta è efficiente ma espone necessariamente gli endpoint di rete ai peer. Per questo Freedom deve permettere policy in cui il direct path sia disabilitato e il traffico passi attraverso relay o percorsi schermati quando la privacy di rete è prioritaria.

Ordine di tentativo e policy non sono fissi universalmente: il client può scegliere tra direct, NAT traversal, relay e percorsi privacy in base a disponibilità, rischio e preferenze dell'utente.

Se tutte le route conosciute falliscono, il rendezvous distribuito viene usato per recuperare nuovi candidate.

## Censorship resistance / path diversity

Freedom non promette invisibilità assoluta o disponibilità contro un avversario capace di interrompere completamente la connettività. L'obiettivo è evitare **single points of control**.

Il protocollo deve poter degradare e cambiare percorso quando tecnicamente possibile:

```text
direct path blocked     -> altro route
relay A blocked         -> relay B / altro path
RPC A blocked           -> RPC B
provider unavailable    -> provider alternativo
transport filtrato      -> transport alternativo
```

Bootstrap, RPC, relay, fee relayer e provider devono essere multipli e sostituibili. Nessuno deve autenticare un DeviceID solo perché controlla il percorso di rete.

Quando il peer è recentemente attivo sul control-plane ma il data-plane fallisce, Freedom può aumentare aggressivamente la path diversity anziché limitarsi a considerare il peer offline.

## Relay

Un relay Freedom è un nodo di transito, non un server di messaggistica.

```text
Alice -> ciphertext -> Relay -> ciphertext -> Bob
```

Un relay:

- non possiede le chiavi E2EE;
- non conserva la conversazione;
- non crea mailbox persistenti;
- usa buffer piccoli, limitati e con TTL;
- può essere sostituito durante la sessione;
- deve applicare limiti di banda, memoria e connessioni.

Principio: **forward, not store**.

## Sessione sicura

Una route non autentica un peer. Dopo aver trovato un percorso, gli endpoint eseguono un handshake autenticato bilateralmente.

La chiave pubblica attesa viene risolta dal `DeviceID` tramite `ChainAdapter`. Entrambe le parti devono dimostrare il possesso della private key corrispondente.

Il transcript dell'handshake deve legare almeno:

- entrambi i DeviceID;
- key epoch;
- chiavi effimere;
- nonce;
- versione protocollo;
- suite crittografica;
- identificatore della sessione.

Il progetto deve usare primitive e protocolli crittografici standard, non crittografia proprietaria.

## Messaggistica sincrona

Una volta stabilita la sessione:

```text
Alice <============================> Bob
          authenticated E2EE
```

Messaggi, ACK, file metadata, signaling chiamate e route update vengono trasportati nel canale sicuro.

Se non esiste una sessione autenticata attiva o il peer non è raggiungibile, il protocollo base non deposita il messaggio sulla blockchain, sui relay o in una coda locale per consegna futura.

I client possono offrire una modalità **Live** in cui la cronologia della sessione non viene persistita e lo stato locale della conversazione viene eliminato alla chiusura/uscita secondo la policy del client.

## Scope prodotto

Il primo lancio di Freedom Messenger è deliberatamente focalizzato sul **1:1**. Il V1 deve rendere estremamente affidabili onboarding, contatto, sessione, messaggi, media, messaggi vocali, chiamata audio, videochiamata, modalità Live e Network Indicator prima di ampliare il prodotto.

I gruppi non sono un blocker del lancio. Arrivano successivamente come **Live Groups / Live Rooms**: sessioni multi-party sincrone, senza mailbox condivisa e senza consegna automatica della cronologia agli utenti assenti.

Voce e video multi-party sono una fase successiva e devono usare un'architettura scalabile con infrastruttura media sostituibile/non autoritativa, invece di trasformare un singolo SFU in un requisito permanente del protocollo.

Dettagli: [`docs/PRODUCT_SCOPE.md`](docs/PRODUCT_SCOPE.md).

## Gas e fee relayer

Le rare operazioni on-chain possono richiedere fee della chain. Freedom può supportare **fee relayer indipendenti** che sponsorizzano il gas senza possedere l'identità dell'utente.

Requisiti:

- la private identity key resta sul device;
- nessuna private key del relayer dentro l'APK/client;
- un relayer non può firmare come DeviceID;
- più relayer devono poter coesistere;
- il blocco di un singolo relayer non deve bloccare il protocollo;
- messaggi, ACK, file, audio, video e route update in-session non generano transazioni on-chain;
- eventuali recovery beacon producono write solo in condizioni di failure/recovery e non diventano heartbeat continui.

## Monetizzazione

Freedom non deve monetizzare il contenuto delle conversazioni o richiedere storage centrale dei messaggi.

Principio economico:

> **monetizzare capacità, comodità e servizi professionali; non la conversazione.**

Principio commerciale aggiuntivo:

> **la censura non deve diventare un paywall.**

Possibili linee:

- core messaging/live E2EE gratuito;
- resilienza base, Network Indicator e route fallback nel core gratuito;
- **Emergency Shield Free** con capacità gestita limitata quando necessaria per tentare il bypass di un blocco;
- **Freedom Plus / Shield** per capacità relay gestita superiore, Always-Shielded, percorsi privacy/multi-hop, path diversity più ampia, candidate pre-warmed, failover parallelo e **Maximum Resilience**;
- **Freedom Business** per SDK, deployment, relay dedicati, supporto e SLA;
- relay community/best-effort e relay gestiti a pagamento opzionale;
- fee relayer per gas sostituibili e non autoritativi.

La monetizzazione non deve trasformare Freedom in un servizio dipendente da un singolo soggetto commerciale. Il piano Pro non deve comprare una cifratura più forte, una diagnosi tecnica più onesta o il diritto esclusivo al recovery di base.

La quota Emergency Shield definitiva deve essere determinata dopo misure reali di costo/banda/abuso; non va fissato un numero arbitrario di messaggi prima dei test.

Dettagli: [`docs/MONETIZATION.md`](docs/MONETIZATION.md).

## Store

Freedom Protocol è separato dai client ufficiali.

I client Android e iOS implementano il livello necessario per la conformità dello store senza modificare il trust model del protocollo:

- blocco locale di un DeviceID;
- segnalazione volontaria con contenuti scelti dall'utente;
- privacy policy e termini;
- permessi minimi;
- nessuna master key;
- nessuna scansione server-side necessaria delle conversazioni E2EE.

Freedom è un sistema di contatti espliciti tramite DeviceID/QR, non una random chat.

## Stato del codice Android

Il codice Android presente nel repository è un **transport/crypto spike precedente alla specifica attuale**. Dimostra socket diretto e handshake cifrato, ma usa ancora IP manuale e fingerprint/TOFU.

Non rappresenta più l'M1 canonico. Verrà rifattorizzato seguendo questa sequenza:

```text
DeviceID
 -> NEAR Testnet identity
 -> QR contact
 -> chain verification
 -> rendezvous
 -> route establishment
 -> mutual authentication
 -> E2EE messaging
```

## Documentazione

- [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) — architettura completa.
- [`docs/PROTOCOL.md`](docs/PROTOCOL.md) — oggetti e flussi del protocollo.
- [`docs/CHAIN.md`](docs/CHAIN.md) — NEAR, Device Registry e rendezvous.
- [`docs/THREAT_MODEL.md`](docs/THREAT_MODEL.md) — modello di sicurezza.
- [`docs/ADAPTIVE_DEFENSE.md`](docs/ADAPTIVE_DEFENSE.md) — liveness/recovery pairwise, rilevamento interferenza e Freedom Shield.
- [`docs/NETWORK_STATUS_UI.md`](docs/NETWORK_STATUS_UI.md) — indicatore multistato, apertura automatica, trasparenza e Emergency Shield Free.
- [`docs/PRODUCT_SCOPE.md`](docs/PRODUCT_SCOPE.md) — scope V1, Live Groups/Rooms e roadmap multi-party.
- [`docs/MONETIZATION.md`](docs/MONETIZATION.md) — principi economici e servizi opzionali.
- [`docs/LAUNCH_PLAN.md`](docs/LAUNCH_PLAN.md) — piano dettagliato di validazione, creator program e lancio.
- [`docs/STORE_COMPLIANCE.md`](docs/STORE_COMPLIANCE.md) — separazione protocollo/client e vincoli store.
- [`ANDROID.md`](ANDROID.md) — roadmap Android.

## Roadmap sintetica

```text
M0  specifica
M1  DeviceID + NEAR Testnet + QR
M2  rendezvous read-before-write
M3  authenticated secure session
M4  NAT traversal + route updates
M5  relay forward-only
M6  V1: 1:1 messaging + media + voice/video + Live mode + Network Indicator
M7  V1.5: Live Groups / Live Rooms
M8  V2: group voice/video + media forwarding scalabile
M9  adaptive recovery + Emergency Shield + Freedom Shield hardening
M10 iOS + platform wake integration
M11 hardening, censorship resistance, testing, interoperability
```

## TODO

- [ ] Brand client ufficiale: **Freedom Messenger** — *Powered by Freedom Protocol*.
- [ ] **Censorship resistance / path diversity:** nessun singolo server, relay, RPC endpoint, provider blockchain, fee relayer, IP o percorso di rete deve costituire un punto unico di controllo o interruzione.
- [ ] **Adaptive Defense:** implementare recovery beacon pairwise, temporanei e cifrati per distinguere peer recentemente attivo da peer offline quando il data-plane fallisce; usare più segnali prima di classificare una probabile interferenza e attivare route/relay/transport alternativi.
- [ ] **Freedom Network Indicator:** indicatore sempre accessibile, multistato e cliccabile; apertura automatica una volta per incidente in caso di `SUSPECTED`/`UNAVAILABLE`; distinguere fatti osservati da inferenze e non dichiarare sorveglianza passiva non rilevabile.
- [ ] **Emergency Shield Free:** fornire una quota limitata di capacità gestita per tentare il bypass anche agli utenti Free; dimensionare la quota dopo misure reali e non trasformare la censura in un paywall.
- [ ] **Freedom Shield / Pro:** Always-Shielded, relay gestiti multipli, multi-hop, pre-warming, failover parallelo e Maximum Resilience senza rendere il piano Pro un requisito del protocollo base.
- [ ] **Network privacy / metadata resistance:** evitare identificatori di rete stabili e correlabili; supportare alias/session identifiers pairwise o temporanei e percorsi relay/shielded quando l'utente non vuole esporre il proprio endpoint, minimizzando il più possibile latenza e overhead.
- [ ] **Resilient bootstrap / rendezvous:** bootstrap, discovery, RPC e rendezvous devono avere fonti multiple, intercambiabili e verificabili; la perdita, censura o compromissione di una singola fonte non deve impedire a due peer autorizzati di ritrovarsi quando esiste almeno un percorso disponibile.
- [ ] **Transport diversity:** consentire transport alternativi/sostituibili per ridurre la dipendenza da una singola firma di rete o classe di endpoint.
- [ ] **V1 product:** completare 1:1 text/media/file, voice messages, audio call, video call, Live mode, Network Indicator e onboarding senza configurazione tecnica manuale; i gruppi non devono bloccare il lancio.
- [ ] **Live Rooms:** introdurre successivamente gruppi sincroni/effimeri senza mailbox condivisa o consegna offline automatica; progettare separatamente voce/video multi-party e forwarding media scalabile.
- [ ] **Monetizzazione:** mantenere core interoperabile gratuito e monetizzare capacità relay/privacy gestita, funzioni Plus/Shield e servizi Business senza monetizzare contenuti o metadati di conversazione e senza creare dark pattern durante gli incidenti di rete.
- [ ] **Launch:** completare Founder Cohort, security/privacy review, Creator Pilot e criteri Go/No-Go definiti in [`docs/LAUNCH_PLAN.md`](docs/LAUNCH_PLAN.md) prima di scalare la promozione pubblica.

Freedom è definito dalle proprietà tecniche del protocollo: identità verificabile, comunicazione E2EE sincrona, routing distribuito, relay non fidati, path diversity, adaptive recovery, trasparenza sullo stato di rete e minima dipendenza dal registro distribuito durante una sessione attiva.
