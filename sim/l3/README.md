# Freedom L3 ChainAdapter differential gate

Status: **real NEAR Sandbox adapter implemented; production light-client verification still separate**.

L3 exists to stop the deterministic core/oracle from drifting away from the actual control-plane implementation.

## Canonical side

The reference side is the same shared Java core used by L1:

```text
core/src/main/java/dev/freedom/core/FreedomCore.java
```

`sim/l3/vectors.json` covers BootstrapFreshnessFloor, stale/fresh/highest-seen checkpoint behavior, failed execution, successful resulting-state verification and mutation rollback.

Oracle-only validation remains available:

```bash
python sim/l3/differential.py --oracle-only
```

That command is useful for fast checks but is **not** real L3 acceptance.

## Real NEAR Sandbox adapter

The repository now contains the adapter and sandbox contract under `near/`.

Run the real differential:

```bash
python sim/l3/differential.py \
  --adapter-cmd "cargo run --quiet --manifest-path near/l3-adapter/Cargo.toml"
```

The adapter:

1. compiles `near/control-plane-contract`;
2. starts a local NEAR Sandbox with `near-workspaces`;
3. deploys and initializes the contract;
4. accepts the same persistent JSONL requests as the canonical oracle;
5. submits real contract transactions;
6. checks actual execution success/failure;
7. performs post-transaction contract views;
8. returns only `FREEDOM_L3\t<json>` response frames to the harness.

Toolchain/sandbox build diagnostics may appear on stderr and are not protocol frames.

## Current real L3 evidence

The real gate verifies that:

```text
failed contract execution
 -> is_failure
 -> state remains unchanged
 -> CONTROL_PLANE_EXECUTION_FAILED

successful mutation
 -> sandbox transaction succeeds
 -> resulting contract state is read back
 -> exact requested transition matches
 -> local success

lower state version
 -> contract rejects transaction
 -> previous state remains committed
 -> CONTROL_PLANE_ROLLBACK
```

Bootstrap/checkpoint rules are client-side. For a proof-valid checkpoint path the adapter also requires a successful block read from the running NEAR node and records `near_block_height` / `near_block_hash` as evidence.

## Trust boundary

A local `near-workspaces` Sandbox is a **real NEAR execution environment**, but the local sandbox node itself is trusted by this test.

Therefore this L3 gate does not yet prove the production path:

```text
untrusted RPC
 -> independently verified NEAR light-client/finality proof
 -> independently verified state proof
 -> VERIFIED_STATE
```

Production Freedom must verify proofs against its `NetworkAnchor`; raw RPC success is not `VERIFIED_STATE`.

The sandbox gate and the future light-client proof-verifier gate are complementary, not interchangeable.

## Contract scope

`near/control-plane-contract` is currently an executable kernel for the first canonical state transitions, not the full production control-plane contract.

Identity, revocation, rendezvous, recovery-anchor, entitlement, release and governance methods are added incrementally only with canonical schema/domain vectors and corresponding differential tests.

Messages, media, mailbox and full pairwise backup state are forbidden here.
