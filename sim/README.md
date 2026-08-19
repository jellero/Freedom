# Freedom simulator lab

Status: **executable L1 deterministic simulator + executable L2 Docker network smoke**.

The canonical development method is `docs/ADVANCED_DEVELOPMENT.md`.

## Shared-core rule

`sim/simctl.py` does **not** own the security transition state machines anymore.

It compiles and launches the same pure-Java core in `core/src/main/java` that is also included in the Android source set:

```text
scenario DSL / virtual time / fault orchestration
                 |
                 v
        sim/jvm/CoreStateServer
                 |
                 v
        core/FreedomCore.java
                 |
      +----------+----------+
      |                     |
     L1 sim             Android source set
```

Python remains responsible for scheduling, fault injection, evidence and assertions. Route/recovery/freshness/rekey/control-plane transition state belongs in `core/`.

## L0 shared-core test

```bash
python tools/run_core_tests.py
```

This compiles pure Java 17 with `javac` and runs the state-machine self-tests without building an APK.

## L1 deterministic runner

Run every deterministic scenario:

```bash
python sim/simctl.py --all
```

CI can reuse already-compiled core classes:

```bash
python sim/simctl.py --all --quiet --no-build --evidence-dir build/sim-evidence
```

Current executable L1 state includes:

- route failure + alternate relay recovery;
- peer identity independent from route;
- no mailbox writes;
- pairwise-backup stale/latest handling;
- monotonic recovery anchor;
- bootstrap freshness floor/highest-seen state;
- bounded rekey transition and key-epoch state;
- verified control-plane mutation acceptance rule in the shared core.

`sim/scenarios/*.yaml` are versioned acceptance fixtures. `10s` is virtual scheduler time, never `sleep(10)`.

## L2 real Docker network smoke

Run:

```bash
python sim/l2/run_docker.py
```

The L2 smoke uses real Docker bridge networks and TCP sockets. It creates two relays and probes them from isolated client containers, then verifies:

```text
client on network A -> relay A
client on network B -> relay A
    peer-visible source address changes

stop relay A
    primary probe fails
    alternate relay B remains reachable
```

This validates real process/container/network behavior. It does **not** claim Docker bridge networking reproduces carrier CGNAT/mobile networks; those remain L5 gates.

The Docker harness is designed for a disposable CI/VM host. Do not expose a sensitive workstation Docker daemon to autonomous agents.

## Scenario DSL v1

The L1 parser accepts a deliberately restricted YAML subset with no third-party dependency:

```yaml
version: 1
name: example
seed: 1234
clock: virtual
nodes:
  - alice
  - bob
steps:
  - at: 0s
    action: connect
    from: alice
    to: bob

  - at: 5s
    assert: no_mailbox_write
```

Expanding DSL semantics requires versioned tests; arbitrary YAML features are intentionally unsupported.

## Current scenario baseline

```text
relay-block-nat-rebind.yaml
pairwise-backup-rollback.yaml
stale-control-plane.yaml
rekey-lost-ack.yaml
```

The scenarios are regression oracles, not proof that every current Android UI flow already calls the canonical core.

## L3 differential control-plane gate

L3 acceptance means the same canonical transition vectors are executed against:

```text
shared-core/control-plane oracle
real NearChainAdapter local/test environment
```

and compared on accepted/rejected result, failure class and resulting canonical state.

The repository does not yet contain a canonical new `NearChainAdapter` implementation/contract to test. Therefore L3 must remain **explicitly incomplete rather than fake-green** until that adapter exists. When added, it must plug into the differential contract under `sim/l3/` and become a required gate before control-plane features are declared implemented.

## Evidence

L1 evidence contains scenario/source/seed/virtual time/events/assertions/result and marks the core as `shared-java-17`.

L2 outputs its real network transition result. Future L2 evidence will add packet/network traces where useful.

Secrets, plaintext conversations and recovery private material must never be written to evidence artifacts.

## Acceptance boundary

```text
L0 shared core          -> pure transition/unit evidence
L1 virtual simulator    -> deterministic orchestration/fault evidence
L2 Docker               -> real kernel/socket/container evidence
L3 ChainAdapter         -> real control-plane differential evidence
L4 Android emulator     -> platform integration
L5 physical networks    -> real Wi-Fi/mobile/CGNAT
L6 external review      -> independent security/interoperability
```
