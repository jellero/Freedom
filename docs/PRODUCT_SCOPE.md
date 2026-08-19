# Freedom — Product Scope

Status: **canonical product scope**

Normative security rules: [`SECURITY_INVARIANTS.md`](SECURITY_INVARIANTS.md).
Control-plane security: [`CONTROL_PLANE_SECURITY.md`](CONTROL_PLANE_SECURITY.md).
Advanced development: [`ADVANCED_DEVELOPMENT.md`](ADVANCED_DEVELOPMENT.md).

## 1. Obiettivo

Freedom Communication deve dimostrare: **comunicazione privata live, autenticata E2EE, sincrona, senza mailbox centrale e senza dipendenza permanente da un singolo percorso/provider.**

> **Semplice quando tutto funziona. Trasparente quando qualcosa cerca di impedirti di comunicare.**

## 2. Launch scope — V1

La prima release pubblica è focalizzata sul **1:1**.

Funzioni essenziali:

- RootRecoveryKey/RootIdentity inizializzate localmente;
- DeviceAuthorizationDelegation;
- DeviceKey + DeviceRecordCommitment opaco;
- DeviceCertificate verificabile offline;
- Recovery Kit con KDF memory-hard + AEAD + >=128-bit recovery entropy;
- registrazione sponsorizzata quando serve, senza wallet NEAR obbligatorio;
- verified checkpoint/state proof per stato control-plane security-sensitive;
- verified finality/state per mutazioni;
- contatto-persona via QR/link;
- `BOOTSTRAP_UNVERIFIED` / safety-code verification opzionale;
- pairwise identity/rendezvous;
- pairwise state recovery via device transfer o encrypted bundle;
- expected-contact authenticated handshake;
- handshake offer binding / anti-downgrade;
- forward secrecy tra sessioni;
- bounded key lifetime + rekey;
- transport semantic separation stream/datagram;
- 1 active device Free;
- 10 active contacts Free come **product policy del client ufficiale**;
- synchronous 1:1 text/media/file/voice/video;
- Live mode;
- relay forward-only;
- RelayDescriptor/provenance-aware selection;
- device relay opt-in;
- Relay Contributor +10 product contact slots;
- Adaptive Defense base;
- Network Indicator;
- Emergency Shield bounded;
- Share Freedom / Install QR;
- BootstrapTrustAnchor pinned per first sideload;
- threshold-verified release/security policy;
- signer-set anti-rollback;
- contract upgrade governance threshold/timelock oppure security core immutable;
- block/report/store-compliance essentials.

## 3. Identity / recovery

```text
RootRecoveryKey
 -> DeviceAuthorizationDelegation
 -> privacy-preserving DeviceAuthorizationProof
 -> DeviceRecordCommitment + DeviceKey
 -> verified activation
 -> DeviceCertificate
```

Restore:

```text
Recovery Kit
 -> RootIdentity/root epoch
 -> NEW DeviceAuthorizationKey if needed
 -> NEW DeviceKey
 -> verified activation
 -> NEW DeviceCertificate
 -> entitlement restore
 -> PairwiseRecoveryBundle/device transfer when available
```

Root compromise usa `UserRootRotation`, non il semplice restore della stessa root.

## 4. Contact assurance

Un contatto rappresenta una persona/RootIdentity, non ogni device.

Prima del bootstrap un descriptor sostituito può collegare l'utente all'attaccante. Il client deve poter mostrare safety code/fingerprint e distinguere contatto verificato da bootstrap non indipendentemente verificato.

Pairwise alias non viene descritto come unlinkability assoluta contro contatti colludenti.

## 5. Session security gate

Prima del V1 pubblico:

```text
expected-contact authentication
DeviceCertificate/delegation validation
DeviceKey possession proof
verified revocation/freshness
forward secrecy
bounded traffic-key lifetime
rekey
replay protection
both-offer-set downgrade protection
control/media sequence-space separation
```

## 6. Synchronous semantics

```text
active authenticated session -> transmit now
no active authenticated session -> fail/discard
```

Vietati mailbox on-chain, relay inbox persistente, automatic offline retry queue e store-and-forward.

## 7. Contatti Free / Relay Contributor

```text
FREE                     10 product contact slots
FREE + RELAY CONTRIBUTOR 20 product contact slots
```

V1: enforcement nel client ufficiale, rubrica locale/cifrata, nessun social graph pubblicato per enforcement commerciale. La quota non è una regola di interoperabilità tra client.

## 8. Control-plane gate

Security-sensitive read:

```text
NetworkAnchor
 -> VerifiedControlPlaneCheckpoint
 -> state proof
 -> canonical object
```

Security-sensitive write:

```text
submit
 -> finality proof
 -> execution success
 -> resulting state proof
 -> exact transition
 -> UX/local state
```

RPC response o tx hash da soli non bastano.

## 9. Storage gate

Ogni temporary control-plane record implementa reclaim/overwrite/ring/lease concreto.

Blocker:

> active state che cresce indefinitamente perché ogni epoch crea una nuova map key senza prune/reclaim.

## 10. Governance gate

Prima della production:

```text
ReleaseAuthorization   >= 3-of-5
ReleaseRevocation      >= 3-of-5
CriticalSecurityPolicy >= 3-of-5
ContractUpgrade        >= 3-of-5 + timelock
GovernanceRootRotation >= 3-of-5 + recovery
```

Signer-set transitions sono monotonic/cross-authorized e highest-seen state impedisce rollback.

## 11. Device Relay

Device relay opt-in/resource-bounded; non possiede plaintext/session keys e non diventa Internet egress.

Relay diversity usa provenance/observed metadata; `N relay IDs` non viene assunto come `N independent operators`.

## 12. Shield

Freedom Shield forte è post-core fino a implementazione di [`SHIELD.md`](SHIELD.md): circuit setup, per-hop keys, layered forwarding, Sybil/provenance tests.

La label production `SHIELDED` non appare prima di quei gate.

## 13. Share Freedom / release security

```text
source bytes
 -> exact SHA-256
 -> threshold FreedomRelease
 -> signer-set transition/epoch anti-rollback
 -> Android signer lineage
 -> ReleaseStatus proof
 -> SecurityPolicy proof
 -> install
```

First sideload usa pinned BootstrapTrustAnchor indipendente dalla source.

## 14. Payment / entitlement

Payment flow preferito:

```text
PaymentAttestation
 -> one-time EntitlementVoucher / blind credential
 -> redemption nullifier
 -> verified entitlement transition
```

Timing correlation può restare e non viene negata.

## 15. Adaptive Defense / Network Indicator

```text
NORMAL
SHIELDED
DEGRADED
SUSPECTED
UNAVAILABLE
```

`SUSPECTED` è inferenza di rete, non prova di censura/sorveglianza.

## 16. Gateway — post-V1

```text
app -> local Gateway -> Freedom path -> explicit Egress -> Internet
```

Target Free iniziale managed Gateway: `100 MB/day`, separato da Communication/Emergency Shield.

`DEVICE_RELAY` non diventa egress.

## 17. Cosa NON blocca V1

Non sono prerequisiti: groups, group media, communities, bots, mailbox/cloud history, full Shield multi-hop production, full Maximum Resilience, advanced padding, tokenized relay economy, embedded browser, whole-device Gateway.

## 18. Roadmap

```text
V1
  identity/root/delegation/recovery
  DeviceCertificate + verified control-plane proofs
  pairwise contact/recovery
  anti-downgrade authenticated session
  FS/rekey/transport semantics
  1:1 communication
  relay/device relay + provenance
  Adaptive Defense base
  Share Freedom + threshold governance

V1.5
  Live Groups

V2
  scalable multi-party media

Pro/Shield evolution
  true Shield circuit protocol
  Always-Shielded
  Maximum Resilience

Post-V1 Gateway
  explicit egress
  selected-app/whole-device
  managed quota
  Maximum Reachability
```

## 19. Launch blockers

- control-plane state accettato da RPC senza proof;
- link pubblico RootIdentity→device presentato come privacy production;
- active temporary state non reclaimable/bounded;
- RootRecoveryKey usata come daily operational key;
- root compromise senza `UserRootRotation`;
- pairwise recovery non definito;
- first-contact substitution non gestibile/verificabile;
- handshake offer stripping/downgrade non testato;
- stream/datagram semantics confuse;
- transaction Failure/state mismatch accettati;
- signer-set rollback possibile;
- single-key contract upgrade production;
- Recovery Kit brute-forceable;
- global DeviceID reintrodotto;
- mailbox/offline queue reintrodotta;
- release first-install senza independent pinned anchor;
- Relay diversity basata solo su self-declared IDs;
- claim `SHIELDED` prima del vero circuit protocol;
- merchant/payment identity linkata inutilmente all'entitlement;
- social graph pubblicato per enforcement della quota contatti.
