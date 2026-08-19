# Freedom — Registration Economics & Anti-Abuse

## 1. Obiettivo

Freedom deve poter sostenere una base Free molto grande senza trasformare la registrazione in un vettore con cui un attaccante può obbligare il treasury a pagare storage/gas illimitati.

> **il primo utilizzo deve essere gratuito per una persona; creare identità in massa deve avere un costo computazionale ed economico crescente per un aggressore.**

## 2. Nessuna write al semplice install

```text
install
 -> generate RootIdentity locally
 -> generate DeviceKey locally
 -> generate DeviceRecordCommitment locally
 -> Recovery Kit
 -> 0 mandatory chain writes
```

Una installazione abbandonata non deve consumare storage permanente del protocollo.

La prima registrazione on-chain avviene quando serve realmente rendere verificabile la RootIdentity/device authorization per l'uso del protocollo.

Dettagli identità: [`IDENTITY_MODEL.md`](IDENTITY_MODEL.md).

## 3. Sponsored registration

Freedom può sponsorizzare il costo delle operazioni essenziali Free tramite fee relayer/treasury.

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

La difficoltà deve essere bassa per un singolo utente legittimo su hardware economico, ma aumentare il costo di milioni di registrazioni automatiche.

Il valore concreto non deve essere fissato senza benchmark reali su dispositivi Android rappresentativi.

## 5. Relayer multipli e rate limit

Più fee relayer indipendenti possono sponsorizzare nuove registrazioni.

Ogni relayer applica limiti bounded per finestra temporale e budget. Il blocco/rate-limit di un singolo relayer non deve diventare censura globale.

I limiti possono considerare segnali anti-abuso locali senza trasformare IP, numero di telefono, account commerciale o DeviceRecordCommitment in identità pubblica Freedom.

## 6. Una sponsorship per RootIdentity

La chain deve poter determinare che una RootIdentity ha già ricevuto la propria registrazione iniziale sponsorizzata.

Un reinstall/nuovo telefono usa il Recovery Kit e la RootIdentity esistente; non deve creare una nuova RootIdentity per ottenere una nuova sponsorship.

Nuove DeviceKey/DeviceRecordCommitment autorizzate sono soggette al `max_devices` dell'entitlement.

## 7. Budget globale/dinamico

Quando il traffico di registrazione supera drasticamente il baseline:

```text
proof difficulty     -> può aumentare
per-relayer rate     -> può ridursi
sponsorship budget   -> resta bounded
existing users       -> non vengono disabilitati
```

Un attacco alle nuove registrazioni non deve interrompere comunicazioni o recovery degli utenti già registrati.

## 8. Storage minimale e bounded

```text
PERMANENT
root commitment quando necessario
opaque current device record commitment
current device public key / key epoch / status
entitlement/device-slot summary quando necessario

TEMPORARY
rendezvous
recovery beacon
purchase intent
route hints
```

Non memorizzare messaggi, media, history, social graph, alias pairwise leggibili o presenza continua.

Un commitment opaco riduce la leggibilità ma non elimina correlazioni temporali: activation/revocation/rendezvous devono essere misurati anche come pattern osservabili.

## 9. Costo cresce con eventi rari, non con comunicazioni

```text
message            -> 0 chain writes
voice/video frame  -> 0 chain writes
active session     -> 0 presence heartbeat writes
normal route       -> 0 recovery writes
```

Chain writes sono legate a eventi rari del control-plane: registration, key rotation/revocation, device activation, rendezvous/recovery, entitlement/payment state.

## 10. Free tier anti-abuse

Policy prodotto iniziale:

- 1 RootIdentity sponsorizzata;
- 1 device attivo Free;
- 10 contatti-persona attivi Free;
- messaging/calls core non limitati in base al numero di messaggi per finanziare la chain;
- Emergency Shield limitato in base a capacità/costo reale;
- recovery essenziale non usato come leva commerciale.

Il limite contatti non sostituisce l'anti-Sybil ma aumenta il costo di abuso su larga scala senza rendere inutilizzabile il piano Free.

## 11. Metriche da misurare prima di mainnet

- byte reali per Root/device record serializzato;
- costo medio registration/rotation/revocation;
- storage effettivo per 100k / 1M RootIdentity/device record;
- costo e latenza dei recovery beacon;
- correlabilità temporale tra activation/revocation/rendezvous;
- distribuzione hardware per benchmark PoW;
- attacchi simulati a relayer/sponsorship;
- costo mensile treasury per utenti Free reali vs identità abusive.

Non fissare prezzi o quote permanenti prima di queste misure.
