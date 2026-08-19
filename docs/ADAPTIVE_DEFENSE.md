# Freedom — Adaptive Defense

## 1. Obiettivo

Freedom deve poter distinguere, per quanto tecnicamente possibile, tra:

- peer realmente offline;
- perdita normale della route/NAT mapping;
- failure di un relay/RPC/provider;
- probabile filtraggio, blocco o interferenza del data path.

Il protocollo non deve dichiarare di poter rilevare una sorveglianza passiva: un osservatore può monitorare il traffico senza produrre un segnale osservabile dall'endpoint.

Freedom può invece rilevare **incoerenze di raggiungibilità** e usare il registro/rendezvous come control-plane di emergenza per coordinare un cambio percorso.

> **se entrambi i peer dimostrano attività recente sul control-plane ma il data-plane tra loro non funziona, Freedom deve trattare il caso come probabile route failure/interferenza e tentare automaticamente percorsi indipendenti.**

Questa informazione non deve restare nascosta nel motore di rete: il client deve poterla spiegare all'utente in modo comprensibile e tecnicamente onesto.

## 2. Control-plane e data-plane

```text
CONTROL PLANE
Root/device authorization / pairwise rendezvous / recovery coordination

DATA PLANE
messaggi / file / audio / video / session frames
```

Il registro distribuito non trasporta traffico applicativo e non è nel packet hot path.

In condizioni normali, una sessione attiva usa il data-plane e non produce heartbeat blockchain continui.

## 3. Recovery / liveness beacon

Freedom non deve pubblicare una presenza globale leggibile del tipo `global identity -> online`.

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

`state` può rappresentare:

```text
SEEKING_PATH
TRYING_ALTERNATIVE
```

Il beacon:

- è cifrato/autenticato per il peer previsto;
- vive in uno slot opaco derivato dal secret di coppia;
- non espone in chiaro RootIdentity, DeviceRecordCommitment, pairwise alias, IP o motivo del recovery quando evitabile;
- ha TTL breve;
- prova **attività recente**, non presenza assoluta in tempo reale;
- non viene scritto continuamente durante il normale funzionamento.

Dettagli identità: [`IDENTITY_MODEL.md`](IDENTITY_MODEL.md).

## 4. Rilevamento di probabile interferenza

Un singolo timeout non è sufficiente per concludere che esiste censura o filtraggio.

```text
connettività Internet locale          OK
accesso ad almeno un registry/RPC     OK
beacon recente del peer               OK
route diretta                         FAIL
relay/path corrente                   FAIL
percorso alternativo                  OK / disponibile
```

Quando entrambi i peer pubblicano beacon recenti ma non riescono a stabilire il data-plane attraverso il percorso corrente, Freedom può classificare:

```text
PEER_RECENTLY_ACTIVE
DATA_PATH_UNAVAILABLE
INTERFERENCE_OR_ROUTE_FAILURE_SUSPECTED
```

Il client non deve mostrare claim come "sei sorvegliato" o attribuzioni a un attore specifico senza evidenza.

Messaggio UX corretto:

> **Interferenza o anomalia di rete rilevata. Freedom sta usando un percorso alternativo.**

## 5. Adaptive Defense Engine

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

## 6. Coordinamento attraverso il rendezvous

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

Il payload cifrato può includere nuovi candidate o hint di trasporto, ma deve rispettare metadata minimization e record bounded.

Il registro non deve diventare una chat di controllo ad alta frequenza.

## 7. Failure classes

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
- un percorso indipendente riesce oppure il pattern si ripete oltre soglia.

Azione: transport/path diversity più aggressiva.

## 8. Freedom Network Indicator

```text
NORMAL       percorso funzionante
SHIELDED     percorso protetto/shielded attivo
DEGRADED     degradazione o fallback
SUSPECTED    interferenza/filtraggio o route failure selettiva sospetta
UNAVAILABLE  peer recentemente attivo ma nessun percorso valido trovato
```

Quando viene rilevato `SUSPECTED` o `UNAVAILABLE`, il pannello può aprirsi automaticamente una volta per incidente e spiegare:

- fatti osservati;
- inferenza corrente;
- percorso fallito;
- contromisure tentate;
- percorso alternativo eventualmente attivo;
- livello di protezione.

Il colore non deve essere l'unico segnale. Testo e icona devono accompagnarlo.

Dettagli UX: [`NETWORK_STATUS_UI.md`](NETWORK_STATUS_UI.md).

## 9. Privacy e metadata trade-off

Usare il registro per liveness/recovery crea inevitabilmente un pattern temporale osservabile a livello chain/provider.

Per questo:

- niente presenza globale continua;
- beacon solo dopo failure o modalità esplicita;
- slot pairwise opachi e rotanti;
- payload cifrati;
- TTL breve;
- frequenza limitata;
- read-before-write;
- nessuna cancellazione necessaria dopo recovery;
- evitare RootIdentity, DeviceRecordCommitment, pairwise alias e IP in chiaro;
- provider/RPC multipli;
- valutare batching/padding solo se il beneficio giustifica costo/complessità.

Il control-plane può ridurre l'ambiguità tra offline e percorso bloccato, ma non elimina traffic analysis.

## 10. Gas e costi

```text
messaggio normale                 -> 0 chain writes
sessione attiva                   -> 0 heartbeat writes
route valida                      -> 0 recovery writes
perdita completa route            -> recovery beacon possibile
interferenza sospetta             -> recovery coordination possibile
```

Fee relayer indipendenti possono sponsorizzare il gas senza possedere l'identità dell'utente. Un singolo fee relayer non deve essere necessario per attivare il recovery.

## 11. Core, Emergency Shield e Freedom Pro

La capacità minima di rilevare route failure e cambiare provider/percorso è una proprietà di resilienza del protocollo e non deve essere rimossa dal core gratuito.

### Core

- route health checks;
- fallback RPC/provider;
- fallback relay/path;
- pairwise recovery rendezvous;
- rilevamento `peer recently active + data path unavailable`;
- cambio route automatico quando esiste alternativa compatibile;
- stessa informazione significativa mostrata agli utenti Free e Pro.

### Emergency Shield Free

Quando community/direct/fallback gratuiti non bastano e l'infrastruttura gestita può superare il blocco, il client ufficiale può offrire una quota limitata di capacità Shield gratuita.

Il numero definitivo deve essere deciso solo dopo misure reali di costo e abuso.

Freedom non deve lasciare deliberatamente offline un utente Free dopo aver rilevato una probabile interferenza solo per creare un paywall.

### Freedom Pro — Shield

Il piano Pro può monetizzare capacità infrastrutturale e contromisure più costose:

- **Always-Shielded mode** senza direct IP;
- maggiore pool di relay gestiti;
- budget Shield molto superiore;
- multi-hop gestito;
- path diversity più ampia;
- pre-warming candidate;
- failover parallelo più rapido;
- transport rotation più aggressiva;
- bridge/non-public relay pool quando disponibile;
- padding/metadata protection opzionale;
- **Maximum Resilience** con percorsi indipendenti pronti prima del failure.

Il piano Pro non compra una cifratura più forte, una classificazione tecnica più favorevole o informazioni diagnostiche fondamentali migliori.

## 12. Maximum Resilience

```text
Maximum Resilience
  direct path optional/off
  multiple relay candidates
  multiple RPC/providers
  alternate transports preselected
  recovery slots ready
  fast failover
```

L'obiettivo è ridurre il tempo necessario a recuperare da blocco o perdita di un singolo percorso. Non è garanzia di anonimato o incensurabilità assoluta.

## 13. Anti-dark-pattern

Adaptive Defense non deve essere usato come leva di paura commerciale.

Il client non deve:

- elevare artificialmente `DEGRADED` a `SUSPECTED` per vendere Pro;
- cambiare classificazione tecnica in base al tier;
- nascondere agli utenti Free il fatto che un peer risulta recentemente attivo;
- attribuire sorveglianza/censura a un attore specifico senza evidenza;
- degradare route Free funzionanti;
- mostrare un paywall prima delle contromisure Free disponibili durante un incidente critico.

## 14. Invarianti

- E2EE resta endpoint-to-endpoint;
- il registro non trasporta messaggi/media;
- nessuna presenza globale leggibile necessaria;
- nessun global DeviceID necessario;
- rendezvous e recovery sono pairwise;
- nessun heartbeat on-chain continuo nel funzionamento normale;
- nessun singolo RPC, relay, bridge, transport o fee relayer obbligatorio;
- un beacon prova attività recente, non "online" in senso assoluto;
- il sistema può rilevare interferenza/route failure, non sorveglianza passiva invisibile;
- il recovery smette di scrivere appena una sessione valida viene ristabilita;
- il costo on-chain dipende da eventi di recovery, non dal volume della conversazione;
- stato e spiegazioni fondamentali restano visibili anche nel tier Free.
