# AGENTS.md — Freedom engineering rules

These instructions apply to Codex/agentic development in this repository.

## Read first for security-sensitive work

1. `docs/SECURITY_INVARIANTS.md`
2. `docs/CONTROL_PLANE_SECURITY.md`
3. `docs/REVOCATION.md`
4. `docs/PAIRWISE_RECOVERY.md`
5. `spec/README.md`
6. `spec/ENCODING_PROFILE.md`
7. `spec/freedom.cddl`
8. `spec/crypto-domains.txt`
9. `spec/vectors/dcbor-v1.json`
10. `docs/IDENTITY_MODEL.md`
11. `docs/PROTOCOL.md`
12. `docs/THREAT_MODEL.md`
13. `docs/REPOSITORY_GOVERNANCE.md`
14. subsystem-specific docs.

Normative MUST/MUST NOT rules override older implementation behavior.

## Architecture invariants

Do not introduce global user/device network IDs, on-chain messages/mailbox, persistent relay inbox, automatic offline delivery queue, RootIdentity/RootControlCommitment/DeviceRecordCommitment as routing IDs, public social graph, mandatory single RPC/provider/relay/egress, master decryption key, single-key production super-admin, tx-hash-is-success, RPC-not-found-is-non-revoked, silent Shield downgrade, unbounded temporary active state or unregistered cryptographic purpose/domain constants.

Do not claim a pairwise backup is the latest verified state after total device loss unless freshness is provided by surviving trusted state or the canonical independent monotonic recovery anchor.

## Canonical schema / bytes / crypto domains

`spec/freedom.cddl` is the source of truth for frozen object field names/shapes.

`spec/ENCODING_PROFILE.md` freezes `Freedom-DCBOR-1` byte semantics. Existing `Freedom-DCBOR-1` expected bytes MUST NOT change silently.

`spec/vectors/dcbor-v1.json` is the shared byte-level fixture consumed across languages.

`spec/crypto-domains.txt` is the source of truth for fixed SIGN/MAC/AEAD/HASH/KDF protocol domains.

All security objects use deterministic canonical encoding + explicit purpose/context binding according to `spec/README.md` and `spec/ENCODING_PROFILE.md`.

Do not create a second incompatible Markdown/code struct, vector set, serializer rule or ad-hoc crypto label for convenience.

## Normative-spec human gate

Agents may propose changes to normative/security files, but MUST NOT autonomously weaken/remove a MUST/MUST NOT, change a trust assumption, cryptographic domain, `Freedom-DCBOR-1` byte rule/vector, canonical signed schema, revocation/recovery/governance/rekey state machine merely to make implementation/tests pass.

A failing test is evidence, not permission to weaken the oracle.

### Branch/PR rule

For normative/security-sensitive changes:

```text
agent branch/worktree
 -> automated checks
 -> pull request
 -> human/code-owner review
 -> human merge
```

Agents MUST NOT intentionally direct-push such changes to `main`, even if repository settings technically allow it.

The target repository configuration is protected `main` with required PR/code-owner/status checks as documented in `docs/REPOSITORY_GOVERNANCE.md`.

## Development method

Follow `docs/ADVANCED_DEVELOPMENT.md`.

Prefer simulator-first development. Full Android APK install is an integration gate, not the primary protocol loop.

Use isolated worktrees/branches for parallel tasks.

The deterministic L1 runner is:

```text
python sim/simctl.py --all
```

Do not replace an existing executable scenario with prose-only acceptance criteria.

## Test discipline

For every bug/security fix:

1. reproduce with a failing test/scenario when feasible;
2. define expected behavior from canonical spec;
3. implement smallest coherent fix;
4. add regression coverage;
5. run negative/adversarial cases;
6. run relevant unit/property/scenario tests;
7. update threat/docs if the security boundary genuinely changes;
8. request human review if normative semantics change.

For docs/spec/security/simulator changes run at minimum:

```text
python tools/check_spec_consistency.py
python tools/check_vectors.py
python sim/simctl.py --all --quiet
```

All three are repository gates; do not report a protocol/spec task as complete while one is failing.

## Simulation targets

Model endpoints, relay/bridge/egress failure, NAT rebinding, loss/reorder, DNS/TLS/transport blocking, stale/malicious RPC, failed tx, stale bootstrap checkpoint, revocation ambiguity, rendezvous overwrite/front-run, signer rollback, clock faults, storage exhaustion, Relay Sybil/eclipse, first-contact substitution, root compromise-recovery races, stale pairwise backup mirrors, recovery-anchor rollback and release/governance failures.

## Pairwise recovery tests

When touching pairwise backup/recovery logic, test at minimum:

```text
latest bundle + latest anchor -> accept
old valid bundle + newer anchor -> reject
missing anchor after total device loss -> no latest-freshness claim
anchor rollback -> reject
root-compromise restore -> RecoveryStateKey rotation
post-restore -> peer re-auth + future rendezvous rotation
```

Integrity is not a substitute for freshness.

## Encoding/vector tests

When touching a frozen object or security encoding:

```text
positive canonical bytes
strict decoder acceptance
non-canonical negative rejection
correct SIGN/MAC/AEAD/HASH/KDF purpose binding
network/version separation
```

A cross-language implementation must consume the shared fixtures, not redefine expected bytes locally.

## Recovery quorum tests

Reject duplicate recovery-key commitments, zero threshold and threshold greater than the number of distinct recovery keys.

A profile claiming independent compromise recovery must be reviewed for custody-domain independence, not just key count.

## Docker/host safety

Do not give an agent unrestricted access to a production/personal host Docker daemon.

Prefer disposable VM/dedicated CI runner or rootless/isolated runtime.

Do not mount `/var/run/docker.sock` from a sensitive workstation into an agent-controlled container unless that host itself is disposable and contains no sensitive data/credentials.

Production/mainnet/release secrets are never available to autonomous test agents.

## Android gates

Host simulation does not replace Android validation for Keystore, lifecycle/background restrictions, permissions, signing/update, real network handover, camera/QR and `VpnService`.

## Governance boundaries

Do not automatically change production/mainnet signer sets, release roots, contract governance anchors, migration anchors, user recovery-policy roots, pairwise recovery-anchor semantics or frozen encoding profiles/vectors.

Production governance/custody changes require explicit human review and canonical threshold procedures.

## Evidence

Keep tests reproducible. Preserve seed/config, virtual/network event trace, node/code hashes and assertions without logging private/session/recovery secrets.
