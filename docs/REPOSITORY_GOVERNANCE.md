# Freedom — Repository Governance

Status: **engineering governance / required for agentic development**.

This document governs how canonical specifications and security-sensitive changes move into `main`.

## 1. Problem

A Markdown rule saying “human review required” is not enough if an automated agent can push directly to an unprotected default branch.

Target repository policy:

```text
agent/worktree branch
 -> specification consistency
 -> frozen byte vectors
 -> deterministic simulator scenarios
 -> pull request
 -> human review for normative/security changes
 -> required checks green
 -> merge to protected main
```

## 2. Protected main requirement

`main` SHOULD be configured in GitHub repository settings with branch/ruleset protection that at minimum:

- requires pull requests before merge for normative/security-sensitive changes;
- requires the specification/vector/simulator workflow;
- prevents force-push/deletion of `main`;
- requires review from the relevant code owner for canonical spec/security files;
- prevents an autonomous agent credential from bypassing those controls.

If repository-plan/platform constraints prevent exact settings, use the closest ruleset that preserves the same security property.

## 3. Normative files

Human-reviewed canonical/security set includes at least:

```text
spec/**
docs/SECURITY_INVARIANTS.md
docs/CONTROL_PLANE_SECURITY.md
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

`spec/ENCODING_PROFILE.md`, `spec/vectors/**` and `spec/crypto-domains.txt` are specifically security-sensitive because changing deterministic bytes or a fixed SIGN/MAC/AEAD/HASH/KDF domain can change interoperability and cross-object/network/purpose acceptance semantics.

`CODEOWNERS` names the human owner for these paths.

## 4. Agent permissions

Agents may:

- create branches/worktrees;
- implement changes;
- add/update tests;
- add simulator scenarios;
- open PRs;
- respond to review;
- update non-normative docs consistent with canonical state.

Agents MUST NOT independently merge a change that weakens a security invariant, changes a trust assumption, frozen encoding profile/vector, cryptographic domain, recovery/revocation/governance/rekey state machine or canonical signed schema.

## 5. Repository protocol gates

`.github/workflows/spec-consistency.yml` is the required lightweight protocol/specification gate.

It runs three independent checks:

```text
python tools/check_spec_consistency.py
python tools/check_vectors.py
python sim/simctl.py --all --quiet
```

### Specification consistency

`tools/check_spec_consistency.py` catches repository-level drift such as required canonical files, stale terminology/formulas, schema/domain references and malformed documentation assets.

### Frozen byte vectors

`tools/check_vectors.py` checks `Freedom-DCBOR-1` exact canonical bytes, strict decoding, standalone signature-preimage bytes and negative/non-canonical rejection fixtures.

Changing expected bytes is not a routine refactor; it is a normative compatibility change.

### Deterministic simulator

`sim/simctl.py` executes versioned L1 virtual-time scenarios. Current fixtures cover route/NAT recovery, pairwise-backup rollback, bootstrap freshness and a first rekey loss/confirmation path.

A passing simulator scenario proves the modeled state/oracle behavior, not the full production implementation.

## 6. CI is not the oracle

A green workflow demonstrates repository consistency with the currently frozen vectors and modeled deterministic scenarios.

It does **not** prove cryptographic correctness, real ChainAdapter behavior, Android platform behavior or real-network resilience.

Security acceptance still requires the higher test levels in `ADVANCED_DEVELOPMENT.md`, cross-language vectors, L2/L3/L4/L5 testing and independent/human review where specified.

## 7. Scenario/oracle integrity

Agents MUST NOT make a failing security scenario green by simply deleting/weakening the assertion or changing a normative vector to match buggy implementation behavior.

The acceptable flow is:

```text
failing oracle
 -> determine code bug vs explicit normative-spec change
 -> fix implementation when spec is unchanged
 -> or request human review for normative change
```

Vector/scenario changes that alter a security property require the same human gate as the corresponding normative document.

## 8. Current enforcement caveat

Until GitHub branch/ruleset protection is actually enabled, these controls are partly procedural rather than server-enforced.

Agents must therefore behave conservatively:

> **normative/security-sensitive changes go through a branch + PR + human merge, even if GitHub technically permits a direct push.**
