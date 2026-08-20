# Freedom NEAR development stack

Status: **real L3 sandbox integration + L4 independent proof verification / not production mainnet acceptance**.

This directory contains the executable NEAR `ChainAdapter` development path and the verifier that removes RPC authority after a trusted `NetworkAnchor` has been established.

## Components

```text
control-plane-contract/
    minimal NEAR smart-contract kernel used to exercise monotonic control-plane state,
    bootstrap-floor storage and mutation failure/rollback semantics on a real node.

l3-adapter/
    persistent JSONL adapter used by `sim/l3/differential.py`.
    It starts NEAR Sandbox through `near-workspaces`, compiles/deploys the contract,
    executes transactions, observes transaction failure/success and reads resulting state.

proof-verifier/
    independent NEAR light-client / proof verifier. It accepts RPC objects only after
    validating them against release/bootstrap-pinned `NearNetworkAnchor` trust material.
```

The NEAR stack intentionally does **not** import Android code.

## Run L3

Requires Rust 1.93+ and Java 17 for the shared canonical core side.

```bash
python sim/l3/differential.py \
  --adapter-cmd "cargo run --quiet --manifest-path near/l3-adapter/Cargo.toml"
```

The first run downloads/builds Rust dependencies and the NEAR Sandbox binary through the pinned `near-workspaces` dependency.

## What the L3 gate proves

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

## L4: RPC is transport, not authority

`proof-verifier` implements the security boundary required by `docs/CONTROL_PLANE_SECURITY.md`:

```text
NetworkAnchor
  -> NEAR light-client head verification
       -> epoch continuity
       -> validator signatures
       -> >2/3 approved stake
       -> next validator-set commitment
  -> verified block-merkle root
       -> execution outcome proof
  -> verified-head block hash
       -> ordered chunk.prev_state_root set
       -> local Merkle reconstruction of aggregate prev_state_root
       -> authenticated shard selection
       -> ContractData trie inclusion / non-inclusion proof
  -> locally accepted state
```

The verifier follows the NEAR light-client validation model and keeps the trusted head unchanged on any failed check. Transaction hashes are evidence only; execution success is accepted only when the execution outcome proves into the independently verified head.

For state, the verifier does **not** treat the light-client header's `prev_state_root` as a contract trie root. In the supported pre-Spice profile it first requires the exact full block to hash to the independently verified head, reconstructs the header's aggregate state commitment from that block's ordered `chunk.prev_state_root` values, selects the authenticated shard root, and only then walks the exact `ContractData` trie proof returned by `view_state(include_proof=true)`.

The Sandbox L4 gate is deliberately one-shard. That makes shard index `0` uniquely authenticated and allows both inclusion and non-inclusion to be tested without pretending that a production multi-shard routing rule has already been implemented. The gate tampers with signed light-client data, execution-proof data, the full block's shard state root, returned contract-state bytes and trie proof nodes. Every corruption must fail closed.

Run it from this directory:

```bash
cd near/proof-verifier
cargo test --manifest-path ../Cargo.toml \
  -p freedom-near-proof-verifier \
  --test sandbox_proofs -- --nocapture
```

### Remaining production boundary

The test bootstraps `NearNetworkAnchor` from the trusted local Sandbox process so it can test the post-anchor verifier deterministically. Production Freedom **MUST NOT** bootstrap that anchor from the RPC being verified. Mainnet/testnet anchor packaging, release signing, rotation and emergency recovery remain part of the independently authenticated Freedom bootstrap/update path.

Production multi-shard state verification also requires an independently authenticated mapping from account/key to shard index. This is security-critical for non-inclusion: a valid proof from the wrong shard can truthfully prove that a key is absent from that shard. The current Sandbox gate avoids that ambiguity by asserting exactly one shard.

The current state-root binding is explicitly the pre-Spice NEAR profile. A protocol profile in which the light-client header no longer commits to state through the ordered chunk-state-root Merkle aggregate MUST be rejected until Freedom has a separately specified and tested verifier for that profile. No fallback to RPC trust is permitted.

Therefore the presence of this verifier does not make an arbitrary RPC response `VERIFIED_STATE`. The valid production chain remains:

```text
independently authenticated NetworkAnchor
 -> independently verified NEAR light-client head
 -> independently verified execution proof
 -> verified shard-state commitment + authenticated shard routing
 -> independently verified trie proof
 -> canonical Freedom object/state transition
```

## Contract scope

`control-plane-contract` is deliberately small. It is an executable kernel for the first differential transitions, not the complete Freedom control-plane contract. Identity, revocation, rendezvous, recovery anchor, entitlement, release and governance objects are added only with canonical CDDL/domain vectors and corresponding L3/L4 tests.

No messages, media, mailbox or full pairwise backup belong in this contract.
