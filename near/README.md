# Freedom NEAR development stack

Status: **real L3 sandbox integration / not production mainnet acceptance**.

This directory contains the first executable NEAR `ChainAdapter` development path.

## Components

```text
control-plane-contract/
    minimal NEAR smart-contract kernel used to exercise monotonic control-plane state,
    bootstrap-floor storage and mutation failure/rollback semantics on a real node.

l3-adapter/
    persistent JSONL adapter used by `sim/l3/differential.py`.
    It starts NEAR Sandbox through `near-workspaces`, compiles/deploys the contract,
    executes transactions, observes transaction failure/success and reads resulting state.
```

The adapter intentionally does **not** import Android code.

## Run

Requires Rust 1.93+ and Java 17 for the shared canonical core side.

```bash
python sim/l3/differential.py \
  --adapter-cmd "cargo run --quiet --manifest-path near/l3-adapter/Cargo.toml"
```

The first run downloads/builds Rust dependencies and the NEAR Sandbox binary through the pinned `near-workspaces` dependency.

## What this L3 gate proves

The differential run uses the same transition vector on both sides:

```text
shared Freedom core oracle
            vs
NEAR Sandbox + deployed contract + NearChainAdapter
```

It checks, with real sandbox execution, that:

- a bootstrap floor committed to the contract is read back exactly;
- a submitted contract call that panics is not treated as success;
- failed execution does not mutate committed state;
- a successful mutation is followed by a state read and exact-transition comparison;
- a lower committed version is rejected by the contract and does not roll state back;
- checkpoint acceptance also requires a successful read from the running NEAR node while Freedom's bootstrap/highest-seen rules remain client-side.

Extra `near_block_height` / `near_block_hash` fields are evidence and are not canonical Freedom state.

## Important boundary: proof verification

This is now a **real ChainAdapter integration test**, but it is not yet a production light client.

`near-workspaces` talks to a trusted local sandbox node. Therefore a successful L3 sandbox run does not by itself prove the production requirement:

```text
untrusted RPC response
 -> independently verified NEAR finality/light-client proof
 -> verified state proof
```

NEAR exposes light-client proof RPCs, but production Freedom must verify those proofs against its `NetworkAnchor` rather than trusting the provider. That verifier remains a separate security component/gate.

Do not rename sandbox trust into `VERIFIED_STATE` in production code.

## Contract scope

`control-plane-contract` is deliberately small. It is an executable kernel for the first differential transitions, not the complete Freedom control-plane contract. Identity, revocation, rendezvous, recovery anchor, entitlement, release and governance objects are added only with canonical CDDL/domain vectors and corresponding L3 tests.

No messages, media, mailbox or full pairwise backup belong in this contract.
