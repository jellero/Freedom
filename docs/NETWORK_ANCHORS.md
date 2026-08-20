# Freedom — NetworkAnchor Bootstrap & Rotation

Status: **canonical / normative design rules**.

Normative baseline: [`SECURITY_INVARIANTS.md`](SECURITY_INVARIANTS.md).
Control-plane verification: [`CONTROL_PLANE_SECURITY.md`](CONTROL_PLANE_SECURITY.md).
Release/bootstrap trust: [`APP_DISTRIBUTION.md`](APP_DISTRIBUTION.md).
Governance: [`EMERGENCY_UPDATES.md`](EMERGENCY_UPDATES.md).
Canonical schema: [`../spec/freedom.cddl`](../spec/freedom.cddl).
Cryptographic domains: [`../spec/crypto-domains.txt`](../spec/crypto-domains.txt).

## 1. Purpose

`NetworkAnchor` is the independently authenticated starting point from which a `ChainAdapter` can verify consensus/finality and state without treating an RPC provider as a trust root.

```text
BootstrapTrustAnchor / already trusted NetworkAnchor
        |
        v
canonical NetworkAnchor
        |
        +-> exact network / adapter / verifier profile
        +-> trusted checkpoint
        +-> adapter-specific anchor payload
        +-> NETWORK_ANCHOR signer-set authorization
        `-> anti-rollback lineage
                |
                v
independent ChainAdapter consensus verification
        |
        v
VerifiedControlPlaneCheckpoint
        |
        v
state proof -> canonical Freedom object
```

A URL, RPC response, mirror, QR or transaction hash is never a `NetworkAnchor` trust source by itself.

## 2. Canonical object

The frozen object shape is `network-anchor` in `spec/freedom.cddl`.

The outer object is chain-agnostic. Adapter-specific trust material is contained in the signed `adapter_anchor_payload` and interpreted only under the exact declared `verifier_profile`.

Fields bind at least:

```text
network_id
chain_adapter_id
chain_network_id
anchor_epoch
verifier_profile
verifier_policy_version
trusted_checkpoint_height
trusted_checkpoint_hash
adapter_anchor_payload
signer_set_epoch
signer_set_commitment
issued_at_height
activation_height
previous_anchor_commitment
signatures
```

`chain_network_id` distinguishes the underlying chain network (for example NEAR mainnet/testnet) from Freedom's own `network_id`.

## 3. NetworkAnchorCommitment V1

For `network-anchor` version 1:

```text
unsigned_body = network-anchor with signatures removed
preimage = FreedomSigningInputV1(
    network_id,
    FREEDOM/NETWORK_ANCHOR,
    1,
    unsigned_body
)

NetworkAnchorCommitmentV1 = SHA-256(preimage)
```

The signature-input envelope and deterministic bytes are frozen by `Freedom-DCBOR-1`. The concrete production signature algorithm/suite is a separate cryptographic-suite decision and MUST be pinned before production interoperability.

Using the unsigned signing preimage makes the anchor commitment independent of signature ordering while still binding every security-relevant body field, network and domain.

## 4. Initial bootstrap

A fresh verifier does not accept an arbitrary threshold-signed anchor.

For the initial anchor, the current authentic `BootstrapTrustAnchor.accepted_contract_or_controlplane_anchor` MUST equal the exact `NetworkAnchorCommitmentV1` expected by that verifier/release.

Initial acceptance requires:

```text
strict Freedom-DCBOR-1 parse
exact network_id / chain_adapter_id / chain_network_id
exact verifier_profile / verifier_policy_version
previous_anchor_commitment == null
anchor commitment == bootstrap-pinned commitment
NETWORK_ANCHOR signer-set commitment/epoch valid
threshold signatures valid
adapter_anchor_payload valid for declared profile
payload checkpoint height/hash == outer checkpoint height/hash
trusted checkpoint satisfies BootstrapFreshnessFloor
issued_at_height <= activation_height <= trusted_checkpoint_height
```

Only after these checks may the adapter payload instantiate the consensus verifier.

The release/bootstrap trust path is therefore the trust root for the first anchor; the RPC is not.

## 5. Ordinary anchor rotation

An already initialized client persists at least:

```text
current NetworkAnchorCommitment
highest anchor_epoch
highest trusted_checkpoint_height
highest NETWORK_ANCHOR signer_set_epoch
network / adapter / chain-network / verifier-profile context
```

A candidate rotation MUST satisfy all of the following:

```text
candidate.anchor_epoch == current.anchor_epoch + 1
candidate.previous_anchor_commitment == current commitment
same network_id
same chain_adapter_id
same chain_network_id
same verifier_profile
same verifier_policy_version
candidate trusted checkpoint height >= highest verified/anchor height
candidate payload binds exact declared checkpoint
candidate threshold authorization valid
activation rule satisfied
consensus continuity from current independently verified state to candidate checkpoint verified
```

A rejected candidate MUST NOT mutate the previously trusted anchor or highest-seen state.

## 6. Governance authorization is not consensus

The `NETWORK_ANCHOR` signer role authorizes adoption of a candidate anchor package. It does **not** grant authority to manufacture chain history or override the chain's own consensus.

Therefore, after bootstrap, threshold signatures alone are insufficient for an ordinary same-chain anchor rotation. The `ChainAdapter` MUST independently prove consensus/finality continuity from state already trusted by the client to the candidate checkpoint (or a separately reviewed equivalent proof for that adapter).

This rule is security-critical:

```text
valid Freedom quorum
+ invalid chain continuity
= reject
```

Otherwise a compromised governance quorum would become a control-plane super-admin capable of fabricating arbitrary state.

## 7. Signer-set rotation

`NetworkAnchor.signer_set_commitment` identifies a canonical `signer-set` whose role is exactly:

```text
NETWORK_ANCHOR
```

Production policy target:

```text
NetworkAnchorAuthorization >= 3-of-5
```

This removes unilateral single-key authority; it does not remove quorum-collusion risk.

If `signer_set_epoch` changes, the normal canonical `signer-set-transition` rules apply:

```text
next epoch == previous + 1
previous threshold authorizes
next threshold accepts
activation height monotonic
highest-seen persisted
old set cannot reactivate
```

A NetworkAnchor package cannot silently invent a new signer set merely by naming a new commitment.

The `NETWORK_ANCHOR` role cannot by itself:

- authorize application releases;
- lower `BootstrapFreshnessFloor`;
- change `SecurityPolicy`;
- revoke/replace unrelated governance roles;
- change chain/adapter/profile;
- override consensus/finality;
- perform arbitrary state migration.

## 8. Chain / adapter / verifier-profile changes

Ordinary NetworkAnchor rotation is intentionally narrow.

Changing any of these:

```text
chain_adapter_id
chain_network_id
verifier_profile
verifier_policy_version in a way that changes verification semantics
```

is not an ordinary anchor rotation.

Chain migration requires the canonical `ChainMigrationManifest + StateMigrationProof` path. A verifier-profile change that changes parsing/consensus/state-root semantics requires an independently authenticated software/release update with a reviewed verifier profile and bootstrap anchor/floor.

There is no fallback from an unsupported profile to raw RPC trust.

## 9. NEAR V1 adapter payload

The first executable profile is:

```text
NEAR-NEP25-PRE-SPICE-BORSH-V1
```

For this profile, `adapter_anchor_payload` is a deterministic Borsh payload containing:

```text
payload version
trusted LightClientBlockLiteView
current epoch ValidatorStake set
next epoch ValidatorStake set
```

The NEAR verifier MUST reject the payload unless:

- the payload version is supported;
- no trailing/malformed bytes are accepted;
- trusted-head height/hash equal the outer `NetworkAnchor` checkpoint;
- both required validator sets are present;
- the next-validator-set commitment matches the trusted head;
- the outer chain network/profile context is exactly the configured one.

The current state-proof verifier profile is explicitly pre-Spice. If NEAR changes the commitment model, Freedom fails closed until a separate profile is specified and tested.

## 10. Freshness / anti-rollback

`BootstrapFreshnessFloor` and persisted highest-seen state are independent checks.

An authentic anchor below a release floor or below already observed monotonic state is rejected even if its signatures are valid.

A fresh install with an authentic but obsolete verifier can still be frozen in that verifier's historical trust state if every acquisition channel is attacker-controlled. Freedom does not claim to solve freshness from nothing; verifier/release freshness needs independent bootstrap assurance.

## 11. Failure classes

Recommended protocol failures:

```text
NETWORK_ANCHOR_INVALID
NETWORK_ANCHOR_NOT_ACTIVE
BOOTSTRAP_STATE_TOO_OLD
CONTROL_PLANE_ROLLBACK
CONTROL_PLANE_PROOF_INVALID
GOVERNANCE_TRANSITION_INVALID
```

No failure path promotes unverified RPC state.

## 12. Acceptance gates

Required coverage includes:

- wrong bootstrap-pinned anchor commitment -> reject;
- malformed/non-canonical anchor -> reject;
- wrong network/adapter/chain/profile -> reject;
- outer checkpoint vs adapter payload mismatch -> reject;
- insufficient/invalid threshold signatures -> reject;
- stale anchor epoch/height -> reject;
- broken previous-anchor lineage -> reject;
- signer-set jump/rollback without valid transition -> reject;
- threshold-valid rotation without consensus continuity -> reject;
- honest continuity + valid governance rotation -> accept;
- rejected candidate leaves prior anchor untouched;
- unsupported NEAR commitment/profile -> fail closed;
- fresh-install floor below/above cases.

## 13. Invariants

- RPC is transport, not anchor authority;
- initial anchor is independently pinned by the authentic bootstrap/release path;
- anchor signatures are threshold-governed and role-scoped;
- governance authorization never substitutes chain consensus;
- ordinary rotation is monotonic and same-context;
- chain/profile changes use separate reviewed migration/update paths;
- rejected candidates cannot roll back or mutate trusted state;
- production signature suite remains an explicit reviewed dependency, not an implicit assumption.
