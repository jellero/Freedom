# Freedom simulator lab

Status: **executable L1 deterministic engineering simulator / not a protocol implementation**.

The canonical development method is `docs/ADVANCED_DEVELOPMENT.md`.

This directory is the versioned host-side multi-node lab that Codex/CI expands. It deliberately does **not** contain a fake independent implementation of Freedom cryptography.

## Current executable runner

`sim/simctl.py` is now executable with the Python standard library only.

Run every deterministic L1 scenario:

```bash
python sim/simctl.py --all
```

Quiet CI form:

```bash
python sim/simctl.py --all --quiet
```

Generate machine-readable evidence:

```bash
python sim/simctl.py --all --evidence-dir build/sim-evidence
```

The runner provides:

- seeded deterministic execution;
- a virtual-clock event scheduler;
- a restricted versioned YAML scenario DSL;
- independent logical nodes;
- relay blocking and NAT rebinding;
- deterministic route recovery;
- pairwise-backup stale/latest mirror behavior;
- bootstrap-freshness-floor rejection/acceptance;
- a first explicit rekey transition oracle;
- assertions and JSON evidence traces.

## Current scenarios

`sim/scenarios/*.yaml` are executable acceptance fixtures.

Current baseline:

```text
relay-block-nat-rebind.yaml
    relay failure + NAT rebinding
    -> alternate route recovery
    -> peer identity unchanged
    -> no mailbox write

pairwise-backup-rollback.yaml
    latest verified recovery anchor + stale valid backup
    -> rollback reject
    -> latest matching bundle accept
    -> peer re-auth
    -> future rendezvous state rotation

stale-control-plane.yaml
    valid checkpoint below BootstrapFreshnessFloor
    -> BOOTSTRAP_STATE_TOO_OLD
    -> later fresh verified checkpoint accepted

rekey-lost-ack.yaml
    STABLE(1) -> INIT -> COMMIT -> dropped Ack -> confirmed Ack
    -> STABLE(2)
    -> no split brain
    -> old send key erased
```

These scenarios are intentionally small. They are regression oracles that production core modules must eventually drive; they are not evidence that the current Android spike already implements those properties.

## Scenario DSL v1

The L1 runner accepts a deliberately restricted YAML shape:

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

Supported L1 scenarios use non-decreasing virtual timestamps. `10s` means scheduler time, not `sleep(10)`.

The parser is intentionally dependency-free so CI does not require PyYAML. Expanding the DSL requires versioned semantics and tests rather than accepting arbitrary YAML features.

## Architecture goal

As the host-side production core appears, the simulator should execute the **same** state machines and serialization rather than duplicating them:

```text
Alice endpoint
Bob endpoint
Relay A / B
Bridge
Egress
Honest / stale / malicious RPC
Control-plane mock
NAT / censor / clock fault injectors
Release mirror
Latest / stale pairwise-backup mirrors
```

The current runner owns orchestration/fault injection/oracles only. Cryptographic and protocol logic should migrate into shared `core/` modules and be called from `simctl`.

## Time model

### L1 — virtual deterministic time

Scenario actions and internally scheduled events use the virtual scheduler. No wall-clock sleeps are required.

### L2 — real kernel/network time

Docker/network namespaces/`tc netem`/nftables will validate socket/kernel behavior separately. L2 must not replace L1 deterministic state-machine oracles.

## Docker boundary

Do not mount a sensitive workstation's `/var/run/docker.sock` into an agent container.

Use a disposable VM, isolated CI runner or rootless disposable runtime. No production/mainnet/release secrets are available to the lab.

## Evidence output

Each evidence report records:

```text
scenario
source fixture
seed
virtual time
actions/internal events
assertions
result
```

Future runners add core/spec commit hashes and node code hashes.

Secrets, plaintext conversations and recovery private material must not be written to evidence artifacts.

## No mock-only acceptance

A passing L1 scenario proves only the modeled state/oracle behavior.

Control-plane transitions that pass against a deterministic mock must also pass differential tests against the real `ChainAdapter` local/test environment before being considered implemented.

Similarly, Android-specific properties remain L4/L5 gates.
