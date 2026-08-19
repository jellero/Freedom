# Freedom — Protocol Specification

> **Specifica target.** Il protocollo alpha attualmente eseguibile è descritto in [`IMPLEMENTATION_STATUS.md`](IMPLEMENTATION_STATUS.md) e non deve essere presentato come conforme a questo draft.

Status: **design draft**

Questa specifica descrive gli oggetti logici e i flussi minimi del protocollo. Gli encoding binari definitivi e le primitive concrete verranno fissati prima dell'interoperabilità pubblica.

## 1. Principi

- ogni oggetto parsabile è versionato;
- RootIdentity, DeviceKey, device commitment, alias pairwise e session keys hanno ruoli separati;
- **non esiste un `DeviceID` globale richiesto dal wire protocol**;
- le informazioni di routing non autenticano l'identità;
- la blockchain/control-plane non trasporta contenuti applicativi o APK;
- i relay inoltrano, non archiviano;
- un relay può essere dedicated, community, private, managed o un normale dispositivo Freedom opt-in;
- i contenuti applicativi vengono inviati soltanto dentro una sessione autenticata attiva;
- nessuna consegna offline automatica nel protocollo base;
- read-before-write è obbligatorio per rendezvous/recovery;
- una sessione attiva gestisce direttamente i propri route update;
- installare l'app non implica una write chain;
- entitlement e pagamento non alterano le primitive E2EE del core.

Dettagli identità: [`IDENTITY_MODEL.md`](IDENTITY_MODEL.md).

## 2. RootIdentity

```text
RootIdentity {
    version
    root_public_key
    root_commitment
    root_epoch
}
```

La RootIdentity serve per:

- recovery ownership;
- autorizzazione/revoca device;
- entitlement/licenze;
- sponsorship state quando necessario.

Non viene usata come chiave di sessione, message key o identificatore di routing.

## 3. Device record opaco

Ogni device genera una DeviceKey e un handle tecnico del control-plane:

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

`device_record_commitment` non è username, contact ID o endpoint di rete. Non deve essere inserito nei normali frame applicativi quando il contesto di sessione è sufficiente.

## 4. Device authorization

Un nuovo device recuperato/aggiunto genera nuova DeviceKey e nuovo commitment, quindi viene autorizzato dalla RootIdentity:

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

Il control-plane deve poter far rispettare `active_devices <= max_devices` senza richiedere un elenco pubblico leggibile dei device dell'account.

## 5. Key rotation / revocation

```text
RotateDeviceKey {
    device_record_commitment
    old_epoch
    new_epoch
    new_public_key
    authorization_proof
}
```

Una rotazione valida incrementa l'epoch. Revocation e recovery devono impedire l'uso di chiavi obsolete per nuovi handshake.

Il commitment tecnico può restare stabile durante una normale rotazione, ma non viene usato come global network identity.

## 6. Recovery Kit

Il formato utente è QR/bundle cifrato + recovery code separato. Il protocollo non impone che la Root private key sia mai pubblicata.

Un restore produce:

```text
recover RootIdentity
 -> generate NEW DeviceKey
 -> generate NEW DeviceRecordCommitment
 -> ActivateDevice
 -> resolve entitlement
```

Dettagli: [`ACCOUNT_RECOVERY_LICENSES.md`](ACCOUNT_RECOVERY_LICENSES.md).

## 7. Contact descriptor

Il contatto logico rappresenta una persona/RootIdentity, non ogni suo singolo device.

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

La capability è una capability di bootstrap/rendezvous e non consente impersonation.

Un contatto può successivamente usare più device autorizzati senza diventare più contatti nella rubrica.

## 8. Pairwise identity state

Dopo il primo handshake autenticato:

```text
PairIdentityState {
    peer_root_identity_proof
    pair_secret
    pairwise_contact_alias
    rendezvous_secret
}
```

Il secret non viene scritto on-chain. Alias e slot sono specifici della relazione e devono essere differenti tra coppie differenti.

## 9. Rendezvous record

```text
RendezvousRecord {
    version
    expires_at
    ciphertext
}
```

Payload cifrato:

```text
RendezvousPayload {
    sender_pairwise_alias
    sender_device_proof
    sender_key_epoch
    rendezvous_nonce
    route_candidates[]
    relay_candidates[]
    ephemeral_transport_public_key
}
```

Il record pubblico non espone una relazione leggibile RootIdentity/device/route. Ogni record è autosufficiente; non esiste sequence/revision storica obbligatoria.

## 10. Read-before-write

```text
remote = read(remote_slot)
if remote usable -> try(remote), DO_NOT_WRITE
else:
  local = read(local_slot)
  if local usable -> WAIT/POLL
  else -> write(new independent record)
```

TTL/backoff impediscono write continue.

## 11. RecoveryBeacon

Quando il data-plane è perso:

```text
RecoveryBeacon {
    version
    issued_at
    expires_at
    recovery_nonce
    route_generation
    state
    candidate_hints[]?
}
```

Il beacon è pairwise/opaco/cifrato e indica attività recente, non presenza globale.

Dettagli: [`ADAPTIVE_DEFENSE.md`](ADAPTIVE_DEFENSE.md).

## 12. Route candidate

```text
RouteCandidate {
    transport
    endpoint
    candidate_type
    priority
    observed_at
    expires_at
}
```

Candidate iniziali: `LOCAL`, `OBSERVED`, `DIRECT`, `RELAY`; future classi possono includere shielded/bridge/obfuscated transport.

## 13. Relay candidate

```text
RelayCandidate {
    relay_id
    relay_class
    endpoint
    transport
    capability_token?
    capacity_hint?
    expires_at
}
```

Classi iniziali:

```text
DEDICATED
COMMUNITY
DEVICE
PRIVATE
MANAGED
```

La classe descrive la natura operativa del relay, **non il suo livello di fiducia**.

Un `DEVICE` relay può essere un telefono/tablet/desktop Freedom opt-in. Non deve necessariamente avere un IP pubblico: può essere raggiungibile tramite NAT mapping, transport compatibile o connessioni outbound/circuiti già stabiliti.

Dettagli: [`RELAYS.md`](RELAYS.md).

## 14. Transport token

Routing e identità sono separati.

Il transport layer usa capability temporanee:

```text
TransportToken
RelayCircuitToken
NextHopToken
RouteCapability
```

Un relay non dovrebbe ricevere RootIdentity o DeviceRecordCommitment quando un token di circuito è sufficiente.

## 15. Route update in-session

```text
RouteUpdate {
    sequence
    candidates[]
    relay_candidates[]
    issued_at
    expires_at
}
```

Viaggia nella sessione autenticata. Finché almeno un path è valido non si usa la blockchain per aggiornare il routing della coppia.

## 16. Handshake

```text
SessionContext {
    session_id
    local_pairwise_alias
    remote_pairwise_alias
    local_device_record_commitment_or_proof
    remote_device_record_commitment_or_proof
    local_key_epoch
    remote_key_epoch
    negotiated_version
    negotiated_crypto_suite
    tx_keys
    rx_keys
    created_at
}
```

Il transcript lega almeno:

```text
network_id
protocol_version
pairwise aliases
current device authorization proofs
key epochs
ephemeral keys
nonces
suite
session_id
```

Entrambi verificano che la DeviceKey corrente sia autorizzata dalla RootIdentity attesa e non revocata, tramite `ChainAdapter`/cache verificata secondo freshness policy.

Il relay non partecipa come authority all'autenticazione endpoint-to-endpoint.

## 17. Encrypted frame / replay

```text
EncryptedFrame {
    version
    session_hint
    sequence
    ciphertext
}
```

Sequence monotono per direzione e protetto da AEAD. Reject di replay e memoria bounded.

`session_hint` deve essere temporaneo e non trasformarsi in identificatore globale stabile.

## 18. Text message

Una volta stabilita la sessione, l'identità del mittente è già implicita nel contesto autenticato.

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

Non inserire un device identifier stabile in ogni messaggio salvo necessità protocollare dimostrata.

Valido solo dentro una sessione autenticata attiva. Nessuna queue di retry/offline delivery.

## 19. ACK

```text
MessageAck {
    message_id
    ack_type
    logical_time
}
```

ACK: `RECEIVED`, `READ` opzionale. `RECEIVED` non implica persistenza su disco.

## 20. Synchronous / offline behavior

```text
SEND
  |
  +-- active authenticated session? -- no --> DISCARD/FAIL
  |
  +-- yes --> transmit --> ACK/session result
```

Nessun deposito su blockchain, relay persistente o mailbox locale futura.

## 21. Live / ephemeral mode

In Live mode il client può evitare cronologia persistente, backup/preview plaintext e distruggere session state/key al termine. Non può impedire al peer remoto o a un OS compromesso di copiare ciò che riceve.

## 22. Relay packet e circuiti

```text
RelayPacket {
    version
    packet_id
    next_hop_token
    hop_limit
    expires_at
    ciphertext
}
```

Buffer/size/TTL/hop/quota bounded. Nessuna `StoreRequest` nel protocollo base.

Il relay usa capability/token di circuito e non richiede un mapping pubblico tra identità stabili e destinatari.

Un device che funge da relay mantiene separati:

```text
ENDPOINT_CONTEXT
RELAY_CONTEXT
```

Le chiavi/sessioni dell'utente locale non vengono usate per decifrare traffico relayato.

## 23. Relay contribution proof

Il client può ottenere un benefit `Relay Contributor` solo se il device soddisfa una policy minima di contributo.

```text
RelayContributionProof {
    version
    relay_commitment
    contribution_epoch
    availability_commitment?
    forwarding_commitment?
    policy_version
    expires_at
    attestations[]
}
```

Requisiti:

- il semplice toggle `relay_enabled` non è sufficiente;
- la prova non deve contenere plaintext o lista dei peer serviti;
- evitare reward proporzionali senza limite al volume;
- supportare aggregazione/commitment opachi;
- TTL/epoch bounded, così il benefit scade se il contributo termina.

Il proof può autorizzare:

```text
EntitlementBenefit {
    benefit_type = RELAY_CONTRIBUTOR_CONTACTS
    value = 10
    expires_at
    proof_commitment
}
```

Policy iniziale Free:

```text
base contacts               10
Relay Contributor bonus    +10
maximum while qualified     20
```

## 24. Attachment

```text
AttachmentManifest {
    attachment_id
    media_type
    plaintext_size
    chunk_size
    chunks[]
    integrity
}
```

Trasferimento solo con sessione/route attiva; niente storage persistente automatico.

## 25. Call signaling

```text
CallInvite
CallAccept
CallCandidate
CallEnd
```

Il signaling viaggia E2EE; media keys separate dalle messaging keys.

## 26. Presence

Presence è off-chain, opportunistica e preferibilmente pairwise. Non genera heartbeat blockchain continui e non richiede un identificatore globale di presence.

## 27. Entitlement

```text
FreedomEntitlement {
    version
    root_commitment
    tier
    entitlement_epoch
    max_devices
    base_contact_slots
    issued_at
    expires_at?
    policy_version
    status
}
```

Policy iniziale Free: 1 active device, 10 active contacts. La contact list resta locale/cifrata; eventuali contact-slot commitment non devono rivelare il social graph.

Benefit temporanei, incluso `RELAY_CONTRIBUTOR_CONTACTS`, modificano la capacità effettiva senza cambiare il tier principale.

## 28. PurchaseIntent / PaymentAttestation

```text
PurchaseIntent {
    purchase_ref_hash
    root_commitment
    product
    amount
    currency_or_asset
    provider
    expires_at
}

PaymentAttestation {
    provider
    provider_transaction_commitment
    purchase_ref_hash
    amount
    currency_or_asset
    status
    observed_at
    worker_id
    signature
}
```

Il callback di pagamento nel client non è prova autoritativa. PayPal può essere verificato da worker outbound-only; crypto può essere verificata on-chain quando compatibile.

Dettagli: [`PAYMENTS.md`](PAYMENTS.md).

## 29. EmergencyBulletin / SecurityPolicy / FreedomRelease

```text
EmergencyBulletin {
    bulletin_id
    severity
    issued_at
    expires_at
    geographic_scope?
    payload_hash
    signatures[]
}

SecurityPolicy {
    policy_epoch
    min_supported_version
    min_secure_version
    vulnerable_versions[]
    disabled_features[]
    severity
    reason_hash
    remediation_release
    signatures[]
}

FreedomRelease {
    version_code
    package_id
    artifact_sha256
    artifact_size
    signing_cert_fingerprint
    source_descriptors[]
    signatures[]
}
```

L'APK resta off-chain. La posizione utente per bulletin geografici resta locale. Policy critiche dovrebbero supportare threshold signatures.

Dettagli: [`EMERGENCY_UPDATES.md`](EMERGENCY_UPDATES.md).

## 30. Sponsored registration proof

La registrazione iniziale può richiedere una prova anti-abuso/adaptive PoW firmata/contestualizzata e validata dal relayer/contratto secondo policy.

La sponsorship deve essere bounded per RootIdentity, relayer e budget globale.

Dettagli: [`REGISTRATION_ECONOMICS.md`](REGISTRATION_ECONOMICS.md).

## 31. Error classes

```text
MALFORMED
UNSUPPORTED_VERSION
CHAIN_IDENTITY_NOT_FOUND
CHAIN_IDENTITY_REVOKED
KEY_EPOCH_MISMATCH
AUTHENTICATION_FAILED
REPLAY_DETECTED
ROUTE_UNAVAILABLE
RENDEZVOUS_EXPIRED
RELAY_REFUSED
PEER_OFFLINE
SESSION_REKEY_REQUIRED
DEVICE_LIMIT_REACHED
CONTACT_LIMIT_REACHED
ENTITLEMENT_INVALID
RELAY_CONTRIBUTION_EXPIRED
PAYMENT_PENDING
SECURITY_UPDATE_REQUIRED
```

## 32. Resource limits

Ogni implementazione deve limitare frame/handshake size, route candidates, relay buffer, connessioni, write frequency, rendezvous retry/backoff, recovery writes, sponsorship rate e temporary state.

Per `DEVICE_RELAY` sono obbligatori limiti di banda, CPU, RAM, batteria/temperatura e circuiti simultanei secondo policy locale.

## 33. Chain abstraction

```text
ChainAdapter {
    registerRoot
    registerDeviceRecord
    resolveDeviceRecord
    rotateDeviceKey
    revokeDeviceRecord
    activateDeviceSlot
    revokeDeviceSlot
    resolveEntitlement
    read/writeRendezvous
    read/writeRecoveryBeacon
    readPurchaseIntent
    readPaymentAttestation
    readEmergencyBulletins
    readSecurityPolicy
    readReleaseManifest
    verifyState
}
```

L'attestazione Relay Contributor può vivere nel control-plane o essere verificata tramite un adapter/benefit layer separato; non deve essere inserita nel packet hot path.

La prima implementazione usa NEAR Testnet.

## 34. Milestone eseguibili

### M1 — identity/recovery

- RootIdentity + DeviceKey + DeviceRecordCommitment;
- Recovery Kit;
- install senza write;
- sponsored registration;
- device record resolve/rotation/revocation;
- QR contact basato su RootIdentity/contact capability;
- alias pairwise.

### M2 — rendezvous/recovery

- capability bootstrap;
- pairwise opaque slots;
- read-before-write;
- TTL;
- RecoveryBeacon bounded.

### M3 — secure communication

- mutual authentication RootIdentity + current DeviceKey authorization;
- encrypted text/ACK senza global device identifier nei frame;
- no offline queue;
- fresh session on reconnect.

### M4 — network paths / relays

- NAT traversal;
- route update;
- dedicated/community relay forward-only;
- `DEVICE_RELAY` opt-in;
- RelayCandidate discovery;
- transport/circuit tokens;
- Adaptive Defense.

### M5 — account/commercial control plane

- entitlement;
- max_devices;
- Free contact policy;
- Relay Contributor +10 contact benefit;
- contribution proof/expiry;
- PayPal/crypto payment adapters;
- payment attestation idempotency.

### M6 — safety/update plane

- EmergencyBulletin;
- SecurityPolicy;
- FreedomRelease;
- multi-source artifact verification.
