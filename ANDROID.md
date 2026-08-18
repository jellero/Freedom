# Freedom Android

## Stato

Android è la prima piattaforma di implementazione.

Il codice attualmente presente sotto `app/` è un **transport/crypto spike** sviluppato prima della specifica corrente. Dimostra una connessione TCP diretta, un handshake autenticato e messaggi cifrati, ma usa ancora IP manuale e fingerprint/TOFU.

Questa parte resta utile come laboratorio di trasporto, ma **non è più l'M1 canonico**.

La nuova implementazione deve partire da DeviceID + NEAR Testnet.

## Obiettivo UX iniziale

Il primo client deve essere estremamente semplice.

Schermate:

```text
Home
My Identity
Add Contact / Scan QR
Contacts
Conversation
Network Debug
Settings
Blocked Devices
```

## M1 — Device identity

### Primo avvio

```text
Install Freedom
      |
Generate DeviceID
      |
Generate identity key
      |
Private key -> Android Keystore
      |
Register DeviceID + public key on NEAR Testnet
      |
READY
```

La schermata `My Identity` mostra:

```text
Freedom Device ID
network: NEAR Testnet
key epoch
status
QR
```

La raw private key non viene mai mostrata.

## M1 — QR

Il QR contiene un `FreedomContact`:

```text
version
network_id
device_id
rendezvous_capability
expires_at?
```

La capability viene generata con SecureRandom e deve poter essere ruotata.

Azioni:

```text
[Show QR]
[Share contact]
[Rotate contact QR]
```

## Add Contact

```text
[ Scan QR ]

oppure

Paste Freedom contact
```

Dopo il parse:

```text
DeviceID
   |
NearChainAdapter.resolveDevice
   |
public key + key epoch + status
   |
CONTACT VERIFIED
```

La UI deve distinguere:

```text
Identity found
Identity verified
Identity revoked
Network unavailable
```

## M2 — Rendezvous

Quando A vuole aprire una conversazione con B:

```text
known route?
  yes -> connect
  no

read remote rendezvous
  found -> try it, no write
  empty

check local current rendezvous
  already valid -> wait/poll
  empty -> publish one offer
```

Ogni rendezvous letto è autosufficiente: il client non mantiene una revisione precedente e non deve conoscere uno storico per interpretarlo. Un nuovo rendezvous viene creato da zero con nuovo nonce/materiale effimero.

La UI normale non deve mostrare transazioni o dettagli chain salvo errore.

La modalità debug mostra:

```text
remote slot
local slot
expires_at
chain tx id
candidate count
```

Non esiste un `rendezvous sequence` o `revision`.

## M3 — Secure session

Dopo aver trovato un percorso:

```text
resolve current remote DeviceRecord
       |
mutual authenticated handshake
       |
fresh session keys
       |
E2EE ACTIVE
```

La UI mostra semplicemente:

```text
Identity verified
End-to-end encrypted
```

Il fingerprint manuale/TOFU del vecchio spike viene rimosso dal normale flusso.

## M4 — Route maintenance

Durante la sessione Android monitora il percorso e condivide route update direttamente con il peer.

Eventi rilevanti:

- cambio Wi-Fi/mobile;
- cambio endpoint osservato;
- perdita candidate;
- nuovo candidate;
- relay disponibile/non disponibile.

Se almeno un route rimane valido:

```text
RouteUpdate -> E2EE session
```

Nessuna write blockchain.

I sequence number eventualmente usati nei `RouteUpdate` o nei frame cifrati appartengono esclusivamente alla sessione attiva per ordinamento/anti-replay e non sono revisioni del rendezvous blockchain.

## NAT traversal

Il progetto deve supportare progressivamente:

```text
direct
observed candidate
UDP hole punching
relay
```

Per il debugging iniziale può esistere un campo IP/porta manuale, ma deve essere marcato `Developer / Debug` e non rappresentare l'identità del contatto.

## Endpoint observation

Un device dietro NAT potrebbe non conoscere autonomamente l'endpoint pubblico effettivo.

L'architettura deve quindi supportare endpoint osservati da peer/relay indipendenti.

Un observer non autentica il device: fornisce soltanto una candidate di rete. La successiva connessione viene sempre autenticata usando DeviceID + blockchain identity.

## Relay mode

In una milestone successiva Android può offrire relay mode opt-in.

UI prevista:

```text
Relay mode: OFF/ON
Wi-Fi only
Max bandwidth
Max concurrent circuits
Battery constraints
```

Relay mode non salva conversazioni.

## Messaging

La prima conversation screen implementa:

```text
text
sent
received ACK
pending peer offline
retry when peer reachable
```

Se il peer è offline:

```text
Waiting for peer
```

Il messaggio rimane nel database locale.

Nessun upload su chain/relay.

## Attachments

Solo dopo il text messaging stabile.

Trasferimento:

```text
active secure session
 -> encrypted chunks
 -> direct/relay transport
 -> receiver endpoint
```

Se il route cade, il trasferimento può essere ripreso quando la sessione viene ristabilita, nei limiti del protocollo definito.

## Voice/video

Dopo il signaling E2EE stabile:

```text
CallInvite
CallAccept
CallCandidate
CallEnd
```

Il media transport usa chiavi separate e può viaggiare direct o tramite relay compatibile.

## Chain module Android

Struttura target:

```text
app/
core/
  identity/
  protocol/
  session/
  routing/
chain/
  ChainAdapter
  near/
transport/
  direct/
  nat/
  relay/
platform-android/
```

La separazione può inizialmente vivere come package/module graduali, senza sovra-ingegnerizzare la prima build.

## Secure storage

Android Keystore viene usato per le identity key quando supportato.

Il database locale deve separare:

- identity metadata;
- contacts;
- pair rendezvous secrets;
- conversation data;
- network cache.

I pair secret e materiale sensibile devono essere protetti a riposo secondo le primitive Android disponibili.

## Android 17 / local network

Il manifest e runtime flow devono gestire i permessi di rete locale richiesti dalla versione Android target.

La UI deve spiegare il permesso in relazione alla funzionalità di comunicazione sulla rete locale.

## Debug screen

Durante lo sviluppo:

```text
DeviceID
Key epoch
Chain state/finality
RPC endpoint selected
Remote DeviceID
Rendezvous slot hash
Rendezvous TTL
Known route candidates
Current path
Public/observed endpoint
Relay
Session ID
TX/RX sequence
RTT
```

`TX/RX sequence` riguarda la sessione cifrata, non il rendezvous on-chain.

Non mostrare private/session/rendezvous secrets in chiaro.

## Build

Il progetto Android esistente usa attualmente Android Gradle Plugin 9.3.x e API 37. Prima di investire nella UI definitiva va completato il refactor architetturale e aggiunta una pipeline CI che esegua almeno:

```text
assembleDebug
unit tests
protocol serialization tests
crypto vectors
lint
```

## Roadmap Android

```text
A1  refactor identity layer
A2  NearChainAdapter Testnet
A3  DeviceID registration
A4  QR contact
A5  identity resolve/verify
A6  rendezvous capability + slots
A7  read-before-write flow
A8  mutual authenticated session
A9  text + ACK
A10 route update
A11 NAT traversal
A12 relay
A13 attachments
A14 voice/video
A15 store compliance polish
```
