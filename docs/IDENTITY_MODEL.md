# Freedom — Identity Model

Status: **canonical design draft**

## 1. Obiettivo

Freedom deve poter autenticare persone e device, ruotare/revocare chiavi e far rispettare limiti multi-device senza trasformare un identificatore globale del device in un indirizzo visibile a tutta la rete.

Decisione architetturale:

> **Freedom non usa un `DeviceID` globale come identità pubblica o identificatore di trasporto.**

La separazione canonica è:

```text
RootIdentity              -> ownership / recovery / entitlement
DeviceKey                 -> chiave operativa di un singolo device
DeviceRecordCommitment    -> handle opaco del control-plane
PairwiseContactAlias      -> alias specifico della relazione tra due contatti
TransportToken            -> token temporaneo di route/circuito
Session keys              -> materiale effimero della sessione E2EE
```

Questi livelli non devono essere confusi o riutilizzati automaticamente tra loro.

## 2. RootIdentity

La `RootIdentity` rappresenta ownership e continuità dell'identità Freedom.

Serve per:

- Recovery Kit;
- autorizzazione/revoca device;
- recovery dopo reset o furto;
- entitlement/licenze;
- limiti `max_devices`;
- sponsorship/anti-abuse quando necessario.

Non viene usata come message key, media key o transport identifier.

Il client può rappresentarla internamente con un commitment stabile:

```text
RootIdentity {
    version
    root_public_key
    root_commitment
    root_epoch
}
```

Il `root_commitment` non deve essere inviato inutilmente nel network layer.

## 3. DeviceKey e DeviceRecordCommitment

Ogni device genera una propria `DeviceKey` operativa.

Il control-plane può mantenere uno stato del device indicizzato da un handle opaco:

```text
DeviceRecordCommitment = H(
    root_commitment ||
    device_random_secret ||
    domain_separator
)
```

Formato concettuale:

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

Il commitment serve per lookup/rotation/revocation e può restare stabile durante una rotazione della DeviceKey, ma:

- non è username;
- non è contact ID;
- non è endpoint di rete;
- non deve essere annunciato ai relay quando non necessario;
- non deve comparire nei normali message frame;
- non deve essere riutilizzato come alias tra relazioni differenti.

Il nome `DeviceID` viene quindi rimosso dal modello canonico per evitare che un handle tecnico venga trattato come identità globale.

## 4. Device authorization

Un nuovo device viene autorizzato dalla RootIdentity:

```text
ActivateDevice {
    root_commitment
    device_record_commitment
    device_public_key
    entitlement_epoch
    nonce
    root_signature
}
```

Il control-plane deve poter verificare:

```text
active_devices <= max_devices
```

senza pubblicare una lista leggibile dei device appartenenti a una persona.

## 5. Rotation e revocation

La rotazione non richiede un identificatore globale di rete.

```text
RotateDeviceKey {
    device_record_commitment
    old_epoch
    new_epoch
    new_public_key
    authorization_proof
}
```

La revoca marca il record opaco come non valido per nuovi handshake.

Un attacker che conosce il commitment non ottiene automaticamente informazioni di routing o capacità di contatto.

## 6. Contatto = persona / RootIdentity, non device

La rubrica Freedom rappresenta un contatto logico, non un singolo telefono.

```text
Bob / RootIdentity
  |- Phone
  |- Tablet
  `- Desktop
```

Aggiungere Bob non deve creare tre contatti diversi.

Il primo contatto viene bootstrapato intenzionalmente tramite QR/link/NFC/copia-incolla con un descriptor che contiene solo quanto necessario alla relazione:

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

`root_identity_proof` può essere una public key/commitment + prova secondo l'encoding definitivo. Non deve diventare un identificatore di routing globale.

## 7. Pairwise identity

Dopo un handshake autenticato, Alice e Bob derivano stato specifico della loro relazione:

```text
PairSecret_AB
PairwiseContactAlias_AB
PairRendezvousSecret_AB
```

Gli alias pairwise sono differenti per relazioni differenti:

```text
Alice <-> Bob     alias X
Alice <-> Carol   alias Y
```

Conoscere X non deve consentire a un terzo di collegarlo automaticamente a Y.

## 8. Rendezvous

Gli slot di rendezvous/recovery vengono derivati dal `PairRendezvousSecret`, non da un DeviceRecordCommitment pubblico.

```text
PairRendezvousSecret
    -> directional rotating slots
    -> encrypted RendezvousRecord
    -> encrypted RecoveryBeacon
```

Il payload cifrato può includere una prova del device corrente e il suo `key_epoch`, ma il record pubblico non deve esporre una relazione leggibile tra root, device e route.

## 9. Handshake

L'handshake autentica:

1. la relazione con la RootIdentity/contact identity attesa;
2. l'autorizzazione della DeviceKey corrente;
3. il possesso della DeviceKey;
4. il transcript effimero della sessione.

Il transcript deve legare almeno:

```text
protocol_version
network_id
local_pairwise_alias
remote_pairwise_alias
local_device_record_commitment_or_proof
remote_device_record_commitment_or_proof
local_key_epoch
remote_key_epoch
ephemeral_keys
nonces
negotiated_suite
session_id
```

La forma wire definitiva può usare hash/prove compatte invece dei commitment grezzi quando possibile.

## 10. Transport identity

Routing e identità sono separati.

Il transport layer usa token/capability temporanei:

```text
TransportToken
RelayCircuitToken
NextHopToken
RouteCapability
```

Un relay non dovrebbe ricevere:

```text
RootIdentity Alice -> RootIdentity Bob
```

né:

```text
DeviceRecordCommitment Alice -> DeviceRecordCommitment Bob
```

quando gli basta conoscere:

```text
token 7F2A... -> circuit 91C...
```

## 11. Message layer

Una volta stabilita la sessione, i message frame non hanno bisogno di un identificatore globale del mittente.

L'identità del mittente è già implicita nella sessione autenticata.

Preferire quindi:

```text
ChatMessage {
    message_id
    logical_sequence
    sent_at
    body
    reply_to?
}
```

rispetto a inserire un device identifier stabile in ogni messaggio.

Lo stesso principio vale per ACK, signaling e frame media quando il contesto di sessione è sufficiente.

## 12. Privacy invariants

- nessun `DeviceID` globale user-facing o transport-facing;
- RootIdentity non usata come routing identifier;
- device record indicizzati con commitment opachi;
- contatti rappresentano persone/RootIdentity, non singoli device;
- alias pairwise differenti tra relazioni;
- rendezvous/recovery derivati da secret pairwise;
- relay/circuiti usano token temporanei;
- message frame evitano identificatori stabili quando il session context basta;
- nessun mapping pubblico leggibile `RootIdentity -> device list`;
- nessun mapping pubblico leggibile `device -> IP/relay/route`;
- rotazione/revoca restano verificabili senza introdurre un global network ID.

## 13. Trade-off

Rimuovere un `DeviceID` globale riduce la superficie di correlazione, ma non elimina automaticamente metadata leakage.

Restano da minimizzare e misurare:

- timing delle write del control-plane;
- correlazione tra device activation e rendezvous;
- RPC/provider visibility;
- IP/ASN observation;
- relay ingress/egress timing;
- dimensioni/pattern del traffico;
- root/account commitment usati per entitlement.

Per questo la metadata privacy va verificata empiricamente e confrontata con sistemi come SimpleX, non assunta dalla sola nomenclatura.
