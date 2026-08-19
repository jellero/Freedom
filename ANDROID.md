# Freedom Android

## Stato

Android è la prima piattaforma di implementazione.

Il codice sotto `app/` è ancora un **transport/crypto spike** precedente alla specifica corrente. Dimostra TCP diretto, handshake cifrato e scambio E2EE di test, ma usa IP manuale e fingerprint/TOFU.

La nuova implementazione canonica parte da:

```text
RootIdentity
DeviceKey
DeviceRecordCommitment
PairwiseContactAlias
TransportToken
```

Non esiste un `DeviceID` globale richiesto dal client o dal wire protocol.

Dettagli: [`docs/IDENTITY_MODEL.md`](docs/IDENTITY_MODEL.md).

## UX iniziale

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

Commitment, epoch, RPC e dettagli chain restano nel debug/advanced UI salvo recovery/errori.

## A1 — RootIdentity e device authorization

```text
Install Freedom
      |
Generate RootIdentity
      |
Generate DeviceKey
      |
Generate opaque DeviceRecordCommitment
      |
protected storage / Android Keystore
      |
Generate Recovery Kit
      |
0 mandatory chain writes
```

Quando serve identità verificabile:

```text
anti-abuse proof
 -> sponsored registration when eligible
 -> RootIdentity + authorized device record
 -> READY
```

`My Freedom` non mostra un global device identifier come identità utente.

## A2 — Contact QR

Il contatto rappresenta una persona/RootIdentity.

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

Dopo il primo handshake:

```text
PairSecret
PairwiseContactAlias
PairRendezvousSecret
```

Alias differenti per contatti differenti.

## A3 — Rendezvous

```text
known route?
  yes -> connect
  no

read pairwise remote rendezvous
  found -> try, no write
  empty

read local rendezvous
  valid -> wait/poll
  empty -> publish one bounded offer
```

Gli slot derivano da `PairRendezvousSecret`, non da RootIdentity/device commitment pubblico.

## A4 — Secure session

```text
verify expected RootIdentity/contact
 -> verify DeviceKey authorization / epoch
 -> mutual authenticated handshake
 -> fresh session keys
 -> E2EE ACTIVE
```

Il transcript usa identity proof/alias pairwise e device authorization proof.

## A5 — Messaging sincrono

```text
SEND
  |
  +-- active authenticated session? -- no --> FAIL / DISCARD
  |
  `-- yes --> transmit --> ACK/session result
```

Nessun retry offline implicito e nessuna queue di delivery futura.

Una bozza UI locale non è una mailbox.

## A6 — Route maintenance

Durante una sessione:

```text
Wi-Fi/mobile change
candidate change
relay availability
transport health
        |
        v
RouteUpdate -> E2EE session
```

Nessuna write blockchain se almeno un path resta valido.

## A7 — NAT / transport fabric

Supporto progressivo:

```text
DIRECT
OBSERVED
UDP hole punching
RELAY
BRIDGE
SHIELDED / MULTI_HOP
PLUGGABLE / OBFUSCATED TRANSPORT
```

Il transport layer deve essere modulare:

```text
TransportAdapter
  connect()
  probe_capabilities()
  health()
  classify_failure()
  close()
```

Il core non deve dipendere da un singolo protocollo di rete.

## A8 — Relay mode

`DEVICE_RELAY` è opt-in.

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
- non riceve RootIdentity/device commitment quando non necessario;
- non diventa open proxy Internet;
- può qualificare al Relay Contributor benefit.

## A9 — Attachments

```text
active secure session
 -> encrypted chunks
 -> direct/relay/Shield transport
 -> receiver endpoint
```

La perdita route non trasforma il file in storage offline di rete.

## A10 — Voice/video

```text
CallInvite
CallAccept
CallCandidate
CallEnd
```

Signaling E2EE; media keys separate; path direct/relay/Shield compatibile.

## Network Indicator

Il client deve mostrare almeno:

```text
NORMAL
SHIELDED
DEGRADED
SUSPECTED
UNAVAILABLE
```

Advanced status può mostrare:

```text
current path
transport family
relay/bridge class
control-plane status
failure classification
active fallback
```

## Adaptive Defense

Dopo failure selettive:

```text
PATH_FAILURE
PROTOCOL_BLOCK_SUSPECTED
DPI_OR_FILTERING_SUSPECTED
BRIDGE_UNREACHABLE
CONTROL_PLANE_DEGRADED
```

Il client prova alternative bounded senza attribuire censura/sorveglianza a un attore specifico.

## Share Freedom

```text
Share Freedom
 -> Install QR
 -> peer / relay / mirror / store
 -> verify release/hash/signer
 -> Android installer
```

La Direct build può servire una cache APK standalone già verificata. Non incorporare per default una seconda copia dell'APK.

## Freedom Gateway — post-V1

Gateway è separato dalla chat Freedom e dal `DEVICE_RELAY`.

```text
Chrome / Firefox / selected apps
           |
           v
     Android VpnService
           |
     Freedom Gateway
           |
 path / transport selector
   |- relay
   |- bridge
   |- Shield / multi-hop
   `- alternate transport
           |
     explicit Egress
           |
        Internet
```

Modalità:

```text
OFF
SELECTED_APPS
WHOLE_DEVICE
```

Requisiti Android:

- consenso tunnel esplicito;
- selected-app allowlist quando richiesta;
- whole-device mode opzionale;
- DNS dentro tunnel quando la policy lo richiede;
- IPv4/IPv6 leak test;
- split tunneling visibile;
- kill-switch/strict mode opzionale;
- current egress/path status;
- nessun silent fallback direct in strict mode;
- conflitto con altro tunnel/VPN spiegato all'utente.

Il prodotto e la UI si chiamano **Freedom Gateway**. `VpnService` è la primitive Android, non il nome commerciale della funzione.

Il Gateway non integra un browser generalista: usa il browser/app già installato dall'utente.

Dettagli: [`docs/GATEWAY.md`](docs/GATEWAY.md).

## Gateway security boundary

```text
Freedom Communication
  endpoint-authenticated E2EE
  strongest communication boundary

Freedom Gateway
  encrypted path to egress
  application protocol remains responsible after egress
```

Non usare la UI "End-to-end encrypted by Freedom" per traffico Gateway generico.

Stati Gateway separati possibili:

```text
GATEWAY_OFF
GATEWAY_DIRECT_EGRESS
GATEWAY_SHIELDED
GATEWAY_DEGRADED
GATEWAY_FILTERING_SUSPECTED
GATEWAY_UNAVAILABLE
```

## Gateway UI / managed capacity

Vista semplice candidata:

```text
FREEDOM GATEWAY

Status             Protected
Mode               3 selected apps
Path               Shielded
Egress             CH / managed
Filtering          None
Managed capacity   82 MB / 100 MB today

[ Disconnect ]
[ Apps ]
[ Network details ]
```

Target Free iniziale, quando il managed Gateway sarà disponibile:

```text
100 MB / giorno di managed Gateway capacity
```

Questa quota:

- è separata dal traffico Freedom Communication;
- è separata da Emergency Shield Communication;
- non viene consumata da direct/community/private communication paths;
- può essere ricalibrata con costi reali;
- deve essere mostrata come **capacity state**, non come stato di sicurezza o censura.

Se la quota finisce:

```text
Managed Gateway capacity used for today
Freedom Communication remains available
Private/other eligible paths remain independent
```

Non mostrare `SUSPECTED` o `UNAVAILABLE` solo perché il budget managed è terminato.

Configurazione candidata:

```text
Freedom Gateway       ON/OFF
Mode                   Selected apps / Whole device
Protected apps         Chrome, Firefox, ...
Protection             Standard / Shielded / Maximum Reachability
DNS leak protection    ON
Kill switch            optional
Managed quota          used / remaining
Egress                 region / class
```

## Maximum Reachability — post-V1

Android deve poter supportare una policy avanzata:

```text
MAXIMUM_REACHABILITY
  maintain bounded alternative candidates
  try independent providers
  rotate transport family after filtering evidence
  use non-public bridge when available
  parallel connect within battery/data policy
  aggressive bounded failover
```

Nessun claim "passa tutti i firewall". Il test target è misurare quante classi di blocco reali vengono superate.

## Censorship / firewall test lab

Prima di claim pubblici forti creare test riproducibili:

```text
block known relay IPs
block one provider ASN
block UDP
block QUIC
block known protocol fingerprint
DNS poisoning/blocking
SNI/domain filtering
active probe simulated
high loss / latency / reordering
partial allowlist environment
RPC provider block
```

Acceptance criteria devono misurare:

- time-to-detect;
- time-to-failover;
- successful alternate transport rate;
- false positive rate;
- battery/data cost;
- inability to correlate identity through fallback artifacts.

## Module target

```text
app/
core/
  identity/
  protocol/
  session/
  routing/
  adaptive/
chain/
  ChainAdapter
  near/
transport/
  direct/
  nat/
  relay/
  bridge/
  pluggable/
gateway/
  android-vpn/
  tunnel/
  egress/
platform-android/
```

La separazione può essere introdotta gradualmente senza sovra-ingegnerizzare lo spike.

## Secure storage

Separare almeno:

- RootIdentity metadata/private material;
- device authorization metadata;
- contacts/pairwise aliases;
- pair rendezvous secrets;
- conversation data secondo policy;
- network cache;
- Gateway configuration;
- trusted release/update state.

Non loggare private/session/rendezvous/Gateway tunnel keys.

## Debug screen

```text
Root commitment hash abbreviated
Device record commitment hash abbreviated
Key epoch
Chain state/finality
RPC selected
Pairwise alias hash
Rendezvous slot hash / TTL
Known route candidates
Current path
Transport family
Relay / bridge / egress class
Session ID
TX/RX sequence
RTT
Failure classification
Gateway mode/status when active
Gateway quota used/remaining
```

## Build / CI

Minimo:

```text
assembleDebug
unit tests
protocol serialization tests
crypto vectors
transport adapter tests
lint
```

Successivamente:

```text
network namespace / emulator integration tests
DPI/firewall simulation
relay failover tests
Gateway DNS/leak tests
Gateway quota accounting tests
multi-device tests
fuzzing
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
A17 transport abstraction / bridge support
A18 store compliance polish

POST-V1 GATEWAY
G1  explicit egress protocol
G2  selected-app VpnService
G3  whole-device + DNS/leak controls
G4  managed quota accounting + 100 MB/day Free target
G5  egress failover / diversity
G6  shielded multi-hop Gateway
G7  pluggable anti-censorship transports
G8  bridge anti-enumeration
G9  DPI/firewall lab
G10 Maximum Reachability
```
