# AGENTS.md — Freedom engineering rules

These instructions apply to Codex/agentic development in this repository.

## Read first for security-sensitive work

1. `docs/SECURITY_INVARIANTS.md`
2. `docs/CONTROL_PLANE_SECURITY.md`
3. `docs/REVOCATION.md`
4. `spec/README.md`
5. `spec/freedom.cddl`
6. `docs/IDENTITY_MODEL.md`
7. `docs/PROTOCOL.md`
8. `docs/THREAT_MODEL.md`
9. `docs/REPOSITORY_GOVERNANCE.md`
10. subsystem-specific docs.

Normative MUST/MUST NOT rules override older implementation behavior.

## Architecture invariants

Do not introduce global user/device network IDs, on-chain messages/mailbox, persistent relay inbox, automatic offline delivery queue, RootIdentity/RootControlCommitment/DeviceRecordCommitment as routing IDs, public social graph, mandatory single RPC/provider/relay/egress, master decryption key, single-key production super-admin, tx-hash-is-success, RPC-not-found-is-non-revoked, silent Shield downgrade or unbounded temporary active state.

## Canonical schema

`spec/freedom.cddl` is the source of truth for frozen object field names/shapes.

All signed security objects use deterministic canonical encoding + explicit signing domains. Encrypted objects/frames use purpose/context-bound AEAD associated data according to `spec/README.md`.

Do not create a second incompatible Markdown/code struct for convenience.

## Normative-spec human gate

Agents may propose changes to normative/security files, but MUST NOT autonomously weaken/remove a MUST/MUST NOT, change a trust assumption, signing/AEAD domain, canonical signed schema, revocation/recovery/governance/rekey state machine merely to make implementation/tests pass.

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

Run at minimum:

```text
python tools/check_spec_consistency.py
```

when changing docs/spec/security architecture.

## Simulation targets

Model endpoints, relay/bridge/egress failure, NAT rebinding, loss/reorder, DNS/TLS/transport blocking, stale/malicious RPC, failed tx, stale bootstrap checkpoint, revocation ambiguity, rendezvous overwrite/front-run, signer rollback, clock faults, storage exhaustion, Relay Sybil/eclipse, first-contact substitution, root compromise-recovery races and release/governance failures.

## Docker/host safety

Do not give an agent unrestricted access to a production/personal host Docker daemon.

Prefer disposable VM/dedicated CI runner or rootless/isolated runtime.

Do not mount `/var/run/docker.sock` from a sensitive workstation into an agent-controlled container unless that host itself is disposable and contains no sensitive data/credentials.

Production/mainnet/release secrets are never available to autonomous test agents.

## Android gates

Host simulation does not replace Android validation for Keystore, lifecycle/background restrictions, permissions, signing/update, real network handover, camera/QR and `VpnService`.

## Governance boundaries

Do not automatically change production/mainnet signer sets, release roots, contract governance anchors, migration anchors or user recovery-policy roots.

Production governance/custody changes require explicit human review and canonical threshold procedures.

## Evidence

Keep tests reproducible. Preserve seed/config, virtual/network event trace, node/code hashes and assertions without logging private/session/recovery secrets.
