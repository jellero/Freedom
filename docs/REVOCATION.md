# Freedom — Revocation & Freshness Model

Status: **canonical / normative design rules**.

Schema: [`../spec/freedom.cddl`](../spec/freedom.cddl).
Normative baseline: [`SECURITY_INVARIANTS.md`](SECURITY_INVARIANTS.md).
Control-plane: [`CONTROL_PLANE_SECURITY.md`](CONTROL_PLANE_SECURITY.md).

## 1. Obiettivo

Freedom verifica certificate validity + key/authorization/root epochs + revocation state + freshness senza rendere una singola RPC trust anchor.

## 2. Device revocation

Schema canonico: `device-revocation-record`.

```text
certificate.key_epoch <= revoked_key_epoch_floor
 -> revoked
```

Revocation epoch/floor è monotono.

Authorization può essere:

```text
DEVICE_CONTROL
RECOVERY_OR_SUCCESSOR
```

La seconda modalità consente recovery revocation quando la scoped DeviceControlKey non è disponibile, purché la proof sia autorizzata dalla current verified identity/recovery state.

## 3. Authorization-key revocation

Schema: `authorization-revocation-record`.

```text
certificate.authorization_epoch <= revoked_authorization_epoch_floor
 -> certificate chain revoked
```

Una nuova delegation usa epoch maggiore e non riattiva certificati vecchi.

## 4. RootControlState / root epoch

Root continuity/recovery può usare un opaque `root_control_commitment` con current root epoch e recovery-policy commitment.

Un `UserRootRotation` valido porta a un root epoch maggiore. Rollback a root epoch precedente viene rifiutato.

`root_control_commitment` non è routing/contact identity, ma può correlare gli eventi della stessa recovery lineage sul control-plane.

## 5. Sticky recovery policy

Se la lineage ha una `UserRecoveryPolicy`, una normal root rotation **MUST** ereditare lo stesso recovery-policy commitment.

La current root da sola non può rimuovere/sostituire la policy.

V1 non supporta recovery-policy mutation arbitraria.

## 6. Compromise recovery pending

Una `COMPROMISE_RECOVERY` validata dal recovery quorum può targettare il latest current root state della stessa `root_control_commitment` lineage.

Quando accepted:

```text
RECOVERY_PENDING
```

fino all'activation height:

- normal root rotations sono bloccate;
- recovery policy mutation è bloccata;
- high-risk device authorization può essere bloccata/pending;
- current root non può cancellare unilateralmente la recovery;
- cancellation/replacement richiede la independent recovery authority.

Questo impedisce alla root rubata di evadere dalla recovery policy tramite normal rotation race.

## 7. Revocation proof

Production revocation/non-revocation deriva da stato riconducibile a `VerifiedControlPlaneCheckpoint`.

A seconda dell'adapter:

```text
inclusion proof current active state
or
inclusion proof revocation state
or
canonical non-inclusion proof
```

La semantica deve essere univoca e coperta da vectors.

`404`, `null`, `not found` da RPC non è non-revocation proof.

## 8. Freshness classes

```text
FRESHNESS_STRICT
FRESHNESS_NORMAL
FRESHNESS_DEGRADED_EXISTING_SESSION
```

Strict: post-recovery, new/unseen DeviceKey, high-risk release/governance/security operations.

Normal: ordinary new handshake with known certificate and sufficiently fresh verified cache.

Degraded-existing-session: bounded continuation di una sessione già autenticata durante temporary control-plane outage; non autorizza un nuovo unknown peer/device.

## 9. Stale state

Freshness failure -> `REVOCATION_STATE_STALE`.

Possibili azioni: alternate provider/path refresh, bounded continuation of existing session if policy allows, oppure high-risk new-handshake failure.

Outage non diventa `NOT_REVOKED`.

## 10. Highest-seen

Persistire almeno:

```text
highest_verified_checkpoint
highest_root_epoch per contact/root lineage
highest_authorization_epoch per known chain
highest_device_key_epoch per device record
highest_revocation_epoch per namespace
```

Proof valido ma inferiore al highest-seen rilevante -> rollback reject.

## 11. Bootstrap freshness

Fresh install usa `BootstrapFreshnessFloor` della propria release/verifier.

State sotto il floor viene rifiutato.

Un verifier autentico ma esso stesso molto vecchio, ottenuto soltanto da canali attacker-controlled, non può conoscere magicamente uno state più recente; verifier freshness richiede independent bootstrap assurance.

## 12. DeviceCertificate validation order

```text
canonical parse
 -> signing domain
 -> expected contact/root proof
 -> delegation signature/scope
 -> child capability/expiry checks
 -> device-record binding
 -> DeviceKey possession
 -> highest-seen root/auth/key epochs
 -> current-enough revocation proof
 -> authenticated
```

## 13. Invarianti

- RPC not-found != non-revoked;
- revocation floors/epochs monotonic;
- child certificate authority <= parent delegation;
- stale revocation state not `VERIFIED`;
- root rollback rejected;
- recovery policy sticky through normal root rotation;
- pending compromise recovery cannot be cancelled by current root alone;
- first install enforces freshness floor.
