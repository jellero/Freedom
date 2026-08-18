# Freedom — Network Status UI

## 1. Obiettivo

Freedom Messenger non deve nascondere completamente lo stato della rete come un messenger generalista.

Il prodotto deve restare semplice nell'uso normale, ma quando la rete degrada, filtra o interrompe un percorso deve rendere visibile all'utente **cosa Freedom ha osservato, cosa può inferire e quale contromisura sta applicando**.

Principio UX:

> **Semplice quando tutto funziona. Trasparente quando qualcosa cerca di impedirti di comunicare.**

Freedom è pensato anche per utenti che hanno bisogno di capire se la propria capacità di comunicare viene limitata. La trasparenza sullo stato di rete è quindi una feature del prodotto, non una schermata diagnostica nascosta.

---

## 2. Freedom Network Indicator

Il client ufficiale deve mostrare un piccolo indicatore di stato di rete sempre accessibile dalla schermata principale/chat.

L'indicatore è cliccabile e apre il pannello **Freedom Network**.

Stati concettuali:

```text
NORMAL       percorso funzionante, nessuna anomalia significativa rilevata
SHIELDED     traffico instradato attraverso un percorso protetto/shielded
DEGRADED     degradazione, instabilità o fallback in corso
SUSPECTED    probabile filtraggio/interferenza o route failure selettiva
UNAVAILABLE  peer recentemente attivo ma nessun percorso valido disponibile
```

Una possibile rappresentazione visiva usa un pallino multicolore, ma **il colore non deve essere l'unico segnale**. Stato testuale, icona e descrizione devono rendere il significato accessibile anche a utenti con daltonismo o display non affidabili.

Esempio semantico:

```text
● NORMAL      Network OK
● SHIELDED    Shielded route active
● DEGRADED    Network degraded
● SUSPECTED   Interference suspected
● UNAVAILABLE No working route
```

Colori definitivi, contrasto e accessibilità sono una decisione di design system/client, non del wire protocol.

---

## 3. Apertura automatica in caso di problema

Normalmente il pannello resta chiuso e l'indicatore è discreto.

Quando Freedom passa per la prima volta a uno stato importante come `SUSPECTED` o `UNAVAILABLE`, il pannello può aprirsi automaticamente per spiegare l'evento.

Non deve aprirsi ripetutamente durante lo stesso incidente di rete.

Esempio:

```text
Freedom Network

Peer activity        RECENT
Registry             REACHABLE / VERIFIED
Current path         FAILED
Alternate path       AVAILABLE
Protection           SHIELDED

Possibile filtraggio, interferenza o anomalia di rete rilevata.
Freedom ha attivato un percorso alternativo.
```

L'utente deve poter chiudere il pannello e continuare a usare l'app.

---

## 4. Evidenza, inferenza e linguaggio

Freedom deve distinguere chiaramente tra fatti osservati e inferenze.

### Fatti osservabili

Esempi:

- peer con `RecoveryBeacon` recente;
- RPC A non raggiungibile;
- RPC B raggiungibile;
- direct path fallito;
- relay A fallito;
- relay B riuscito;
- handshake alterato/non autenticabile;
- route ripristinata tramite transport alternativo.

### Inferenze

Esempi:

- `INTERFERENCE_OR_ROUTE_FAILURE_SUSPECTED`;
- filtraggio selettivo probabile;
- provider specifico probabilmente indisponibile.

Il client **non deve dichiarare**:

- "sei monitorato";
- "il governo ti sta bloccando";
- "la tua rete è sotto sorveglianza";
- attribuzioni a ISP, Stato, azienda o altro attore senza evidenza sufficiente.

Un osservatore passivo può monitorare senza lasciare segnali rilevabili. Freedom può rilevare anomalie/interferenza del percorso, non provare ogni forma di sorveglianza.

---

## 5. Pannello Freedom Network

Il pannello deve avere due livelli.

### 5.1 Vista semplice

Mostra solo ciò che serve per capire la situazione:

```text
FREEDOM NETWORK

Status             Interference suspected
Peer               Recently active
Current route       Failed
Fallback            Active
Protection          Shielded
```

Azioni possibili:

```text
View details
Retry paths
Enable Shield
Network settings
```

Le azioni disponibili dipendono dalla policy e dallo stato reale del client.

### 5.2 Vista tecnica

Per gli utenti che la vogliono:

```text
registry state
recovery beacon freshness
route generation
candidate class
relay class
transport class
provider/RPC health
last failure reason
fallback attempts
current protection policy
```

Non mostrare per default secret, private key, session key o identificatori che aumentano il rischio di leakage.

---

## 6. Core Free: informazione e bypass non sono un paywall

Un utente Free deve:

- vedere lo stesso stato di rete significativo;
- ricevere la stessa spiegazione del problema;
- beneficiare del rilevamento `peer recently active + data path unavailable`;
- usare fallback RPC/provider;
- cambiare route/relay/transport quando esistono alternative gratuite/community;
- ricevere una quantità limitata di capacità Shield gestita di emergenza quando l'infrastruttura commerciale è necessaria per superare il blocco.

Principio:

> **Freedom non deve rilevare che un utente è probabilmente censurato e poi lasciarlo intenzionalmente offline per vendergli Pro.**

Non deve comparire un paywall aggressivo nel momento critico prima che il client abbia tentato le contromisure Free disponibili.

---

## 7. Emergency Shield Budget

La capacità Shield gratuita può essere limitata perché relay, multi-hop e media hanno costi reali.

Il limite definitivo deve essere deciso dopo test di costo e abuso.

Possibili unità interne:

- byte/giorno su managed relay;
- minuti audio/video shielded;
- sessioni di emergenza;
- token/capability di capacità;
- combinazione pesata per tipo di traffico.

Il client può tradurre questa quota in una UX semplice, ad esempio **messaggi/sessioni di emergenza disponibili**, senza fingere che cento messaggi di testo abbiano lo stesso costo di cento video.

Non fissare un numero permanente prima di misurare costi reali di relay, bandwidth e abuse resistance.

Quando il budget Free è quasi esaurito, il client può informare l'utente in modo neutro. L'upgrade Pro deve essere una scelta, non una condizione per capire cosa sta succedendo.

---

## 8. Freedom Pro / Shield

Pro estende la capacità e l'automazione, non la verità mostrata all'utente.

Possibili benefici:

```text
Always-Shielded
larger managed relay budget
multi-hop
wider relay/provider diversity
pre-warmed alternate paths
parallel failover
aggressive transport rotation
bridge/non-public pools
Maximum Resilience
```

Free e Pro devono usare gli stessi principi di autenticazione E2EE e la stessa classificazione onesta degli eventi di rete.

---

## 9. Anti-dark-pattern

Il client ufficiale non deve:

- chiamare `SUSPECTED` ogni normale perdita di pacchetto per spingere Pro;
- aumentare artificialmente la severità di un evento quando il budget Free finisce;
- nascondere il motivo di un fallback agli utenti Free;
- usare paura o claim non verificati per vendere Shield;
- degradare deliberatamente route Free funzionanti;
- bloccare recovery protocollare di base perché l'utente non paga.

Il livello commerciale può determinare **quanta infrastruttura gestita** viene consumata, non alterare l'interpretazione tecnica dei segnali.

---

## 10. Notification policy

Gli eventi devono essere classificati per severità.

```text
INFO       route cambiata senza impatto significativo
NOTICE     degradazione o fallback
WARNING    interferenza/filtraggio sospetto
CRITICAL   peer recentemente attivo ma nessun percorso valido trovato
```

Le notifiche devono evitare spam.

Per un incidente persistente il client aggiorna lo stesso stato invece di produrre popup continui.

Quando la rete viene recuperata:

> **Percorso ripristinato. Freedom sta comunicando tramite una route alternativa.**

Il pannello conserva eventualmente solo uno stato diagnostico locale limitato secondo la privacy policy; non deve creare una cronologia centrale degli eventi di censura associata all'identità dell'utente.

---

## 11. Posizionamento prodotto

Freedom non cerca di essere invisibile nella propria architettura per sembrare un messenger tradizionale.

L'interfaccia principale rimane essenziale:

```text
Chat
Call
Video
Live
Network indicator
```

La complessità tecnica appare solo quando è utile oppure quando l'utente la richiede.

Questo permette a Freedom di essere semplice nell'uso quotidiano senza rinunciare alla propria natura di strumento per persone che attribuiscono valore a resilienza, privacy di rete e trasparenza.

---

## 12. Invarianti UX

- indicatore di rete sempre accessibile;
- apertura automatica solo per eventi rilevanti e senza spam;
- colore mai unico vettore informativo;
- fatti e inferenze separati;
- nessun claim di sorveglianza passiva rilevata;
- stato significativo visibile anche agli utenti Free;
- bypass/recovery base disponibile anche Free;
- una quota di emergenza Shield Free può usare infrastruttura gestita;
- Pro aumenta capacità/resilienza, non compra una classificazione più onesta;
- nessun dark pattern di paura durante un incidente di rete.
