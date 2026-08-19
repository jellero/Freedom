# Freedom — Gateway & Censorship-Resilient Transport

Status: **canonical design draft / post-V1 capability**

![Freedom Gateway architecture](assets/freedom-gateway.svg)

## 1. Due proprietà diverse

Freedom deve distinguere chiaramente due livelli di sicurezza.

### Freedom Communication

È il core del protocollo:

```text
Alice Freedom endpoint
        |
        | authenticated E2EE live session
        |
Bob Freedom endpoint
```

Proprietà target:

- autenticazione endpoint-to-endpoint;
- chiavi di sessione possedute solo dagli endpoint;
- nessuna mailbox/offline queue nel protocollo base;
- relay non fidati e forward-only;
- path sostituibili;
- identity/routing separati;
- metadata minimization pairwise;
- Adaptive Defense e recovery distribuito.

Questa è la superficie con le garanzie di comunicazione più forti di Freedom.

### Freedom Gateway

È una capacità di rete opzionale che permette ad applicazioni non-Freedom di usare il fabric Freedom come percorso verso Internet:

```text
Browser / app / device traffic
          |
          v
   Freedom Gateway client
          |
     encrypted tunnel
          |
 route / relay / shielded path
          |
          v
 explicit Freedom Egress
          |
          v
       Internet
```

Il Gateway protegge e rende più resiliente **il percorso di rete**. Non trasforma automaticamente il protocollo dell'applicazione in Freedom E2EE.

Esempio: una sessione HTTPS conserva la propria cifratura HTTPS oltre l'egress; traffico applicativo plaintext potrebbe essere leggibile dall'egress o dalla destinazione.

Principio:

> **Freedom Communication protegge la conversazione endpoint-to-endpoint. Freedom Gateway protegge e diversifica il percorso di rete del dispositivo.**

---

## 2. Perché non integrare un browser completo

Freedom non deve diventare un browser generalista per ottenere questa funzione.

Su Android il Gateway può usare `VpnService` come API di sistema per instradare:

```text
SELECTED_APPS
oppure
WHOLE_DEVICE
```

Il prodotto e la UI si chiamano **Freedom Gateway**, non "VPN Freedom". `VpnService` è soltanto la primitive Android necessaria a creare il tunnel locale.

L'utente continua quindi a usare Chrome, Firefox o le proprie app. Freedom controlla il percorso senza assumersi la superficie di sicurezza e manutenzione di un motore browser/WebView generalista.

Una UI browser embedded può essere usata solo per flussi controllati specifici quando necessario; non è una proprietà core.

---

## 3. Gateway modes

```text
OFF

SELECTED_APPS
  solo applicazioni scelte dall'utente

WHOLE_DEVICE
  tutto il traffico instradabile tramite il Gateway locale
```

Requisiti UX:

- consenso esplicito;
- stato persistente visibile;
- applicazioni protette elencabili;
- route/egress corrente visibile in advanced/network status;
- stop immediato;
- policy kill-switch opzionale;
- split tunneling esplicito;
- DNS instradato coerentemente per evitare leak quando la policy lo richiede;
- quota managed mostrata separatamente dal traffico Freedom Communication.

Su piattaforme che consentono un solo servizio di tunnel device-level attivo, Freedom deve spiegare il conflitto con altri servizi equivalenti.

---

## 4. Relay e Internet egress sono ruoli differenti

Un normale relay Freedom non è un exit proxy Internet.

```text
DEVICE_RELAY / COMMUNITY_RELAY
  Freedom circuit -> Freedom circuit
  NO arbitrary Internet egress
```

Il Gateway usa esclusivamente nodi con ruolo esplicito:

```text
MANAGED_EGRESS
PRIVATE_EGRESS
BUSINESS_EGRESS
future authorized EGRESS class
```

Flusso:

```text
Phone
  -> Relay A
  -> Relay B optional
  -> Egress C
  -> Internet
```

Un Relay Contributor che offre il proprio telefono non deve diventare inconsapevolmente un open proxy o un exit node Internet.

---

## 5. Cosa rende il Gateway diverso da una VPN classica

Una VPN classica, semplificando, usa tipicamente:

```text
client -> protocollo VPN -> server VPN -> Internet
```

Freedom Gateway è progettato come una rete adattiva:

```text
client
  |
  +-> transport A -> egress
  +-> transport B -> relay -> egress
  +-> bridge non pubblico -> egress
  +-> shielded/multi-hop -> egress
  +-> future pluggable transport -> egress
```

Il valore target non è "cifratura più forte di ogni VPN". Il valore è:

- nessun singolo IP/protocollo/relay/egress permanente;
- path e provider diversity;
- transport diversity;
- relay community/device come ingress/intermediate hop, non exit aperti;
- route health + Adaptive Defense;
- failover automatico;
- transport/bridge aggiornabili senza cambiare l'identità Freedom;
- possibile multi-hop per separare origine ed egress.

Una VPN moderna può già offrire multi-hop, split tunneling o server multipli. Freedom deve quindi essere valutato sulla **combinazione tra fabric adattivo, anti-censura, device/community relay, control-plane e comunicazione Freedom**, non sul semplice fatto di creare un tunnel.

---

## 6. Firewall e censorship resistance

Requisito di progetto:

> **Freedom non deve avere un singolo fingerprint di rete, protocollo, IP, dominio, relay, provider o transport la cui interdizione blocchi l'intero sistema.**

Non è tecnicamente corretto promettere che Freedom attraverserà *ogni* firewall.

Un firewall/avversario può, per esempio:

- consentire solo una allowlist stretta di destinazioni;
- bloccare tutto il traffico cifrato sconosciuto;
- applicare DPI e active probing;
- bloccare tutti gli IP/bridge scoperti;
- interrompere TCP, UDP o Internet interamente;
- richiedere un proxy/autenticazione enterprise non controllata dal client.

In questi casi nessun sistema IP può garantire universalmente il passaggio.

Obiettivo corretto:

> **Quando esiste almeno un carrier di rete ancora utilizzabile, Freedom deve poter provare automaticamente transport e path indipendenti progettati per confondersi con traffico consentito o evitare le primitive bloccate.**

---

## 7. Pluggable Transport Layer

I transport devono essere adapter sostituibili, non logica hardcoded nel protocollo applicativo.

```text
TransportAdapter
  connect(candidate, policy)
  probe_capabilities()
  health()
  classify_failure()
  close()
```

Classi possibili/benchmark, da implementare solo dopo review specifica:

```text
NATIVE_TCP_TLS
UDP / QUIC-like carrier
HTTPS / WebSocket-like carrier
HTTP/2 or HTTP/3 compatible tunnel
bridge transport
obfuscated transport
active-probing-resistant transport
WebTunnel-like transport
Snowflake-like ephemeral proxy transport
future transports
```

Freedom dovrebbe poter integrare transport anti-censura esistenti e reviewati quando compatibili invece di inventare primitive proprietarie senza necessità.

Transport mimicry/obfuscation è una proprietà di reachability, non una prova di anonimato.

---

## 8. Bridge discovery e anti-enumeration

I nodi anti-censura più sensibili non devono necessariamente comparire tutti in una directory pubblica enumerabile.

Possibili fonti:

- capability pairwise;
- bootstrap multipli;
- bridge descriptor firmati e temporanei;
- distribuzione out-of-band;
- peer già autenticati;
- pool regionali/rotanti;
- control-plane con record opachi;
- sorgenti future compatibili.

Requisiti:

- TTL;
- rotazione;
- nessuna singola directory obbligatoria;
- resistenza a scraping/enumerazione da progettare separatamente;
- rate limit;
- bridge compromise non deve compromettere identity/E2EE.

---

## 9. Adaptive transport selection

Il Network/Adaptive Defense Engine deve classificare separatamente:

```text
PATH_FAILURE
PROTOCOL_BLOCK_SUSPECTED
DPI_OR_FILTERING_SUSPECTED
BRIDGE_UNREACHABLE
EGRESS_UNREACHABLE
LOCAL_NETWORK_RESTRICTED
CONTROL_PLANE_DEGRADED
```

Il client può quindi cambiare automaticamente:

```text
same transport / different endpoint
 -> different provider
 -> different relay
 -> different egress
 -> different transport family
 -> non-public bridge
 -> shielded/multi-hop
```

Non deve dichiarare chi sta censurando senza evidenza.

---

## 10. Maximum Reachability

Modalità avanzata opzionale:

```text
MAXIMUM_REACHABILITY
  maintain multiple transport strategies
  keep bounded alternate candidates warm
  rotate independent providers
  prefer non-public bridges after filtering evidence
  parallel-connect where resource policy allows
  aggressive failover
  bounded probing/backoff
```

Questa modalità può consumare più batteria, banda e capacità relay e deve essere configurabile.

Il nome non implica una garanzia matematica di raggiungibilità.

---

## 11. Security boundary dell'egress

Il Gateway cambia il trust model rispetto a Freedom Communication.

Un egress può osservare almeno:

- connessione di uscita;
- destinazione IP;
- timing e volume;
- DNS se risolto dall'egress;
- contenuto applicativo se il protocollo finale non è cifrato end-to-end dall'app.

Con multi-hop:

```text
Client -> Relay A -> Egress B -> Internet
```

obiettivo:

```text
Relay A conosce origine adiacente, non destinazione Internet finale
Egress B conosce destinazione, non direttamente l'IP originale del client
```

Non promettere anonimato contro osservatore globale o collusione completa dei nodi.

Per comunicazioni Freedom-to-Freedom, l'egress Internet non è necessario: resta preferibile il data-plane Freedom E2EE endpoint-to-endpoint.

---

## 12. Abuse, legalità operativa e resource control

Gli egress devono essere nodi esplicitamente amministrati e protetti da policy anti-abuso.

Possibili controlli:

- authenticated capability;
- rate/bandwidth quotas;
- connessioni simultanee bounded;
- abuse controls compatibili col servizio;
- blocco di porte/protocolli ad alto rischio quando necessario;
- logging minimizzato secondo threat model e obblighi applicabili;
- revoca di egress compromessi;
- provider/geographic diversity.

Questi controlli non devono essere applicati ai payload Freedom E2EE come meccanismo di moderazione della conversazione.

---

## 13. Managed Gateway capacity e monetizzazione

La capacità Gateway gestita ha un costo reale di egress, relay, bandwidth, IP reputation, abuse handling e geografia. È quindi una superficie legittima da monetizzare senza toccare la sicurezza crittografica di Freedom Communication.

### Free — target iniziale

Policy di prodotto iniziale da validare con misure reali:

```text
FREEDOM GATEWAY FREE
managed capacity target: 100 MB / day
reset: daily
carry-over: no, salvo futura policy esplicita
priority: standard
```

I **100 MB/giorno** sono un target iniziale, non un parametro immutabile del protocollo. Devono essere ricalibrati dopo misure reali di:

- costo egress per regione/provider;
- mix browsing/app traffic;
- abuso e automazione;
- overhead dei transport anti-censura;
- multi-hop/Shield;
- capacità infrastrutturale.

La quota riguarda esclusivamente **managed Gateway capacity**.

Non consuma automaticamente la quota Gateway:

```text
Freedom Communication direct
Freedom Communication su community/device relay
private relay / private egress dell'utente, secondo policy
```

Emergency Shield per Freedom Communication resta contabilizzato separatamente: il limite Gateway non deve diventare un modo per bloccare la comunicazione core durante un incidente.

### Plus / Shield

I tier premium possono offrire:

- quota Gateway managed molto superiore;
- maggiore egress/provider diversity;
- priorità/capacità superiore;
- multi-hop Gateway;
- bridge/non-public pools;
- transport rotation più aggressiva;
- candidate pre-warmed;
- `MAXIMUM_REACHABILITY` con resource budget più alto.

I numeri premium definitivi devono derivare dai costi reali e non sono fissati nella specifica iniziale.

### Business / Private Gateway

Business può offrire:

```text
PRIVATE_EGRESS
BUSINESS_EGRESS
region/policy dedicated pools
custom quotas
SLA
private deployment
```

Un egress privato gestito dall'organizzazione può avere una policy economica indipendente dai 100 MB/giorno del managed Gateway Free.

Principio commerciale:

> **Freedom Communication non diventa più sicuro pagando. Freedom Gateway monetizza capacità di rete gestita e resilienza aggiuntiva.**

---

## 14. Store/platform separation

Su Android, `VpnService` è il meccanismo previsto per un tunnel device-level, ma la build distribuita tramite store deve rispettare le policy vigenti su dichiarazione, consenso e uso della funzionalità.

Freedom mantiene quindi la separazione:

```text
Freedom Protocol
Freedom Communication
Freedom Gateway
platform/store client policy
```

Una restrizione dello store può cambiare la build ufficiale senza eliminare il protocollo o la Direct build compatibile con la piattaforma.

---

## 15. Benchmark esterni

Freedom Gateway non nasce in un vuoto tecnico.

Benchmark da studiare:

- **Tor** — bridges e pluggable transports come obfs4, WebTunnel, Snowflake;
- **Psiphon** — circumvention adattiva, protocol diversity e Conduit come relay volontario non-egress;
- **Tailscale** — device exit nodes e routing di traffico non-overlay;
- **Proton VPN Secure Core** — multi-hop VPN gestito.

La differenziazione Freedom deve essere formulata come integrazione coerente di:

```text
Freedom Communication E2EE/live
+ pairwise identity/recovery
+ replaceable relay fabric
+ community/device relay
+ Adaptive Defense
+ censorship-oriented pluggable transports
+ optional device Gateway
```

non come claim che nessuno abbia mai costruito singole parti equivalenti.

---

## 16. Roadmap

Gateway non deve ritardare Freedom Communication V1.

Sequenza consigliata:

```text
G0  reusable path/transport abstraction
G1  explicit managed/private egress
G2  Android selected-app Gateway prototype
G3  whole-device mode + DNS/leak controls
G4  managed quota accounting + 100 MB/day Free target
G5  egress diversity / health / failover
G6  shielded multi-hop Gateway
G7  pluggable anti-censorship transport interface
G8  bridge distribution / anti-enumeration
G9  active filtering tests / DPI lab
G10 Maximum Reachability policy
G11 independent security/censorship review
```

## 17. Invarianti

- Freedom Communication e Freedom Gateway hanno trust model differenti;
- il Gateway non riduce la sicurezza E2EE del core;
- `DEVICE_RELAY` non è automaticamente Internet egress;
- nessun singolo transport è obbligatorio;
- nessun singolo egress/provider è obbligatorio;
- transport anti-censura sono sostituibili;
- il client non promette universal firewall bypass;
- il sistema deve reagire automaticamente a failure selettive quando esistono alternative;
- l'assenza totale di connettività resta fuori dal potere di un protocollo IP;
- metadata e limiti del Gateway devono essere descritti separatamente dalle garanzie della comunicazione Freedom;
- il target Free `100 MB/day` riguarda managed Gateway capacity, non il volume di Freedom Communication;
- quote/prezzi Gateway possono cambiare senza cambiare il wire protocol.