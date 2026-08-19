# Freedom — Registration Economics & Anti-Abuse

## 1. Obiettivo

Freedom deve poter sostenere una base Free molto grande senza trasformare la registrazione in un vettore con cui un attaccante può obbligare il treasury a pagare storage/gas illimitati.

Principio:

> **il primo utilizzo deve essere gratuito per una persona; creare identità in massa deve avere un costo computazionale ed economico crescente per un aggressore.**

## 2. Nessuna write al semplice install

```text
install
 -> generate RootIdentity locally
 -> generate DeviceIdentity locally
 -> Recovery Kit
 -> 0 mandatory chain writes
```

Una installazione abbandonata non deve consumare storage permanente del protocollo.

La prima registrazione on-chain avviene quando serve realmente rendere l'identità verificabile per l'uso del protocollo.

## 3. Sponsored registration

Freedom può sponsorizzare il costo delle operazioni essenziali Free tramite fee relayer/treasury.

La sponsorship non è una primitive illimitata. Una richiesta di nuova RootIdentity deve superare policy anti-abuso prima che un relayer paghi la transaction.

Pipeline concettuale:

```text
NEW ROOT IDENTITY
 -> valid signature?
 -> anti-abuse proof valid?
 -> sponsorship already consumed?
 -> relayer rate limit OK?
 -> global sponsorship budget OK?
 -> REGISTER
```

## 4. Proof-of-work adattivo

Il client può risolvere una challenge computazionale leggera legata almeno a:

```text
challenge
root_public_key
nonce
expiry/context
```

La difficoltà deve essere sufficientemente bassa per un singolo utente legittimo su hardware economico, ma aumentare il costo di milioni di registrazioni automatiche.

La difficoltà può essere adattata in base a pressione/abuso osservato, con limiti massimi che evitino di escludere device deboli.

Il valore concreto non deve essere fissato senza benchmark reali su dispositivi Android rappresentativi.

## 5. Relayer multipli e rate limit

Più fee relayer indipendenti possono sponsorizzare nuove registrazioni.

Ogni relayer applica limiti bounded per finestra temporale e budget. Il blocco/rate-limit di un singolo relayer non deve diventare censura globale perché altri relayer compatibili possono esistere.

I limiti possono considerare segnali anti-abuso locali senza trasformare IP, numero di telefono o account commerciale in identità Freedom.

## 6. Una sponsorship per RootIdentity

La chain deve poter determinare che una RootIdentity ha già ricevuto la propria registrazione iniziale sponsorizzata.

Un reinstall/nuovo telefono usa il Recovery Kit e la RootIdentity esistente; non deve creare una nuova RootIdentity per ottenere una nuova sponsorship.

Nuove DeviceIdentity autorizzate sono soggette al `max_devices` dell'entitlement.

## 7. Budget globale/dinamico

Treasury e relayer possono definire un budget massimo di sponsorship per intervallo.

Quando il traffico di registrazione supera drasticamente il baseline:

```text
proof difficulty     -> può aumentare
per-relayer rate     -> può ridursi
sponsorship budget   -> resta bounded
existing users       -> non vengono disabilitati
```

Un attacco alle nuove registrazioni non deve interrompere comunicazioni o recovery degli utenti già registrati.

## 8. Storage minimale e bounded

Lo stato permanente deve essere ridotto al minimo necessario:

```text
PERMANENT
root/account commitment quando necessario
current identity key/commitment
key epoch
status
entitlement/device-slot summary quando necessario
```

Stato operativo come rendezvous, recovery beacon, purchase intent e route hint deve essere temporaneo, riutilizzabile/sovrascrivibile o reclaimable quando il modello della chain lo consente.

Non memorizzare messaggi, media, history, social graph o presenza continua.

## 9. Costo cresce con eventi rari, non con comunicazioni

Invarianti economiche:

```text
message            -> 0 chain writes
voice/video frame  -> 0 chain writes
active session     -> 0 presence heartbeat writes
normal route       -> 0 recovery writes
```

Chain writes sono legate a eventi rari del control-plane: registrazione, key rotation/revocation, device activation, rendezvous di recovery, recovery beacon, entitlement/payment state.

## 10. Free tier anti-abuse

Policy prodotto iniziale:

- 1 RootIdentity sponsorizzata;
- 1 device attivo Free;
- 10 contatti attivi Free;
- messaging/calls core non limitati in base al numero di messaggi per finanziare la chain;
- Emergency Shield limitato in base a capacità/costo reale;
- recovery essenziale non viene usato come leva commerciale.

Il limite contatti non sostituisce l'anti-Sybil ma aumenta il costo di abuso su larga scala senza rendere inutilizzabile il piano Free.

## 11. Metriche da misurare prima di mainnet

- byte reali per record serializzato;
- costo medio registrazione/rotation/revocation;
- storage effettivo per 100k / 1M identità;
- costo e latenza dei recovery beacon;
- distribuzione hardware per benchmark PoW;
- attacchi simulati a relayer/sponsorship;
- costo mensile treasury per utenti Free reali vs identità abusive.

Non fissare prezzi o quote permanenti prima di queste misure.
