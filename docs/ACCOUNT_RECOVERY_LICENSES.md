# Freedom — Account Recovery & Licenses

Status: **canonical design draft**

Normative security rules: [`SECURITY_INVARIANTS.md`](SECURITY_INVARIANTS.md).

## 1. Separazione delle identità

```text
RootIdentity                    -> ownership / recovery
DeviceCertificate               -> autorizzazione offline DeviceKey
DeviceKey                       -> chiave operativa del device
DeviceRecordCommitment          -> handle opaco control-plane
PairwiseContactAlias            -> relazione specifica
Session keys                    -> materiale effimero
EntitlementCommitment           -> licenza/capacità domain-separated
SponsorshipCommitment           -> sponsorship domain-separated
```

Freedom non richiede un `DeviceID` globale user-facing o transport-facing.

## 2. Prima installazione

La prima installazione genera localmente:

```text
RootKeyPair
RootIdentity
DeviceKeyPair
device_random_secret
DeviceRecordCommitment
Recovery Kit
```

e **0 mandatory chain writes**.

La registrazione/activation avviene quando serve rendere lo stato verificabile.

## 3. Recovery Kit

```text
EncryptedRecoveryBundle {
    version
    root_secret_ciphertext
    root_identity_commitment_or_fingerprint
    kdf_params
    checksum
}
```

Il QR non contiene la Root private key in chiaro. Il recovery code separato è necessario per decifrare/derivare la chiave del bundle.

## 4. Ripristino

```text
Restore
 -> recover RootIdentity
 -> generate NEW DeviceKey
 -> generate NEW DeviceRecordCommitment
 -> authorize/activate device
 -> wait verified finality/state
 -> issue/use NEW DeviceCertificate
 -> resolve entitlement proof/state
```

Un restore non clona la vecchia DeviceKey o il vecchio device record.

## 5. DeviceCertificate

Dopo activation verificata:

```text
DeviceCertificate {
    version
    network_id
    root_identity_commitment_or_proof
    device_public_key
    key_epoch
    protocol_version
    capabilities?
    issued_at
    expires_at
    certificate_id
    root_authorization_signature
}
```

Il peer lo verifica offline durante handshake; control-plane/cache serve per revocation/freshness.

## 6. Entitlement domain-separated

Una licenza segue la continuità della RootIdentity, ma il control-plane non riusa `root_commitment` come global account identifier.

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

`EntitlementCommitment` è domain-separated da device authorization, payment, sponsorship e pairwise identity.

## 7. Multi-device

```text
active_devices <= max_devices
```

Policy iniziale:

```text
FREE     -> 1 device attivo
PRO      -> max_devices definito dal piano
BUSINESS -> policy dedicata
```

L'attivazione usa stato device/account privacy-preserving:

```text
ActivateDevice {
    device_authorization_commitment
    device_record_commitment
    device_public_key
    key_epoch
    entitlement_epoch
    nonce
    root_signature
}
```

Il successo locale arriva solo dopo finalità + verifica dello stato risultante.

## 8. Privacy degli slot device

Non pubblicare strutture leggibili `RootIdentity -> devices[]`.

Preferire slot/commitment opachi. `DeviceRecordCommitment` non viene riutilizzato come contact alias, payment binding o token relay.

## 9. Contatti Free

Il contatto logico rappresenta una persona/RootIdentity, non un device.

Policy iniziale:

```text
base_contact_slots = 10
```

Rubrica locale/cifrata; nessun social graph pubblico.

Se serve enforcement resistente a client modificati, usare commitment/slot/nullifier opachi.

## 10. Relay Contributor

```text
FREE                     10 contact slots
FREE + RELAY CONTRIBUTOR 20 contact slots
```

Il benefit è temporaneo e deriva da contributo relay reale del device.

```text
EntitlementBenefit {
    benefit_type = RELAY_CONTRIBUTOR_CONTACTS
    value = 10
    issued_at
    expires_at
    proof_commitment
    status
}
```

Il nuovo device ripristinato deve contribuire di nuovo; il benefit non viene clonato automaticamente.

Se il benefit scade:

- non cancellare contatti;
- non invalidare RootIdentity;
- non terminare sessioni;
- impedire solo nuove aggiunte sopra quota finché la policy torna valida.

## 11. Verified finality

Activation, revocation, entitlement change e benefit state non sono considerati riusciti dal solo transaction hash.

```text
submit
 -> acceptable finality
 -> execution success
 -> state verification
 -> local transition
```

## 12. Invarianti

- RootIdentity, DeviceCertificate, DeviceKey, commitment, pairwise alias e session keys separati;
- nessun global DeviceID network-facing;
- recovery genera nuova DeviceKey;
- entitlement commitment domain-separated;
- multi-device enforcement senza device list pubblica;
- contatto = persona/RootIdentity;
- rubrica non pubblica;
- Relay Contributor non diventa Pro;
- scadenza benefit non cancella contatti;
- Recovery Kit resta utile anche se servizi commerciali ufficiali sono indisponibili;
- transaction hash != success.
