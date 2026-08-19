# Freedom — Launch Plan

## 1. Obiettivo

Il lancio di Freedom non deve ottimizzare subito per il numero assoluto di download. Il primo obiettivo è creare un nucleo di utenti che comprenda la proposta del protocollo, completi sessioni reali e produca feedback tecnico utile.

> **dimostrare che Freedom è comprensibile, utilizzabile e tecnicamente credibile prima di amplificare la distribuzione.**

Freedom non deve essere promosso come "un altro messenger privato" o come "un messenger blockchain".

Posizionamento consigliato:

> **Freedom Messenger — Powered by Freedom Protocol**
>
> Comunicazione privata live, E2EE, sincrona, senza mailbox centrale e senza un singolo server o percorso necessario alla conversazione.

Claim come "impossibile da tracciare", "incensurabile", "anonimato garantito" o "rileva la sorveglianza" non devono essere usati.

## 2. Proprietà da spiegare prima della blockchain

Ordine narrativo:

```text
comunicazione live privata
 -> nessuna offline mailbox
 -> authenticated E2EE
 -> identità verificabile senza global DeviceID di rete
 -> percorsi/relay sostituibili
 -> recovery pairwise
 -> control-plane verificabile
 -> NEAR come prima implementazione ChainAdapter
```

Il modello identità pubblico deve essere coerente con [`IDENTITY_MODEL.md`](IDENTITY_MODEL.md):

```text
RootIdentity             ownership / recovery
DeviceKey                device authorization
DeviceRecordCommitment   control-plane opaco
PairwiseContactAlias     relazione specifica
TransportToken           route/circuito temporaneo
```

Un contatto è una persona/RootIdentity, non un singolo telefono.

## 3. Principi del lancio

1. **Credibilità prima della scala.** Prima tester tecnici e reviewer, poi creator, poi pubblico più ampio.
2. **Demo reale, non slideshow.** Due dispositivi devono completare una comunicazione reale.
3. **Editorial independence.** Accesso Pro o benefit non comprano una recensione positiva.
4. **Trasparenza.** Sponsorship/gift devono essere dichiarati quando applicabile.
5. **Privacy coerente.** Nessun analytics invasivo, social graph o identificatore globale introdotto per il marketing.
6. **Nessun lock-in commerciale.** Il protocollo base resta utilizzabile senza servizi ufficiali obbligatori.
7. **Niente overclaim.** Un claim pubblico deve corrispondere a una proprietà effettivamente implementata e testata.

## 4. Prerequisiti prima della promozione pubblica

Il tester deve poter:

```text
installare Freedom
 -> inizializzare RootIdentity + DeviceKey
 -> aggiungere un contatto tramite capability/QR
 -> derivare la relazione pairwise
 -> stabilire una sessione autenticata
 -> inviare/ricevere un messaggio live
 -> verificare che un peer offline NON riceva delivery futura automatica
 -> interrompere e ristabilire una nuova sessione
```

Blocker prima dei creator pubblici:

- crash riproducibili nel percorso principale;
- onboarding che richiede configurazione tecnica manuale non prevista nel prodotto;
- perdita/corruzione RootIdentity o DeviceKey;
- global DeviceID reintrodotto in routing/frame/telemetria senza necessità reviewata;
- alias pairwise correlabili tra contatti per errore;
- errori di autenticazione non spiegabili;
- latenza sistematica anomala dei messaggi;
- retry/offline queue implicita che contraddice la semantica sincrona;
- dipendenze hardcoded da credenziali personali o singola infrastruttura;
- claim privacy non implementati;
- update/distribuzione non autenticati.

## 5. Security readiness

Prima della promozione ampia:

- threat model aggiornato;
- primitive crittografiche standard;
- Root key, DeviceKey e session/media keys separate;
- nessuna private key infrastrutturale incorporata nel client;
- logging production senza plaintext/private/session/rendezvous secrets;
- metadata review su commitment, alias pairwise e transport token;
- procedura pubblica per responsible disclosure;
- security review indipendente quando il protocollo esce dalla fase sperimentale.

## 6. Pubblico iniziale

Ordine consigliato:

```text
1. Founder Cohort
2. Security / privacy reviewers
3. Creator piccoli e medi pertinenti
4. Creator privacy/tech più grandi
5. Tech generalista
6. Pubblico mainstream
```

Categorie prioritarie:

- privacy digitale;
- cybersecurity;
- Android / open source;
- digital rights;
- decentralizzazione tecnica;
- anti-censura / network resilience;
- tecnologia consumer avanzata.

Evitare inizialmente un posizionamento dominato da creator crypto: la blockchain è infrastruttura, non il beneficio principale.

## 7. Founder Cohort

Closed beta iniziale indicativa: **20–50 persone**.

Profili utili:

- sviluppatori;
- utenti Android esperti;
- persone attente alla privacy;
- amministratori di rete;
- tester abituati a descrivere bug;
- piccoli creator disponibili a testare prima di pubblicare.

Obiettivi:

- failure onboarding;
- tempo alla prima sessione autenticata;
- comprensione della semantica sincrona;
- reti/NAT/device differenti;
- verifica pairwise identity e recovery;
- relay/device relay in condizioni reali;
- Share Freedom / Install QR quando pronto;
- claim confusi o troppo forti.

## 8. Security & Privacy Reviewers

Percorso separato dagli influencer.

Materiale minimo:

- repository;
- `IDENTITY_MODEL.md`;
- `ARCHITECTURE.md`;
- `PROTOCOL.md`;
- `THREAT_MODEL.md`;
- build/instructions riproducibili quando disponibili;
- scope delle parti sperimentali;
- responsible disclosure.

Obiettivo:

> trovare problemi prima che il marketing trasformi un'ipotesi tecnica in una promessa pubblica.

I reviewer devono essere liberi di pubblicare critiche.

## 9. Creator Pilot

Target iniziale indicativo: **20–30 creator piccoli/medi** altamente pertinenti.

Programma possibile:

**Freedom Founding Creator — Pro Lifetime**

Benefit:

- feature Pro del client ufficiale;
- accesso anticipato;
- badge Founder opzionale;
- feedback channel;
- capacità relay gestita con fair-use;
- privacy routes/multi-hop quando realmente implementati;
- trial/inviti Pro limitati per community.

"Lifetime" riguarda il tier/funzionalità, non banda infrastrutturale infinita.

L'entitlement Pro segue la RootIdentity e non deve diventare un trust anchor E2EE.

## 10. Demo consigliata

La demo deve mostrare proprietà osservabili:

```text
Alice                         Bob
  |                            |
  | contact capability / QR    |
  |---------- bootstrap ------>|
  |                            |
  | pairwise authenticated     |
  |<======== E2EE live =======>|
  |                            |
  X session ends               X
```

Possibili momenti:

1. scambio contatto;
2. session establishment;
3. invio live;
4. peer offline -> messaggio non accodato;
5. nessun messaggio come transazione blockchain;
6. route/relay switch quando implementato;
7. Network Indicator quando implementato;
8. Share Freedom verificato quando implementato.

Il Live local-storage mode può essere mostrato come feature client, non come definizione centrale del protocollo.

## 11. Referral e attribuzione privacy-preserving

Misurare solo eventi minimali, preferibilmente aggregati/opt-in:

```text
install / first open
RootIdentity initialized
contact added
first authenticated session completed
first live message sent successfully
reconnect completed
Pro trial activated
```

Non raccogliere per marketing:

- plaintext;
- lista contatti/social graph;
- RootIdentity o root commitment persistente associato al funnel quando non necessario;
- DeviceRecordCommitment dei peer;
- PairwiseContactAlias;
- contenuto dei rendezvous;
- cronologia messaggi;
- transport/circuit token;
- IP associato alla relazione tra utenti quando evitabile.

Un referral code può attribuire l'acquisizione senza diventare parte dell'identità Freedom.

## 12. Metriche di lancio

### Activation

- onboarding completion;
- contact-added rate;
- first authenticated session success;
- first live send success;
- tempo medio/mediano alla prima sessione riuscita.

### Reliability

- session establishment success rate;
- send success rate durante sessione attiva;
- reconnect success rate;
- crash-free session rate;
- distribuzione latenza;
- failure per classe rete/NAT/transport con raccolta privacy-preserving.

### Network resilience

Quando implementata:

- direct success rate;
- relay fallback success;
- recovery success;
- time-to-recover;
- false-positive rate del Network Indicator;
- failure di singolo provider/relay senza perdita globale del servizio.

## 13. Go / No-Go

Target iniziali da validare con dati reali, non proprietà del protocollo:

- onboarding completion >= 80%;
- first authenticated session success >= 85% tra tester con prerequisiti soddisfatti;
- crash-free sessions >= 99%;
- nessun bug critico aperto su key/identity handling;
- nessuna latenza sistematica di più secondi per un semplice messaggio su sessione stabile;
- nessuna offline queue implicita;
- nessun claim pubblico dipendente da feature non implementata.

## 14. Public Launch

Il lancio pubblico può includere:

- release stabile;
- sito con spiegazione semplice;
- video demo breve;
- repository/spec pubblici;
- security/responsible disclosure page;
- comparison page basata su proprietà tecniche;
- Founder/Early Supporter program limitato;
- community privacy/open-source;
- distribuzione `Share Freedom` solo quando la verifica anti-fake è pronta.

La homepage deve spiegare Freedom prima di spiegare NEAR.

## 15. Monetizzazione durante il lancio

Seguire `MONETIZATION.md`.

### Free

- RootIdentity/recovery;
- 1 device;
- 10 contatti-persona;
- E2EE/live;
- direct/fallback base;
- community/device relay quando disponibile;
- Network Indicator;
- Emergency Shield bounded.

### Relay Contributor

Free qualificato come relay utile:

```text
10 base contacts + 10 bonus = 20
```

### Pro / Shield

- contatti/device superiori;
- managed relay capacity;
- Always-Shielded;
- multi-hop;
- Maximum Resilience;
- limiti media/file superiori.

### Business

- SDK;
- deployment;
- relay/egress privati quando previsti;
- supporto/SLA;
- integrazioni.

La sicurezza crittografica base non dipende dal piano pagato.

## 16. Gateway / browser nel lancio

Un browser web integrato **non è un obiettivo V1**.

Un futuro `Freedom Gateway` a livello dispositivo può essere molto più coerente perché estende il path selector Freedom ad app esistenti. È però un sottosistema separato dal messenger relay e richiede threat model, egress policy, DNS/leak prevention, Android VPN integration e review store dedicati.

Non trasformare `DEVICE_RELAY`/Relay Contributor in Internet exit node.

## 17. Founder Program

Categorie possibili:

```text
Founding Tester
Founding Reviewer
Founding Creator
Early Supporter
```

Benefit possibili:

- Pro Lifetime con fair-use;
- badge opzionale;
- accesso anticipato;
- canale feedback;
- riconoscimento pubblico solo su consenso.

La qualifica Founder non dà privilegi crittografici o di trust.

## 18. Feedback loop

```text
release
 -> utenti reali
 -> bug / misunderstanding
 -> fix
 -> documentation
 -> new release
 -> next cohort
```

Priorità:

```text
security/privacy
reliability
onboarding
network resilience
UX/performance
positioning
feature requests
```

## 19. Cosa non fare

- non comprare subito grandi quantità di traffico paid;
- non mandare APK non verificati a creator;
- non promettere anonimato assoluto/incensurabilità;
- non presentare NEAR come motivo principale per usare Freedom;
- non pagare per recensioni positive;
- non creare analytics invasivi;
- non regalare banda infinita senza fair-use;
- non scalare mentre il percorso principale ha bug di latency/reliability;
- non trasformare Pro in requisito per l'interoperabilità base;
- non introdurre browser/gateway prima che il core network/session sia stabile.

## 20. North-star metric

> **numero di nuovi utenti che completano con successo almeno una sessione autenticata Freedom con un altro contatto.**

Misura comunicazione reale, non installazioni.

## 21. Risultato atteso

Il lancio deve creare contemporaneamente:

1. **utenti reali** che comunicano tramite Freedom;
2. **credibilità tecnica** tramite reviewer e documentazione;
3. **distribuzione** tramite creator che comprendono il prodotto.

> **prima rendere Freedom dimostrabile, poi renderlo raccontabile, infine renderlo grande.**
