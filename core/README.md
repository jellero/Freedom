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

## Change discipline

A security-relevant core behavior change requires:

1. canonical-spec justification;
2. core self-test/regression;
3. L1 scenario coverage where applicable;
4. vectors when wire/crypto-domain semantics change;
5. human review for normative semantic changes.
