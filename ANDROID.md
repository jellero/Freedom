# Freedom Android

Status: **implementation roadmap for the canonical specification**

Normative security rules: [`docs/SECURITY_INVARIANTS.md`](docs/SECURITY_INVARIANTS.md).
Control-plane security: [`docs/CONTROL_PLANE_SECURITY.md`](docs/CONTROL_PLANE_SECURITY.md).
Advanced development method: [`docs/ADVANCED_DEVELOPMENT.md`](docs/ADVANCED_DEVELOPMENT.md).

Android è la prima piattaforma di prodotto, ma **non è il loop principale di sviluppo del protocollo**. Protocol, routing, control-plane verifier, release verifier e relay logic devono essere host-side/simulator-first; APK/emulator/device sono gate di integrazione piattaforma.

## 1. Identity stack

```text
RootRecoveryKey
RootIdentity
DeviceAuthorizationKey / delegation
DeviceCertificate
DeviceKey
DeviceRecordCommitment
PairwiseContactAlias
TransportToken
```

No global DeviceID.

## 2. Prima installazione

```text
Install Freedom
 -> generate RootRecoveryKey / RootIdentity
 -> generate DeviceAuthorizationKey + delegation
 -> generate DeviceKey
 -> opaque DeviceRecordCommitment
 -> RecoveryStateKey
 -> Recovery Kit
 -> 0 mandatory chain writes
```

## 3. Control-plane state

Quando serve stato verificabile:

```text
anti-abuse/device proof
 -> submit
 -> verify finality checkpoint
 -> verify execution
 -> verify resulting state proof
 -> DeviceCertificate / READY
```

`tx hash` o singola risposta RPC non significano `READY`.

## 4. DeviceCertificate

```text
DeviceCertificate {
    version
    network_id
    root_identity_commitment_or_proof
    authorization_epoch
    device_public_key
    key_epoch
    protocol_version
    capabilities?
    issued_at
    expires_at
    certificate_id
    authorization_signature
}
```

Verifica offline; revocation/freshness da cache/control-plane proof verificati.

## 5. Device authorization privacy

Production target: `DeviceAuthorizationProof`/slot nullifier senza mapping pubblico RootIdentity→device.

Se una build Testnet usa proof linkabile, debug/telemetry/documentazione devono dichiararlo esplicitamente.

## 6. Recovery

Recovery Kit usa >=128-bit recovery entropy, memory-hard KDF e AEAD.

Root compromise usa `UserRootRotation`; non è equivalente a device restore.

Pairwise state:

```text
surviving device transfer
or
encrypted PairwiseRecoveryBundle
```

Se manca pairwise backup, ownership torna ma i contatti richiedono re-bootstrap.

## 7. Contact bootstrap

```text
FreedomContact
 -> BOOTSTRAP_UNVERIFIED
 -> handshake
 -> optional safety code/fingerprint/out-of-band verification
 -> CONTACT_VERIFIED
```

Descriptor substitution prima del primo bootstrap è una minaccia distinta da QR copy/replay.

## 8. Secure session

```text
verify expected contact
 -> verify delegation + DeviceCertificate
 -> verify DeviceKey possession
 -> verified revocation/freshness
 -> bind both handshake offer sets
 -> strongest-allowed version/suite selection
 -> fresh ephemeral E2EE
```

## 9. Forward secrecy / rekey

- FS per sessione;
- traffic-key lifetime bounded per tempo/frame/byte;
- authenticated rekey;
- control/messaging keys separate da media keys;
- failure esplicita se rekey required fallisce.

## 10. Transport semantics

Ogni adapter dichiara:

```text
RELIABLE_ORDERED_STREAM
UNRELIABLE_DATAGRAM
```

Text/control/rekey usano reliable semantics o reliability layer esplicito. Media può usare datagram separati senza bloccare chat/control.

## 11. Messaging sincrono

```text
active authenticated session? yes -> transmit
active authenticated session? no  -> FAIL / DISCARD
```

No offline retry queue.

## 12. Rendezvous / storage

Pairwise slots, read-before-write, bounded size/TTL.

TTL non basta: il contract/runtime deve implementare overwrite/ring/prune/lease/reclaim e active-state bound.

## 13. Relay mode

`DEVICE_RELAY` opt-in/resource-bounded. RelayDescriptor firmato, provenance-aware selection, no assumption `N relay IDs = N operators`, no Internet egress.

## 14. Shield

Production `SHIELDED` richiede [`docs/SHIELD.md`](docs/SHIELD.md): real circuit setup, per-hop keys, layered forwarding, Sybil/provenance tests.

## 15. Network Indicator / Adaptive Defense

```text
NORMAL
SHIELDED
DEGRADED
SUSPECTED
UNAVAILABLE
```

`SUSPECTED` è inferenza, non sorveglianza/censura provata.

## 16. Share Freedom

```text
Install QR
 -> untrusted bytes
 -> exact hash
 -> verified signer-set epoch/transition
 -> threshold FreedomRelease
 -> Android signer lineage
 -> ReleaseStatus/SecurityPolicy proof
 -> installer
```

First sideload usa BootstrapTrustAnchor pinned.

## 17. Governance client state

Android conserva anti-rollback state:

```text
highest_verified_checkpoint
highest_signer_set_epoch
highest_policy_epoch
highest_release_status_epoch
accepted_contract_lineage
```

Wall clock locale non può riattivare policy/certificati vecchi; usare VerifiedTimeAnchor/height/epoch.

## 18. Gateway — post-V1

```text
apps -> VpnService -> Freedom Gateway -> path/Shield -> explicit Egress -> Internet
```

DNS/leak controls, visible split tunnel, strict/kill-switch, no silent direct fallback.

Target Free managed Gateway iniziale: `100 MB/day`, separato da Communication/Emergency Shield.

## 19. Simulator-first development

Loop primario:

```text
core source
 -> host-side compile/test
 -> multi-process/container simulation
 -> NAT/firewall/RPC/clock/storage chaos
 -> regression matrix
```

Non produrre/installare APK per ogni iterazione protocol/control-plane.

Docker/Codex lab dettagliato in [`docs/ADVANCED_DEVELOPMENT.md`](docs/ADVANCED_DEVELOPMENT.md).

## 20. Quando Android è obbligatorio

APK/emulator/physical device gate per:

- Android Keystore;
- process death/restart;
- lifecycle/background/Doze;
- permissions/notifications;
- package signing/update;
- camera/QR;
- real network handover;
- `VpnService`;
- vendor-specific socket/background behavior.

## 21. Module target

```text
app/
core/
  identity/
  protocol/
  session/
  routing/
  controlplane/
  release/
transport/
  direct/
  nat/
  relay/
  bridge/
  shield/
sim/
  node/
  chain/
  nat/
  censor/
  clock/
  scenario/
  oracle/
gateway/
  android-vpn/
  tunnel/
  egress/
platform-android/
```

## 22. Test gates

Host/sim first:

```text
canonical vectors
DeviceCertificate/delegation tests
privacy authorization proof tests
first-contact substitution
handshake offer-stripping/downgrade
FS/rekey/replay
stream/datagram reorder/loss
checkpoint/state proof
malicious/stale/forked RPC
storage reclaim convergence
RootRotation/pairwise recovery
relay Sybil/eclipse
Shield circuits
release/signer/contract rollback
payment voucher/nullifier
```

Android gates:

```text
Keystore instrumentation
process/background tests
network handover
package signing/update
camera/QR
VpnService DNS/IPv4/IPv6 leaks
```

## 23. Roadmap

```text
A1  core identity hierarchy + Recovery Kit
A2  DeviceAuthorizationProof / DeviceCertificate
A3  control-plane checkpoint/state verifier
A4  pairwise contact + first-contact verification UX
A5  pairwise recovery / multi-device
A6  anti-downgrade authenticated session
A7  FS/rekey + transport semantic split
A8  synchronous text/ACK
A9  route/NAT simulator
A10 RelayDescriptor / DEVICE_RELAY
A11 attachments/media
A12 Shield circuit prototype + simulator
A13 Share Freedom + governance rollback protection
A14 Adaptive Defense / Network Indicator
A15 Android integration gates

POST-V1 GATEWAY
G1 explicit egress
G2 selected-app VpnService
G3 whole-device + DNS/leak controls
G4 managed quota
G5 egress/transport diversity
G6 Shielded Gateway
G7 anti-censorship transports/bridges
G8 DPI/firewall lab
G9 Maximum Reachability
```
