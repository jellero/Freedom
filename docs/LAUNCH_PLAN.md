# Freedom — Launch Plan

Status: **canonical launch plan**

Normative security rules: [`SECURITY_INVARIANTS.md`](SECURITY_INVARIANTS.md).
Control-plane security: [`CONTROL_PLANE_SECURITY.md`](CONTROL_PLANE_SECURITY.md).
Advanced development/testing: [`ADVANCED_DEVELOPMENT.md`](ADVANCED_DEVELOPMENT.md).

## 1. Obiettivo

Il lancio deve dimostrare che Freedom Communication è comprensibile, utilizzabile e tecnicamente credibile prima di scalare.

> **Freedom Communication — Powered by Freedom Protocol**
>
> Comunicazione privata live, autenticata E2EE, sincrona, senza mailbox e senza dipendenza permanente da un singolo server/percorso.

Claim vietati senza evidenza: impossibile da tracciare/bloccare, universal firewall bypass, anonimato garantito, rilevamento sorveglianza passiva.

## 2. Ordine narrativo

```text
live private communication
 -> no mailbox
 -> expected-contact E2EE
 -> offline-verifiable DeviceCertificate
 -> forward secrecy / rekey
 -> pairwise identity / no global DeviceID
 -> replaceable paths
 -> verified control-plane proofs
 -> threshold governance / anti-rollback
 -> NEAR as first ChainAdapter
```

## 3. Security blockers before public creators

- RootRecoveryKey/Recovery Kit non verificati end-to-end;
- Recovery Kit con entropy/KDF/AEAD insufficienti;
- DeviceAuthorizationDelegation/DeviceCertificate assenti;
- root compromise senza `UserRootRotation`;
- pairwise recovery non definito;
- first-contact substitution non gestibile con assurance state/safety code;
- handshake che accetta una key non legata al contatto atteso;
- offer stripping/downgrade non testato;
- FS/rekey/key lifetime non dimostrati;
- transport stream/datagram semantics confuse;
- security-sensitive RPC state accettato senza checkpoint/state proof;
- stale/forked/rollback state accettato;
- transaction Failure/state mismatch trattati come successo;
- public RootIdentity→device linkage presentato come privacy production;
- active temporary state non reclaimable/bounded;
- global DeviceID/mailbox/offline queue reintrodotti;
- relay diversity basata solo su self-declared IDs;
- `SHIELDED` claim senza true circuit protocol;
- signer-set rollback/recovery non testato;
- single-key contract upgrade production;
- first sideload senza pinned BootstrapTrustAnchor;
- release/policy/status proof/anti-rollback non fail-closed;
- payment→entitlement linkage diretto inutile quando voucher/nullifier flow è richiesto;
- social graph pubblicato per enforceare contact quota;
- merchant/infrastructure secrets nel client.

## 4. Demo minima reale

```text
Alice                         Bob
  |                            |
  | Contact descriptor         |
  |---------- bootstrap ------>|
  |                            |
  | expected-contact auth      |
  | DeviceCertificate verify   |
  |<======== E2EE live =======>|
  |                            |
  X session ends               X
```

Mostrare anche:

- `BOOTSTRAP_UNVERIFIED` vs verified contact assurance;
- peer offline -> no future delivery;
- nuova sessione -> new ephemeral material;
- route switch se implementato;
- no dependence on a single RPC;
- Share Freedom verification quando pronto.

## 5. Founder Cohort

20–50 technical/privacy users indicativi. Obiettivi: onboarding, identity assurance, recovery, NAT/network diversity, latency, relay behavior, Network Indicator, Share Freedom, claim clarity.

## 6. Security & Privacy Reviewers

Materiale minimo:

- `SECURITY_INVARIANTS.md`;
- `CONTROL_PLANE_SECURITY.md`;
- `IDENTITY_MODEL.md`;
- `ARCHITECTURE.md`;
- `PROTOCOL.md`;
- `CHAIN.md`;
- `THREAT_MODEL.md`;
- `SHIELD.md`;
- `APP_DISTRIBUTION.md`;
- `ADVANCED_DEVELOPMENT.md`;
- test vectors/scenario artifacts quando disponibili.

Reviewer liberi di pubblicare critiche.

## 7. Development evidence before launch

Il percorso principale deve passare almeno:

```text
unit/property vectors
multi-node container simulation
NAT rebinding/handover
relay block / provider block
stale/malicious RPC
checkpoint rollback
clock skew
storage reclaim stress
first-contact substitution
handshake downgrade
signer-set rollback
contract upgrade governance
release first-install attack
```

Poi Android emulator/physical-device gates per Keystore, lifecycle/background, package signing/update, real network handover e VpnService dove applicabile.

## 8. Privacy funnel

Solo telemetry minimale/aggregata/opt-in. Non raccogliere plaintext, social graph, pairwise aliases, device commitments peer, rendezvous content o identity-IP mappings non necessari.

## 9. Metrics

- first authenticated session success;
- contact assurance completion when requested;
- revocation/certificate behavior;
- rekey success;
- malicious/stale RPC rejection;
- storage convergence to bound;
- recovery/root rotation success;
- relay fallback/eclipsing resistance;
- release/governance rollback rejection;
- Network Indicator false positives.

## 10. Go / No-Go

Indicative product targets possono essere misurati, ma nessun numero sostituisce i security blockers sopra.

No-Go se esiste un bug critico aperto su identity, keys, proofs, recovery, storage bounds, governance, release verification o synchronous delivery semantics.

## 11. Public Launch

Stable release + public specs + responsible disclosure + demo + review evidence.

Share Freedom pubblico solo quando first-install/bootstrap/proof/anti-rollback gates sono pronti.

## 12. Monetization during launch

Free: 1 device, 10 **local product contact slots**, Communication core, Network Indicator, Emergency Shield bounded, Gateway target 100 MB/day quando disponibile.

Relay Contributor: +10 local product slots.

La quota contatti non è protocol-interoperability rule e non giustifica social-graph publication.

Paid tiers comprano capacità/convenience, non stronger peer identity/E2EE truth.

## 13. Gateway

Post-V1. Browser integrato non è requisito. Device relay non diventa Internet exit.

## 14. Governance readiness

Production:

```text
ReleaseAuthorization   >= 3-of-5
ReleaseRevocation      >= 3-of-5
CriticalSecurityPolicy >= 3-of-5
ContractUpgrade        >= 3-of-5 + timelock
GovernanceRootRotation >= 3-of-5 + recovery
```

Signer transition, quorum-loss recovery, code-hash lineage e rollback rejection devono essere testati.

## 15. North-star metric

> **nuovi utenti che completano con successo almeno una sessione autenticata Freedom con un altro contatto.**

## 16. Sequenza

```text
simulation/security gates
 -> Founder Cohort
 -> independent reviewers
 -> Creator Pilot
 -> public launch
 -> scale after reliability/security evidence
```

> **prima rendere Freedom dimostrabile, poi raccontabile, infine grande.**
