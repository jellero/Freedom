# Freedom Android

## Stato

Android è la prima piattaforma di implementazione.

Il codice attualmente presente sotto `app/` è un **transport/crypto spike** sviluppato prima della specifica corrente. Dimostra connessione TCP diretta, handshake cifrato e scambio E2EE di test, ma usa ancora IP manuale e fingerprint/TOFU.

Questa parte resta utile come laboratorio di trasporto, ma **non è l'M1 canonico**.

La nuova implementazione deve partire dal modello:

```text
RootIdentity
DeviceKey
DeviceRecordCommitment
PairwiseContactAlias
TransportToken
```

Non esiste un `DeviceID` globale richiesto dal client o dal wire protocol.

Dettagli: [`docs/IDENTITY_MODEL.md`](docs/IDENTITY_MODEL.md).

## Obiettivo UX iniziale

Il primo client deve essere estremamente semplice.

Schermate:

```text
Home
My Freedom
Add Contact / Scan QR
Contacts
Conversation
Network Status
Settings
Blocked Contacts
```

Commitment, epoch, RPC e dettagli chain restano nel debug/advanced UI salvo necessità di recovery.

## A1 — RootIdentity e device authorization

### Primo avvio

```text
Install Freedom
      |
Generate RootIdentity
      |
Generate DeviceKey
      |
Generate opaque DeviceRecordCommitment
      |
Private material -> Android Keystore / protected storage
      |
Generate Recovery Kit
      |
0 mandatory chain writes
```

Quando l'identità deve diventare verificabile:

```text
anti-abuse proof
      |
sponsored registration when eligible
      |
RootIdentity + authorized device record
      |
READY
```

La schermata `My Freedom` non mostra un global device identifier come identità dell'utente.

Può mostrare:

```text
Recovery status
Current device: authorized / revoked
Network
security/update status
[Show Contact QR]
[Recovery Kit]
```

La raw Root private key e la DeviceKey privata non vengono mai mostrate.

## A2 — Contact QR

Il contatto rappresenta una persona/RootIdentity, non ogni singolo telefono.

```text
FreedomContact {
    version
    network_id
    root_identity_proof
    contact_capability
    bootstrap_device_certificate?
    bootstrap_route_hints[]?
    expires_at?
}
```

La `contact_capability` viene generata con CSPRNG e deve poter essere ruotata/scadere.

Azioni:

```text
[Show Contact QR]
[Share contact]
[Rotate contact QR]
```

Dopo il primo handshake i peer derivano:

```text
PairSecret
PairwiseContactAlias
PairRendezvousSecret
```

Alias differenti devono essere usati per contatti differenti.

## Add Contact

```text
[ Scan Contact QR ]

oppure

Paste Freedom contact
```

Dopo il parse:

```text
root identity proof
      |
bootstrap device authorization verification
      |
authenticated first handshake
      |
derive pairwise identity state
      |
CONTACT VERIFIED
```

La UI distingue:

```text
Contact verified
Current device verified
Device revoked
Control-plane unavailable
Contact capability expired
```

## A3 — Rendezvous

Quando Alice vuole aprire una conversazione con Bob:

```text
known route?
  yes -> connect
  no

read pairwise remote rendezvous
  found -> try it, no write
  empty

check local current rendezvous
  already valid -> wait/poll for peer coordination
  empty -> publish one bounded offer
```

Gli slot derivano dal `PairRendezvousSecret`, non da RootIdentity o DeviceRecordCommitment pubblico.

Ogni rendezvous è autosufficiente, con nuovo nonce/materiale effimero e TTL breve.

La UI normale non mostra transazioni o dettagli chain salvo errore.

Debug:

```text
pairwise slot hash
expires_at
chain tx id when written
candidate count
```

## A4 — Secure session

Dopo aver trovato un percorso:

```text
verify expected RootIdentity/contact
      |
verify current DeviceKey authorization / epoch
      |
mutual authenticated handshake
      |
fresh session keys
      |
E2EE ACTIVE
```

Il transcript usa identity proof/alias pairwise e device authorization proof; non richiede un global DeviceID.

La UI mostra semplicemente:

```text
Contact verified
End-to-end encrypted
```

Il fingerprint manuale/TOFU del vecchio spike viene rimosso dal normale flusso.

## A5 — Messaging sincrono

Freedom non implementa retry offline implicito.

```text
SEND
  |
  +-- active authenticated session? -- no --> FAIL / DISCARD
  |
  +-- yes --> transmit --> ACK/session result
```

La conversation screen implementa:

```text
text
sent
received ACK
read ACK optional
```

Se il peer non è raggiungibile:

```text
Peer not reachable
Message not sent
```

Il protocollo base **non mette il messaggio in una coda per inviarlo quando il peer torna online** e non lo deposita su chain/relay.

Se il client conserva una bozza digitata dall'utente, quella bozza resta UI state e non deve essere confusa con una delivery queue.

## A6 — Route maintenance

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

Sequence number di `RouteUpdate`/frame appartengono alla sessione attiva e non sono revisioni del rendezvous.

## A7 — NAT traversal

Supporto progressivo:

```text
direct
observed candidate
UDP hole punching
relay
shielded / future transports
```

Per debugging può esistere IP/porta manuale marcato `Developer / Debug`; non rappresenta l'identità del contatto.

Un observer fornisce soltanto una candidate di rete. La connessione viene autenticata con Root/contact identity + current DeviceKey authorization.

## A8 — Relay mode

Android può offrire `DEVICE_RELAY` opt-in.

```text
Relay mode: OFF/ON
Wi-Fi only
Charging only optional
Battery minimum
Max bandwidth
Max concurrent circuits
```

Relay mode:

- non salva conversazioni;
- usa circuit token temporanei;
- non riceve RootIdentity/device commitment se non necessario;
- non diventa un open proxy Internet;
- può qualificare il Free al benefit Relay Contributor secondo policy verificabile.

Il ruolo futuro `FREEDOM_GATEWAY`/Internet egress è separato da `DEVICE_RELAY`.

## A9 — Attachments

Trasferimento solo con sessione/route attiva:

```text
active secure session
 -> encrypted chunks
 -> direct/relay transport
 -> receiver endpoint
```

Se il route cade, il trasferimento corrente può fallire o essere ripreso solo come parte di una nuova sessione/negoziazione esplicita; non diventa automaticamente storage offline di rete.

## A10 — Voice/video

```text
CallInvite
CallAccept
CallCandidate
CallEnd
```

Il signaling è E2EE; media keys separate dalle messaging keys. Media può viaggiare direct o tramite relay compatibile.

## Chain module Android

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

La separazione può inizialmente vivere come package/module graduali senza sovra-ingegnerizzare la prima build.

## Secure storage

Android Keystore viene usato per chiavi applicabili quando supportato.

Il database locale separa:

- RootIdentity metadata non segreto;
- device authorization metadata;
- contacts / pairwise aliases;
- pair rendezvous secrets;
- conversation data secondo modalità/policy;
- network cache.

Pair secret e materiale sensibile devono essere protetti a riposo secondo le primitive Android disponibili.

## Share Freedom

La Direct build può mostrare un Install QR e, opzionalmente, servire un APK standalone già verificato tramite endpoint locale temporaneo/capability.

```text
Share Freedom
 -> Install QR
 -> peer / relay / mirror
 -> verify release manifest/hash/signer
 -> Android installer
```

Non incorporare per default una seconda copia dell'APK nel client.

Dettagli: [`docs/APP_DISTRIBUTION.md`](docs/APP_DISTRIBUTION.md).

## Gateway candidato post-V1

Un eventuale `Freedom Gateway` Android può usare il meccanismo VPN della piattaforma per instradare traffico di app selezionate o dell'intero device dentro un tunnel Freedom verso egress espliciti.

Questo è **separato dal relay messenger** e non è un blocker V1.

```text
selected apps / whole device
       |
Android VPN interface
       |
Freedom path selector
       |
PRIVATE / MANAGED / EGRESS gateway
       |
Internet
```

Un `DEVICE_RELAY` community non diventa automaticamente egress Internet.

## Debug screen

Durante lo sviluppo:

```text
Root commitment hash (abbreviato)
Device record commitment hash (abbreviato)
Key epoch
Chain state/finality
RPC endpoint selected
Pairwise alias hash
Rendezvous slot hash
Rendezvous TTL
Known route candidates
Current path
Public/observed endpoint
Relay / circuit token hash
Session ID
TX/RX sequence
RTT
```

Non mostrare private/session/rendezvous secrets in chiaro.

## Build

La CI deve eseguire almeno:

```text
assembleDebug
unit tests
protocol serialization tests
crypto vectors
lint
```

## Roadmap Android

```text
A1  RootIdentity + DeviceKey + opaque device record
A2  Recovery Kit + sponsored registration
A3  NearChainAdapter Testnet
A4  person/contact QR + pairwise alias
A5  device authorization resolve/rotation/revocation
A6  pairwise rendezvous capability + slots
A7  read-before-write flow
A8  mutual authenticated session
A9  synchronous text + ACK
A10 route update
A11 NAT traversal
A12 relay / DEVICE_RELAY
A13 attachments
A14 voice/video
A15 Share Freedom Direct
A16 Network Indicator / Adaptive Defense
A17 store compliance polish
A18 optional Freedom Gateway evaluation/implementation
```
