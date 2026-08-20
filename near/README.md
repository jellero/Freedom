# Freedom NEAR development stack

Status: **real L3 sandbox integration + L4 independent proof verification / not production mainnet acceptance**.

This directory contains the executable NEAR `ChainAdapter` development path and the verifier that removes RPC authority after a trusted canonical `NetworkAnchor` has been established.

Normative NetworkAnchor lifecycle: [`../docs/NETWORK_ANCHORS.md`](../docs/NETWORK_ANCHORS.md).
Canonical outer object: [`../spec/freedom.cddl`](../spec/freedom.cddl) `network-anchor`.

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
    independent NEAR light-client / proof verifier plus the deterministic NEAR
    NetworkAnchor adapter-payload codec. RPC objects are accepted only after validation
    against trust material authenticated by the outer Freedom NetworkAnchor.
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

## Canonical NetworkAnchor boundary

Freedom does not define the trust root as a Rust `NearNetworkAnchor` struct or an RPC URL. The normative outer object is canonical `network-anchor` encoded under `Freedom-DCBOR-1`, domain-separated as `FREEDOM/NETWORK_ANCHOR` and independently authorized according to `docs/NETWORK_ANCHORS.md`.

The outer object binds:

```text
Freedom network_id
chain_adapter_id = NEAR
chain_network_id
anchor_epoch
verifier_profile / policy version
trusted checkpoint height/hash
adapter_anchor_payload
NETWORK_ANCHOR signer-set epoch/commitment
activation / previous-anchor lineage
```

For the first executable NEAR profile:

```text
NEAR-NEP25-PRE-SPICE-BORSH-V1
```

`adapter_anchor_payload` is deterministic Borsh V1 containing:

```text
payload version
trusted LightClientBlockLiteView
current epoch ValidatorStake set
next epoch ValidatorStake set
```

`NearNetworkAnchor::from_adapter_payload(...)` rejects before verifier construction when:

- the profile is unsupported;
- Borsh bytes are malformed or contain trailing data;
- payload version is unsupported;
- trusted-head height/hash differs from the outer canonical anchor;
- current/next validator sets are missing;
- the next-validator-set hash does not match the trusted head.

The outer Freedom threshold signatures/commitment are intentionally not implemented by the NEAR codec itself: they belong to the canonical bootstrap/governance verifier layer. This prevents NEAR-specific code from becoming a second Freedom governance format.

## L4: RPC is transport, not authority

`proof-verifier` implements the post-anchor security boundary required by `docs/CONTROL_PLANE_SECURITY.md`:

```text
independently authenticated canonical NetworkAnchor
  -> validated NEAR adapter payload
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

The same gate now also round-trips the deterministic NEAR NetworkAnchor payload and proves that wrong outer checkpoint binding and trailing/malformed payload bytes are rejected before a light-client verifier can be constructed.

Run it from this directory:

```bash
cd near/proof-verifier
cargo test --manifest-path ../Cargo.toml \
  -p freedom-near-proof-verifier \
  --test sandbox_proofs -- --nocapture
```

## Rotation semantics

A future production `NetworkAnchor` rotation is **not** accepted merely because `NETWORK_ANCHOR` Freedom signatures are valid.

Ordinary same-chain/profile rotation requires:

```text
valid canonical previous-anchor lineage
+ monotonic anchor/checkpoint/signer epochs
+ valid NETWORK_ANCHOR threshold authorization
+ independently verified NEAR consensus continuity from already trusted state
```

The current NEAR light-client verifier provides the consensus-continuity primitive; the shared `NetworkAnchorState` provides the chain-agnostic monotonic/governance acceptance state. A production adapter must compose both rather than treating either one alone as sufficient.

## Remaining production boundary

The Sandbox test constructs its first anchor from the trusted local Sandbox process only as a deterministic fixture, then serializes/deserializes it through the exact adapter payload profile. Production Freedom **MUST NOT** bootstrap the outer NetworkAnchor from the RPC being verified.

Still required before production/mainnet acceptance:

```text
Freedom-DCBOR-1 outer NetworkAnchor parser/commitment verifier in production path
explicit production NETWORK_ANCHOR signature suite + signer-set verifier
release/bootstrap packaging of exact initial NetworkAnchorCommitmentV1
ordinary rotation composition: Freedom authorization + NEAR consensus continuity
independently authenticated account/key -> shard routing for multi-shard state proofs
reviewed mainnet/testnet anchor packages and operational rotation/recovery procedure
new verifier profile if NEAR commitment semantics differ from the pre-Spice profile
```

Production multi-shard state verification especially requires an independently authenticated mapping from account/key to shard index. A valid proof from the wrong shard can truthfully prove that a key is absent from that shard. The current Sandbox gate avoids that ambiguity by asserting exactly one shard.

The current state-root binding is explicitly the pre-Spice NEAR profile. A protocol profile in which the light-client header no longer commits to state through the ordered chunk-state-root Merkle aggregate MUST be rejected until Freedom has a separately specified and tested verifier for that profile. No fallback to RPC trust is permitted.

Therefore the presence of this verifier does not make an arbitrary RPC response or an arbitrary threshold-signed anchor `VERIFIED_STATE`. The valid production chain remains:

```text
authentic BootstrapTrustAnchor / trusted prior NetworkAnchor
 -> canonical NetworkAnchor authorization + lineage
 -> independently verified NEAR consensus continuity
 -> independently verified execution proof
 -> verified shard-state commitment + authenticated shard routing
 -> independently verified trie proof
 -> canonical Freedom object/state transition
```

## Contract scope

`control-plane-contract` is deliberately small. It is an executable kernel for the first differential transitions, not the complete Freedom control-plane contract. Identity, revocation, rendezvous, recovery anchor, entitlement, release and governance objects are added only with canonical CDDL/domain vectors and corresponding L3/L4 tests.

No messages, media, mailbox or full pairwise backup belong in this contract.
