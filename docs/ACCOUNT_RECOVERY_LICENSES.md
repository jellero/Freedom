# Freedom — Account Recovery & Licenses

Status: **canonical design draft**

Normative security rules: [`SECURITY_INVARIANTS.md`](SECURITY_INVARIANTS.md).
Identity details: [`IDENTITY_MODEL.md`](IDENTITY_MODEL.md).

## 1. Separazione

```text
RootRecoveryKey              -> cold recovery / root continuity
RootIdentity                 -> ownership/root epoch
DeviceAuthorizationKey       -> delegated device authorization
DeviceCertificate            -> offline DeviceKey authorization
DeviceKey                    -> operational device key
DeviceRecordCommitment       -> opaque control-plane handle
PairwiseContactAlias         -> relationship state
RecoveryStateKey             -> encrypted pairwise backup
Session keys                 -> ephemeral communication
EntitlementCommitment        -> domain-separated license state
```

No global `DeviceID` network-facing.

## 2. Prima installazione

```text
RootRecoveryKey
RootIdentity
DeviceAuthorizationKey + delegation
DeviceKey
device_random_secret
DeviceRecordCommitment
RecoveryStateKey
Recovery Kit
0 mandatory chain writes
```

`RootRecoveryKey` non deve essere usata come chiave operativa per ogni handshake/device action.

## 3. Recovery Kit cryptographic envelope

```text
EncryptedRecoveryBundle {
    version
    kdf_id
    kdf_params
    salt
    nonce
    root_recovery_secret_ciphertext
    recovery_state_key_ciphertext?
    root_identity_fingerprint
    bundle_integrity
}
```

Requisiti minimi:

- recovery secret/code generato casualmente con **almeno 128 bit di entropia**; non una password breve scelta dall'utente;
- salt random almeno 128 bit;
- KDF memory-hard standard, target `Argon2id` (o sostituto standard equivalente dopo review);
- parametri KDF versionati e benchmarkati su device reali;
- AEAD standard per il bundle (`AES-GCM` o `ChaCha20-Poly1305`/equivalente reviewato);
- checksum non sostituisce AEAD authentication;
- rate limiting UX locale non è difesa sufficiente contro brute-force offline: la sicurezza deriva dall'entropia + KDF.

QR e recovery code devono essere conservabili separatamente.

## 4. DeviceAuthorizationDelegation

```text
DeviceAuthorizationDelegation {
    root_epoch
    authorization_public_key
    authorization_epoch
    capabilities
    valid_from
    expires_at
    root_recovery_signature
}
```

Una delegated key compromessa può essere revocata/ruotata senza cambiare automaticamente la RootRecoveryKey.

## 5. Ripristino normale

```text
Restore
 -> decrypt Recovery Kit
 -> recover RootIdentity/root epoch
 -> generate NEW DeviceAuthorizationKey if needed
 -> generate NEW DeviceKey
 -> generate NEW DeviceRecordCommitment
 -> privacy-preserving device activation
 -> verified finality/state proof
 -> NEW DeviceCertificate
 -> resolve entitlement proof/state
```

Non clonare vecchie DeviceKey.

## 6. Root compromise

Perdita di device e compromissione root sono distinte.

```text
LOST_DEVICE
 -> revoke old device
 -> authorize replacement

ROOT_COMPROMISE
 -> UserRootRotation
 -> new RootRecoveryKey / root epoch
 -> rotate DeviceAuthorizationDelegation
 -> re-authorize surviving devices
```

```text
UserRootRotation {
    old_root_epoch
    new_root_public_key
    new_root_commitment
    continuity_proof
    recovery_policy_proof
    issued_at
}
```

Una root compromessa non è “recuperata” continuando a usarla.

## 7. DeviceCertificate

```text
DeviceCertificate {
    version
    network_id
    root_identity_commitment_or_proof
    authorization_epoch
    device_public_key
    key_epoch
    protocol_version
    capabilities?
    issued_at
    expires_at
    certificate_id
    authorization_signature
}
```

Verifica offline; revocation/freshness da proof/cache verificati.

## 8. Multi-device privacy

```text
active_devices <= max_devices
```

Production target usa `DeviceAuthorizationProof`/slot nullifier per evitare una lista pubblica `RootIdentity -> devices[]`.

Una implementazione testnet linkabile deve dichiarare la limitazione.

## 9. Pairwise continuity

`PairSecret` / `PairRendezvousSecret` non vengono messi on-chain.

Due recovery path:

```text
A. surviving authorized device
   -> authenticated device-to-device pairwise-state transfer

B. Recovery Kit + encrypted PairwiseRecoveryBundle
```

```text
PairwiseRecoveryBundle {
    version
    state_epoch
    contacts_metadata_ciphertext
    pairwise_state_ciphertext
    integrity
}
```

Il bundle è cifrato con `RecoveryStateKey` e non pubblica il social graph.

Se non esiste device sopravvissuto né pairwise backup valido:

> la RootIdentity e l'entitlement possono essere recuperati, ma i contatti richiedono re-bootstrap.

## 10. Entitlement

```text
FreedomEntitlement {
    version
    entitlement_commitment
    tier
    issued_at
    expires_at?
    entitlement_epoch
    max_devices
    base_contact_slots
    policy_version
    status
}
```

`EntitlementCommitment` è domain-separated.

## 11. Contact slots V1

`base_contact_slots = 10` è una **policy del client ufficiale**, non un requisito di interoperabilità/security V1.

Quindi:

- la rubrica resta locale/cifrata;
- il control-plane non pubblica il social graph per far rispettare la quota;
- un client modificato può tecnicamente aggirare una policy locale: questo non consente impersonation o accesso a plaintext altrui;
- un futuro enforcement resistente a tampering richiede nullifier/ZK/credential dedicati prima di diventare normativo.

## 12. Relay Contributor

```text
FREE                     10 local product slots
FREE + RELAY CONTRIBUTOR 20 local product slots
```

Il benefit è entitlement/product policy; non modifica session acceptance di peer remoti.

Il nuovo device deve riqualificarsi se il benefit dipende da quel device.

Scadenza benefit non cancella contatti né termina sessioni; limita nuove aggiunte nel client ufficiale secondo policy.

## 13. Verified finality / time

Activation, revocation, root rotation ed entitlement changes richiedono:

```text
finality proof
+ execution success
+ resulting state proof
+ anti-rollback/highest-seen checks
```

Expiry usa `VerifiedTimeAnchor`/height/epoch quando possibile, non soltanto wall clock locale.

## 14. Invarianti

- RootRecoveryKey distinta da DeviceAuthorizationKey e DeviceKey;
- Recovery Kit >=128-bit recovery entropy + memory-hard KDF + AEAD;
- recovery normale genera nuova DeviceKey;
- root compromise usa `UserRootRotation`;
- pairwise state non è social graph on-chain;
- pairwise recovery è device-transfer o encrypted bundle;
- assenza di pairwise backup implica re-bootstrap contatti;
- multi-device production target privacy-preserving;
- contact-slot quota V1 non è interoperability rule;
- transaction hash != success.
