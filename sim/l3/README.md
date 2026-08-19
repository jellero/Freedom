# Freedom L3 ChainAdapter differential gate

Status: **harness implemented; real NearChainAdapter integration pending**.

L3 exists to stop the deterministic mock/oracle from drifting away from the actual control-plane implementation.

## Canonical side

The reference side is the same shared Java core used by L1:

```text
core/src/main/java/dev/freedom/core/FreedomCore.java
```

`sim/l3/vectors.json` currently covers:

- BootstrapFreshnessFloor;
- stale/fresh/highest-seen checkpoint behavior;
- failed execution is not success;
- resulting-state mismatch/rollback semantics through the shared mutation state.

Validate only the canonical side:

```bash
python sim/l3/differential.py --oracle-only
```

This is useful in CI but is **not** real L3 acceptance.

## Real adapter contract

A real adapter is launched as a persistent process:

```bash
python sim/l3/differential.py \
  --adapter-cmd "<command that launches the NearChainAdapter test driver>"
```

The process receives one JSON object per line on stdin and returns one JSON object per line on stdout.

Requests use the shapes in `vectors.json`, for example:

```json
{"op":"verify_checkpoint","height":99,"proof_valid":true}
```

and:

```json
{
  "op":"verify_mutation",
  "finality_proof_valid":true,
  "execution_succeeded":false,
  "resulting_state_proof_valid":true,
  "exact_transition_matched":true,
  "resulting_version":1
}
```

Expected response fields include:

```json
{"accepted":false,"failure":"BOOTSTRAP_STATE_TOO_OLD"}
```

or:

```json
{"accepted":true,"committed_version":2}
```

The harness compares both the canonical expected fields and overlapping state fields between core and adapter.

## Acceptance rule

A control-plane feature is not L3-complete until the **real** local/test `NearChainAdapter` driver passes this harness.

`--oracle-only` cannot be used as evidence that NEAR finality, proof verification, contract execution, storage reclaim or resulting-state proofs work correctly.

The repo currently does not contain the new canonical NearChainAdapter/contract implementation, so L3 is intentionally reported as pending rather than fabricated as green.
