# Freedom — Network Status UI

## 1. Obiettivo

Freedom non deve nascondere completamente lo stato della rete.

Quando la rete degrada, filtra o interrompe un percorso, il client deve rendere visibile:

- cosa Freedom ha osservato;
- cosa può inferire;
- quale contromisura sta applicando;
- **se lo stato riguarda Freedom Communication oppure Freedom Gateway**.

Principio UX:

> **Semplice quando tutto funziona. Trasparente quando qualcosa cerca di impedirti di comunicare.**

## 2. Freedom Network Indicator

Indicatore sempre accessibile dalla schermata principale/chat.

Stati base:

```text
NORMAL       percorso funzionante
SHIELDED     percorso protetto/shielded attivo
DEGRADED     degradazione o fallback
SUSPECTED    probabile filtraggio/interferenza o failure selettiva
UNAVAILABLE  nessun percorso valido trovato
```

Il colore non è mai l'unico segnale: usare testo/icona/descrizione accessibile.

## 3. Communication e Gateway non sono la stessa cosa

Il pannello deve distinguere due security boundary.

### Freedom Communication

```text
COMMUNICATION
Security      Endpoint-to-endpoint encrypted
Peer          Verified
Session       Active
Route         Shielded relay
Interference  None / Suspected
```

Se la sessione è autenticata E2EE, può essere mostrato chiaramente:

> **End-to-end encrypted by Freedom**

### Freedom Gateway

```text
GATEWAY
Mode          Selected apps / Whole device
Tunnel        Protected
Egress        Active
Route         Shielded / Bridge / Direct egress
Filtering     None / Suspected
Managed quota 82 MB / 100 MB today
```

Il Gateway **non deve** mostrare `End-to-end encrypted by Freedom` per traffico Internet generico.

Copy corretto:

> **Protected path to Freedom egress**

oppure:

> **Shielded network path active**

La sicurezza oltre l'egress dipende anche dal protocollo dell'applicazione finale, ad esempio HTTPS.

Il contatore `100 MB/day` è mostrato solo quando il traffico usa managed Gateway capacity. Private/business egress o policy differenti mostrano la propria quota separatamente.

## 4. Apertura automatica

Normalmente il pannello resta chiuso.

Quando passa per la prima volta a `SUSPECTED` o `UNAVAILABLE`, può aprirsi una volta per incidente.

Esempio Communication:

```text
Freedom Network — Communication

Peer activity        RECENT
Control-plane        REACHABLE / VERIFIED
Current path         FAILED
Alternate path       AVAILABLE
Protection           SHIELDED

Possibile filtraggio o anomalia di rete.
Freedom sta usando un percorso alternativo.
```

Esempio Gateway:

```text
Freedom Network — Gateway

Local Internet       AVAILABLE
Current transport    FILTERED / FAILED
Bridge               ACTIVE
Egress               REACHABLE
Gateway path          RECOVERED
Managed quota         82 / 100 MB today

Il percorso normale è stato degradato.
Freedom Gateway sta usando un transport alternativo.
```

## 5. Evidenza vs inferenza

### Fatti osservabili

- RecoveryBeacon recente;
- RPC A fail / RPC B ok;
- direct path fail;
- relay A fail / relay B ok;
- bridge raggiungibile;
- transport family A fail;
- transport family B ok;
- egress reachability;
- handshake authentication failure;
- route recovery.

### Inferenze

- `INTERFERENCE_OR_ROUTE_FAILURE_SUSPECTED`;
- `PROTOCOL_BLOCK_SUSPECTED`;
- `DPI_OR_FILTERING_SUSPECTED`;
- provider specifico probabilmente indisponibile.

Non dichiarare:

- "sei monitorato";
- "il governo ti sta bloccando";
- "questo firewall non può fermarci";
- attribuzioni a ISP/Stato senza evidenza.

## 6. Vista semplice

Communication:

```text
FREEDOM COMMUNICATION

Status             Connected
Peer               Verified
Encryption         End-to-end
Route              Shielded
Interference       None
```

Gateway:

```text
FREEDOM GATEWAY

Status             Connected
Mode               3 selected apps
Path               Shielded
Egress             CH / managed
Filtering          None
Free managed       82 / 100 MB today
```

Se la quota gestita è quasi esaurita:

```text
Gateway managed capacity
12 MB remaining today
```

La UI non deve trasformare questo stato economico in `DEGRADED`, `SUSPECTED` o `UNAVAILABLE` se tecnicamente la rete funziona.

## 7. Vista tecnica

Campi possibili:

```text
control-plane state
recovery beacon freshness
route generation
candidate class
relay / bridge class
transport family
provider/RPC health
last failure reason
fallback attempts
current protection policy
Gateway egress class
Gateway DNS/leak state
Gateway quota class
Gateway bytes used / remaining
```

Non mostrare secret, private key, session key o identificatori globali non necessari.

## 8. Maximum Reachability UI

Modalità futura Gateway/transport:

```text
Maximum Reachability: ON

Normal path          BLOCKED
Transport A          FAILED
Transport B          FAILED
Private bridge       ACTIVE
Egress               REACHABLE
Parallel fallback    READY
```

Copy consigliato:

> **Freedom ha trovato un percorso alternativo attraverso la rete filtrata.**

Non:

> **Freedom passa qualsiasi firewall.**

## 9. Core Free e anti-paywall

Un utente Free deve:

- vedere lo stesso stato significativo;
- ricevere la stessa spiegazione tecnica;
- beneficiare del recovery/fallback core;
- cambiare route/relay/transport quando esistono alternative gratuite/community;
- ricevere quota Emergency Shield quando prevista;
- quando Gateway managed è disponibile, ricevere il target iniziale di **100 MB/giorno** di capacità egress gestita.

Principio:

> **La censura non deve diventare un paywall.**

La policy commerciale Gateway può limitare capacità egress gestita, ma non deve falsificare classificazioni o indebolire la comunicazione Freedom core.

La quota Gateway e la quota Emergency Shield Communication devono restare separate anche in UI.

## 10. Emergency Shield / Pro

Pro può aumentare:

```text
Always-Shielded
managed relay budget
multi-hop
relay/provider diversity
pre-warmed alternatives
parallel failover
aggressive transport rotation
bridge/non-public pools
Maximum Resilience
Gateway managed quota
egress/provider diversity
Maximum Reachability budget
```

Free e Pro usano gli stessi principi di autenticazione e la stessa interpretazione tecnica degli eventi.

## 11. Anti-dark-pattern

Il client non deve:

- chiamare ogni packet loss `SUSPECTED`;
- elevare severità per vendere Pro;
- nascondere il motivo del fallback;
- usare paura/sorveglianza non dimostrata;
- degradare route Free funzionanti;
- mostrare `E2EE Freedom` su Gateway generico;
- nascondere che un egress Gateway è una trust boundary differente;
- dichiarare universal firewall bypass;
- rappresentare `quota Gateway esaurita` come interferenza/censura;
- bloccare Freedom Communication perché il managed Gateway ha finito i 100 MB del giorno.

## 12. Notification policy

```text
INFO       route cambiata senza impatto
NOTICE     degradazione/fallback
WARNING    filtering/interference suspected
CRITICAL   nessun percorso valido trovato
```

Gli eventi devono essere deduplicati per incidente.

Recovery Communication:

> **Percorso ripristinato. La sessione Freedom è nuovamente attiva.**

Recovery Gateway:

> **Gateway ripristinato tramite un percorso alternativo.**

Quota Gateway quasi esaurita è una notifica di capacità separata, non una network incident notification.

## 13. Main UI

```text
Chat
Call
Video
Live
Network indicator
```

Gateway, quando implementato:

```text
Freedom Gateway
  |- ON / OFF
  |- Selected apps / Whole device
  |- Protection mode
  |- Managed quota remaining
  `- Network details
```

Non serve integrare un browser generalista.

## 14. Invarianti UX

- Network Indicator sempre accessibile;
- Communication e Gateway chiaramente separati;
- E2EE label solo dove tecnicamente corretta;
- fatti e inferenze separati;
- colore mai unico segnale;
- nessun claim di sorveglianza passiva rilevata;
- nessun universal firewall claim;
- diagnostica significativa anche Free;
- Pro aumenta capacità/resilienza, non la verità mostrata;
- egress Gateway visibile come ruolo separato dal relay;
- quota Free managed Gateway mostrata come capacity state, non security state;
- quota Gateway e Emergency Shield Communication contabilizzate e comunicate separatamente.
