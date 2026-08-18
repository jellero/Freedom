# Freedom — Adaptive Defense

## 1. Obiettivo

Freedom deve poter distinguere, per quanto tecnicamente possibile, tra:

- peer realmente offline;
- perdita normale della route/NAT mapping;
- failure di un relay/RPC/provider;
- probabile filtraggio, blocco o interferenza del data path.

Il protocollo non deve dichiarare di poter rilevare una sorveglianza passiva: un osservatore può monitorare il traffico senza produrre un segnale osservabile dall'endpoint.

Freedom può invece rilevare **incoerenze di raggiungibilità** e usare il registro/rendezvous come control-plane di emergenza per coordinare un cambio percorso.

Principio:

> **se entrambi i peer dimostrano attività recente sul control-plane ma il data-plane tra loro non funziona, Freedom deve trattare il caso come probabile route failure/interferenza e tentare automaticamente percorsi indipendenti.**

Questa informazione non deve restare nascosta nel motore di rete: il client deve poterla spiegare all'utente in modo comprensibile e tecnicamente onesto.

---

## 2. Control-plane e data-plane

Freedom separa:

```text
CONTROL PLANE
identity / registry / rendezvous / recovery coordination

DATA PLANE
messaggi / file / audio / video / session frames
```

Il registro distribuito non trasporta traffico applicativo e non è nel packet hot path.

In condizioni normali, una sessione attiva usa il data-plane e non produce heartbeat blockchain continui.

Il control-plane torna utile quando tutte le route conosciute falliscono o quando il client deve capire se il peer è ancora recentemente attivo ma separato da un problema di rete.

---

## 3. Recovery / liveness beacon

Freedom non deve pubblicare una presenza globale leggibile come `DeviceID -> online`.

Dopo il primo contatto autenticato, i peer possiedono un `PairRendezvousSecret`. Da questo derivano slot pairwise opachi e rotanti.

Quando il data path fallisce, un endpoint può pubblicare nello slot previsto un beacon cifrato a TTL breve:

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

`state` può rappresentare, ad esempio:

```text
SEEKING_PATH
TRYING_ALTERNATIVE
```

Il beacon:

- è cifrato/autenticato per il peer previsto;
- vive in uno slot opaco derivato dal secret di coppia;
- non espone in chiaro DeviceID, IP, contatto o motivo del recovery quando evitabile;
- ha TTL breve;
- non costituisce una prova assoluta di presenza in tempo reale, ma una prova di **attività recente**;
- non deve essere scritto continuamente durante il normale funzionamento.

---

## 4. Rilevamento di probabile interferenza

Un singolo timeout non è sufficiente per concludere che esiste censura o filtraggio.

Il client deve combinare più segnali indipendenti.

Esempio:

```text
connettività Internet locale          OK
accesso ad almeno un registry/RPC     OK
beacon recente del peer               OK
route diretta                         FAIL
relay/path corrente                   FAIL
percorso alternativo                  OK / disponibile
```

Quando entrambi i peer pubblicano beacon recenti ma non riescono a stabilire il data-plane attraverso il percorso corrente, Freedom può classificare lo stato come:

```text
PEER_RECENTLY_ACTIVE
DATA_PATH_UNAVAILABLE
INTERFERENCE_OR_ROUTE_FAILURE_SUSPECTED
```

Il client non deve mostrare claim come "sei sorvegliato" o "il governo ti sta bloccando".

Messaggio UX corretto:

> **Interferenza o anomalia di rete rilevata. Freedom sta usando un percorso alternativo.**

---

## 5. Adaptive Defense Engine

Macchina a stati concettuale:

```text
NORMAL
  |
  | route failures oltre soglia
  v
VERIFYING_REACHABILITY
  |
  | peer recentemente attivo via control-plane
  | ma data-plane indisponibile
  v
INTERFERENCE_SUSPECTED
  |
  v
ALTERNATIVE_PATH_SEARCH
  |
  +-> altro direct/NAT candidate
  +-> relay differente
  +-> transport differente
  +-> shielded path
  +-> bridge/non-public candidate quando supportato
  |
  v
RECOVERED
  |
  +-> stop recovery writes
  +-> beacon scade naturalmente
```

Il motore deve applicare backoff, limiti e soglie per evitare loop, consumo batteria e chain-write spam.

---

## 6. Coordinamento attraverso il rendezvous

Il recovery slot può essere usato anche per coordinare il cambio route quando il data-plane non è disponibile.

Esempio concettuale:

```text
Alice                           Bob
  |                              |
  |-- RecoveryBeacon_A --------->|  registry
  |<--------- RecoveryBeacon_B --|
  |                              |
  |  entrambi recentemente attivi
  |  current path non funziona
  |                              |
  |==== tentativo route B ======>|
  |<=== authenticated E2EE =====>|
```

Il payload cifrato può includere nuovi candidate o hint di trasporto, ma deve continuare a rispettare metadata minimization e record bounded.

Il registro non deve diventare una chat di controllo ad alta frequenza.

---

## 7. Failure classes

Il motore può distinguere almeno:

### Peer offline probabile

- nessuna sessione;
- nessun beacon recente;
- rendezvous scaduto;
- nessun altro segnale di attività.

Azione: nessuna consegna asincrona; send fallisce/scarta secondo la semantica Freedom.

### Route failure probabile

- peer recentemente attivo;
- current candidate fallisce;
- altri candidate funzionano o diventano disponibili.

Azione: route switch.

### Provider/RPC failure

- un RPC fallisce;
- altri RPC indipendenti rispondono e verificano stato coerente.

Azione: provider rotation.

### Interferenza/filtraggio sospetto

- peer recentemente attivo;
- connettività generale disponibile;
- classi specifiche di route/transport falliscono ripetutamente;
- un percorso indipendente o differente riesce oppure il pattern si ripete oltre soglia.

Azione: transport/path diversity più aggressiva.

---

## 8. Freedom Network Indicator

Lo stato dell'Adaptive Defense Engine deve alimentare un indicatore di rete visibile e cliccabile nel client ufficiale.

Stati UX concettuali:

```text
NORMAL       percorso funzionante
SHIELDED     percorso protetto/shielded attivo
DEGRADED     degradazione o fallback
SUSPECTED    interferenza/filtraggio o route failure selettiva sospetta
UNAVAILABLE  peer recentemente attivo ma nessun percorso valido trovato
```

Quando viene rilevato un evento `SUSPECTED` o `UNAVAILABLE`, il pannello può aprirsi automaticamente una volta per incidente per spiegare:

- fatti osservati;
- inferenza corrente;
- percorso fallito;
- contromisure tentate;
- percorso alternativo eventualmente attivo;
- livello di protezione.

Il colore non deve essere l'unico segnale. Testo e icona devono accompagnarlo.

Il motore deve fornire al layer UI dati sufficienti per distinguere **evidenza** da **inferenza**.

Dettagli UX: [`NETWORK_STATUS_UI.md`](NETWORK_STATUS_UI.md).

---

## 9. Privacy e metadata trade-off

Usare il registro per liveness/recovery crea inevitabilmente un pattern temporale osservabile a livello di chain/provider.

Per questo:

- niente presenza globale continua;
- beacon solo dopo failure o in modalità esplicitamente configurata;
- slot pairwise opachi e rotanti;
- payload cifrati;
- TTL breve;
- frequenza limitata;
- read-before-write;
- nessuna cancellazione necessaria dopo recovery;
- evitare DeviceID/IP in chiaro;
- provider/RPC multipli;
- valutare batching/padding solo se il beneficio privacy giustifica costo e complessità.

Il sistema deve documentare che il control-plane può ridurre l'ambiguità tra offline e percorso bloccato, ma non elimina la traffic analysis.

---

## 10. Gas e costi

I recovery beacon possono richiedere una write on-chain nella prima implementazione NEAR.

Queste write devono essere **eccezionali**, non proporzionali al numero di messaggi.

```text
messaggio normale                 -> 0 chain writes
sessione attiva                   -> 0 heartbeat writes
route valida                      -> 0 recovery writes
perdita completa route            -> recovery beacon possibile
interferenza sospetta             -> recovery coordination possibile
```

Fee relayer indipendenti possono sponsorizzare il gas senza possedere l'identità dell'utente.

Un singolo fee relayer non deve essere necessario per attivare il recovery.

---

## 11. Core, Emergency Shield e Freedom Pro

La capacità minima di rilevare route failure e cambiare provider/percorso è una proprietà di resilienza del protocollo e non deve essere rimossa dal core gratuito.

### Core

- route health checks;
- fallback RPC/provider;
- fallback relay/path;
- recovery rendezvous;
- rilevamento `peer recently active + data path unavailable`;
- cambio route automatico quando esiste un'alternativa compatibile;
- stessa informazione significativa mostrata agli utenti Free e Pro.

### Emergency Shield Free

Quando community/direct/fallback gratuiti non bastano e l'infrastruttura gestita può superare il blocco, il client ufficiale può offrire una quota limitata di capacità Shield gratuita.

La quota può essere contabilizzata internamente per byte, tempo, sessione o capacity token e presentata all'utente in forma semplice.

Il numero definitivo deve essere deciso solo dopo misure reali di costo e abuso.

Freedom non deve lasciare deliberatamente offline un utente Free dopo aver rilevato una probabile interferenza solo per creare un paywall.

### Freedom Pro — Shield

Il piano Pro può monetizzare capacità infrastrutturale e contromisure più costose:

- **Always-Shielded mode** senza direct IP;
- maggiore pool di relay gestiti;
- budget Shield molto superiore;
- multi-hop gestito;
- path diversity più ampia;
- pre-warming di candidate alternativi;
- failover parallelo più rapido;
- transport rotation più aggressiva;
- bridge/non-public relay pool quando disponibile;
- padding/metadata protection opzionale quando implementato;
- policy **Maximum Resilience** che mantiene più percorsi indipendenti pronti prima del failure.

Il piano Pro non compra una cifratura più forte, una classificazione tecnica più favorevole o informazioni diagnostiche fondamentali migliori.

---

## 12. Maximum Resilience

Modalità Pro opzionale:

```text
Maximum Resilience
  direct path optional/off
  multiple relay candidates
  multiple RPC/providers
  alternate transports preselected
  recovery slots ready
  fast failover
```

L'obiettivo è ridurre il tempo necessario a recuperare da blocco o perdita di un singolo percorso.

Non deve essere presentata come garanzia di anonimato o incensurabilità assoluta.

---

## 13. Anti-dark-pattern

Adaptive Defense non deve essere usato come leva di paura commerciale.

Il client non deve:

- elevare artificialmente `DEGRADED` a `SUSPECTED` per vendere Pro;
- cambiare la classificazione tecnica in base al tier;
- nascondere agli utenti Free il fatto che un peer risulta recentemente attivo;
- attribuire sorveglianza o censura a un attore specifico senza evidenza;
- degradare route Free funzionanti;
- mostrare un paywall prima di aver tentato le contromisure Free disponibili durante un incidente critico.

---

## 14. Invarianti

Adaptive Defense deve rispettare queste invarianti:

- E2EE resta endpoint-to-endpoint;
- il registro non trasporta messaggi/media;
- nessuna presenza globale leggibile necessaria;
- nessun heartbeat on-chain continuo nel funzionamento normale;
- nessun singolo RPC, relay, bridge, transport o fee relayer obbligatorio;
- un beacon prova attività recente, non "online" in senso assoluto;
- il sistema può rilevare interferenza/route failure, non una sorveglianza passiva invisibile;
- il recovery smette di scrivere appena una sessione valida viene ristabilita;
- il costo on-chain dipende da eventi di recovery, non dal volume della conversazione;
- stato e spiegazioni fondamentali restano visibili anche nel tier Free;
- l'accesso a Emergency Shield Free non deve essere manipolato per creare falsi incentivi commerciali.
