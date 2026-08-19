# Freedom — Launch Plan

Status: **canonical launch plan**.

Normative security: [`SECURITY_INVARIANTS.md`](SECURITY_INVARIANTS.md).
Control-plane: [`CONTROL_PLANE_SECURITY.md`](CONTROL_PLANE_SECURITY.md).
Revocation: [`REVOCATION.md`](REVOCATION.md).
Advanced development: [`ADVANCED_DEVELOPMENT.md`](ADVANCED_DEVELOPMENT.md).

## 1. Obiettivo

Prima del lancio pubblico Freedom Communication deve essere comprensibile, interoperabile e tecnicamente dimostrabile.

Claim vietati senza evidenza: “incensurabile”, “non tracciabile”, “passa ogni firewall”, “rileva la sorveglianza”, “root compromise sempre recuperabile”, “nessun singolo attore” se un singolo operator controlla il governance quorum.

## 2. Security gates prima dei creator pubblici

Blocker:

- canonical schema drift;
- ad-hoc/non-domain-separated signatures;
- DeviceCertificate non legato al contatto atteso;
- certificate scope/expiry > delegation parent;
- revocation/non-revocation oracle ambiguo;
- fresh install accetta checkpoint sotto BootstrapFreshnessFloor;
- rendezvous slot sovrascrivibile senza derived write key;
- active state non reclaimable;
- root-compromise claim senza UserRecoveryPolicy indipendente;
- pairwise backup rollback/post-restore future-state rotation non gestiti;
- offer stripping/downgrade;
- forward secrecy/rekey state-machine incomplete;
- stream/datagram semantic confusion;
- transaction Failure/state mismatch accettati;
- signer/policy/status rollback;
- single-key contract upgrade;
- governance quorum operationalmente centralizzato ma descritto come no single actor;
- chain migration senza StateMigrationProof;
- first sideload senza pinned trust/freshness anchor;
- `SHIELDED` claim senza vero circuit protocol;
- global DeviceID, public social/device graph o mailbox reintrodotti.

## 3. Simulation evidence prima del pilot

Prima di affidarsi a test Android end-to-end, il core deve avere almeno:

```text
L0 canonical/unit vectors
L1 deterministic multi-node virtual-time simulation
L2 network chaos for relevant routing features
L3 real ChainAdapter integration for control-plane features
```

Scenario minimi:

- stale/malicious/forked RPC;
- bootstrap old checkpoint;
- revocation freshness;
- rendezvous overwrite/front-run;
- storage stress;
- rekey simultaneous/lost/duplicate messages;
- NAT rebinding/path switch;
- relay Sybil/provenance;
- root compromise recovery race/timelock;
- release/signer rollback;
- StateMigrationProof valid/invalid.

## 4. Android gates

Android emulator/device restano obbligatori per Keystore, process death/restart, background/Doze, package signing/update, real network handover, camera/QR, permissions e `VpnService`.

## 5. Demo minima reale

```text
Alice                         Bob
  |                            |
  | Contact descriptor         |
  |---------- bootstrap ------>|
  | DeviceCertificate/revocation verify
  | both-offer handshake       |
  |<======== E2EE live =======>|
  |                            |
  | route changes              |
  | rekey remains coherent     |
  X session ends               X
```

Demo deve mostrare anche peer offline -> no delivery future.

## 6. Founder cohort / reviewers

Prima founder/power users, poi security/privacy reviewers indipendenti, poi creator pilot.

Materiale per reviewer:

- repo;
- `spec/freedom.cddl`;
- security/control-plane/revocation/identity/protocol/threat docs;
- test vectors;
- scenario evidence;
- build artifacts quando rilevanti;
- responsible disclosure.

## 7. Product quota disclaimer

`1 device / 10 contacts / +10 Relay Contributor` V1 è product/service policy, non protocol security enforcement.

Il lancio non deve vendere queste quote come anti-tamper guarantees. Il business model deve poggiare soprattutto su capacità/servizi realmente enforceable quando serve.

## 8. Governance readiness

Prima di una release production ufficiale:

```text
threshold signer sets configured
custody/operator domains documented
contract upgrade immutable-or-threshold path verified
quorum-loss recovery tested
bootstrap freshness floor current
```

## 9. Go / No-Go

No-Go se esiste un bug critico aperto su identity/revocation/session/release/control-plane verification o se una label production viene mostrata senza derived state verificato.

## 10. Public launch

Public launch può includere stable client, sito/docs, demo, repository/spec, responsible disclosure, comparison page e Share Freedom soltanto quando bootstrap verifier/trust/freshness sono pronti.

## 11. Principle

> **prima rendere Freedom verificabile e riproducibile, poi raccontabile, infine grande.**
