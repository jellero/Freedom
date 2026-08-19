# Freedom — Identity Model

Status: **canonical design draft**

Normative security rules: [`SECURITY_INVARIANTS.md`](SECURITY_INVARIANTS.md).

## 1. Obiettivo

Freedom deve autenticare persone e device, ruotare/revocare chiavi e far rispettare limiti multi-device senza trasformare un identificatore globale in un indirizzo visibile a tutta la rete.

Decisione architetturale:

> **Freedom non usa un `DeviceID` globale come identità pubblica o identificatore di trasporto.**

Separazione canonica:

```text
RootIdentity                    -> ownership / recovery
DeviceCertificate               -> autorizzazione offline della DeviceKey
DeviceKey                       -> chiave operativa del device
DeviceRecordCommitment          -> handle opaco del control-plane
PairwiseContactAlias            -> alias specifico della relazione
TransportToken                  -> token temporaneo di route/circuito
Session keys                    -> E2EE effimera
EntitlementCommitment           -> entitlement/licenza domain-separated
PaymentBindingCommitment        -> binding pagamento domain-separated
SponsorshipCommitment           -> anti-abuse/sponsorship domain-separated
```

Questi livelli **MUST NOT** essere riutilizzati automaticamente tra loro.

## 2. RootIdentity

`RootIdentity` rappresenta ownership e continuità dell'identità Freedom.

```text
RootIdentity {
    version
    root_public_key
    root_commitment
    root_epoch
}
```

Serve per:

- Recovery Kit;
- autorizzazione/revoca device;
- recovery dopo reset/furto;
- derivazione di commitment domain-separated;
- governance locale dell'identità.

Non viene usata come:

- message/media key;
- session key;
- contact alias di rete;
- routing identifier;
- payment provider reference.

Il `root_commitment` non deve essere inviato inutilmente nel network layer e non deve diventare il correlatore universale del control-plane.

## 3. Domain-separated account state

Quando un servizio richiede stato stabile, Freedom deve derivare commitment separati per dominio invece di riutilizzare un unico `root_commitment` ovunque.

Concettualmente:

```text
DeviceAuthorizationCommitment = H(root context, "device-auth", ...)
EntitlementCommitment         = H(root context, "entitlement", ...)
PaymentBindingCommitment      = H(root context, "payment", ...)
SponsorshipCommitment         = H(root context, "sponsorship", ...)
```

Requisiti:

- commitment differenti non devono essere linkabili per semplice uguaglianza;
- rendezvous pairwise non usa commitment account-global;
- il provider di pagamento non riceve commitment Freedom globali in plaintext salvo necessità dimostrata;
- enforcement futuri possono usare slot/nullifier/blind commitment/ZK proof se necessari per ridurre correlazione.

## 4. DeviceKey e DeviceRecordCommitment

Ogni device genera una propria `DeviceKey`.

Il control-plane mantiene un handle tecnico opaco:

```text
DeviceRecordCommitment = H(
    DeviceAuthorizationCommitment ||
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

Il commitment serve a lookup/rotation/revocation e può restare stabile durante una normale rotazione della DeviceKey, ma:

- non è username;
- non è contact ID;
- non è endpoint di rete;
- non deve essere annunciato ai relay quando non necessario;
- non deve comparire nei normali message frame;
- non deve essere riutilizzato come alias tra relazioni differenti.

## 5. DeviceCertificate — autorizzazione verificabile offline

La RootIdentity autorizza la DeviceKey corrente tramite un certificato verificabile senza RPC nel packet hot path:

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

Il certificato prova che la DeviceKey è stata autorizzata dalla RootIdentity attesa.

Un peer deve poter verificare offline:

```text
signature
network_id
DeviceKey binding
key_epoch
protocol/version constraints
expiry
expected RootIdentity/contact relationship
```

Il control-plane serve per revocation, rotation, freshness e recovery; **non deve essere una RPC obbligatoria per ogni handshake**.

Una cache di revoca troppo vecchia può richiedere refresh prima di un nuovo handshake secondo policy, ma nessuna singola RPC diventa trust anchor.

## 6. Device activation

Un nuovo device genera nuova DeviceKey e nuovo commitment e viene autorizzato dalla RootIdentity:

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

Dopo stato finalizzato/verificato il device può emettere/usare il `DeviceCertificate` coerente con il record.

Il control-plane deve poter far rispettare:

```text
active_devices <= max_devices
```

senza pubblicare un elenco leggibile dei device appartenenti alla stessa persona.

## 7. Rotation e revocation

```text
RotateDeviceKey {
    device_record_commitment
    old_epoch
    new_epoch
    new_public_key
    authorization_proof
}
```

Una rotazione incrementa `key_epoch` e produce un nuovo `DeviceCertificate`.

La revoca rende il record/certificato non valido per nuovi handshake secondo freshness policy.

Un attacker che conosce il commitment non ottiene automaticamente informazioni di routing o capacità di contatto.

## 8. Contatto = persona / RootIdentity

La rubrica rappresenta un contatto logico, non un singolo telefono.

```text
Bob / RootIdentity
  |- Phone
  |- Tablet
  `- Desktop
```

Bootstrap intenzionale tramite QR/link/NFC/copia-incolla:

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

`contact_capability` abilita bootstrap/rendezvous iniziale ma non concede impersonation.

## 9. Pairwise identity

Dopo il primo handshake autenticato Alice e Bob derivano stato specifico della relazione:

```text
PairSecret_AB
PairwiseContactAlias_AB
PairRendezvousSecret_AB
```

Relazioni differenti devono produrre alias differenti:

```text
Alice <-> Bob     alias X
Alice <-> Carol   alias Y
```

Conoscere X non deve consentire di collegarlo automaticamente a Y.

## 10. Rendezvous

Gli slot rendezvous/recovery derivano da `PairRendezvousSecret`, non da RootIdentity o DeviceRecordCommitment pubblico.

```text
PairRendezvousSecret
    -> directional rotating slots
    -> encrypted RendezvousRecord
    -> encrypted RecoveryBeacon
```

Il payload cifrato può includere `DeviceCertificate` hash/proof, `key_epoch` e candidate temporanei; il record pubblico non espone una relazione leggibile root/device/route.

## 11. Handshake

L'handshake autentica contemporaneamente:

1. la RootIdentity/contact identity attesa per la relazione;
2. il `DeviceCertificate` corrente;
3. il possesso della DeviceKey;
4. il transcript effimero della sessione.

Il transcript **MUST** legare almeno:

```text
network_id
protocol_version
local_pairwise_alias_or_commitment
remote_pairwise_alias_or_commitment
local_device_certificate_hash_or_proof
remote_device_certificate_hash_or_proof
local_key_epoch
remote_key_epoch
ephemeral_keys
nonces
negotiated_suite
session_id
```

Il peer non deve accettare semplicemente “qualunque chiave che firma se stessa”.

## 12. Forward secrecy

Il materiale statico di RootIdentity/DeviceKey autentica la sessione ma non deve essere sufficiente a ricostruire sessioni precedenti.

Freedom Communication richiede:

- ephemeral key exchange per nuova sessione;
- forward secrecy tra sessioni;
- traffic-key lifetime bounded;
- rekey periodico per sessioni lunghe;
- media keys separate dalle messaging keys;
- ratchet standard/reviewato come target per post-compromise security.

## 13. Transport identity

Routing e identità sono separati.

Il transport usa capability temporanee:

```text
TransportToken
RelayCircuitToken
NextHopToken
RouteCapability
```

Un relay non dovrebbe ricevere RootIdentity/DeviceRecordCommitment quando gli basta un token di circuito.

## 14. Message layer

Dentro una sessione autenticata l'identità del mittente è già implicita nel contesto.

Preferire:

```text
ChatMessage {
    message_id
    logical_sequence
    sent_at
    body
    reply_to?
}
```

Nessun identificatore globale stabile viene aggiunto a ogni message/ACK/media frame senza necessità protocollare dimostrata.

## 15. Privacy invariants

- nessun `DeviceID` globale user-facing o transport-facing;
- RootIdentity non è routing ID;
- DeviceRecordCommitment non è contact ID;
- DeviceCertificate è prova di autorizzazione, non network address;
- contatto = persona/RootIdentity, non device;
- alias pairwise differenti tra relazioni;
- rendezvous/recovery derivati da secret pairwise;
- relay/circuiti usano token temporanei;
- commitment account/service domain-separated;
- nessun mapping pubblico leggibile `RootIdentity -> devices[]`;
- nessun mapping pubblico leggibile `device -> IP/relay/route`;
- sessioni precedenti non dipendono dalla segretezza futura della DeviceKey.

## 16. Trade-off

Questa separazione riduce la correlazione ma non elimina automaticamente metadata leakage.

Restano da misurare:

- timing delle write control-plane;
- activation/revocation pattern;
- RPC/provider visibility;
- IP/ASN observation;
- relay ingress/egress timing;
- dimensioni/pattern del traffico;
- uso e frequenza dei commitment di entitlement/sponsorship/payment.

La metadata privacy va verificata empiricamente; non è garantita dalla sola nomenclatura.
