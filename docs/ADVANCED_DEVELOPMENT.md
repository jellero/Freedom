# Freedom — Advanced Development Method

Status: **internal engineering methodology / executable baseline**.

Questo documento definisce il loop di sviluppo avanzato di Freedom. Non appartiene al README prodotto e non costituisce una garanzia del protocollo.

## 1. Principio

Il loop principale non dipende dalla compilazione/installazione continua dell'APK Android.

```text
canonical spec / vectors
        |
        v
shared pure-Java core
        |
   +----+-------------------+
   |                        |
L0 core tests           Android source set
   |
   v
L1 deterministic sim
   |
   v
L2 Docker/kernel network
   |
   v
L3 real ChainAdapter differential
   |
   v
L4/L5 Android + physical networks
```

Il core viene compilato/eseguito a ogni ciclo host-side con `javac`; non serve produrre/installare un APK per testare state machine, routing policy, recovery/freshness e control-plane acceptance rules.

## 2. Shared core — regola obbligatoria

Le transition rule security-sensitive eseguibili vivono in:

```text
core/src/main/java/dev/freedom/core/
```

Lo stesso source tree è incluso nell'Android source set.

`sim/simctl.py` gestisce soltanto:

- scenario DSL;
- virtual clock;
- scheduling;
- fault injection;
- process orchestration;
- assertions/evidence.

Non deve reimplementare una state machine già presente nel core.

Baseline implementata:

```text
RouteState
PairwiseRecoveryState
BootstrapFreshnessState
MutationVerificationState
RekeyState
```

## 3. Fast loop Codex

Per un task protocol/core:

```text
spec/task
 -> failing self-test/scenario
 -> isolated branch/worktree
 -> shared-core implementation
 -> L0/L1
 -> relevant L2/L3
 -> reviewer agent
 -> human gate if normative semantics changed
 -> merge only with required checks green
```

Codex può implementare, generare test, eseguire scenari e correggere regressioni; non è una trust authority e non può indebolire gli oracle per ottenere verde.

## 4. Comandi baseline

```bash
python tools/check_spec_consistency.py
python tools/check_dev_stack.py
python tools/check_vectors.py
python tools/run_core_tests.py
python sim/simctl.py --all --quiet
python sim/l3/differential.py --oracle-only
```

Per modifiche network/routing, su host Docker disposable:

```bash
python sim/l2/run_docker.py
```

Quando esiste il driver NearChainAdapter reale:

```bash
python sim/l3/differential.py --adapter-cmd "<NearChainAdapter test driver>"
```

`--oracle-only` **non è L3 reale**.

## 5. Livelli di test

```text
L0  core unit/self-tests + canonical byte vectors
L1  deterministic virtual-time simulation
L2  real Docker/container/socket/network behavior
L3  real ChainAdapter local/test differential
L4  Android emulator
L5  physical devices + Wi-Fi/mobile/CGNAT
L6  external security/interoperability review
```

Ogni livello risponde a una domanda diversa; nessun livello sostituisce automaticamente quello successivo.

## 6. L0 — core e bytes

L0 include:

- `Freedom-DCBOR-1` exact byte vectors;
- strict decoding/negative vectors;
- crypto-domain registry checks;
- shared-core transition self-tests.

Modificare expected bytes o una state machine normativa richiede review umana.

## 7. L1 — deterministic simulator

`sim/simctl.py` usa virtual time, non `sleep()` come oracle.

```yaml
- at: 10s
  action: block
  target: relay_a
```

`10s` è tempo virtuale.

Baseline scenari eseguibili:

```text
relay block + NAT/rebind event -> alternate route
pairwise backup rollback -> reject stale / accept latest / rotate future state
stale control-plane checkpoint -> BootstrapFreshnessFloor reject
rekey lost Ack -> stable next epoch without split brain
```

L1 deve crescere con handshake, revocation, rendezvous, storage, recovery races, governance e Shield state machines man mano che entrano nel shared core.

## 8. L2 — Docker/network chaos reale

`sim/l2/run_docker.py` è già eseguibile e usa container/network namespace/TCP reali.

Baseline:

```text
client network A -> relay A
client network B -> relay A
    source address visible to relay changes

relay A stopped/blocked
    primary path fails
    relay B remains reachable
```

Questo prova integrazione kernel/socket/container, non carrier CGNAT.

Espansioni previste:

- `tc netem` latency/loss/reorder/jitter;
- nftables/iptables block by IP/port/subnet;
- UDP/QUIC-like blocking;
- DNS failure/poisoning;
- SNI/domain filtering test endpoints;
- active reset/proxy fault injection;
- bridge/egress failure;
- path mutation mid-session.

## 9. Docker safety

Non dare a Codex il Docker socket di una workstation sensibile.

Preferire:

```text
disposable VM
isolated CI runner
rootless disposable runtime
nested lab host
```

Production/mainnet/release secrets non entrano nel laboratorio.

## 10. L3 — differential control-plane

Il control-plane mock/oracle non è sufficiente.

`sim/l3/differential.py` confronta i canonical transition vector con un adapter reale persistente.

Baseline vector:

- freshness floor;
- stale/fresh/highest-seen checkpoint;
- failed execution is not success;
- verified resulting-state transition;
- state rollback rejection.

Acceptance reale:

```text
shared-core oracle result
        ==
NearChainAdapter local/test result
```

Confrontare almeno:

- accepted/rejected;
- failure class;
- verified height/epoch;
- resulting canonical state/version;
- revocation semantics;
- storage reclaim;
- pairwise recovery-anchor update;
- finality/execution/state-proof behavior.

Il repo attuale non contiene ancora il nuovo canonical NearChainAdapter/contract: L3 resta esplicitamente pending fino a quell'implementazione, non fake-green.

## 11. Adversarial matrix obbligatoria

Il laboratorio deve coprire progressivamente:

```text
NETWORK
relay/provider/IP/port ban
NAT/address rebinding
loss/reorder/jitter/throttle/reset
DNS/SNI/transport blocking
allowlist-only environments

CONTROL PLANE
stale/fork/non-final checkpoint
invalid inclusion/non-inclusion proof
RPC omission/conflict/unavailable
submitted-but-failed tx
partial execution/state mismatch
bootstrap floor / highest-seen rollback

IDENTITY / RECOVERY
revoked device/authorization epoch
root rotation/recovery race
duplicate/invalid recovery quorum
stale PairwiseRecoveryBundle/Anchor rollback
first-contact substitution

SESSION
handshake offer stripping
wrong expected relationship
replayed ephemeral/transcript mismatch
rekey simultaneous/lost/duplicate/replay
route switch during rekey
old-key grace/expiry

ROUTING / SHIELD
Relay Sybil/eclipse
false provenance
circuit rebuild
hop compromise/collusion
no silent direct fallback

RELEASE / GOVERNANCE
insufficient threshold
signer-set rollback
contract code-hash/timelock failure
invalid StateMigrationProof
malicious first-install source
```

## 12. Storage/resource tests

Simulare create/renew/expire/prune/overwrite su grandi cardinalità logiche.

Assert:

- active-state upper bound;
- reclaim/refund/bounty bounded;
- no message/media/mailbox state;
- resource/concurrency caps;
- no unbounded queues introduced by recovery/routing.

## 13. Android gates

APK/emulator/device resta obbligatorio per proprietà Android-specifiche:

```text
Keystore
process death
Doze/background
permissions
package signing/update
real socket handover
camera/QR
VpnService
```

Il fatto che L0-L3 passino non prova queste proprietà.

## 14. Evidence artifacts

Ogni run deve conservare quando applicabile:

```text
scenario/version
seed
spec/core commit hash
virtual/network event trace
node/adapter versions
assertions
failure class
redacted logs
result
```

Mai loggare session keys, recovery private material o plaintext conversazioni.

## 15. Parallel agent roles

Usare worktree/branch isolate, per esempio:

```text
agent/core-protocol
agent/control-plane
agent/identity-recovery
agent/routing
agent/l2-chaos
agent/l3-near
agent/test-oracle
agent/reviewer
```

Reviewer e fixer idealmente separati per evitare che lo stesso agente definisca, implementi e auto-approvi l'oracolo.

## 16. Definition of done

Una feature security/network non è done con una demo manuale.

Richiede, dove applicabile:

```text
canonical semantics
shared-core implementation
positive + negative tests
adversarial scenario
resource bound
replay/rollback behavior
safe evidence/logging
relevant L0/L1/L2/L3 gate
Android/platform gate se specifico
```

## 17. Regola finale

> **Simulare velocemente non significa simulare la sicurezza. Il loop rapido deve eseguire le stesse regole core che il prodotto userà, e i livelli reali devono restare esplicitamente separati dai mock.**
