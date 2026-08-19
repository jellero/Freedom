# Freedom — Advanced Development Method

Status: **development methodology / internal engineering documentation**.

Questo documento definisce il loop di sviluppo avanzato di Freedom. Non appartiene al README prodotto e non descrive una garanzia del protocollo.

## 1. Principio

Il loop principale non dipende dalla compilazione/installazione continua dell'APK Android.

```text
PROTOCOL / CONTROL-PLANE / ROUTING
 -> host-side core
 -> deterministic simulation
 -> containers/processes
 -> scenario tests

ANDROID PLATFORM INTEGRATION
 -> emulator/device gates
 -> Keystore/lifecycle/VpnService/platform behavior
```

Il core deve comunque essere compilato/eseguito nel proprio runtime; semplicemente non serve produrre/installare l'APK a ogni iterazione.

## 2. Obiettivo laboratorio

Simulare automaticamente:

```text
Alice / Bob endpoints
Relay A / Relay B
Bridge
Gateway Egress
Honest RPC
Stale RPC
Malicious RPC
Control-plane mock
Censor/firewall
NAT A / NAT B
Clock-fault node
Release mirror
```

## 3. Codex come orchestratore

Codex può leggere specifiche, implementare, generare test, eseguire scenario matrix, analizzare failure, patchare e rieseguire regressioni.

Codex non è trust authority. Test generati dallo stesso agente richiedono oracle derivati dalla specifica, review separata e test vector indipendenti per primitive security-sensitive.

## 4. Normative-spec human gate

Gli agenti possono proporre cambiamenti alla specifica ma non devono autonomamente indebolire:

- MUST/MUST NOT;
- trust assumptions;
- signing domains;
- canonical signed schemas;
- revocation/freshness semantics;
- recovery/governance/rekey state machines.

Queste modifiche richiedono human review esplicita prima di essere considerate canonical/main-ready.

## 5. Lavoro parallelo

Usare worktree/branch isolate:

```text
agent/protocol
agent/control-plane
agent/identity
agent/relay
agent/chaos-lab
agent/release-security
agent/test-oracle
agent/reviewer
```

Reviewer agent prima produce criticità/failure riproducibili; fixer agent separato applica la patch.

## 6. Canonical schema

`spec/freedom.cddl` è la source of truth dei field name/object shape.

Test devono verificare deterministic encoding + signing domain. Evitare simulator-specific struct divergenti.

## 7. Target modulare

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
  oracle/

platform-android/
  keystore/
  lifecycle/
  vpn/
```

Il simulatore riusa le stesse state machine/serialization production dove possibile.

## 8. Docker/runner safety

Non dare a Codex il Docker socket di una workstation sensibile.

Preferire:

```text
disposable VM
isolated CI runner
rootless container runtime
nested disposable lab host
```

`/var/run/docker.sock` equivale praticamente a un potere host molto elevato e non viene montato in agent containers salvo che l'host stesso sia disposable e privo di secret/dati importanti.

Production/mainnet/release secrets non sono presenti nel laboratorio.

## 9. Docker topology target

```text
freedom-alice
freedom-bob
relay-a
relay-b
bridge-a
egress-a
rpc-honest
rpc-stale
rpc-malicious
control-plane-mock
chaos-proxy
release-mirror
```

## 10. Due livelli di tempo

### L1 deterministic virtual time

State-machine/scenario tests usano event scheduler/virtual clock.

Esempio DSL:

```yaml
- at: 10s
  action: block
  target: relay_a
```

`10s` qui è **tempo virtuale**, non sleep reale.

Questo rende deterministicamente testabili timeout, rekey, expiry, retry e race.

### L2 real network time

`tc netem`, namespace, nftables/proxy fault injection usano tempo reale e validano integrazione socket/kernel.

Non usare timing reale come unico oracle dei test state-machine.

## 11. Network chaos

Strumenti possibili:

- Linux network namespaces;
- `tc netem`;
- `nftables`/`iptables`;
- conntrack/NAT rules;
- Toxiproxy-like fault injection;
- DNS test resolver;
- TLS/reverse-proxy endpoints.

## 12. NAT scenarios

```text
open/public
full-cone-like
restricted
port-restricted
symmetric-like
mapping change mid-session
Wi-Fi -> mobile
NAT rebinding
hairpin unavailable
```

Docker NAT non sostituisce CGNAT/mobile reali; L5 device/network testing resta necessario.

## 13. Censorship scenarios

```text
block relay IP
block provider subnet
block UDP/QUIC-like
block port
DNS failure/poisoning
SNI/domain filtering
reset after fingerprint
throttle/jitter/loss
active-probe bridge
allowlist-only environment
```

Policy deve poter cambiare durante sessione.

## 14. Control-plane adversarial scenarios

```text
honest finalized state
stale state
fork/non-final checkpoint
invalid inclusion/non-inclusion proof
fresh-install checkpoint below BootstrapFreshnessFloor
valid old signer/policy/status
submitted-but-failed tx
partial execution
state mismatch
RPC omission/conflict
all RPC unavailable
```

## 15. Revocation tests

Simulare:

- active device;
- revoked device key epoch;
- revoked authorization epoch;
- root rotation;
- RPC `not found` without proof;
- stale revocation cache;
- highest-seen rollback;
- fresh install with stale checkpoint.

## 16. Rendezvous attack tests

Simulare:

```text
first valid write
attacker observes slot
attacker overwrite without write private key
front-run malformed record
replay generation N
rollback generation
expired-slot rewrite
legitimate generation increment
```

Oracle: soltanto la derived pairwise write key autorizza write/update.

## 17. Storage simulation

Migliaia/milioni logici di create/renew/expire/prune/overwrite.

Assert active storage bound, bounded refunds/bounties e assenza message/media state.

## 18. Identity/recovery simulation

```text
normal restore
lost device
DeviceAuthorizationKey compromise
RootRecoveryKey compromise without recovery policy
RootRecoveryKey compromise with valid recovery quorum
competing malicious old-root transition
recovery delay
UserRootRotation
pairwise backup rollback
pairwise restore + future-state rotation
```

## 19. Contact bootstrap attacks

Copied/replayed/expired/substituted descriptor, wrong RootIdentity proof, valid attacker descriptor substituted for Bob, colluding contacts.

## 20. Handshake matrix

Combinare versions, suite offers, transport semantics, certificate epochs, revocation freshness e route changes.

Negative tests: offer stripping, wrong relationship, stale/revoked cert, replayed ephemeral, transcript mismatch.

## 21. Rekey matrix

Testare almeno:

```text
normal N -> N+1
simultaneous Init
lost Init
lost Commit
lost Ack
duplicate Init/Commit/Ack
replayed old rekey object
route switch during rekey
old-key in-flight grace
old-key send after Ack
required-rekey timeout
```

No split-brain epoch silenzioso.

## 22. Transport semantic tests

`RELIABLE_ORDERED_STREAM` e `UNRELIABLE_DATAGRAM` hanno suite separate. Media loss/reorder non blocca control/text.

## 23. Relay Sybil/provenance

Generare molti relay IDs dello stesso adversary, false self-declared metadata e provenance attestations duplicate/collusive.

Oracle: N IDs o N attestazioni dello stesso issuer domain non equivalgono automaticamente a N operatori indipendenti.

## 24. Shield simulation

Vero circuit setup, per-hop keys, layered forwarding, compromise/collusion, rebuild, provenance-aware selection. Due proxy TCP non contano.

## 25. Release/governance simulation

```text
valid threshold release
insufficient signatures
single signer compromise
quorum compromise model
signer-set rotation/rollback
fresh-install stale checkpoint
old verifier limitation case
contract timelock/code hash
unauthorized upgrade
StateMigrationProof valid/invalid
malicious first-install source
```

## 26. Differential control-plane testing

Il `control-plane-mock` è utile solo se non diverge semanticamente dal `ChainAdapter` reale.

Ogni canonical transition/vector rilevante deve poter essere eseguito contro almeno:

```text
control-plane-mock
NearChainAdapter local/test environment
```

Assert equivalenza su:

- accepted/rejected transition;
- resulting canonical object/state root semantics;
- revocation behavior;
- expiry/height behavior;
- storage reclaim;
- failure class.

Una feature non viene dichiarata corretta soltanto perché passa nel mock.

## 27. Test levels

```text
L0 pure/unit + canonical vectors
L1 multi-process deterministic virtual-time simulation
L2 Docker/network chaos real-time
L3 real ChainAdapter integration
L4 Android emulator
L5 physical devices / real Wi-Fi/mobile/CGNAT
L6 external security/interoperability review
```

## 28. Automatic loop

```text
spec/task
 -> derive failing oracle/test
 -> isolated implementation
 -> L0/L1
 -> relevant L2/L3
 -> reviewer agent
 -> invariant/threat-model check
 -> human gate if normative spec changes
 -> merge only when required gates pass
```

## 29. Android gates

APK/emulator/device obbligatorio per Keystore, process death, Doze/background, permissions, actual Android sockets/handover, camera/QR, package signing/update e `VpnService`.

## 30. Evidence artifacts

Ogni scenario produce:

```text
scenario manifest
seed
virtual-time event trace
node versions/code hashes
network events
security state transitions
assertions
redacted logs
result
```

## 31. Definition of done

Una feature security/network è done quando esistono spec, positive/negative tests, adversarial scenario, resource bound, replay/rollback behavior, safe logs e platform gate se necessario.
