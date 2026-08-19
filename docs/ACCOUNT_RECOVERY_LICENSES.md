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

Il Recovery Kit deve usare:

- >=128-bit random recovery entropy;
- >=128-bit random salt;
- memory-hard KDF (`Argon2id` target o standard equivalente reviewato);
- versioned KDF params;
- standard AEAD;
- checksum soltanto come detection/UX, non authentication.

Rate limiting locale non sostituisce entropy/KDF contro brute force offline.

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

Se l'utente possiede soltanto una RootRecoveryKey e nessun recovery factor indipendente precommitted, Freedom non può distinguere proprietario e ladro dopo furto completo di quella secret. In quel profilo il progetto non promette compromise recovery.

## 5. UserRecoveryPolicy

Schema canonico: `user-recovery-policy`.

La policy può usare più recovery keys/shares distribuite tra custody domains indipendenti.

Il policy commitment è fissato prima dell'incidente; non può essere inventato dopo che la root è già compromessa.

## 6. Ripristino normale

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

## 7. Pairwise recovery

Due path:

```text
A. surviving authorized device
   -> authenticated pairwise-state transfer

B. encrypted PairwiseRecoveryBundle
   -> bytes from user-chosen backup source
   -> decrypt/verify locally
```

Schema canonico: `pairwise-recovery-bundle`.

La source dei backup è non fidata; può essere file locale esportato, private cloud scelto dall'utente, storage dell'organizzazione o più mirror di ciphertext.

Il bundle contiene `state_epoch`, `recovery_key_epoch`, `contacts_metadata_ciphertext`, `pairwise_state_ciphertext` e integrity data.

Dopo restore:

```text
reject detectable rollback
 -> re-authenticate each peer
 -> rotate/re-derive future rendezvous state
 -> establish fresh session
```

Una copia vecchia del bundle non deve diventare future rendezvous authority indefinita.

Se manca surviving device e manca backup valido, ownership/entitlement possono tornare ma i contatti richiedono re-bootstrap.

## 8. Device quota V1

`max_devices` commerciale V1 è product/service policy del client ufficiale, non security/interoperability invariant.

Un client modificato può aggirare una policy locale; questo non concede impersonation o accesso ai plaintext altrui.

Hard enforcement privacy-preserving futuro richiede credential/nullifier/ZK reviewati.

## 9. Contact slots V1

`base_contact_slots = 10` è anch'esso product policy locale/servizio, non protocol security primitive.

No social graph pubblico per enforcement commerciale.

## 10. Relay Contributor

```text
FREE                     10 product slots
FREE + RELAY CONTRIBUTOR 20 product slots
```

È un incentivo UX/commerciale del client ufficiale, non una garanzia anti-tamper del protocollo. Il modello economico non deve dipendere esclusivamente da questo bonus.

## 11. Verified state / revocation

Activation, revocation, root rotation ed entitlement changes richiedono finality proof + execution success + resulting-state proof + anti-rollback.

Freshness/revocation segue `REVOCATION.md`; `RPC not found` non è non-revocation proof.

## 12. Invarianti

- RootRecoveryKey distinta da DeviceAuthorizationKey, DeviceControlKey e DeviceKey;
- root-compromise recovery richiede independent precommitment;
- recovery normale genera nuova DeviceKey;
- pairwise backup resta ciphertext;
- post-restore pairwise future state viene ruotato/re-derivato;
- assenza backup implica re-bootstrap;
- contact/device quota V1 non è interoperability rule;
- transaction hash != success.
