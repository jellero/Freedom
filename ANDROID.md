# Freedom Android

Status: **implementation roadmap for the canonical specification**

Normative security rules: [`docs/SECURITY_INVARIANTS.md`](docs/SECURITY_INVARIANTS.md).

Android è la prima piattaforma di implementazione. Questo documento descrive il target architetturale, non attribuisce automaticamente al codice corrente le proprietà della specifica.

## 1. Identity stack

```text
RootIdentity
DeviceCertificate
DeviceKey
DeviceRecordCommitment
PairwiseContactAlias
TransportToken
```

Non esiste un `DeviceID` globale richiesto dal client o dal wire protocol.

## 2. Prima installazione

```text
Install Freedom
 -> generate RootIdentity
 -> generate DeviceKey
 -> generate opaque DeviceRecordCommitment
 -> secure storage / Android Keystore
 -> generate Recovery Kit
 -> 0 mandatory chain writes
```

Quando serve stato verificabile:

```text
anti-abuse proof
 -> sponsored registration/activation
 -> wait finality
 -> verify execution/resulting state
 -> issue/use DeviceCertificate
 -> READY
```

`tx hash` non significa `READY`.

## 3. DeviceCertificate

Android deve gestire e verificare:

```text
DeviceCertificate {
    version
    network_id
    root_identity_commitment_or_proof
    device_public_key
    key_epoch
    protocol_version
    capabilities?
    issued_at
    expires_at
    certificate_id
    root_authorization_signature
}
```

Il certificato consente handshake offline-verifiable. Chain/cache serve per revocation/freshness, senza RPC obbligatoria nel packet hot path.

## 4. Contact QR / pairwise identity

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

Dopo handshake:

```text
PairSecret
PairwiseContactAlias
PairRendezvousSecret
```

Alias differenti per relazioni differenti.

## 5. Secure session

```text
verify expected RootIdentity/contact
 -> verify DeviceCertificate
 -> verify DeviceKey possession
 -> apply revocation/freshness policy
 -> authenticated ephemeral key exchange
 -> E2EE ACTIVE
```

Il transcript lega pairwise context, certificate proof/hash, epochs, ephemeral keys, nonces, suite e session ID.

È vietato accettare una chiave semplicemente perché firma correttamente se non è legata al contatto atteso.

## 6. Forward secrecy / rekey

Android deve implementare:

- ephemeral key exchange per nuova sessione;
- forward secrecy tra sessioni;
- traffic-key lifetime bounded per tempo/frame/byte;
- authenticated rekey prima dei limiti;
- messaging/media keys separate;
- failure esplicita se un rekey obbligatorio non può essere completato.

Ratchet standard/reviewato: target successivo per post-compromise security.

## 7. Messaging sincrono

```text
SEND
  |
  +-- active authenticated session? -- no --> FAIL / DISCARD
  |
  `-- yes --> transmit --> ACK/session result
```

Nessun retry offline implicito e nessuna queue di delivery futura.

## 8. Rendezvous

```text
known route?
  yes -> connect
  no

read pairwise remote rendezvous
  usable -> try, no write
  empty

read local rendezvous
  valid -> wait/poll
  empty -> publish one bounded offer
```

Slot derivati da `PairRendezvousSecret`.

## 9. Route / transport

```text
DIRECT
OBSERVED
NAT_TRAVERSAL
RELAY
BRIDGE
SHIELDED / MULTI_HOP
PLUGGABLE / OBFUSCATED TRANSPORT
```

```text
TransportAdapter
  connect()
  probe_capabilities()
  health()
  classify_failure()
  close()
```

## 10. Relay mode

`DEVICE_RELAY` è opt-in e resource-bounded:

```text
relay_enabled
wifi_only
charging_only
battery_minimum
metered_network_allowed
max_bandwidth
max_concurrent_circuits
max_memory
max_cpu
background_policy
```

Relay mode:

- non salva conversazioni;
- usa circuit token temporanei;
- non riceve identity globali quando non necessari;
- non diventa open Internet proxy;
- può qualificare Relay Contributor.

## 11. Attachments / voice / video

Trasferimento solo dentro sessione/route attiva.

Voice/video:

```text
CallInvite
CallAccept
CallCandidate
CallEnd
```

Signaling E2EE; media keys separate e soggette a key lifetime/rekey.

## 12. Network Indicator / Adaptive Defense

```text
NORMAL
SHIELDED
DEGRADED
SUSPECTED
UNAVAILABLE
```

`SUSPECTED` deriva da osservazioni/inferenze implementate; non significa “sei monitorato”.

Fallback possibili:

```text
same transport / different endpoint
 -> different provider
 -> relay / bridge
 -> different transport family
 -> Shield / multi-hop
```

## 13. Share Freedom / first sideload

```text
Share Freedom
 -> Install QR
 -> peer / relay / mirror / store
 -> exact artifact hash
 -> threshold FreedomRelease signatures
 -> Android signer lineage
 -> ReleaseStatus / SecurityPolicy
 -> installer
```

Per il primo sideload il `Freedom Bootstrap Verifier` usa root pinned:

```text
expected_package_id
release_signer_set_root_commitment
android_signing_root_or_lineage_anchor
minimum verifier policy
```

Il peer/QR non può ridefinire queste root.

## 14. Freedom Gateway — post-V1

Gateway è separato da Freedom Communication e `DEVICE_RELAY`.

```text
Chrome / Firefox / selected apps
 -> Android VpnService
 -> Freedom Gateway
 -> path / transport selector
 -> explicit Egress
 -> Internet
```

Modalità:

```text
OFF
SELECTED_APPS
WHOLE_DEVICE
```

Requisiti:

- consenso esplicito;
- DNS/leak controls;
- split tunneling visibile;
- kill-switch/strict mode;
- no silent fallback direct in strict mode;
- current egress/path status;
- `DEVICE_RELAY != INTERNET_EGRESS`.

Target Free managed Gateway iniziale:

```text
100 MB / giorno
```

Quota separata da Freedom Communication ed Emergency Shield.

## 15. Maximum Reachability / censorship lab

Policy futura:

```text
MAXIMUM_REACHABILITY
  bounded warm alternatives
  independent providers
  transport-family rotation
  non-public bridge when available
  parallel connect within resource policy
  aggressive bounded failover
```

Test riproducibili:

- block known relay IPs;
- block one provider ASN;
- block UDP/QUIC;
- protocol fingerprint block;
- DNS/SNI filtering;
- active-probe simulation;
- loss/latency/reordering;
- partial allowlist;
- RPC/provider block.

Nessun claim “passa tutti i firewall”.

## 16. Secure storage

Separare almeno:

- RootIdentity/private material;
- DeviceKey / DeviceCertificate;
- device authorization state;
- pairwise contacts/rendezvous secrets;
- session state secondo policy;
- network cache;
- Gateway config;
- release/signer-set/SecurityPolicy state.

Non loggare private/session/rendezvous/Gateway tunnel keys.

## 17. Module target

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
release/
  verifier/
  bootstrap/
platform-android/
```

## 18. Test gates

Minimo prima dell'interoperabilità pubblica:

```text
assemble
unit tests
protocol serialization/test vectors
DeviceCertificate positive/negative tests
expected-contact handshake negative tests
forward-secrecy/rekey tests
replay/downgrade tests
control-plane Failure/finality/state-mismatch tests
Keystore instrumentation tests
release/bootstrap verification tests
transport resource-bound tests
```

Successivamente:

```text
DPI/firewall simulation
relay failover/load tests
Gateway DNS/leak tests
multi-device/recovery tests
fuzzing
```

## 19. Roadmap Android

```text
A1  RootIdentity + DeviceKey + opaque device record
A2  Recovery Kit + sponsored registration
A3  DeviceCertificate offline-verifiable
A4  NearChainAdapter + verified finality/state
A5  person/contact QR + pairwise alias
A6  device rotation/revocation/freshness
A7  pairwise rendezvous + read-before-write
A8  expected-contact authenticated session
A9  forward secrecy + bounded key lifetime/rekey
A10 synchronous text + ACK
A11 route update / NAT
A12 relay / DEVICE_RELAY
A13 attachments
A14 voice/video
A15 Share Freedom + BootstrapTrustAnchor
A16 Network Indicator / Adaptive Defense
A17 transport abstraction / bridge support
A18 store/direct release polish

POST-V1 GATEWAY
G1  explicit egress protocol
G2  selected-app VpnService
G3  whole-device + DNS/leak controls
G4  managed quota + 100 MB/day Free target
G5  egress failover/diversity
G6  shielded multi-hop Gateway
G7  pluggable anti-censorship transports
G8  bridge anti-enumeration
G9  DPI/firewall lab
G10 Maximum Reachability
```
