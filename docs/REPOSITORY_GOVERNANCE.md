# Freedom — Repository Governance

Status: **engineering governance / required for agentic development**.

This document governs how canonical specifications and security-sensitive changes move into `main`.

## 1. Problem

A Markdown rule saying “human review required” is not enough if an automated agent can push directly to an unprotected default branch.

Target repository policy:

```text
agent/worktree branch
 -> automated consistency/security checks
 -> pull request
 -> human review for normative/security changes
 -> required checks green
 -> merge to protected main
```

## 2. Protected main requirement

`main` SHOULD be configured in GitHub repository settings with branch/ruleset protection that at minimum:

- requires pull requests before merge for normative/security-sensitive changes;
- requires the spec-consistency workflow;
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
docs/IDENTITY_MODEL.md
docs/PROTOCOL.md
docs/THREAT_MODEL.md
docs/SHIELD.md
docs/EMERGENCY_UPDATES.md
docs/APP_DISTRIBUTION.md
AGENTS.md
```

`CODEOWNERS` names the human owner for these paths.

## 4. Agent permissions

Agents may:

- create branches/worktrees;
- implement changes;
- add/update tests;
- open PRs;
- respond to review;
- update non-normative docs consistent with canonical state.

Agents MUST NOT independently merge a change that weakens a security invariant, changes a trust assumption, signing/AEAD domain, recovery/revocation/governance state machine or canonical signed schema.

## 5. Specification consistency CI

`.github/workflows/spec-consistency.yml` runs `tools/check_spec_consistency.py`.

The checker is intentionally small and dependency-free. It verifies repository-level invariants such as:

- required canonical files exist;
- README does not absorb the internal Codex/Docker development method;
- known stale terminology/formulas do not reappear;
- required CDDL object families remain present;
- SVG concept/architecture assets remain well-formed XML;
- key normative docs continue linking to the canonical schema.

It does **not** prove cryptographic correctness. It is drift detection, not security review.

## 6. CI is not the oracle

A green workflow means only that automated repository checks passed.

Security acceptance still requires the test levels in `ADVANCED_DEVELOPMENT.md`, canonical vectors and independent/human review where specified.

## 7. Current enforcement caveat

Until GitHub branch/ruleset protection is actually enabled, these controls are partly procedural rather than server-enforced.

Agents must therefore behave conservatively:

> **normative/security-sensitive changes go through a branch + PR + human merge, even if GitHub technically permits a direct push.**
