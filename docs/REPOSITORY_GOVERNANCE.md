# Freedom — Repository Governance

Status: **engineering governance / required for agentic development**.

This document governs how canonical specifications and security-sensitive executable core changes move into `main`.

## 1. Required flow

```text
agent/worktree branch
 -> specification consistency
 -> development-stack consistency
 -> frozen byte vectors
 -> shared-core self-tests
 -> deterministic L1 simulator
 -> L3 canonical/real adapter gates as relevant
 -> L4 proof/NetworkAnchor adapter gate as relevant
 -> relevant L2 Docker smoke
 -> pull request
 -> human review for normative/security changes
 -> required checks green
 -> human merge to protected main
```

## 2. Protected main requirement

`main` SHOULD be configured with branch/ruleset protection that at minimum requires PRs, relevant status checks and code-owner review for normative/security-sensitive changes; prevents force-push/deletion; and prevents autonomous agent credentials from bypassing those controls.

Until server-side protection is enabled, the same rule remains mandatory procedurally.

## 3. Human-reviewed security set

At minimum:

```text
spec/**
core/**
near/**
sim/jvm/**
sim/l3/**
docs/SECURITY_INVARIANTS.md
docs/CONTROL_PLANE_SECURITY.md
docs/NETWORK_ANCHORS.md
docs/REVOCATION.md
docs/PAIRWISE_RECOVERY.md
docs/IDENTITY_MODEL.md
docs/PROTOCOL.md
docs/THREAT_MODEL.md
docs/SHIELD.md
docs/EMERGENCY_UPDATES.md
docs/APP_DISTRIBUTION.md
AGENTS.md
```

`spec/ENCODING_PROFILE.md`, `spec/vectors/**` and `spec/crypto-domains.txt` are security-sensitive because byte/domain changes alter interoperability/security semantics.

`core/**` is equally security-sensitive once a normative transition is executable: moving a rule from Markdown into Java does not make it an ordinary implementation detail.

`near/proof-verifier/**` is security-sensitive because changes can alter whether untrusted RPC/NetworkAnchor payload data is promoted into trusted consensus/state.

## 4. Agent permissions

Agents may create branches/worktrees, implement changes, add tests/scenarios, run L0-L4/L2 gates, open PRs and respond to review.

Agents MUST NOT independently weaken a security invariant, trust assumption, frozen encoding/vector, cryptographic domain, NetworkAnchor bootstrap/rotation rule, recovery/revocation/governance/rekey state machine, canonical signed schema or shared-core transition rule merely to make tests pass.

Agents MUST NOT autonomously merge normative/security-sensitive changes, even if repository permissions technically allow it.

## 5. Repository gates

Current lightweight gates include:

```text
python tools/check_spec_consistency.py
python tools/check_dev_stack.py
python tools/check_vectors.py
python tools/run_core_tests.py
python sim/simctl.py --all --quiet
python sim/l3/differential.py --oracle-only
```

Network/routing changes also run, on a disposable Docker-capable runner:

```text
python sim/l2/run_docker.py
```

Real NEAR control-plane changes run L3:

```text
python sim/l3/differential.py \
  --adapter-cmd "cargo run --quiet --manifest-path near/l3-adapter/Cargo.toml"
```

NEAR proof/NetworkAnchor adapter changes run L4:

```text
cd near/proof-verifier
cargo test --manifest-path ../Cargo.toml \
  -p freedom-near-proof-verifier \
  --test sandbox_proofs -- --nocapture
```

### Specification consistency

Catches canonical-file/schema/domain/document drift, including required NetworkAnchor schema/domain/document/scenario markers.

### Development-stack consistency

Ensures the Android source set, simulator bridge, shared core, L2 harness, L3 differential contract and L4 NEAR verifier remain connected instead of silently diverging.

### Frozen byte vectors

Checks `Freedom-DCBOR-1` exact bytes/strict decoding/signing preimages/negative cases. NetworkAnchor has an explicit canonical encoding/signing-input vector.

### Shared-core tests

Compiles pure Java 17 with `javac` and tests route, pairwise recovery, bootstrap freshness, verified control-plane mutation, NetworkAnchor acceptance/rollback and rekey state transitions without building the APK.

### L1 deterministic simulator

Python owns DSL/virtual time/fault injection; the state transitions execute in the shared Java core. Passing L1 is not evidence for Android, real-network behavior or cryptographic consensus verification.

### L2 Docker network smoke

Uses real container namespaces/TCP sockets and verifies address rebinding plus primary-relay disappearance/fallback. It is not a CGNAT/mobile substitute.

### L3 differential harness

`--oracle-only` validates only canonical transition vectors. **It is not real L3 acceptance.** Real L3 uses `near/l3-adapter` + NEAR Sandbox and compares accepted/rejected result, failure class and resulting canonical state.

### L4 proof / NetworkAnchor adapter gate

`near/proof-verifier` verifies post-anchor NEAR light-client consensus, execution and state proofs against untrusted RPC objects. It also verifies the deterministic `NEAR-NEP25-PRE-SPICE-BORSH-V1` adapter payload against the outer declared checkpoint before verifier construction.

L4 Sandbox does not authenticate the production outer NetworkAnchor by itself. Production bootstrap pin/signature/rotation/multi-shard requirements remain separate normative gates.

## 6. CI is not the security oracle

A green workflow proves only the gates it actually executed. It does not prove cryptographic correctness, production NetworkAnchor signer custody, authentic mainnet bootstrap packaging, Android platform behavior, real carrier networking or external interoperability.

Higher L5/L6/L7 gates remain required according to `ADVANCED_DEVELOPMENT.md`.

## 7. Scenario/oracle integrity

Do not make a failing security scenario green by deleting/weakening assertions or rewriting expected vectors around buggy behavior.

```text
failing oracle
 -> determine implementation bug vs explicit normative change
 -> fix implementation if spec unchanged
 -> otherwise request human review for normative change
```

For NetworkAnchor specifically, it is forbidden to turn a threshold signature into a substitute for chain consensus merely because continuity verification is inconvenient or unavailable.

## 8. Current enforcement caveat

Until GitHub branch/ruleset protection is actually enabled, these controls are partly procedural.

> **Normative/security-sensitive changes still go through branch + PR + human review/merge even if GitHub technically permits a direct push or autonomous merge.**
