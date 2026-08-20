# AGENTS.md — Freedom engineering rules

These instructions apply to Codex/agentic development in this repository.

## Read first for security-sensitive work

1. `docs/SECURITY_INVARIANTS.md`
2. `docs/CONTROL_PLANE_SECURITY.md`
3. `docs/NETWORK_ANCHORS.md`
4. `docs/REVOCATION.md`
5. `docs/PAIRWISE_RECOVERY.md`
6. `spec/README.md`
7. `spec/ENCODING_PROFILE.md`
8. `spec/freedom.cddl`
9. `spec/crypto-domains.txt`
10. `spec/vectors/dcbor-v1.json`
11. `docs/IDENTITY_MODEL.md`
12. `docs/PROTOCOL.md`
13. `docs/THREAT_MODEL.md`
14. `docs/ADVANCED_DEVELOPMENT.md`
15. `core/README.md`
16. `sim/README.md`
17. `near/README.md`
18. `docs/REPOSITORY_GOVERNANCE.md`
19. subsystem-specific docs.

Normative MUST/MUST NOT rules override older implementation behavior.

## Architecture invariants

Do not introduce global user/device network IDs, on-chain messages/mailbox, persistent relay inbox, automatic offline delivery queue, RootIdentity/RootControlCommitment/DeviceRecordCommitment as routing IDs, public social graph, mandatory single RPC/provider/relay/egress, master decryption key, single-key production super-admin, tx-hash-is-success, RPC-not-found-is-non-revoked, silent Shield downgrade, unbounded temporary active state or unregistered cryptographic purpose/domain constants.

Do not claim a pairwise backup is the latest verified state after total device loss unless freshness is provided by surviving trusted state or the canonical independent monotonic recovery anchor.

A governance/quorum signature over a NetworkAnchor MUST NOT be treated as a substitute for the underlying chain's consensus/finality continuity after bootstrap.

## Canonical schema / bytes / crypto domains

`spec/freedom.cddl` is the source of truth for frozen object field names/shapes.

`spec/ENCODING_PROFILE.md` freezes `Freedom-DCBOR-1`; existing expected bytes MUST NOT change silently.

`spec/vectors/dcbor-v1.json` is the shared byte-level fixture consumed across languages.

`spec/crypto-domains.txt` is the source of truth for SIGN/MAC/AEAD/HASH/KDF domains.

Do not create a second incompatible struct, serializer rule, vector set or crypto label for convenience.

## Shared core rule

Security-relevant transition logic implemented for host simulation belongs in:

```text
core/src/main/java/dev/freedom/core/
```

The Android source set compiles that same source tree. `sim/simctl.py` may own scenario parsing, virtual time, fault injection, process/container orchestration and evidence, but MUST NOT re-implement route/recovery/freshness/rekey/control-plane/NetworkAnchor acceptance rules already present in the shared core.

When a new canonical state machine becomes executable:

1. implement it in shared core;
2. add core self-tests;
3. drive it from L1 scenarios;
4. make Android/platform adapters call the same core when that production flow is implemented.

## Normative-spec human gate

Agents may propose changes to normative/security files, but MUST NOT autonomously weaken/remove a MUST/MUST NOT, change a trust assumption, cryptographic domain, `Freedom-DCBOR-1` byte rule/vector, canonical signed schema, revocation/recovery/governance/rekey/NetworkAnchor state machine merely to make implementation/tests pass.

A failing test is evidence, not permission to weaken the oracle.

For normative/security-sensitive changes:

```text
agent branch/worktree
 -> automated checks
 -> pull request
 -> human/code-owner review
 -> human merge
```

Agents MUST NOT intentionally direct-push such changes to `main`, even if repository settings technically allow it.

## Development method

Follow `docs/ADVANCED_DEVELOPMENT.md`. Full Android APK install is an integration gate, not the primary protocol loop.

Use isolated worktrees/branches for parallel tasks. Do not replace executable scenarios with prose-only acceptance criteria.

## Required fast gates

For protocol/core/spec/simulator work run at minimum:

```text
python tools/check_spec_consistency.py
python tools/check_dev_stack.py
python tools/check_vectors.py
python tools/run_core_tests.py
python sim/simctl.py --all --quiet
python sim/l3/differential.py --oracle-only
```

For network/routing changes that affect L2 behavior also run on a disposable Docker-capable host:

```text
python sim/l2/run_docker.py
```

For changes that affect ChainAdapter/control-plane behavior, real L3 is now executable and required:

```text
python sim/l3/differential.py \
  --adapter-cmd "cargo run --quiet --manifest-path near/l3-adapter/Cargo.toml"
```

`--oracle-only` validates only the canonical side and MUST NOT be reported as real L3 acceptance.

Real L3 currently means NEAR Sandbox execution + transaction outcome + resulting-state read. Independent post-anchor RPC verification is a separate L4 gate in `near/proof-verifier`; L3 alone does not prove finality/state proofs. L4 still does not make an RPC a valid source for the initial authenticated NetworkAnchor.

Do not report a task complete while a relevant gate is failing.

## Test discipline

For every bug/security fix:

1. reproduce with a failing test/scenario when feasible;
2. derive expected behavior from canonical spec;
3. implement the smallest coherent fix in the shared layer;
4. add regression coverage;
5. run negative/adversarial cases;
6. run relevant L0/L1/L2/L3/L4 gates;
7. update threat/docs only when the security boundary genuinely changes;
8. request human review for normative semantic changes.

## Simulation targets

Model endpoints, relay/bridge/egress failure, NAT/address rebinding, loss/reorder, DNS/TLS/transport blocking, stale/malicious RPC, failed tx, stale bootstrap checkpoint, invalid/mismatched NetworkAnchor, anchor rollback, governance-valid anchor without consensus continuity, revocation ambiguity, rendezvous overwrite/front-run, signer rollback, clock faults, storage exhaustion, Relay Sybil/eclipse, first-contact substitution, root compromise-recovery races, stale pairwise backup mirrors, recovery-anchor rollback and release/governance failures.

## Pairwise recovery tests

Test latest bundle + latest anchor, old valid bundle + newer anchor, missing anchor after total device loss, anchor rollback, RecoveryStateKey rotation after root-compromise restore, peer re-authentication and future rendezvous rotation. Integrity is not freshness.

## Encoding/vector tests

For frozen security objects test positive canonical bytes, strict decoder acceptance, non-canonical negative rejection, correct domain purpose binding and network/version separation. Cross-language implementations consume the shared fixtures instead of redefining expected bytes locally.

## Recovery quorum tests

Reject duplicate recovery-key commitments, zero threshold and threshold greater than distinct keys. Independent compromise recovery requires custody-domain independence, not only key count.

## Docker/host safety

Do not give an agent unrestricted access to a production/personal host Docker daemon. Prefer disposable VM/dedicated CI runner or rootless/isolated runtime. Do not mount a sensitive workstation `/var/run/docker.sock` into an agent-controlled container. Production/mainnet/release secrets are never available to autonomous test agents.

## Android gates

Host simulation does not replace Android validation for Keystore, lifecycle/background restrictions, permissions, package signing/update, real network handover, camera/QR and `VpnService`.

## Governance boundaries

Do not automatically change production/mainnet signer sets, release roots, NetworkAnchor roots/rotation rules, contract governance anchors, migration anchors, user recovery-policy roots, pairwise recovery-anchor semantics or frozen encoding profiles/vectors.

`near/` is security-sensitive execution infrastructure. Changing the sandbox contract/adaptor/verifier so that failing transactions, invalid anchors, invalid consensus continuity or rollback states become accepted requires the same review discipline as changing the corresponding core/spec oracle.

## Evidence

Keep tests reproducible. Preserve seed/config, virtual/network event trace, node/code hashes and assertions without logging private/session/recovery secrets.
