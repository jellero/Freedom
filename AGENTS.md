# AGENTS.md — Freedom engineering rules

These instructions apply to Codex/agentic development in this repository.

## Read first for security-sensitive work

Before modifying identity, protocol, chain/control-plane, relay, Shield, Gateway, payments, recovery or release code, read:

1. `docs/SECURITY_INVARIANTS.md`
2. `docs/CONTROL_PLANE_SECURITY.md`
3. `docs/IDENTITY_MODEL.md`
4. `docs/PROTOCOL.md`
5. `docs/THREAT_MODEL.md`
6. the subsystem-specific document.

Normative MUST/MUST NOT rules override older implementation behavior.

## Architecture invariants

Do not introduce:

- global user/device network IDs;
- on-chain messages/mailbox;
- persistent relay inbox;
- automatic offline delivery queue;
- RootIdentity or DeviceRecordCommitment as routing/contact identifiers;
- a public readable social graph;
- mandatory single RPC/provider/relay/egress;
- master decryption keys;
- single-key production super-admin paths;
- transaction-hash-is-success semantics;
- silent downgrade from strict/Shield policy.

## Development method

Follow `docs/ADVANCED_DEVELOPMENT.md`.

Prefer simulator-first development for protocol/control-plane/routing logic. Do not require a full Android APK install for every development iteration.

Use isolated worktrees/branches for parallel tasks.

## Test discipline

For every bug/security fix:

1. reproduce with a failing test/scenario when feasible;
2. implement the smallest coherent fix;
3. add regression coverage;
4. run relevant unit/property/scenario tests;
5. run negative/adversarial cases;
6. update normative docs/threat model if a security boundary changes.

Never weaken an assertion, oracle or security invariant merely to make CI green.

## Simulation targets

The lab should be able to model:

- multiple endpoints;
- relay/bridge/egress failures;
- NAT rebinding/change;
- packet delay/loss/reorder;
- DNS/TLS/transport blocking;
- stale/malicious RPC responses;
- failed/partial control-plane transactions;
- rollback checkpoints/policies/signer sets;
- clock skew/rollback;
- storage exhaustion/reclaim;
- relay Sybil/eclipse attempts;
- first-contact substitution;
- release/signer governance failures.

## Android gates

Host-side simulation does not replace Android validation for:

- Keystore;
- process/lifecycle/background behavior;
- permissions;
- package signing/update;
- network handover on Android;
- camera/QR integration;
- `VpnService`.

## Security-sensitive governance

Do not change production/root/mainnet signer sets, release roots, contract governance anchors or migration anchors automatically. Such changes require explicit human review and threshold-governance procedures described in the canonical docs.

## Evidence

Keep tests reproducible. Scenario failures should preserve seed/config, node versions/code hashes, network events and assertions without logging private/session/recovery secrets.
