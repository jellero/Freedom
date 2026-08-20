# Freedom shared core

Status: **host-side executable protocol state-machine core**.

`core/src/main/java` contains pure Java 17 security/state transition logic shared by:

- the deterministic host simulator (`sim/simctl.py` through `sim/jvm/CoreStateServer.java`);
- the Android source set (`app/build.gradle.kts` includes the same source tree);
- future host services/adapters where the same transition logic is applicable.

The core deliberately has **no Android, socket, RPC, blockchain SDK or cryptographic-provider dependency**. Those are adapters around it.

Current implemented state machines:

```text
RouteState
  route failure / alternate relay recovery
  peer identity remains independent from route
  no mailbox write

PairwiseRecoveryState
  verified monotonic recovery anchor
  stale/mismatched backup rejection
  post-restore future-state rotation

BootstrapFreshnessState
  BootstrapFreshnessFloor
  proof-validity gate
  highest-seen rollback rejection

MutationVerificationState
  tx hash/submission is not success
  finality + execution + resulting-state proof + exact transition
  failed follow-up operation does not erase prior committed state
  resulting-state rollback rejected

NetworkAnchorState
  exact fresh-install bootstrap pin
  network / adapter / chain-network / verifier-profile binding
  monotonic anchor and checkpoint lineage
  signer-set transition gate
  threshold authorization does not replace chain consensus continuity
  rejected candidate leaves previous trusted anchor intact

RekeyState
  STABLE -> INIT_SENT -> NEW_KEY_PENDING_ACK -> STABLE(next epoch)
  exact +1 epoch
  old-send-key erasure state after confirmed Ack
```

These classes implement only transitions already defined by the canonical specification. They do not invent cryptography and do not prove that the current Android spike already uses every state machine in its live flows.

## Fast host loop

Compile and self-test without building an APK:

```bash
python tools/run_core_tests.py
```

Run all L1 scenarios against the same compiled core:

```bash
python sim/simctl.py --all
```

The simulator's Python code is orchestration, virtual time, faults and assertions. Protocol transition state belongs here.

## Android relationship

The Android source set compiles `core/src/main/java` directly. Platform-specific code must call these state machines rather than copying their rules into Activities/services when the canonical implementation replaces the old spike.

## Control-plane relationship

The shared core defines client-side acceptance semantics. A real `ChainAdapter` supplies cryptographically verified facts/proofs; the core never treats an RPC response or a governance signature alone as trusted chain state.

For NetworkAnchor specifically, the adapter verifies canonical bytes/signatures, exact adapter payload binding and consensus continuity. `NetworkAnchorState` then enforces bootstrap pinning, context/epoch/height monotonicity and signer-set transition semantics defined in `docs/NETWORK_ANCHORS.md`.

## Change discipline

A security-relevant core behavior change requires:

1. canonical-spec justification;
2. core self-test/regression;
3. L1 scenario coverage where applicable;
4. vectors when wire/crypto-domain semantics change;
5. L2/L3/L4 coverage when routing/control-plane/proof behavior is affected;
6. human review for normative semantic changes.
