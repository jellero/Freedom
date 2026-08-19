# AGENTS.md — Freedom engineering rules

These instructions apply to Codex/agentic development in this repository.

## Read first for security-sensitive work

Before modifying identity, protocol, chain/control-plane, relay, Shield, Gateway, payments, recovery or release code, read:

1. `docs/SECURITY_INVARIANTS.md`
2. `docs/CONTROL_PLANE_SECURITY.md`
3. `docs/REVOCATION.md`
4. `spec/README.md`
5. `spec/freedom.cddl`
6. `docs/IDENTITY_MODEL.md`
7. `docs/PROTOCOL.md`
8. `docs/THREAT_MODEL.md`
9. subsystem-specific docs.

Normative MUST/MUST NOT rules override older implementation behavior.

## Architecture invariants

Do not introduce global user/device network IDs, on-chain messages/mailbox, persistent relay inbox, automatic offline delivery queue, RootIdentity/DeviceRecordCommitment as routing IDs, public social graph, mandatory single RPC/provider/relay/egress, master decryption key, single-key production super-admin, `tx-hash-is-success`, silent Shield downgrade or unbounded temporary active state.

## Canonical schema

`spec/freedom.cddl` is the source of truth for protocol object field names/shapes.

Do not duplicate a new incompatible struct in Markdown or code merely because it is convenient.

All signed security objects use deterministic canonical encoding and domain-separated signing input.

## Normative-spec human gate

Agents may propose changes to:

```text
docs/SECURITY_INVARIANTS.md
docs/CONTROL_PLANE_SECURITY.md
docs/REVOCATION.md
docs/IDENTITY_MODEL.md
docs/PROTOCOL.md
spec/freedom.cddl
spec/README.md
```

but MUST NOT autonomously weaken/remove a MUST/MUST NOT, change a trust assumption, signing domain, canonical signed schema or security state machine merely to make implementation/tests pass.

Such changes require explicit human review before they are treated as canonical/main-ready.

A failing test is evidence about implementation or specification; it is not permission to weaken the oracle.

## Development method

Follow `docs/ADVANCED_DEVELOPMENT.md`.

Prefer simulator-first development for protocol/control-plane/routing. Full Android APK install is an integration gate, not the primary loop.

Use isolated worktrees/branches for parallel tasks.

## Test discipline

For every bug/security fix:

1. reproduce with a failing test/scenario when feasible;
2. define expected behavior from canonical spec;
3. implement smallest coherent fix;
4. add regression coverage;
5. run negative/adversarial cases;
6. run relevant unit/property/scenario tests;
7. update docs/threat model if the boundary genuinely changes;
8. request human review if the normative requirement itself changes.

## Simulation targets

Lab should model endpoints, relay/bridge/egress failures, NAT rebinding, packet loss/reorder, DNS/TLS/transport blocking, stale/malicious RPC, failed/partial tx, stale bootstrap checkpoint, revocation ambiguity, rendezvous overwrite/front-run, signer rollback, clock faults, storage exhaustion, Relay Sybil/eclipse, first-contact substitution, root compromise recovery and release/governance failures.

## Docker/host safety

Do not give an agent unrestricted access to a production/personal host Docker daemon.

Preferred execution:

```text
disposable VM / dedicated CI runner
or
rootless/isolated container runtime
```

Do not mount `/var/run/docker.sock` from a sensitive workstation into an agent-controlled container unless that host is itself disposable and contains no sensitive credentials/data.

Production secrets, mainnet admin keys, release roots and personal credentials are never available to autonomous test agents.

## Android gates

Host-side simulation does not replace Android validation for Keystore, lifecycle/background restrictions, permissions, package signing/update, Android network handover, camera/QR and `VpnService`.

## Governance boundaries

Do not automatically change production/root/mainnet signer sets, release roots, contract governance anchors, migration anchors or user recovery-policy roots.

Production governance/custody changes require explicit human review and the threshold procedures described in canonical docs.

## Evidence

Keep tests reproducible. Preserve seed/config, node versions/code hashes, virtual/network events and assertions without logging private/session/recovery secrets.
