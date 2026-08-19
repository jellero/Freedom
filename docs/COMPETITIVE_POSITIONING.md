# Freedom — Competitive Positioning

Status: **product/architecture positioning draft**

Ultima verifica delle fonti concorrenti: 2026-08-19.

Questo documento descrive dove Freedom si colloca rispetto ad altri sistemi di comunicazione orientati a privacy, sicurezza o decentralizzazione. Non assegna un vincitore assoluto: i prodotti ottimizzano problemi diversi e alcune proprietà target di Freedom devono ancora essere dimostrate in implementazione production.

## 1. Che cos'è Freedom

Freedom Messenger è il client ufficiale di **Freedom Protocol**, un protocollo di comunicazione privata progettato attorno a quattro proprietà centrali:

1. **comunicazione sincrona** — il protocollo base consegna contenuti solo quando esiste una sessione autenticata attiva; non crea una mailbox/offline queue;
2. **identità verificabile senza global DeviceID di rete** — RootIdentity per ownership, DeviceKey autorizzate tramite commitment opachi, alias pairwise per le relazioni e token temporanei per il transport;
3. **data-plane sostituibile** — direct path, NAT traversal, relay, device/community relay e futuri transport possono essere cambiati senza cambiare ownership/contact identity;
4. **control-plane distribuito di recovery** — il registro verificabile serve per authorization/revocation, rendezvous/recovery e piccoli manifest/policy, ma non trasporta messaggi, file, audio o video.

Formula sintetica:

> **Synchronous. Ephemeral. Endpoint-to-endpoint.**

Freedom non promette anonimato assoluto, invisibilità contro un osservatore globale o comunicazione quando ogni forma di connettività è stata eliminata.

## 2. Per chi è

Freedom è pensato soprattutto per persone e organizzazioni che attribuiscono valore non soltanto alla cifratura del contenuto, ma anche alla **riduzione delle dipendenze infrastrutturali** e alla possibilità di capire quando la rete sta degradando o filtrando i percorsi disponibili.

Esempi:

- giornalisti, ricercatori e operatori in ambienti con rischio di filtraggio o blocco;
- attivisti e comunità che non vogliono dipendere da un singolo server/provider;
- professionisti che vogliono comunicazioni live senza mailbox o consegna differita;
- team tecnici o organizzazioni che vogliono poter usare relay propri o infrastruttura compatibile;
- utenti che preferiscono contatti espliciti via QR/link invece di una directory pubblica o numero telefonico come identità necessaria;
- utenti che vogliono poter distribuire il client da persona a persona tramite artifact verificati, senza rendere uno store l'unico canale possibile.

Freedom non è progettato per sostituire necessariamente i messenger generalisti in ogni scenario. Il trade-off principale è intenzionale: **se il destinatario non è raggiungibile, il protocollo base non conserva il messaggio per recapitarlo più tardi**.

## 3. Signal

Signal è un riferimento per comunicazione E2EE production, semplicità d'uso e deployment su larga scala. Signal supporta username per iniziare conversazioni senza condividere il numero con il peer, ma continua a richiedere un numero telefonico per la registrazione. Il servizio Signal facilita anche la consegna asincrona dei messaggi e usa meccanismi come Sealed Sender per ridurre i metadata visibili al servizio.

Fonti ufficiali:

- https://support.signal.org/hc/en-us/articles/6712070553754-Phone-Number-Privacy-and-Usernames
- https://signal.org/blog/sealed-sender/

### Differenza Freedom

```text
Signal (semplificato)
endpoint -> servizio di delivery -> endpoint
           supporto asincrono

Freedom
endpoint <-> route sostituibile <-> endpoint
           sessione live
           niente mailbox base
```

Freedom mira inoltre a rendere relay, RPC, provider e route intercambiabili e a esporre all'utente uno stato diagnostico quando il data-plane viene degradato.

### Dove Signal resta benchmark

- maturità production;
- UX;
- affidabilità su larga scala;
- implementazione e analisi crittografica accumulate;
- ecosistema e interoperabilità tra piattaforme già operativi.

Freedom non deve presentare la propria architettura target come prova di sicurezza superiore finché le proprietà non sono implementate, testate e reviewate.

## 4. SimpleX

SimpleX adotta una scelta radicale per la metadata privacy: non assegna identificatori globali agli utenti e usa indirizzi pairwise di queue unidirezionali. I relay mantengono temporaneamente i messaggi finché vengono ricevuti. Gli utenti possono usare server propri e possono usare Tor per nascondere l'IP ai server.

Fonti ufficiali:

- https://simplex.chat/docs/simplex.html
- https://simplex.chat/messaging/

### Differenza Freedom

```text
SimpleX
no global user identifier
pairwise anonymous queues
relay temporaneamente store-and-forward

Freedom
RootIdentity per ownership, non per routing
opaque DeviceRecordCommitment per control-plane
pairwise contact aliases / rendezvous state
transport tokens temporanei
no offline delivery queue nel protocollo base
live authenticated session
```

La precedente idea di un `DeviceID` globale è stata rimossa dal modello canonico proprio per evitare una superficie di correlazione non necessaria.

Il vantaggio potenziale di Freedom è il control-plane verificabile per device authorization/revocation/recovery e la semantica live-only. Il rischio tecnico resta però reale: commitment stabili, timing del registro, activation/revocation e provider visibility possono creare correlazioni anche senza un nome globale.

Per questo **SimpleX resta il benchmark principale di Freedom per metadata discipline**. Freedom non deve dichiararsi più anonimo di SimpleX senza misure e threat-model evidence.

## 5. Session

Session usa una rete decentralizzata di Session Nodes e Onion Requests per nascondere l'IP del mittente. Il mittente individua lo swarm associato all'Account ID del destinatario e inoltra il messaggio attraverso la rete Session; il modello include storage dei messaggi nello swarm per permettere delivery successiva.

Fonte ufficiale:

- https://docs.getsession.org/session-network/session-protocol/onion-requests-and-message-routing

### Differenza Freedom

Freedom evita intenzionalmente lo storage mailbox/store-and-forward nel protocollo base e tratta il relay come nodo **forward-only**. Inoltre può usare device degli utenti come relay best-effort e mira a cambiare classi di transport/percorso quando il path corrente fallisce.

Session è un benchmark utile per:

- rete decentralizzata operativa;
- onion routing;
- protezione IP;
- gestione reale di un insieme distribuito di nodi.

Freedom differisce soprattutto sulla semantica sincrona e sull'Adaptive Defense/control-plane di recovery.

## 6. Briar

Briar è progettato esplicitamente per comunicazioni robuste in condizioni difficili. Non dipende da un server centrale, usa Tor quando Internet è disponibile e può sincronizzare tramite Bluetooth o Wi-Fi quando Internet non è disponibile. Per i messaggi privati, se un contatto è offline, il messaggio viene consegnato quando entrambi tornano online.

Fonti ufficiali:

- https://briarproject.org/manual/
- https://briarproject.org/quick-start/

### Differenza Freedom

Briar accetta la persistenza/sincronizzazione necessaria al recapito successivo; Freedom sceglie la proprietà opposta per il protocollo base:

```text
peer offline -> not delivered
```

Briar è però il benchmark più concreto per Freedom sulla **transport diversity reale**, soprattutto quando la connettività Internet viene meno.

Freedom dovrà dimostrare transport alternativi reali prima di poter sostenere una resilienza comparabile in scenari di shutdown/degrado estremo.

## 7. Matrice concettuale

La tabella descrive il modello architetturale, non una classifica di sicurezza assoluta.

| Proprietà | Freedom target | Signal | SimpleX | Session | Briar |
|---|---|---|---|---|---|
| E2EE | sì | sì | sì | sì | sì |
| Delivery offline nel modello base | **no** | sì | sì, queue temporanee | sì | sì quando i peer tornano disponibili |
| Identificatore utente/device globale richiesto nel transport | **no**: RootIdentity + commitment opachi + alias pairwise | account legato a numero; username opzionale per contatto | **no** | Account ID | modello proprio di identità/contatti |
| Server centrale di delivery obbligatorio | **no** | sì come servizio | no singolo server; relay scelti/self-hosted | rete Session distribuita | no |
| Relay/device community forward-only | **target sì** | non è il modello | relay queue | Session Nodes | peer synchronization |
| Path/transport switching adattivo | **target core** | non è il focus architetturale | server/Tor configurabili | onion routing | Tor/Bluetooth/Wi-Fi |
| Control-plane verificabile di authorization/recovery | **target sì** | modello Signal | no user identity registry | Account ID/network state | modello Briar |
| Network interference indicator | **target sì** | non è focus | non equivalente | non equivalente | connettività/Tor controls, non equivalente |
| Peer-to-peer app distribution verificata | **target Direct build** | store distribution | download channels propri | store/direct secondo progetto | Play/F-Droid/direct download |

Le celle `target` indicano proprietà progettate in Freedom ma non necessariamente production-ready oggi.

## 8. Posizionamento corretto

Claim consigliato:

> **Freedom è un protocollo di comunicazione privata sincrona progettato per non dipendere da una mailbox, da un server centrale o da un singolo percorso di rete.**

Claim da evitare finché non dimostrati:

- "più sicuro di Signal";
- "più anonimo di SimpleX";
- "impossibile da censurare";
- "non tracciabile";
- "funziona sempre";
- "rileva la sorveglianza".

La differenziazione credibile è invece la combinazione:

```text
verifiable RootIdentity / device authorization
+ no global DeviceID in transport
+ pairwise aliases
+ synchronous delivery
+ replaceable routes/relays/transports
+ pairwise recovery control-plane
+ visible network diagnostics
+ community/device relay
+ independent verified app distribution
```

## 9. Benchmark di sviluppo

```text
Signal  -> UX, production reliability, security engineering evidence
SimpleX -> metadata minimization / identifier discipline
Session -> decentralized relay network / onion routing
Briar   -> censorship/offline transport resilience
Freedom -> integrazione coerente delle proprietà target senza perdere semplicità
```

Il successo non consiste nell'avere il maggior numero di feature, ma nel dimostrare end-to-end che queste proprietà restano valide su dispositivi reali, reti ostili e implementazioni indipendenti.
