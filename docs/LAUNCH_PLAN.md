# Freedom — Launch Plan

## 1. Obiettivo

Il lancio di Freedom non deve ottimizzare subito per il numero assoluto di download. Il primo obiettivo è creare un nucleo di utenti che comprenda la proposta del protocollo, riesca a completare una sessione reale con un altro peer e produca feedback tecnico e di prodotto utile.

Obiettivo iniziale:

> **dimostrare che Freedom è comprensibile, utilizzabile e tecnicamente credibile prima di amplificare la distribuzione.**

Freedom non deve essere promosso come "un altro messenger privato" o come "un messenger blockchain".

Posizionamento consigliato:

> **Freedom Messenger — Powered by Freedom Protocol**
>
> Comunicazione privata live, E2EE, sincrona, senza mailbox centrale e senza un singolo server necessario alla conversazione.

Claim come "impossibile da tracciare", "incensurabile" o "anonimato garantito" non devono essere usati. Il marketing deve descrivere proprietà tecniche verificabili e limiti reali.

---

## 2. Principi del lancio

1. **Credibilità prima della scala.** Prima utenti tecnici e reviewer, poi creator, poi pubblico più ampio.
2. **Demo reale, non slideshow.** Un creator deve poter installare Freedom su due dispositivi e completare una comunicazione reale.
3. **Editorial independence.** Accesso Pro, inviti o altri benefit non comprano una recensione positiva.
4. **Trasparenza.** Gift, sponsorship o altre relazioni devono essere dichiarate secondo le regole applicabili della piattaforma e del paese.
5. **Privacy coerente con il prodotto.** Le metriche di lancio non devono richiedere raccolta di contenuti, social graph o metadati di conversazione non necessari.
6. **Nessun lock-in commerciale.** Il protocollo base deve restare interoperabile e utilizzabile anche senza i servizi commerciali ufficiali.
7. **Niente overclaim di sicurezza.** Ogni claim pubblico deve corrispondere a una proprietà effettivamente implementata e testata.

---

## 3. Prerequisiti prima della promozione pubblica

Una campagna creator non deve iniziare finché il client non supera almeno questi controlli.

### 3.1 Flusso minimo funzionante

Il tester deve poter:

```text
installare Freedom
    -> inizializzare identità
    -> aggiungere un contatto
    -> stabilire una sessione autenticata
    -> inviare/ricevere messaggi live
    -> interrompere e ristabilire la sessione
```

### 3.2 Blocchi di lancio

Sono blocker prima di coinvolgere creator pubblici:

- crash riproducibili nel percorso principale;
- onboarding che richiede configurazioni manuali non destinate al prodotto finale;
- perdita o corruzione dell'identità locale;
- errori di autenticazione non spiegabili all'utente;
- tempi anomali di invio/ricezione;
- dipendenze hardcoded da credenziali personali;
- claim privacy non ancora implementati;
- impossibilità di distinguere chiaramente stato online, sessione attiva e peer non raggiungibile.

In particolare, una latenza artificiale o anomala di diversi secondi per ogni messaggio deve essere risolta prima di una demo pubblica.

### 3.3 Security readiness

Prima della promozione ampia:

- threat model aggiornato;
- primitive crittografiche standard;
- nessuna private key di infrastruttura incorporata nel client;
- logging di produzione senza plaintext, private key o session key;
- procedura pubblica per segnalare vulnerabilità;
- security review indipendente quando il protocollo esce dalla fase sperimentale.

---

## 4. Pubblico iniziale

Ordine consigliato:

```text
1. Founder Cohort
2. Security / privacy reviewers
3. Creator piccoli e medi altamente pertinenti
4. Creator privacy/tech più grandi
5. Tech generalista
6. Pubblico mainstream
```

Evitare inizialmente un posizionamento dominato da creator crypto. La blockchain è un meccanismo infrastrutturale del protocollo, non il beneficio che l'utente deve capire per primo.

Categorie prioritarie:

- privacy digitale;
- cybersecurity;
- Android / open source;
- digital rights;
- decentralizzazione tecnica;
- anti-censura / network resilience;
- tecnologia consumer avanzata.

---

## 5. Fase 0 — Founder Cohort

Prima dei creator pubblici, creare una closed beta di circa **20–50 persone** selezionate.

Profilo ideale:

- sviluppatori;
- utenti Android esperti;
- persone attente alla privacy;
- amministratori di rete;
- tester abituati a descrivere bug;
- piccoli creator disponibili a testare senza pubblicare immediatamente.

Obiettivi:

- trovare failure nell'onboarding;
- misurare il tempo necessario per arrivare alla prima sessione;
- capire se la proposta sincrona viene capita senza spiegazioni lunghe;
- testare reti differenti, NAT e condizioni mobili;
- identificare claim confusi o troppo forti;
- produrre una checklist di bug prima della fase creator.

La Founder Cohort non deve essere trattata come campagna marketing. È una fase di validazione.

---

## 6. Fase 1 — Security & Privacy Reviewers

Creare un percorso separato per reviewer tecnici indipendenti.

Non confondere reviewer e influencer.

Materiale da fornire:

- link al repository;
- `ARCHITECTURE.md`;
- `PROTOCOL.md`;
- `THREAT_MODEL.md`;
- build riproducibile o istruzioni di build quando disponibile;
- scope delle parti ancora sperimentali;
- canale per responsible disclosure.

Obiettivo:

> trovare problemi prima che il marketing trasformi un'ipotesi tecnica in una promessa pubblica.

I reviewer devono essere liberi di pubblicare critiche.

---

## 7. Fase 2 — Creator Pilot

Target iniziale: circa **20–30 creator piccoli/medi**, altamente pertinenti.

Preferire creator con community realmente interessata al problema rispetto a creator con numeri molto grandi ma audience generica.

### 7.1 Cosa offrire

Programma consigliato:

**Freedom Founding Creator — Pro Lifetime**

Benefici possibili:

- tutte le feature Pro del client ufficiale;
- accesso anticipato alle feature;
- badge Founder opzionale;
- accesso a canale feedback dedicato;
- capacità relay gestita con fair-use;
- privacy routes / multi-hop quando implementati;
- inviti Pro temporanei per la community.

"Lifetime" deve riferirsi al tier/funzionalità e non promettere banda infrastrutturale infinita senza fair-use.

### 7.2 Entitlement Pro

Obiettivo architetturale: evitare che il diritto Pro richieda un server centrale nel percorso di ogni utilizzo.

Possibile modello futuro:

```text
FreedomProEntitlement {
    subject
    tier
    issued_at
    expires_at?
    policy_version
    issuer_signature
}
```

Il client può verificare una capability firmata localmente o tramite meccanismi sostituibili.

Questo design deve essere definito separatamente prima dell'implementazione e non deve diventare un trust anchor del protocollo E2EE.

### 7.3 Community benefit

Default di campagna suggerito per i primi test:

- creator: Founder Pro Lifetime;
- community del creator: quantità limitata di Pro trial o early-access invite;
- referral/invite identificabile solo per attribuire l'acquisizione, senza tracciare conversazioni.

Il numero e la durata delle trial devono poter cambiare in base ai costi reali dell'infrastruttura.

---

## 8. Creator outreach package

Ogni creator deve ricevere un pacchetto semplice.

### 8.1 One-page brief

Deve spiegare in meno di un minuto:

- cos'è Freedom;
- perché è diverso;
- cosa è già implementato;
- cosa è ancora roadmap;
- quali claim NON fare;
- come eseguire la demo.

### 8.2 Demo consigliata

La demo deve mostrare proprietà osservabili.

Esempio:

```text
Telefono A                  Telefono B
    |                           |
    |---- authenticated -------->|
    |<======== E2EE live =======>|
    |                           |
    |       Live session        |
    |                           |
    X chiusura sessione         X
```

Possibili momenti della demo:

1. scambio contatto;
2. stabilimento sessione;
3. invio messaggio live;
4. assenza di consegna offline automatica;
5. modalità Live/effimera quando implementata;
6. mostrare che il messaggio non è una transazione blockchain;
7. mostrare un cambio route/relay solo quando la feature è effettivamente pronta.

### 8.3 Angoli editoriali

Titoli/concetti possibili, da lasciare comunque alla libertà del creator:

- "Un messenger che non vuole diventare una mailbox";
- "Cosa succede se togliamo il server centrale dalla conversazione?";
- "Ho provato un messenger live E2EE progettato per cambiare percorso";
- "Blockchain per trovare/verificare il peer, non per spedire i messaggi";
- "Direct quando vuoi velocità, relay/shielded quando vuoi proteggere l'endpoint" — solo quando implementato.

Non suggerire titoli assoluti come "impossibile da tracciare" o "impossibile da censurare".

---

## 9. Referral e attribuzione privacy-preserving

Serve sapere quali campagne funzionano senza creare un sistema di sorveglianza incompatibile con Freedom.

Misurare eventi di prodotto minimali, preferibilmente opt-in/aggregati:

```text
install / first open
identity initialized
contact added
first authenticated session completed
first message sent successfully
reconnect completed
Pro trial activated
```

Non raccogliere per il marketing:

- plaintext;
- lista dei contatti;
- DeviceID dei peer in forma leggibile;
- contenuto dei rendezvous;
- cronologia dei messaggi;
- identificatori persistenti non necessari;
- IP associato alla relazione tra utenti quando evitabile.

Un referral code può attribuire la sorgente di acquisizione senza diventare parte dell'identità Freedom.

---

## 10. Metriche di lancio

Le metriche non devono essere solo download.

### 10.1 Activation

Metriche principali:

- percentuale di installazioni che completano l'inizializzazione;
- percentuale che aggiunge almeno un contatto;
- percentuale che completa una prima sessione autenticata;
- percentuale che invia con successo il primo messaggio live;
- tempo medio/mediano fino alla prima sessione riuscita.

### 10.2 Reliability

Misurare:

- session establishment success rate;
- send success rate durante sessione attiva;
- reconnect success rate;
- crash-free session rate;
- distribuzione della latenza, non solo media;
- fallimenti per classe di rete/NAT/transport quando disponibili senza compromettere privacy.

### 10.3 Creator quality

Per ogni creator:

```text
views
 -> click/invite
 -> install
 -> identity initialized
 -> first peer added
 -> first live session
 -> repeated usage
```

Il creator migliore non è necessariamente quello con più view, ma quello che genera più utenti che completano una vera sessione Freedom.

---

## 11. Criteri Go / No-Go

Prima di passare da una fase alla successiva, usare criteri misurabili.

Esempi di target iniziali da validare e modificare con dati reali:

- onboarding completion >= 80%;
- first authenticated session success >= 85% tra tester con prerequisiti soddisfatti;
- crash-free sessions >= 99%;
- nessun bug critico aperto su identity key handling;
- nessuna latenza sistematica di più secondi per un semplice messaggio su sessione locale stabile;
- nessun claim pubblico che dipenda da una feature non implementata.

Queste soglie sono target operativi, non proprietà del protocollo.

---

## 12. Fase 3 — Creator Scale

Solo dopo il pilot:

- selezionare i 3–5 angoli editoriali che hanno prodotto la migliore activation;
- aumentare il numero di creator;
- contattare creator privacy/tech più grandi;
- mantenere referral distinti;
- fornire changelog sintetico delle feature;
- evitare campagne simultanee troppo grandi se il prodotto non è ancora dimensionato per supportarle.

Un creator grande deve arrivare dopo che il funnel è stato già verificato con creator piccoli.

---

## 13. Fase 4 — Public Launch

Il lancio pubblico può includere:

- release stabile del client;
- sito con spiegazione semplice;
- video demo ufficiale breve;
- repository e specifica pubblici;
- pagina security / responsible disclosure;
- comparison page basata su proprietà tecniche verificabili;
- Founder/Early Supporter program limitato;
- comunicato per community privacy/open-source;
- presenza coordinata su social e community tecniche.

La homepage deve spiegare Freedom prima di spiegare NEAR.

Ordine narrativo suggerito:

```text
problema
 -> comunicazione live privata
 -> nessuna mailbox centrale
 -> E2EE
 -> percorsi multipli
 -> identità/rendezvous verificabili
 -> blockchain adapter come implementazione del control plane
```

---

## 14. Monetizzazione durante il lancio

Seguire `MONETIZATION.md`.

### Free

Il core interoperabile deve restare gratuito:

- identity;
- contatti;
- sessioni E2EE;
- messaging live;
- modalità Live quando disponibile;
- direct path;
- capacità community/best-effort dove disponibile.

### Pro

Possibili feature commerciali:

- managed relay capacity;
- privacy/shielded routes;
- multi-hop gestito;
- limiti superiori;
- funzioni client avanzate;
- multi-device/recovery avanzato quando progettato.

### Business

- SDK;
- deployment;
- relay dedicati;
- supporto;
- SLA;
- integrazioni.

Il marketing non deve far dipendere la sicurezza crittografica dal piano pagato.

---

## 15. Founder Program

Il Founder Program può diventare uno strumento di community oltre che marketing.

Possibili categorie:

```text
Founding Tester
Founding Reviewer
Founding Creator
Early Supporter
```

Benefit possibili:

- Pro Lifetime con fair-use infrastrutturale;
- badge opzionale;
- accesso anticipato;
- canale feedback;
- voto consultivo su priorità non-security;
- riconoscimento pubblico solo su consenso.

La qualifica Founder non deve dare privilegi crittografici o di trust nel protocollo.

---

## 16. Feedback loop

Ogni ondata deve produrre un ciclo breve:

```text
release
 -> utenti reali
 -> bug / misunderstanding
 -> fix
 -> documentazione
 -> nuova release
 -> ondata successiva
```

Classificare il feedback almeno in:

- blocker;
- security/privacy;
- reliability;
- onboarding;
- UX;
- performance;
- positioning;
- feature request.

Problemi security/privacy e reliability precedono feature cosmetiche durante le prime fasi.

---

## 17. Cosa non fare

- non comprare subito grandi quantità di traffico paid;
- non mandare l'APK indistintamente a centinaia di creator;
- non promettere anonimato assoluto;
- non presentare NEAR come motivo principale per usare Freedom;
- non pagare per recensioni positive;
- non creare analytics invasivi per misurare il marketing;
- non regalare "banda infinita per sempre" senza fair-use;
- non scalare il lancio mentre il percorso principale ha bug di latenza/reliability;
- non trasformare Pro in un requisito per l'interoperabilità base.

---

## 18. Sequenza operativa consigliata

```text
A. stabilizzare transport/session hot path
B. completare onboarding minimo
C. preparare responsible disclosure
D. Founder Cohort: 20–50 tester
E. correggere blocker
F. 5–10 reviewer security/privacy
G. correggere findings prioritari
H. Creator Pilot: 20–30 creator pertinenti
I. misurare activation, reliability e conversion
J. ottimizzare messaggio e onboarding
K. creator più grandi
L. public launch
M. Freedom Plus / Business con feature realmente disponibili
```

---

## 19. North-star metric

Durante il primo lancio, la metrica più importante non è il download.

Una possibile north-star:

> **numero di nuovi utenti che completano con successo almeno una sessione autenticata Freedom con un altro peer.**

Questa misura se Freedom sta realmente creando comunicazione, invece di misurare soltanto installazioni.

---

## 20. Risultato atteso

Il lancio deve creare tre asset contemporaneamente:

1. **utenti reali** che comunicano tramite Freedom;
2. **credibilità tecnica** tramite reviewer e documentazione;
3. **distribuzione** tramite creator che comprendono davvero il prodotto.

Il programma creator è quindi un acceleratore, non il punto di partenza assoluto.

Principio finale:

> **prima rendere Freedom dimostrabile, poi renderlo raccontabile, infine renderlo grande.**
