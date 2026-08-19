# Freedom — Account Recovery & Licenses

Status: **canonical design draft**.

Normative security rules: [`SECURITY_INVARIANTS.md`](SECURITY_INVARIANTS.md).
Identity: [`IDENTITY_MODEL.md`](IDENTITY_MODEL.md).
Schema: [`../spec/freedom.cddl`](../spec/freedom.cddl).

## 1. Separazione

```text
RootRecoveryKey        -> cold recovery / continuity
UserRecoveryPolicy     -> independent compromise-recovery authority
DeviceAuthorizationKey -> delegated device authorization
DeviceCertificate      -> offline DeviceKey authorization
DeviceKey              -> operational device key
DeviceControlKey       -> scoped control-plane record control
RecoveryStateKey       -> encrypted pairwise backup
EntitlementCommitment  -> domain-separated commercial state
```

No global DeviceID network-facing.

## 2. Prima installazione

```text
RootRecoveryKey
RootIdentity
UserRecoveryPolicy optional-but-required-for-compromise-recovery
DeviceAuthorizationKey + delegation
DeviceKey
DeviceRecordCommitment + DeviceControlKey
RecoveryStateKey
Recovery Kit
0 mandatory chain writes
```

## 3. Recovery Kit envelope

Il Recovery Kit usa >=128-bit random recovery entropy, >=128-bit random salt, memory-hard KDF, versioned KDF params e standard AEAD. Local rate limiting non sostituisce entropy/KDF contro offline brute force.

## 4. Root compromise non è normal restore

```text
LOST_DEVICE
 -> revoke old device
 -> authorize replacement

ROOT_COMPROMISE
 -> independent recovery quorum
 -> recovery delay
 -> UserRootRotation
 -> new root epoch
```

Se l'utente possiede soltanto una RootRecoveryKey e nessun recovery factor indipendente precommitted, Freedom non può distinguere proprietario e ladro dopo furto completo di quella secret. In quel profilo non promette compromise recovery.

## 5. UserRecoveryPolicy — sticky by default

Schema canonico: `user-recovery-policy`.

La policy viene fissata **prima dell'incidente** e non può essere rimossa/sostituita unilateralmente dalla sola RootRecoveryKey corrente.

V1 rule:

```text
normal root rotation
 -> inherits the same UserRecoveryPolicy commitment
```

Quindi una root già compromessa non può fare una rotazione normale e cancellare immediatamente l'unica authority capace di recuperarla.

Una futura modifica della recovery policy richiede una state machine separata esplicitamente specificata e almeno l'autorità del recovery quorum corrente; finché tale transition object non è congelato nella specifica, **policy mutation non è una operazione V1 supportata**.

## 6. Compromise-recovery race semantics

`COMPROMISE_RECOVERY` può essere proposto dal recovery quorum contro **l'ultimo root epoch corrente** della stessa lineage, anche se un attacker ha già eseguito una normale root rotation usando una root rubata.

Quando una valid compromise-recovery transition entra in stato pending:

```text
RECOVERY_PENDING
```

fino alla sua activation height:

- nuove normal root rotations sono bloccate;
- modifiche della recovery policy sono bloccate;
- nuove high-risk device authorizations possono essere bloccate o marcate pending secondo policy;
- la old/current root non può cancellare da sola la recovery transition;
- cancellazione/sostituzione richiede la stessa independent recovery authority prevista dalla policy.

Dopo il delay, se il recovery quorum proof resta valido, la transition porta al nuovo root epoch e supersede la compromised lineage state precedente.

## 7. Ripristino normale

```text
Restore
 -> decrypt Recovery Kit
 -> recover current RootIdentity/root epoch
 -> generate NEW DeviceAuthorizationKey if needed
 -> generate NEW DeviceKey
 -> generate NEW DeviceRecordCommitment/DeviceControlKey
 -> create opaque record
 -> verify finalized state
 -> issue/use NEW DeviceCertificate
 -> resolve entitlement
```

Non clonare vecchie DeviceKey.

## 8. Pairwise recovery

Due path:

```text
A. surviving authorized device -> authenticated pairwise-state transfer
B. encrypted PairwiseRecoveryBundle -> bytes from user-chosen backup source
```

La source del backup è non fidata.

Dopo restore:

```text
reject detectable rollback
 -> re-authenticate each peer
 -> rotate/re-derive future rendezvous state
 -> establish fresh session
```

Una vecchia copia del bundle non deve diventare future rendezvous authority indefinita.

Se manca surviving device e backup valido, ownership/entitlement possono tornare ma i contatti richiedono re-bootstrap.

## 9. Device/contact quotas V1

`max_devices`, `base_contact_slots` e bonus Relay Contributor sono product/service policy, non security/interoperability invariants.

Un client modificato può aggirare una quota locale; il protocollo non pubblica social/device graph per impedirlo.

## 10. Verified state / revocation

Activation, revocation, root rotation ed entitlement changes richiedono finality proof + execution success + resulting-state proof + anti-rollback.

Freshness/revocation segue `REVOCATION.md`; `RPC not found` non è non-revocation proof.

## 11. Invarianti

- RootRecoveryKey distinta da authorization/control/device keys;
- compromise recovery richiede independent precommitment;
- recovery policy V1 è sticky across normal root rotation;
- compromised root alone cannot remove recovery policy or cancel pending compromise recovery;
- normal restore genera nuova DeviceKey;
- pairwise backup resta ciphertext;
- post-restore future pairwise state viene ruotato/re-derivato;
- contact/device quota V1 non è interoperability rule;
- tx hash != success.
