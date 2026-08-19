# Freedom simulator lab

Status: **engineering scaffold / not a protocol implementation**.

The canonical development method is `docs/ADVANCED_DEVELOPMENT.md`.

This directory is the versioned seed for the host-side multi-node simulator that Codex/CI will expand. It deliberately does **not** contain a fake second implementation of Freedom Protocol.

## Goals

The simulator will execute the same core state machines/serialization as production code where possible and orchestrate them as independent nodes:

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

## Time model

L1 scenarios use deterministic virtual time. `at: 10s` means virtual scheduler time, not `sleep(10)`.

L2 Docker/network-namespace scenarios use real kernel/socket time and fault injection.

## Scenario fixtures

`sim/scenarios/*.yaml` are human-readable, versioned acceptance scenarios.

The first fixtures cover:

- route failure + NAT rebinding;
- pairwise backup rollback after total device loss.

The definitive DSL parser/executor will be implemented with the simulator core. Until then these files are normative **engineering fixtures**, not wire-protocol objects.

## Docker boundary

Do not mount a sensitive workstation's `/var/run/docker.sock` into an agent container.

Use a disposable VM, isolated CI runner or rootless disposable runtime. No production/mainnet/release secrets are available to the lab.

## Required runner properties

A future `simctl`/equivalent runner must support:

```text
seeded deterministic execution
virtual-clock scheduling
node/process isolation
network policy mutation
NAT mapping mutation
RPC response/proof fault injection
backup mirror stale/latest selection
assertions from canonical security state
machine-readable evidence artifacts
```

## Evidence output

Each run should preserve:

```text
scenario name/version
seed
core/spec commit hashes
node versions
virtual-time trace
network/control-plane events
assertion results
redacted logs
```

Secrets, plaintext conversations and recovery private material must not be written to evidence artifacts.

## No mock-only acceptance

Control-plane transitions that pass against a deterministic mock must also pass differential tests against the real `ChainAdapter` local/test environment before being considered implemented.
