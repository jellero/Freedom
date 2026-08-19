# Freedom

## Protocollo decentralizzato di comunicazione

Freedom è un protocollo decentralizzato di comunicazione che permette a dispositivi identificati crittograficamente di stabilire sessioni sicure senza dipendere da un server centrale di messaggistica.

La blockchain non trasporta messaggi, file, audio, video o APK. Viene usata come prima implementazione di un **control-plane distribuito e verificabile** per identità, key rotation/revocation, rendezvous/recovery, entitlement, policy di sicurezza e piccoli manifest firmati.

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
- nessun singolo server, relay, RPC, provider, payment provider, IP o percorso deve essere requisito permanente;
- rilevamento di route failure/interferenza probabile attraverso segnali indipendenti, incluso il caso **peer recentemente attivo sul control-plane ma data-plane indisponibile**;
- recovery automatico attraverso route/relay/transport alternativi quando disponibili;
- indicatore di rete visibile e cliccabile che spiega agli utenti Free e Pro cosa Freedom ha osservato e quali contromisure sta applicando;
- quota limitata di Emergency Shield anche per il tier Free quando l'infrastruttura gestita è necessaria e disponibile;
- recovery dell'account tramite RootIdentity e Recovery Kit senza dipendere da un account server centrale;
- entitlement/licenze ripristinabili con controllo on-chain del numero di device attivi;
- pagamenti provider-agnostic tramite PayPal, crypto e futuri adapter senza rendere il provider parte del trust model;
- registrazione Free sponsorizzabile ma protetta da anti-abuse/rate limit/budget bounded;
- emergency bulletin e security policy verificabili;
- aggiornamenti applicativi autenticati da manifest firmati e distribuibili da sorgenti sostituibili;
- protocollo indipendente da Android, iOS, store e servizi commerciali ufficiali;
- possibilità di sostituire la blockchain tramite un adapter senza cambiare il protocollo applicativo.

## Architettura

![Architettura del sistema Freedom](docs/assets/freedom-architecture.svg)

Per la consegna di un messaggio applicativo deve esistere una sessione autenticata attiva tra gli endpoint. Se il destinatario non è raggiungibile, il protocollo base **non accoda il messaggio per una consegna futura** e non lo dissemina nella rete.

## Architettura in una frase

```text
RootIdentity -> DeviceID -> verifiable registry -> rendezvous/recovery -> route -> authenticated E2EE live session -> messages/media
```

## Registro distribuito e blockchain

Freedom necessita della **funzione** di un registro distribuito e verificabile per:

- risolvere `DeviceID -> identity_public_key`;
- verificare key rotation e revocation;
- pubblicare/leggere rendezvous di fallback quando tutte le route note sono perse;
- coordinare recovery pairwise temporaneo quando il data-plane è perso;
- verificare entitlement e limiti device senza account server obbligatorio;
- pubblicare piccoli manifest/policy firmati per sicurezza, emergenze e update.

Questa funzione è fondamentale per il trust model attuale. **NEAR non è fondamentale come tecnologia specifica.**

La prima implementazione usa **NEAR Testnet** attraverso un'interfaccia `ChainAdapter`.

NEAR non fa parte del wire protocol Freedom: è la prima implementazione del registro/control-plane decentralizzato. Il core deve poter supportare adapter differenti senza cambiare DeviceID, session protocol o formato dei messaggi.

Durante una sessione attiva il registro non è nel packet hot path e non viene interrogato o scritto per ogni messaggio.

## RootIdentity, recovery e licenze

Freedom separa ownership/recovery dal singolo device:

```text
RootIdentity   -> recovery / entitlement / device authorization
DeviceIdentity -> handshake e identità operativa del device
Session keys   -> comunicazione effimera
```

Il semplice install genera RootIdentity, DeviceIdentity e Recovery Kit localmente e **non richiede una write blockchain immediata**.

Il Recovery Kit prevede QR/bundle cifrato + recovery code separato. Dopo reset/nuovo telefono viene recuperata la RootIdentity, ma viene generata una **nuova DeviceKey**.

La licenza segue la RootIdentity e la chain fa rispettare un limite `max_devices`; il restore non deve permettere di clonare la stessa licenza su telefoni illimitati.

Policy iniziale Free: **1 device attivo e 10 contatti attivi**. Il limite contatti riguarda slot attivi liberabili; la rubrica resta locale/cifrata e non diventa un social graph pubblico.

Dettagli: [`docs/ACCOUNT_RECOVERY_LICENSES.md`](docs/ACCOUNT_RECOVERY_LICENSES.md).

## Registrazione Free ed economia anti-abuso

Freedom può sponsorizzare le operazioni essenziali Free senza obbligare l'utente a possedere NEAR.

La sponsorship non è illimitata:

```text
new RootIdentity
 -> signature valida
 -> proof anti-abuse / PoW leggero adattivo
 -> sponsorship non già consumata
 -> rate limit del relayer
 -> budget globale bounded
 -> register
```

Fee relayer multipli devono poter coesistere. Un attacco alle nuove registrazioni non deve interrompere gli utenti già registrati.

Il costo blockchain deve crescere con identità realmente usate ed eventi rari, **mai con il numero di messaggi, chiamate o frame media**.

Dettagli: [`docs/REGISTRATION_ECONOMICS.md`](docs/REGISTRATION_ECONOMICS.md).

## Ruolo del registro — rendezvous e recovery

Il registro viene usato per ristabilire un percorso solo quando non esiste più alcuna route Freedom valida tra due endpoint online.

Regola fondamentale:

```text
1. READ
2. se esiste un rendezvous valido -> usa i dati, NON scrivere
3. se non esiste -> WRITE del proprio rendezvous
```

Appena viene ristabilita una sessione, gli aggiornamenti di endpoint, NAT candidate e relay candidate passano direttamente nel canale E2EE.

### Recovery beacon

Freedom non pubblica una presenza globale continua. Dopo la perdita completa del data path, due peer già autenticati possono usare slot pairwise opachi per pubblicare un `RecoveryBeacon` cifrato e a TTL breve.

Un beacon indica **attività recente**, non presenza assoluta in tempo reale.

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

> **Semplice quando tutto funziona. Trasparente quando qualcosa cerca di impedirti di comunicare.**

Il client ufficiale prevede un **Freedom Network Indicator** piccolo, sempre accessibile e cliccabile.

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

`rendezvous_capability` è casuale e può essere monouso o temporanea. Dopo il primo handshake autenticato, i due endpoint derivano un `PairRendezvousSecret` locale usato per generare slot on-chain opachi e rotanti.

## Routing, privacy di rete e censura

Freedom distingue identità e raggiungibilità.

```text
DeviceID -> chi sei
RouteCandidate -> come posso raggiungerti adesso
```

Un indirizzo IP non è un'identità Freedom e non deve diventare un identificatore stabile del device.

La comunicazione diretta è efficiente ma espone gli endpoint di rete ai peer. Il client deve poter preferire relay o percorsi schermati quando la privacy di rete è prioritaria.

## Censorship resistance / path diversity

Freedom non promette invisibilità assoluta o disponibilità contro un avversario capace di interrompere completamente la connettività. L'obiettivo è evitare **single points of control**.

```text
direct path blocked     -> altro route
relay A blocked         -> relay B / altro path
RPC A blocked           -> RPC B
provider unavailable    -> provider alternativo
transport filtrato      -> transport alternativo
```

Bootstrap, RPC, relay, fee relayer e provider devono essere multipli e sostituibili.

## Relay

Un relay Freedom è un nodo di transito, non un server di messaggistica.

```text
Alice -> ciphertext -> Relay -> ciphertext -> Bob
```

Un relay non possiede le chiavi E2EE, non crea mailbox persistenti, usa buffer limitati/temporanei e può essere sostituito durante la sessione.

Principio: **forward, not store**.

## Sessione sicura e messaggistica sincrona

Una route non autentica un peer. Gli endpoint eseguono un handshake autenticato bilateralmente usando la current identity key verificata.

```text
Alice <============================> Bob
          authenticated E2EE
```

Se non esiste una sessione autenticata attiva o il peer non è raggiungibile, il protocollo base non deposita il messaggio sulla blockchain, sui relay o in una coda locale per consegna futura.

I client possono offrire modalità **Live** senza cronologia persistente locale secondo policy.

## Scope prodotto

Il primo lancio di Freedom Messenger è deliberatamente focalizzato sul **1:1**: onboarding, contatto, sessione, messaggi, media, vocali, chiamata, videochiamata, Live mode e Network Indicator.

I gruppi arrivano successivamente come **Live Groups / Live Rooms**, senza mailbox condivisa o consegna automatica della cronologia agli utenti assenti.

Dettagli: [`docs/PRODUCT_SCOPE.md`](docs/PRODUCT_SCOPE.md).

## Pagamenti

Freedom è payment-provider agnostic:

```text
PaymentAdapter
|- PayPal
|- crypto native
|- stablecoin
|- future providers
```

L'utente compra servizi Freedom, non NEAR.

Per PayPal, l'app può aprire il checkout ospitato/in-app senza contattare un server Freedom pubblico. La prova economica non può essere il semplice callback del client: worker privati **outbound-only** possono interrogare PayPal, riconciliare un `purchase_ref` opaco e pubblicare una `PaymentAttestation` on-chain.

Per crypto verificabile direttamente dalla chain, il pagamento può attivare l'entitlement senza worker esterno.

Nessun `client_secret` merchant deve essere inserito nell'APK.

Dettagli: [`docs/PAYMENTS.md`](docs/PAYMENTS.md).

## Gas e fee relayer

Le rare operazioni on-chain possono richiedere fee. Freedom può supportare **fee relayer indipendenti** che sponsorizzano il gas senza possedere l'identità dell'utente.

L'utente Free non deve essere obbligato a comprare NEAR. Treasury/relayer possono sostenere registrazione e operazioni essenziali secondo budget e anti-abuse.

Messaggi, ACK, file, audio, video e route update in-session non generano transazioni on-chain.

## Emergency bulletin e aggiornamenti sicuri

Freedom può pubblicare on-chain piccoli `EmergencyBulletin`, `SecurityPolicy` e `FreedomRelease` firmati.

Le notifiche possono essere geolocalizzate senza pubblicare la posizione dell'utente: il matching tra area del bulletin e posizione/area selezionata avviene localmente.

La blockchain **non ospita l'APK**. Pubblica hash, versione, signing fingerprint e sorgenti. L'APK può arrivare da store, mirror temporanei/dinamici, peer Freedom, relay/update node o futuri transport; la sorgente non è fidata, il manifest firmato sì.

Per policy critiche si preferiscono firme threshold/multi-key. Non deve esistere un kill-switch commerciale arbitrario: una versione vulnerabile può disabilitare selettivamente la superficie insicura mantenendo recovery e update quando sicuri.

Dettagli: [`docs/EMERGENCY_UPDATES.md`](docs/EMERGENCY_UPDATES.md).

## Monetizzazione

> **monetizzare capacità, comodità e servizi professionali; non la conversazione.**

> **la censura non deve diventare un paywall.**

Free comprende core E2EE, 1 device attivo, 10 contatti attivi, Network Indicator, resilienza base ed Emergency Shield con capacità gestita limitata.

Freedom Plus / Shield può offrire contatti/slot device superiori, capacità relay maggiore, Always-Shielded, multi-hop, pre-warming, failover parallelo e Maximum Resilience.

Freedom Business può offrire SDK, deployment, relay dedicati, supporto e SLA.

Dettagli: [`docs/MONETIZATION.md`](docs/MONETIZATION.md).

## Store

Freedom Protocol è separato dai client ufficiali. Le build store rispettano le policy di aggiornamento/distribuzione della piattaforma; una Freedom Direct build può usare sorgenti update indipendenti compatibili con il sistema operativo.

## Stato del codice Android

Il codice Android presente nel repository è un **transport/crypto spike precedente alla specifica attuale**. Dimostra socket diretto e handshake cifrato, ma usa ancora IP manuale e fingerprint/TOFU.

Non rappresenta più l'M1 canonico.

## Documentazione

- [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) — architettura completa.
- [`docs/PROTOCOL.md`](docs/PROTOCOL.md) — oggetti e flussi del protocollo.
- [`docs/CHAIN.md`](docs/CHAIN.md) — blockchain/control-plane, identity, recovery, entitlement e manifest.
- [`docs/THREAT_MODEL.md`](docs/THREAT_MODEL.md) — modello di sicurezza.
- [`docs/ADAPTIVE_DEFENSE.md`](docs/ADAPTIVE_DEFENSE.md) — liveness/recovery pairwise, rilevamento interferenza e Freedom Shield.
- [`docs/NETWORK_STATUS_UI.md`](docs/NETWORK_STATUS_UI.md) — indicatore multistato, trasparenza e Emergency Shield Free.
- [`docs/ACCOUNT_RECOVERY_LICENSES.md`](docs/ACCOUNT_RECOVERY_LICENSES.md) — RootIdentity, Recovery Kit, entitlement e limiti multi-device.
- [`docs/PAYMENTS.md`](docs/PAYMENTS.md) — PayPal outbound-worker, crypto e PaymentAttestation.
- [`docs/REGISTRATION_ECONOMICS.md`](docs/REGISTRATION_ECONOMICS.md) — sponsorship Free, anti-Sybil/PoW, rate limit e storage bounded.
- [`docs/EMERGENCY_UPDATES.md`](docs/EMERGENCY_UPDATES.md) — bulletin geolocalizzati, security policy e aggiornamenti distribuiti firmati.
- [`docs/PRODUCT_SCOPE.md`](docs/PRODUCT_SCOPE.md) — scope V1, Live Groups/Rooms e roadmap multi-party.
- [`docs/MONETIZATION.md`](docs/MONETIZATION.md) — principi economici e servizi opzionali.
- [`docs/LAUNCH_PLAN.md`](docs/LAUNCH_PLAN.md) — piano di validazione e lancio.
- [`docs/STORE_COMPLIANCE.md`](docs/STORE_COMPLIANCE.md) — separazione protocollo/client e vincoli store.
- [`ANDROID.md`](ANDROID.md) — roadmap Android.

## Roadmap sintetica

```text
M0  specifica
M1  RootIdentity + DeviceID + Recovery Kit + sponsored registration
M2  NEAR Testnet registry + QR + rendezvous read-before-write
M3  authenticated secure session
M4  NAT traversal + route updates
M5  relay forward-only
M6  V1: 1:1 messaging + media + voice/video + Live + Network Indicator
M7  entitlement + max_devices + Free contact policy + payment adapters
M8  emergency bulletin + signed secure update plane
M9  V1.5: Live Groups / Live Rooms
M10 V2: group voice/video + media forwarding scalabile
M11 adaptive recovery + Emergency Shield + Freedom Shield hardening
M12 iOS + platform wake integration
M13 hardening, censorship resistance, testing, interoperability
```

## TODO

- [ ] Brand client ufficiale: **Freedom Messenger** — *Powered by Freedom Protocol*.
- [ ] **RootIdentity / Recovery Kit:** QR/bundle cifrato + recovery code separato; restore con nuova DeviceKey.
- [ ] **Sponsored registration / anti-Sybil:** install senza write, PoW/adaptive proof, relayer multipli, rate limit e budget bounded.
- [ ] **Free policy:** 1 device attivo e 10 contatti attivi; contatti liberabili e nessun social graph pubblico.
- [ ] **Entitlement / multi-device:** licenza legata alla RootIdentity con `max_devices` enforced on-chain tramite slot/commitment opachi.
- [ ] **Payments:** adapter PayPal + crypto; PayPal senza server pubblico obbligatorio, worker outbound-only per attestazione; nessun merchant secret nell'APK.
- [ ] **Censorship resistance / path diversity:** nessun singolo server, relay, RPC endpoint, provider blockchain, fee relayer, IP o percorso di rete deve costituire un punto unico di controllo o interruzione.
- [ ] **Adaptive Defense:** recovery beacon pairwise, temporanei e cifrati; classificazione multi-segnale e route/relay/transport alternativi.
- [ ] **Freedom Network Indicator:** indicatore sempre accessibile, multistato e cliccabile; apertura automatica per incidenti significativi.
- [ ] **Emergency Shield Free:** quota limitata di capacità gestita per tentare bypass anche agli utenti Free.
- [ ] **Freedom Shield / Pro:** Always-Shielded, relay multipli, multi-hop, pre-warming, failover parallelo e Maximum Resilience.
- [ ] **Emergency bulletin:** avvisi firmati globali/geografici con matching posizione locale e nessuna posizione utente on-chain.
- [ ] **Secure updates:** release manifest firmato on-chain; APK off-chain da store/mirror/peer/relay sostituibili; hash/signing verification e policy anti-downgrade.
- [ ] **Security policy:** threshold governance e inibizione selettiva di funzioni/versioni vulnerabili, senza kill-switch commerciale arbitrario.
- [ ] **Network privacy / metadata resistance:** alias/session identifiers pairwise o temporanei e percorsi relay/shielded.
- [ ] **Resilient bootstrap / rendezvous:** fonti multiple, intercambiabili e verificabili.
- [ ] **Transport diversity:** transport alternativi/sostituibili.
- [ ] **V1 product:** 1:1 text/media/file, voice messages, audio call, video call, Live mode, Network Indicator e onboarding senza configurazione tecnica manuale.
- [ ] **Live Rooms:** gruppi sincroni/effimeri senza mailbox condivisa o delivery offline automatico.
- [ ] **Monetizzazione:** core interoperabile gratuito e monetizzazione di capacità/feature senza contenuti/metadati e senza dark pattern.
- [ ] **Launch:** Founder Cohort, security/privacy review, Creator Pilot e criteri Go/No-Go prima della scala pubblica.

Freedom è definito dalle proprietà tecniche del protocollo: identità verificabile e recuperabile, comunicazione E2EE sincrona, routing distribuito, relay non fidati, path diversity, adaptive recovery, entitlement verificabili, control-plane di sicurezza e minima dipendenza dal registro distribuito durante una sessione attiva.
