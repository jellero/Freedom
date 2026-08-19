# Freedom Android

Status: **implementation roadmap for the canonical specification**.

Normative security: [`docs/SECURITY_INVARIANTS.md`](docs/SECURITY_INVARIANTS.md).
Control-plane: [`docs/CONTROL_PLANE_SECURITY.md`](docs/CONTROL_PLANE_SECURITY.md).
Revocation: [`docs/REVOCATION.md`](docs/REVOCATION.md).
Canonical schema: [`spec/freedom.cddl`](spec/freedom.cddl).
Advanced development: [`docs/ADVANCED_DEVELOPMENT.md`](docs/ADVANCED_DEVELOPMENT.md).

Android è la prima piattaforma client, ma il loop principale di protocol/control-plane/routing è simulator-first.

## 1. Identity stack

```text
RootRecoveryKey
UserRecoveryPolicy
DeviceAuthorizationKey / Delegation
DeviceCertificate
DeviceKey
DeviceRecordCommitment
DeviceControlKey
PairwiseContactAlias
TransportToken
```

No global DeviceID network-facing.

## 2. Installazione

```text
install
 -> local root/authorization/device/control keys
 -> Recovery Kit
 -> 0 mandatory chain writes
```

Quando serve un opaque device record:

```text
anti-abuse/sponsorship
 -> submit
 -> finality proof
 -> resulting-state proof
 -> record available
```

Peer authorization continua a dipendere dal DeviceCertificate, non dal fatto che il record esista.

## 3. Canonical encoding / signing

Android deve usare `spec/freedom.cddl` + deterministic canonical encoding e signing domains. Niente JSON serialization ad-hoc per oggetti firmati.

## 4. DeviceCertificate / revocation

Validation:

```text
expected contact
 -> delegation scope/expiry
 -> certificate binding
 -> DeviceKey possession
 -> highest-seen epochs
 -> current-enough revocation proof
 -> authenticated
```

`RPC not found` non è non-revocation proof.

## 5. Root compromise

Normal restore e root compromise sono UX/state machine separate.

Compromise recovery è disponibile soltanto se esiste `UserRecoveryPolicy` indipendente precommitted.

## 6. Contact / pairwise state

UI distingue `BOOTSTRAP_UNVERIFIED` / `CONTACT_VERIFIED`.

Pairwise rendezvous usa derived one-time write keys e signed generation-monotonic records.

## 7. Pairwise backup

Android può esportare/importare encrypted `PairwiseRecoveryBundle` tramite user-chosen storage. Dopo restore re-authenticate peer e rotate/re-derive future rendezvous/session state.

## 8. Session

```text
both-offer anti-downgrade
 -> fresh ephemeral exchange
 -> E2EE
 -> bounded key lifetime
 -> RekeyInit / RekeyCommit / RekeyAck
```

Simultaneous/lost/duplicate rekey cases devono essere gestiti dalla state machine canonica.

## 9. Messaging semantics

```text
active authenticated session -> send now
no active session -> fail/discard
```

No offline delivery queue.

## 10. Route / relay / Shield

Adapters dichiarano stream/datagram semantics. Device relay è opt-in/resource-bounded e non Internet egress.

`SHIELDED` può apparire solo dopo vero circuit setup/per-hop keys/layered forwarding.

## 11. Adaptive Defense

`SUSPECTED` deriva da osservazioni/inferenze e non significa surveillance/censorship attribution.

## 12. Share Freedom

Verification:

```text
exact artifact hash
threshold release
verified ReleaseStatus/SecurityPolicy
Android signer lineage
BootstrapTrustAnchor
BootstrapFreshnessFloor
```

## 13. Gateway — post-V1

Android `VpnService` è platform integration per selected-app/whole-device Gateway. Richiede DNS/leak/strict-mode/device tests separati.

## 14. Simulator-first modules

Target:

```text
core/
  identity/
  protocol/
  session/
  routing/
  controlplane/
  release/

sim/
  node/
  chain/
  relay/
  nat/
  censor/
  clock/
  scenario/

platform-android/
  keystore/
  lifecycle/
  vpn/
```

La business logic non deve vivere esclusivamente in Activity/UI code se deve essere riusabile dal simulator.

## 15. Test levels

```text
L0 canonical/unit vectors
L1 deterministic virtual-time multi-node
L2 Docker/network chaos
L3 real NearChainAdapter integration
L4 Android emulator
L5 physical Android + real networks
L6 external review
```

## 16. Android-specific gates

Emulator/device obbligatorio per:

- Keystore;
- process death/restart;
- lifecycle/background/Doze;
- permissions;
- package signing/update;
- actual Wi-Fi/mobile handover;
- camera/QR;
- `VpnService`;
- vendor-specific behavior.

## 17. Core security tests

- deterministic encoding/signing domains;
- delegation/certificate scope/expiry;
- revocation proof/freshness;
- fresh-install bootstrap floor;
- rendezvous overwrite/front-run;
- expected-contact/offer-strip handshake;
- rekey simultaneous/loss/duplicate/route-switch;
- stream/datagram separation;
- storage bound;
- root compromise recovery;
- release/governance rollback;
- StateMigrationProof.

## 18. Roadmap

```text
A1  extract host-side core + canonical schema codec
A2  RootRecoveryKey / authorization / DeviceKey / DeviceControlKey
A3  Recovery Kit + optional UserRecoveryPolicy
A4  opaque device record + verified NearChainAdapter
A5  DeviceCertificate + revocation/freshness
A6  contact bootstrap / assurance states
A7  pairwise rendezvous write authorization
A8  pairwise backup/restore
A9  expected-contact both-offer handshake
A10 forward secrecy + complete rekey state machine
A11 synchronous text/ACK
A12 transport semantic abstraction + NAT/route switch
A13 relay / Device Relay
A14 attachments/media
A15 Share Freedom bootstrap trust/freshness
A16 Adaptive Defense / Network Status
A17 bridge/pluggable transport
A18 release/store/direct polish

POST-V1
S1 true Shield circuit protocol
G1 explicit Gateway egress
G2 selected-app VpnService
G3 whole-device + DNS/leak controls
G4 managed capacity accounting
G5 transport/egress diversity
G6 Maximum Reachability
```
