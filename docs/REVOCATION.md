# Freedom — Revocation & Freshness Model

Status: **canonical / normative design rules**.

Schema source of truth: [`../spec/freedom.cddl`](../spec/freedom.cddl).
Normative baseline: [`SECURITY_INVARIANTS.md`](SECURITY_INVARIANTS.md).
Control-plane verification: [`CONTROL_PLANE_SECURITY.md`](CONTROL_PLANE_SECURITY.md).

## 1. Obiettivo

Freedom deve poter verificare che una DeviceKey, una delegated authorization key o un root epoch non siano stati revocati senza trasformare una singola RPC in trust anchor e senza mettere la chain nel packet hot path di ogni frame.

La verifica distingue:

```text
certificate validity
+ current key/authorization epoch
+ revocation state
+ freshness of the revocation view
```

Un certificato firmato correttamente ma revocato non è valido per un nuovo handshake.

## 2. Device revocation

Schema canonico: `device-revocation-record` in `spec/freedom.cddl`.

Semantica:

```text
DeviceRevocationRecord {
    device_record_commitment
    revoked_key_epoch_floor
    revocation_epoch
    effective_height
    ...
}
```

Regola:

```text
certificate.key_epoch <= revoked_key_epoch_floor
    -> certificate revoked
```

Una revoca è monotona. Un record successivo non può abbassare `revoked_key_epoch_floor` o `revocation_epoch`.

## 3. Authorization-key revocation

Schema canonico: `authorization-revocation-record`.

Serve quando viene compromessa/ritirata una `DeviceAuthorizationDelegation`.

Regola:

```text
certificate.authorization_epoch <= revoked_authorization_epoch_floor
    -> certificate chain revoked
```

Una nuova delegation usa un `authorization_epoch` maggiore e non riattiva automaticamente certificati vecchi.

## 4. Root epoch / UserRootRotation

Un `UserRootRotation` valido crea un nuovo `root_epoch`.

Dopo l'activation height:

- nuovi DeviceCertificate devono legarsi al nuovo root epoch;
- vecchie delegation/certificate possono essere rifiutate secondo la rotation policy;
- rollback a un root epoch precedente già superato è vietato;
- un peer già associato al contatto conserva continuity solo se la root transition supera la policy prevista.

`LOST_DEVICE` non cambia il root epoch. `ROOT_COMPROMISE` sì.

## 5. Revocation proof

Per stato production security-sensitive il client accetta revocation/non-revocation soltanto tramite stato riconducibile a un `VerifiedControlPlaneCheckpoint`.

A seconda del layout del `ChainAdapter`, la prova può essere:

```text
inclusion proof of ACTIVE/current record
or
inclusion proof of revocation record
or
non-inclusion proof within a canonical revocation namespace
```

La semantica concreta deve essere univoca per adapter e coperta da test vector.

Un `404`, `null` o "not found" restituito da una RPC non è una prova di non-revoca.

## 6. Freshness classes

Il protocollo definisce classi di freshness, non una singola regola globale:

```text
FRESHNESS_STRICT
FRESHNESS_NORMAL
FRESHNESS_DEGRADED_EXISTING_SESSION
```

### FRESHNESS_STRICT

Per operazioni ad alto rischio, per esempio:

- primo handshake dopo root/device recovery;
- nuova DeviceKey mai vista;
- install/update di release security-sensitive;
- security/governance transitions.

Richiede checkpoint entro il limite configurato dalla `SecurityPolicy`.

### FRESHNESS_NORMAL

Per nuovi handshake ordinari con certificate già noto e cache verificata sufficientemente recente.

### FRESHNESS_DEGRADED_EXISTING_SESSION

Può consentire a una sessione già autenticata di continuare per un periodo bounded quando il control-plane è temporaneamente irraggiungibile, senza dichiarare nuova revocation freshness.

Non autorizza silenziosamente un nuovo peer/device sconosciuto.

## 7. Stale state

Quando la freshness non soddisfa la policy:

```text
REVOCATION_STATE_STALE
```

Il client deve scegliere esplicitamente tra:

- refresh tramite provider/path alternativo;
- continuazione bounded di una sessione già autenticata se policy lo consente;
- failure di un nuovo handshake high-risk.

Non deve trasformare assenza di connettività al control-plane in `NOT_REVOKED`.

## 8. Highest-seen anti-rollback

Persistire almeno:

```text
highest_verified_checkpoint
highest_root_epoch per contact relationship
highest_authorization_epoch per device chain
highest_device_key_epoch per known device record
highest_revocation_epoch per revocation namespace
```

Stato validamente provato ma inferiore al highest-seen rilevante viene rifiutato come rollback.

## 9. Bootstrap freshness

Un device nuovo non ha highest-seen locale. Per questo il first-install verifier e ogni release recente portano un `BootstrapFreshnessFloor`:

```text
minimum_checkpoint_height
minimum_checkpoint_hash?
minimum_signer_set_epoch
minimum_policy_epoch
issued_in_release_id
```

Un primo bootstrap rifiuta stato sotto il floor incorporato nell'artifact/verifier che sta eseguendo.

Limite inevitabile:

> se anche il bootstrap verifier stesso è una copia autentica ma molto vecchia ottenuta da un ambiente completamente controllato dall'attaccante, il protocollo non può dedurre da solo che esista stato più recente.

Per questo l'assurance di freshness del **verifier stesso** deriva dal canale indipendente con cui viene ottenuto/aggiornato (store, OS/OEM, fingerprint/out-of-band o altro anchor indipendente). Freedom non promette freshness dal nulla.

## 10. DeviceCertificate validation order

Per un nuovo handshake:

```text
1. parse canonical certificate
2. verify signing domain / deterministic encoding
3. verify RootIdentity/contact relationship
4. verify delegation signature and scope
5. verify certificate capabilities subset of delegation
6. verify certificate expiry <= delegation expiry
7. verify DeviceKey possession
8. verify key/root/authorization epochs against highest-seen
9. verify current-enough revocation state proof
10. only then mark peer/device authenticated
```

## 11. Invarianti

- `not found from RPC != non-revoked`;
- revocation epochs/floors sono monotoni;
- certificate child authority non supera la delegation parent;
- stale revocation state non viene mascherato da `VERIFIED`;
- first install usa `BootstrapFreshnessFloor`;
- un verifier autentico ma obsoleto non viene descritto come magicamente freshness-aware;
- rollback di root/authorization/key/revocation epoch già osservati è vietato.
