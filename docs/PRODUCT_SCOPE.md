# Freedom — Product Scope

## 1. Obiettivo

Freedom Messenger non deve competere al lancio sulla quantità di feature. Deve dimostrare in modo affidabile la proposta centrale di Freedom Protocol: **comunicazione privata live, autenticata E2EE, sincrona, senza mailbox centrale e senza dipendenza permanente da un singolo percorso o provider.**

La priorità del prodotto è quindi:

```text
semplicità di onboarding
 -> contatto verificabile
 -> sessione affidabile
 -> messaggi immediati
 -> media
 -> chiamata audio
 -> videochiamata
 -> modalità Live
```

Una feature non deve entrare nel launch scope se aumenta in modo rilevante complessità, superficie d'attacco o instabilità senza rafforzare direttamente questa esperienza.

---

## 2. Launch scope — V1

La prima versione destinata a Founder Cohort, reviewer, creator e successivamente pubblico deve concentrarsi sulle comunicazioni **1:1**.

### 2.1 Funzioni essenziali

- inizializzazione automatica dell'identità Freedom;
- aggiunta contatto tramite QR/link e altri bootstrap espliciti supportati;
- sessione autenticata E2EE senza configurazione manuale di IP, wallet o blockchain;
- chat testuale 1:1;
- foto, video e file;
- messaggi vocali;
- chiamata audio 1:1;
- videochiamata 1:1;
- modalità **Live/effimera**;
- stato comprensibile di peer/sessione: online, sessione attiva, non raggiungibile;
- riconnessione automatica quando esiste un percorso valido;
- route selection coerente con la privacy policy scelta;
- possibilità di evitare il direct path quando la protezione dell'endpoint è prioritaria;
- blocco contatto e funzioni minime richieste dai client ufficiali/store.

### 2.2 Esperienza target

L'obiettivo UX è:

```text
installa Freedom
 -> identità inizializzata automaticamente
 -> scansiona il QR dell'altra persona
 -> peer autenticato
 -> apri chat
 -> messaggio live con latenza normale di rete
 -> chiamata audio/video
```

L'utente non deve essere obbligato a comprendere:

- account NEAR;
- gas;
- RPC;
- NAT;
- relay;
- chiavi crittografiche;
- blockchain adapter.

Questi sono dettagli del protocollo/control plane e devono rimanere invisibili nell'uso normale.

---

## 3. Cosa NON blocca il lancio

Non sono prerequisiti della prima release pubblica:

- gruppi testuali;
- chiamate di gruppo;
- videochiamate di gruppo;
- community pubbliche;
- canali broadcast;
- bot platform;
- feed/social graph;
- mailbox offline;
- sincronizzazione cloud della cronologia.

Freedom deve preferire un core 1:1 estremamente affidabile a una suite ampia ma fragile.

---

## 4. Live Groups — V1.5

I gruppi sono previsti, ma devono preservare la semantica sincrona di Freedom.

Non devono diventare una mailbox condivisa che conserva e recapita automaticamente la cronologia agli assenti.

Esempio:

```text
Freedom Live Group

Alice  online
Bob    online
Carlo  online
David  offline
```

Se Alice invia un messaggio durante la sessione:

```text
Alice -> Bob    delivered
      -> Carlo  delivered
      -> David  not delivered
```

David, tornando online, **non riceve automaticamente i messaggi persi** dal protocollo base.

### 4.1 Scope iniziale gruppi

V1.5 può introdurre:

- piccoli gruppi testuali;
- media nei piccoli gruppi;
- invito tramite QR/link/capability;
- membership autenticata;
- presenza/session state limitata al necessario;
- modalità effimera;
- chiusura della stanza con eliminazione dello stato locale secondo policy.

La dimensione massima iniziale deve essere determinata da test reali di rete, CPU, memoria e battery usage.

---

## 5. Freedom Live Rooms

Il concetto di gruppo consigliato per il prodotto è **Live Room**.

Una Live Room è una sessione privata multi-party che esiste per la comunicazione presente, non una mailbox permanente.

Principio:

> **La stanza serve le persone presenti adesso; non conserva automaticamente la conversazione per chi arriva dopo.**

Possibili proprietà:

```text
Live Room
---------
invito esplicito
membership autenticata
E2EE
history optional/off
Live mode
nessuna mailbox server-side
nessuna consegna offline automatica
room state eliminabile a fine sessione
```

Le policy del client possono consentire cronologia locale persistente quando esplicitamente scelta, ma questo non deve cambiare il comportamento del protocollo base né introdurre storage centrale necessario.

---

## 6. Multi-party voice/video — V2

Voce e video di gruppo non devono essere implementati come semplice mesh P2P non limitata.

Una mesh completa cresce rapidamente con il numero di partecipanti e aumenta banda, CPU, battery usage e complessità di rete.

Per gruppi più grandi Freedom può usare infrastruttura di forwarding media specializzata, ad esempio nodi SFU compatibili con il trust model.

Vincoli:

- il nodo media non deve diventare un trust anchor dell'identità;
- il nodo media non deve possedere plaintext quando è tecnicamente evitabile con il design E2EE scelto;
- devono poter esistere più nodi/operatori;
- il blocco di un singolo nodo non deve rendere il protocollo inutilizzabile;
- il client deve poter cambiare infrastruttura/percorso;
- nessun SFU specifico deve diventare requisito permanente di Freedom Protocol.

La progettazione E2EE multi-party deve essere definita e reviewata separatamente prima dell'implementazione production.

---

## 7. Roadmap prodotto

```text
V1 — Launch
  1:1 text
  1:1 media / file
  voice messages
  audio call
  video call
  Live mode
  QR/link contacts
  automatic identity/bootstrap
  route/privacy policy

V1.5 — Live Groups
  small group text
  group media
  ephemeral Live Rooms
  authenticated membership
  QR/link room invite

V2 — Multi-party realtime
  group voice
  group video
  scalable media forwarding
  replaceable/distributed SFU or equivalent
  multi-party privacy hardening
```

---

## 8. Launch quality gate

Prima del Creator Pilot il V1 deve essere stabile sul percorso principale.

Blocker:

- messaggi con latenza sistematica anomala;
- onboarding che richiede IP o configurazione tecnica manuale;
- crash riproducibili;
- session establishment poco affidabile;
- perdita/corruzione identità;
- chiamate 1:1 non sufficientemente stabili per una demo reale;
- differenza poco chiara tra online, sessione attiva e non raggiungibile;
- privacy claim non ancora implementati;
- dipendenza hardcoded da credenziali personali o singola infrastruttura non sostituibile.

Gruppi e multi-party media **non devono ritardare il lancio** se il V1 1:1 soddisfa questi criteri.

---

## 9. Principio di prodotto

Freedom deve resistere alla tentazione di diventare un messenger generalista prima di aver dimostrato il proprio modello.

Priorità:

> **prima rendere impeccabile una comunicazione privata live tra due persone; poi estendere la stessa semantica a più persone.**
