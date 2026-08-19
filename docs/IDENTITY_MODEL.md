# Freedom — Identity Model

Status: **canonical design draft**

Normative security rules: [`SECURITY_INVARIANTS.md`](SECURITY_INVARIANTS.md).
Control-plane details: [`CONTROL_PLANE_SECURITY.md`](CONTROL_PLANE_SECURITY.md).

## 1. Obiettivo

Freedom autentica persone e device, ruota/revoca chiavi e supporta multi-device senza trasformare un identificatore globale in un indirizzo di rete.

> **Freedom non usa un `DeviceID` globale come identità pubblica o identificatore di trasporto.**

Separazione canonica:

```text
RootRecoveryKey                 -> cold recovery / user-root continuity
RootIdentity                    -> ownership identity / root epoch
DeviceAuthorizationKey          -> delegated device-authorization epoch
DeviceCertificate               -> offline authorization of DeviceKey
DeviceKey                       -> operational device authentication
DeviceRecordCommitment          -> opaque control-plane handle
PairwiseContactAlias            -> relationship-specific alias
PairRendezvousSecret            -> pairwise recovery/rendezvous secret
TransportToken                  -> temporary route/circuit token
Session keys                    -> ephemeral E2EE
EntitlementCommitment           -> domain-separated entitlement state
PaymentBindingCommitment        -> domain-separated payment state
SponsorshipCommitment           -> domain-separated sponsorship state
```

## 2. Root key hierarchy

La chiave di recovery non deve essere usata come chiave operativa quotidiana.

```text
RootRecoveryKey
   |
   +-> RootIdentity / root epoch
   |
   `-> DeviceAuthorizationDelegation
             |
             `-> DeviceCertificate
                        |
                        `-> DeviceKey
```

`RootRecoveryKey` deve restare cold/offline quando la piattaforma e UX lo consentono.

## 3. RootIdentity

```text
RootIdentity {
    version
    root_public_key
    root_commitment
    root_epoch
    recovery_policy_commitment?
}
```

Serve per ownership continuity e recovery, non per routing, messaging, payment reference o session encryption.

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

La delegation può essere ruotata senza usare direttamente `RootRecoveryKey` per ogni device action.

Compromettere `DeviceAuthorizationKey` non deve automaticamente compromettere la RootRecoveryKey.

## 5. Domain-separated account state

```text
DeviceAuthorizationCommitment
EntitlementCommitment
PaymentBindingCommitment
SponsorshipCommitment
```

sono separati per dominio.

Domain separation impedisce uguaglianza/riuso diretto ma non garantisce da sola unlinkability se più commitment compaiono nella stessa transazione o prova.

## 6. DeviceRecordCommitment

```text
DeviceRecordCommitment = H(
    device_authorization_context ||
    device_random_secret ||
    domain_separator
)
```

```text
DeviceRecord {
    version
    device_record_commitment
    device_public_key
    key_epoch
    status
    protocol_version
    authorization_proof
}
```

Non è username, contact ID, route ID o relay token.

## 7. Privacy del device authorization

Una firma RootIdentity pubblicata direttamente accanto a `device_public_key` renderebbe osservabile il mapping account→device.

Target production:

```text
DeviceAuthorizationProof {
    device_record_commitment
    device_public_key
    key_epoch
    slot_nullifier
    authorization_policy_epoch
    proof
}
```

Il proof dimostra membership/authorization/slot valido senza pubblicare la RootIdentity.

La costruzione concreta può usare anonymous credentials / ZK membership e deve essere reviewata.

Se Testnet usa una prova linkabile, questa limitazione deve essere dichiarata esplicitamente e non spacciata per privacy production.

## 8. DeviceCertificate

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

Il peer verifica offline:

- delegation chain;
- network;
- DeviceKey binding;
- key/authorization epoch;
- expiry/freshness policy;
- expected contact relationship.

Revocation state proviene da control-plane/cache crittograficamente verificata, non dalla fiducia in un RPC.

## 9. Device activation / rotation

Concettualmente:

```text
ActivateDevice {
    device_authorization_proof
    device_record_commitment
    device_public_key
    key_epoch
    slot_nullifier
    nonce
}
```

Il target production evita `root_signature` pubblica linkabile come requisito di state transition.

Una rotazione incrementa `key_epoch` e produce nuovo `DeviceCertificate`.

## 10. UserRootRotation

Perdita di device e compromissione della root sono casi differenti.

```text
LOST_DEVICE
 -> revoke old DeviceKey
 -> authorize new DeviceKey

ROOT_COMPROMISE
 -> UserRootRotation
 -> new RootRecoveryKey / root epoch
 -> re-authorize devices
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

## 11. Contatto = persona / RootIdentity

La rubrica rappresenta una persona, non un device.

```text
Bob
  |- Phone DeviceCertificate
  |- Tablet DeviceCertificate
  `- Desktop DeviceCertificate
```

Bootstrap:

```text
FreedomContact {
    version
    network_id
    root_identity_proof
    contact_capability
    bootstrap_device_certificate?
    bootstrap_route_hints[]?
    expires_at?
}
```

La capability abilita bootstrap, non impersonation.

## 12. First-contact substitution

Un attaccante che sostituisce **l'intero descriptor** prima del primo bootstrap può far stabilire una relazione crittograficamente valida con la propria identity.

Questo non è risolvibile dalla sola E2EE.

Il client deve supportare:

```text
BOOTSTRAP_UNVERIFIED
CONTACT_VERIFIED
```

La promozione a `CONTACT_VERIFIED` può usare safety code/fingerprint/out-of-band verification.

Copiare un QR valido non concede private key; sostituire il descriptor prima del bootstrap è una minaccia differente.

## 13. Pairwise identity

Dopo handshake autenticato:

```text
PairSecret_AB
PairwiseContactAlias_AB
PairRendezvousSecret_AB
```

Relazioni differenti producono alias differenti.

## 14. Limite del claim pairwise

Pairwise alias/rendezvous riducono correlazione da parte dell'infrastruttura.

Non garantiscono automaticamente unlinkability contro **contatti colludenti** se Bob e Carol vedono lo stesso root proof/certificate material e lo confrontano.

Claim corretto base:

> Freedom non riutilizza un global routing/contact identifier tra relazioni.

Un claim più forte contro contatti colludenti richiede anonymous credential/pairwise-scoped identity proof specificamente implementati.

## 15. Pairwise recovery / multi-device

`PairSecret` e `PairRendezvousSecret` non vengono messi on-chain.

Recovery supportato:

```text
A. existing authorized device -> authenticated device-to-device transfer
B. encrypted PairwiseRecoveryBundle
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

Il bundle usa una `RecoveryStateKey` separata dal transport/session state e viene protetto dal Recovery Kit policy.

Se tutti i device sono persi e non esiste bundle pairwise valido:

> ownership può essere recuperata, ma i contatti devono essere re-bootstrapati.

Non ricostruire il social graph da stato pubblico.

## 16. Rendezvous

Gli slot derivano da `PairRendezvousSecret`:

```text
PairRendezvousSecret
 -> directional rotating slot
 -> encrypted RendezvousRecord / RecoveryBeacon
```

Il record pubblico non espone root/device/route mapping leggibile.

## 17. Handshake / negotiation

Il transcript lega:

```text
network_id
expected relationship
DeviceCertificate/delegation proofs
key epochs
supported versions from both peers
supported suites from both peers
selected version/suite
ephemeral keys
nonces
session_id
```

La selezione deve rispettare una policy anti-downgrade: autenticare solo la suite finale non è sufficiente se un attacker può fare offer stripping prima della scelta.

Il peer non accetta “qualunque chiave che firma se stessa”.

## 18. Forward secrecy

Freedom Communication richiede ephemeral key exchange, FS tra sessioni, bounded traffic-key lifetime, rekey e media keys separate.

Root/DeviceKey statiche autenticano; non devono ricostruire sessioni concluse.

## 19. Transport identity

```text
TransportToken
RelayCircuitToken
NextHopToken
RouteCapability
```

sono temporanei e separati dall'identity plane.

## 20. Message layer

Dentro una sessione autenticata non serve un global sender ID per ogni frame.

```text
ChatMessage {
    message_id
    logical_sequence
    sent_at
    body
    reply_to?
}
```

## 21. Privacy invariants

- no global DeviceID network-facing;
- RootIdentity non è routing ID;
- DeviceRecordCommitment non è contact ID;
- device authorization production non pubblica account→device mapping leggibile;
- pairwise aliases/rendezvous per relazione;
- commitment service domain-separated;
- no public social graph;
- old sessions non dipendono dalla futura segretezza della DeviceKey;
- colluding-contact unlinkability non viene promessa senza primitive dedicate.

## 22. Trade-off da misurare

- timing control-plane;
- activation/revocation pattern;
- transaction linkage;
- RPC/provider visibility;
- IP/ASN;
- relay ingress/egress timing;
- traffic size/timing;
- contact verification usability;
- pairwise backup attack surface.
