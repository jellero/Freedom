# Freedom Messenger

**Powered by Freedom Protocol**

> **Synchronous. Ephemeral. Endpoint-to-endpoint.**

Freedom è un progetto di comunicazione privata progettato per un problema specifico: permettere a due persone presenti nello stesso momento di stabilire una sessione autenticata E2EE senza rendere una mailbox centrale, un singolo server, un singolo relay, un singolo provider o un singolo percorso di rete un requisito permanente.

Freedom non nasce come clone di WhatsApp, Signal, Session, SimpleX o Briar. Condivide con questi sistemi alcuni obiettivi — cifratura, privacy, decentralizzazione o resilienza — ma sceglie un trade-off centrale differente:

```text
peer raggiungibile + sessione autenticata -> comunica adesso
peer non raggiungibile                    -> non accodare per dopo
```

Il protocollo base è quindi **sincrono by design**. Non crea una mailbox di rete, non deposita messaggi sulla blockchain e non usa relay come storage persistente.

La blockchain/control-plane non trasporta chat, file, audio, video o APK. Serve come prima implementazione di un registro distribuito e verificabile per identity, key rotation/revocation, rendezvous/recovery, entitlement e piccoli manifest/policy firmati. Il traffico applicativo resta off-chain.

## Per chi è

Freedom è pensato soprattutto per utenti e organizzazioni che vogliono controllare non soltanto **chi può leggere la conversazione**, ma anche **da quali infrastrutture dipende la possibilità di stabilirla**.

Esempi:

- giornalisti, ricercatori e operatori in reti soggette a filtraggio o blocchi;
- attivisti e comunità che vogliono ridurre i single points of control;
- professionisti che vogliono comunicazioni live senza dipendere da mailbox o consegna differita;
- organizzazioni che vogliono poter utilizzare relay e infrastruttura compatibile propri;
- utenti che preferiscono identità/contatti espliciti tramite QR/link invece di un numero telefonico obbligatorio;
- utenti che vogliono poter condividere anche l'app stessa da persona a persona attraverso artifact verificati.

Freedom non promette anonimato assoluto, invisibilità contro un osservatore globale, comunicazione quando ogni forma di connettività è stata eliminata o rilevamento certo della sorveglianza passiva.

## In cosa differisce

La differenza non è una singola feature, ma la combinazione di proprietà:

```text
verifiable RootIdentity / DeviceID
        |
        v
pairwise contact bootstrap
        |
        v
distributed rendezvous / recovery
        |
        v
replaceable route
 direct / NAT / relay / device relay / shielded / future transport
        |
        v
authenticated E2EE live session
        |
        v
text / media / voice / video
```

Principi:

- identità separata dal percorso di rete;
- comunicazione applicativa off-chain;
- nessuna offline mailbox nel protocollo base;
- relay **forward-only**, non fidati e sostituibili;
- direct path non obbligatorio;
- più RPC/provider/bootstrap/relay/transport possibili;
- recovery pairwise tramite control-plane solo quando serve;
- stato della rete visibile all'utente quando qualcosa degrada;
- nessun provider commerciale necessario al trust crittografico;
- distribuzione del client verificabile indipendentemente dalla sorgente dei byte.

## Confronto oggettivo con altri sistemi

Freedom non viene presentato come "più sicuro" in assoluto. I concorrenti ottimizzano problemi differenti.

| Sistema | Punto di forza principale | Differenza rispetto a Freedom |
|---|---|---|
| **Signal** | UX, deployment E2EE production e grande esperienza operativa | usa un servizio di delivery e supporta messaggistica asincrona; Freedom punta a live-only, route/relay sostituibili e nessun server di delivery obbligatorio |
| **SimpleX** | metadata privacy e assenza di identificatori utente globali | usa queue pairwise temporanee per il delivery; Freedom usa DeviceID verificabile + control-plane di recovery e non accoda messaggi offline |
| **Session** | rete decentralizzata e onion routing | usa Session Nodes/swarm e storage per delivery; Freedom tratta i relay come forward-only e punta a path/transport switching adattivo |
| **Briar** | resilienza con Tor, Bluetooth e Wi-Fi | sincronizza messaggi quando i peer tornano disponibili; Freedom sceglie semantica sincrona e nessuna consegna futura automatica |

Questa tabella descrive **architetture e trade-off**, non una classifica assoluta. Le proprietà Freedom ancora indicate come target devono essere implementate, misurate e reviewate prima di poter essere trattate come garanzie production.

Analisi dettagliata e fonti: [`docs/COMPETITIVE_POSITIONING.md`](docs/COMPETITIVE_POSITIONING.md).

## Architettura

![Architettura del sistema Freedom](docs/assets/freedom-architecture.svg)

Architettura in una frase:

```text
RootIdentity
 -> DeviceID
 -> verifiable registry/control-plane
 -> rendezvous/recovery
 -> replaceable route
 -> authenticated E2EE live session
 -> messages/media
```

**NEAR non è Freedom Protocol.** È la prima implementazione del registro distribuito tramite `ChainAdapter`. Il core deve poter supportare adapter differenti senza cambiare DeviceID, session protocol o formato applicativo.

## RootIdentity, DeviceIdentity e Recovery Kit

Freedom separa ownership, device e sessione:

```text
RootIdentity   -> recovery / entitlement / device authorization
DeviceIdentity -> handshake operativo del device
Session keys   -> comunicazione effimera
```

La prima installazione genera localmente RootIdentity, DeviceIdentity e Recovery Kit e **non richiede automaticamente una write blockchain**.

Il Recovery Kit usa:

```text
QR / encrypted recovery bundle
+
recovery code separato
```

Dopo reset o nuovo telefono:

```text
recover RootIdentity
 -> generate NEW DeviceKey
 -> activate device
 -> restore entitlement
```

La licenza segue la RootIdentity; la chain può far rispettare `max_devices` senza pubblicare un mapping leggibile account → DeviceID.

Dettagli: [`docs/ACCOUNT_RECOVERY_LICENSES.md`](docs/ACCOUNT_RECOVERY_LICENSES.md).

## Registrazione Free e anti-abuse

L'utente Free non deve essere obbligato a possedere NEAR.

```text
install locale
 -> €0 / 0 chain writes
 -> identity realmente necessaria
 -> adaptive proof / PoW anti-abuse
 -> relayer rate limit
 -> bounded sponsorship budget
 -> registration
```

Obiettivo: il primo utilizzo deve essere accessibile a una persona reale; creare identità in massa deve avere costo crescente per un aggressore.

Messaggi, ACK, file, audio, video e route update **non generano transazioni on-chain**.

Dettagli: [`docs/REGISTRATION_ECONOMICS.md`](docs/REGISTRATION_ECONOMICS.md).

## Contatti Free e Relay Contributor

Policy iniziale:

```text
FREE                      1 device / 10 contatti attivi
FREE + RELAY CONTRIBUTOR  1 device / 20 contatti attivi
```

I 10 contatti sono slot attivi, non un limite lifetime. Eliminare/disattivare un contatto libera uno slot.

La lista contatti resta locale e cifrata. Un eventuale enforcement resistente a client modificati deve usare commitment/slot opachi, non pubblicare il social graph.

Un Free che fornisce capacità relay realmente utile ottiene **+10 contatti attivi**. Il semplice toggle `relay_enabled=true` non basta: il beneficio è temporaneo, privacy-preserving e progettato contro farming/traffico artificiale.

Se il benefit scade con più di 10 contatti già presenti, Freedom non cancella i contatti: blocca soltanto nuove aggiunte finché l'utente torna entro quota o si riqualifica.

## Relay: fisicamente cos'è

Un relay Freedom è una macchina o un dispositivo che esegue software di forwarding:

```text
VPS / VM
server dedicato
mini PC / Raspberry Pi
community node
managed/private relay
telefono / tablet / desktop Freedom
```

Un normale dispositivo Freedom può essere contemporaneamente:

```text
ENDPOINT -> comunica per il proprio utente
RELAY    -> inoltra ciphertext per circuiti altrui
```

Il relay:

- non possiede le chiavi E2EE endpoint-to-endpoint;
- non crea mailbox;
- usa buffer RAM bounded e TTL brevi;
- non è un trust anchor;
- può essere cambiato durante il recovery/path switching;
- non deve diventare un proxy Internet aperto;
- inoltra soltanto pacchetti/circuiti Freedom validi.

Il device relay è opt-in e deve rispettare limiti di batteria, temperatura, Wi-Fi/rete metered, CPU, RAM, banda e circuiti simultanei. Può contribuire anche senza IP pubblico tramite NAT mapping, transport supportati o connessioni outbound già stabilite.

Principio:

> **forward, not store.**

Dettagli: [`docs/RELAYS.md`](docs/RELAYS.md).

## Routing, metadata e Freedom Shield

Un IP non è un'identità Freedom.

```text
DeviceID       -> chi sei
RouteCandidate -> come posso raggiungerti ora
```

Il direct path è efficiente ma può esporre gli endpoint ai peer. Freedom deve quindi poter scegliere policy differenti:

```text
DIRECT
NAT_TRAVERSAL
RELAY
SHIELDED
MULTI_HOP
future/obfuscated transports
```

Shield può preferire relay/multi-hop per ridurre l'esposizione dell'endpoint. Il multi-hop deve essere progettato come vero circuit protocol; concatenare proxy non è sufficiente.

Freedom non deve usare DeviceID come identificatore di trasporto pubblico quando non necessario e deve minimizzare timing/correlazioni del control-plane.

## Adaptive Defense

Il control-plane diventa particolarmente utile quando il data-plane fallisce.

Freedom non pubblica presenza globale continua. Dopo la perdita dei path, peer già autenticati possono usare slot pairwise opachi e `RecoveryBeacon` cifrati/temporanei.

```text
peer recentemente attivo       yes
registry/control-plane          reachable
current data path               fail
        |
        v
INTERFERENCE_OR_ROUTE_FAILURE_SUSPECTED
        |
        v
alternate route / relay / transport
```

Questo **non prova sorveglianza** e non identifica automaticamente chi causa il problema. È un segnale per distinguere meglio peer offline da route/interferenza probabile e reagire cambiando percorso.

Dettagli: [`docs/ADAPTIVE_DEFENSE.md`](docs/ADAPTIVE_DEFENSE.md).

## Freedom Network Indicator

Freedom rende accessibile lo stato della rete invece di nasconderlo completamente.

```text
NORMAL       percorso funzionante
SHIELDED     percorso protetto attivo
DEGRADED     degradazione/fallback
SUSPECTED    probabile filtraggio/interferenza o failure selettiva
UNAVAILABLE  peer recentemente attivo ma nessun path valido
```

Quando viene rilevato un incidente significativo, il pannello può aprirsi automaticamente una volta e mostrare separatamente:

- fatti osservati;
- inferenza;
- route fallite;
- fallback tentati/attivi;
- livello di protezione.

Free e Pro ricevono la stessa diagnosi tecnica.

> **Semplice quando tutto funziona. Trasparente quando qualcosa cerca di impedirti di comunicare.**

Dettagli: [`docs/NETWORK_STATUS_UI.md`](docs/NETWORK_STATUS_UI.md).

## Messaggistica sincrona e Live mode

Per inviare un contenuto deve esistere una sessione autenticata attiva.

```text
SEND
  |
  +-- active authenticated session? -- no --> DISCARD / FAIL
  |
  +-- yes --> transmit --> ACK/session result
```

Nessun deposito automatico su blockchain, relay o coda locale futura.

La modalità **Live** può inoltre evitare la cronologia persistente locale, i backup automatici del plaintext e le preview persistenti; al termine elimina lo stato previsto e distrugge le session key effimere.

Questa proprietà riguarda il client locale: non può impedire al destinatario o a un OS compromesso di fare screenshot, registrazioni o copie.

## Share Freedom: installazione da persona a persona

Freedom Direct deve poter essere distribuito anche da un utente che possiede già una copia genuina.

```text
Alice -> Share Freedom -> mostra QR
Bob   -> fotocamera/browser -> descriptor
      -> peer / relay / mirror / store
      -> download artifact
      -> verify
      -> Android installer
```

Decisione architetturale: **non incorporare per default una seconda copia dell'APK dentro l'app**. Un client Direct può invece mantenere opzionalmente in cache un APK standalone già verificato e servirlo localmente via LAN/hotspot/transport compatibile.

La sorgente dei byte non è fidata. Prima dell'installazione devono essere verificati almeno:

```text
FreedomRelease signatures
artifact SHA-256
package ID
version code
signing certificate / lineage
security policy / downgrade rules
```

Un peer o relay compromesso può servire byte sbagliati, ma non deve poter ridefinire quale signer rappresenta Freedom.

### Primo install e app farlocche

Il primo sideload è il caso più delicato: un APK malevolo può essere firmato perfettamente dalla **chiave dell'attaccante**. Per questo il primo install richiede un trust anchor indipendente dal peer che distribuisce il file, per esempio store ufficiale, bootstrap autenticato o release root/fingerprint verificato.

Dopo una prima installazione genuina, Android e il sistema FreedomRelease possono rifiutare aggiornamenti firmati da identità incompatibili.

Le build Play e Direct restano separate:

```text
Freedom Play   -> Share QR verso canale/store conforme
Freedom Direct -> peer/relay/mirror + artifact verification + installer OS
```

Dettagli: [`docs/APP_DISTRIBUTION.md`](docs/APP_DISTRIBUTION.md) e [`docs/EMERGENCY_UPDATES.md`](docs/EMERGENCY_UPDATES.md).

## Emergency bulletin e secure updates

Il control-plane può pubblicare piccoli oggetti firmati:

```text
EmergencyBulletin
SecurityPolicy
FreedomRelease
```

Un bulletin geografico contiene lo scope; il matching della posizione avviene localmente, senza pubblicare la posizione dell'utente.

L'APK resta off-chain e può arrivare da store, peer, relay/update node o mirror sostituibili. La release è autenticata dal manifest/hash/firma, non dalla provenienza del file.

Le policy critiche dovrebbero usare firme threshold/multi-key. Una vulnerabilità deve portare preferibilmente alla disabilitazione della sola superficie insicura, preservando recovery/update quando tecnicamente sicuri, non a un kill-switch commerciale arbitrario.

Dettagli: [`docs/EMERGENCY_UPDATES.md`](docs/EMERGENCY_UPDATES.md).

## Pagamenti ed entitlement

Freedom è payment-provider agnostic:

```text
PaymentAdapter
|- PayPal
|- crypto native
|- stablecoin
|- future providers
```

L'utente compra servizi Freedom, **non NEAR**.

PayPal può essere aperto dal client senza rendere necessario un server Freedom pubblico. Worker privati outbound-only possono interrogare PayPal e pubblicare una `PaymentAttestation` verificabile. Il semplice callback/OK dell'app non è prova economica autoritativa e nessun merchant `client_secret` deve stare nell'APK.

Crypto verificabile on-chain può attivare direttamente l'entitlement quando il payment adapter lo consente.

Dettagli: [`docs/PAYMENTS.md`](docs/PAYMENTS.md).

## Monetizzazione

Principi:

> **monetizzare capacità, comodità e servizi professionali; non la conversazione.**

> **la censura non deve diventare un paywall.**

### Free

- 1 device attivo;
- 10 contatti attivi;
- +10 con Relay Contributor qualificato;
- core E2EE/live;
- route/RPC fallback;
- Network Indicator;
- community/device relay;
- quota bounded Emergency Shield;
- recovery base;
- sponsorship chain essenziale secondo anti-abuse.

### Freedom Plus / Shield

- contatti/device superiori;
- capacità relay gestita maggiore;
- Always-Shielded;
- multi-hop;
- path/provider diversity più ampia;
- candidate pre-warmed;
- parallel failover;
- transport rotation aggressiva;
- Maximum Resilience;
- limiti media/file superiori.

Pro non compra una cifratura "più forte" né una diagnosi più onesta.

### Business

- SDK/integrations;
- deployment privati;
- relay/Shield pool dedicati;
- supporto/SLA;
- infrastruttura compatibile gestita.

Dettagli: [`docs/MONETIZATION.md`](docs/MONETIZATION.md).

## Scope prodotto

V1 resta deliberatamente 1:1:

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

I gruppi arrivano dopo come **Live Groups / Live Rooms**, mantenendo la semantica sincrona: chi è assente non riceve automaticamente la cronologia persa.

Voice/video multi-party richiederanno forwarding media scalabile e sostituibile, non un singolo SFU come trust anchor permanente.

Dettagli: [`docs/PRODUCT_SCOPE.md`](docs/PRODUCT_SCOPE.md).

## Stato del codice Android

Il codice Android presente nel repository è ancora uno **transport/crypto spike precedente alla specifica corrente**. Dimostra socket diretto, handshake cifrato e comunicazione E2EE di test, ma non rappresenta l'M1 canonico e deve essere rifattorizzato verso l'architettura documentata.

La maturità production non va confusa con la qualità del concept: le proprietà sopra devono essere dimostrate attraverso implementazione, test su device/reti reali, fuzzing, review crittografica e security review indipendente.

## Documentazione

- [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) — architettura completa.
- [`docs/PROTOCOL.md`](docs/PROTOCOL.md) — oggetti e flussi normativi.
- [`docs/CHAIN.md`](docs/CHAIN.md) — control-plane blockchain, identity, recovery, entitlement e manifest.
- [`docs/THREAT_MODEL.md`](docs/THREAT_MODEL.md) — minacce, limiti e mitigazioni.
- [`docs/RELAYS.md`](docs/RELAYS.md) — relay fisici, device relay, resource bounds e Relay Contributor.
- [`docs/ADAPTIVE_DEFENSE.md`](docs/ADAPTIVE_DEFENSE.md) — recovery pairwise e rilevamento interferenza.
- [`docs/NETWORK_STATUS_UI.md`](docs/NETWORK_STATUS_UI.md) — indicator UX e Emergency Shield.
- [`docs/ACCOUNT_RECOVERY_LICENSES.md`](docs/ACCOUNT_RECOVERY_LICENSES.md) — RootIdentity, Recovery Kit e multi-device.
- [`docs/PAYMENTS.md`](docs/PAYMENTS.md) — PayPal outbound-worker, crypto e PaymentAttestation.
- [`docs/REGISTRATION_ECONOMICS.md`](docs/REGISTRATION_ECONOMICS.md) — sponsorship Free, anti-Sybil e storage bounded.
- [`docs/EMERGENCY_UPDATES.md`](docs/EMERGENCY_UPDATES.md) — bulletin, SecurityPolicy e release firmate.
- [`docs/APP_DISTRIBUTION.md`](docs/APP_DISTRIBUTION.md) — Share Freedom, peer APK transfer e trust del primo install.
- [`docs/COMPETITIVE_POSITIONING.md`](docs/COMPETITIVE_POSITIONING.md) — confronto oggettivo con Signal, SimpleX, Session e Briar.
- [`docs/PRODUCT_SCOPE.md`](docs/PRODUCT_SCOPE.md) — V1, Live Groups/Rooms e roadmap.
- [`docs/MONETIZATION.md`](docs/MONETIZATION.md) — modello Free/Shield/Business.
- [`docs/LAUNCH_PLAN.md`](docs/LAUNCH_PLAN.md) — validazione e lancio.
- [`docs/STORE_COMPLIANCE.md`](docs/STORE_COMPLIANCE.md) — separazione protocollo/client e vincoli store.
- [`ANDROID.md`](ANDROID.md) — roadmap Android.

## Roadmap sintetica

```text
M0  specification / threat model
M1  RootIdentity + DeviceID + Recovery Kit + sponsored registration
M2  registry + QR + rendezvous read-before-write
M3  authenticated secure session
M4  NAT traversal + route updates
M5  relay forward-only + community/device relay + Relay Contributor
M6  V1 1:1 text/media/voice/video + Live + Network Indicator
M7  Share Freedom + verified peer/direct distribution
M8  entitlement + max_devices + Free contact policy + payment adapters
M9  emergency bulletin + signed secure update plane
M10 Live Groups / Live Rooms
M11 group voice/video + scalable media forwarding
M12 Adaptive Defense + Emergency Shield + Shield hardening
M13 iOS/platform integration
M14 hardening, fuzzing, censorship tests, interoperability, independent review
```

## Principio finale

Freedom non è definito dall'uso di una blockchain, da un relay specifico o da un'app Android.

È definito dalle proprietà che devono continuare a valere anche quando i componenti cambiano:

> **identità verificabile, comunicazione E2EE sincrona, nessuna mailbox obbligatoria, percorsi sostituibili, relay non fidati, recovery distribuito, trasparenza sullo stato di rete e minima dipendenza da infrastruttura permanente.**