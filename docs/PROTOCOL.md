# Freedom — Protocol Specification

Status: **design draft**

Questa specifica descrive gli oggetti logici e i flussi minimi del protocollo. Gli encoding binari definitivi e le primitive concrete verranno fissati prima dell'interoperabilità pubblica.

## 1. Principi

- ogni oggetto parsabile è versionato;
- RootIdentity, DeviceIdentity e session keys hanno ruoli separati;
- le informazioni di routing non autenticano l'identità;
- la blockchain/control-plane non trasporta contenuti applicativi o APK;
- i relay inoltrano, non archiviano;
- i contenuti applicativi vengono inviati soltanto dentro una sessione autenticata attiva;
- nessuna consegna offline automatica nel protocollo base;
- read-before-write è obbligatorio per rendezvous/recovery;
- una sessione attiva gestisce direttamente i propri route update;
- installare l'app non implica una write chain;
- entitlement e pagamento non alterano le primitive E2EE del core.

## 2. RootIdentity

```text
RootIdentity {
    version
    account_id
    root_public_key
}
```

`account_id` può essere derivato/committed dalla root public key secondo l'encoding definitivo.

La RootIdentity serve per:

- recovery ownership;
- autorizzazione/revoca device;
- entitlement/licenze;
- sponsorship state quando necessario.

Non viene usata come chiave di sessione o message key.

## 3. DeviceID

`DeviceID` è un identificatore stabile del device generato con entropia crittografica.

```text
DeviceRecord {
    version
    device_id
    identity_public_key
    key_epoch
    status
    protocol_version
}
```

Non contiene PII e non è derivato direttamente dalla current public key.

## 4. Device authorization

Un nuovo device recuperato/aggiunto genera una nuova DeviceKey e viene autorizzato dalla RootIdentity:

```text
ActivateDevice {
    account_commitment
    device_commitment
    entitlement_epoch
    nonce
    root_signature
}
```

Il control-plane deve poter far rispettare `active_devices <= max_devices` senza richiedere un elenco pubblico leggibile dei DeviceID dell'account.

## 5. Key rotation / revocation

```text
RotateDeviceKey {
    device_id
    old_epoch
    new_epoch
    new_public_key
    authorization_proof
}
```

Una rotazione valida incrementa l'epoch. Revocation e recovery devono impedire l'uso di chiavi obsolete per nuovi handshake.

## 6. Recovery Kit

Il formato utente è QR/bundle cifrato + recovery code separato. Il protocollo non impone che la Root private key sia mai pubblicata.

Un restore produce:

```text
recover RootIdentity
 -> generate NEW DeviceKey
 -> ActivateDevice
 -> resolve entitlement
```

Dettagli: [`ACCOUNT_RECOVERY_LICENSES.md`](ACCOUNT_RECOVERY_LICENSES.md).

## 7. Contact descriptor

```text
FreedomContact {
    version
    network_id
    device_id
    rendezvous_capability
    expires_at?
}
```

La capability è una capability di bootstrap/rendezvous e non consente impersonation.

## 8. Pair rendezvous state

Dopo il primo handshake:

```text
PairRendezvousState {
    peer_device_id
    rendezvous_secret
}
```

Il secret non viene scritto on-chain. Gli slot possono essere direzionali/rotanti e non richiedono la conoscenza del record precedente.

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
    sender_device_id
    sender_key_epoch
    rendezvous_nonce
    route_candidates[]
    relay_candidates[]
    ephemeral_transport_public_key
}
```

Ogni record è autosufficiente; non esiste sequence/revision storica obbligatoria.

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
    endpoint
    transport
    capability_token?
    expires_at
}
```

Un relay candidate non implica fiducia nel relay.

## 14. Route update in-session

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

## 15. Handshake

```text
SessionContext {
    session_id
    local_device_id
    remote_device_id
    local_key_epoch
    remote_key_epoch
    negotiated_version
    negotiated_crypto_suite
    tx_keys
    rx_keys
    created_at
}
```

Il transcript lega almeno network/protocol version, entrambi i DeviceID/epoch, ephemeral keys, nonce, suite e session_id. Entrambi verificano la current public key tramite `ChainAdapter`/cache verificata secondo freshness policy.

## 16. Encrypted frame / replay

```text
EncryptedFrame {
    version
    session_hint
    sequence
    ciphertext
}
```

Sequence monotono per direzione e protetto da AEAD. Reject di replay e memoria bounded.

## 17. Text message

```text
ChatMessage {
    message_id
    conversation_id
    sender_device_id
    logical_sequence
    sent_at
    body
    reply_to?
}
```

Valido solo dentro una sessione autenticata attiva. Nessuna queue di retry/offline delivery.

## 18. ACK

```text
MessageAck {
    message_id
    ack_type
    receiver_device_id
    logical_time
}
```

ACK: `RECEIVED`, `READ` opzionale. `RECEIVED` non implica persistenza su disco.

## 19. Synchronous / offline behavior

```text
SEND
  |
  +-- active authenticated session? -- no --> DISCARD/FAIL
  |
  +-- yes --> transmit --> ACK/session result
```

Nessun deposito su blockchain, relay persistente o mailbox locale futura.

## 20. Live / ephemeral mode

In Live mode il client può evitare cronologia persistente, backup/preview plaintext e distruggere session state/key al termine. Non può impedire al peer remoto o a un OS compromesso di copiare ciò che riceve.

## 21. Relay packet

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

## 22. Attachment

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

## 23. Call signaling

```text
CallInvite
CallAccept
CallCandidate
CallEnd
```

Il signaling viaggia E2EE; media keys separate dalle messaging keys.

## 24. Presence

Presence è off-chain e opportunistica. Non genera heartbeat blockchain continui.

## 25. Entitlement

```text
FreedomEntitlement {
    version
    account_commitment
    tier
    entitlement_epoch
    max_devices
    issued_at
    expires_at?
    policy_version
    status
}
```

Policy iniziale Free: 1 active device, 10 active contacts. La contact list resta locale/cifrata; eventuali contact-slot commitment non devono rivelare il social graph.

## 26. PurchaseIntent / PaymentAttestation

```text
PurchaseIntent {
    purchase_ref_hash
    account_commitment
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

## 27. EmergencyBulletin / SecurityPolicy / FreedomRelease

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

## 28. Sponsored registration proof

La registrazione iniziale può richiedere una prova anti-abuso/adaptive PoW firmata/contestualizzata e validata dal relayer/contratto secondo policy.

La sponsorship deve essere bounded per RootIdentity, relayer e budget globale.

Dettagli: [`REGISTRATION_ECONOMICS.md`](REGISTRATION_ECONOMICS.md).

## 29. Error classes

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
ENTITLEMENT_INVALID
PAYMENT_PENDING
SECURITY_UPDATE_REQUIRED
```

## 30. Resource limits

Ogni implementazione deve limitare frame/handshake size, route candidates, relay buffer, connessioni, write frequency, rendezvous retry/backoff, recovery writes, sponsorship rate e temporary state.

## 31. Chain abstraction

```text
ChainAdapter {
    registerRoot
    registerDevice
    resolveDevice
    rotateDeviceKey
    revokeDevice
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

La prima implementazione usa NEAR Testnet.

## 32. Milestone eseguibili

### M1 — identity/recovery

- RootIdentity + DeviceIdentity;
- Recovery Kit;
- install senza write;
- sponsored registration;
- DeviceID resolve/rotation/revocation;
- QR contact.

### M2 — rendezvous/recovery

- capability bootstrap;
- opaque slots;
- read-before-write;
- TTL;
- RecoveryBeacon bounded.

### M3 — secure communication

- mutual authentication;
- encrypted text/ACK;
- no offline queue;
- fresh session on reconnect.

### M4 — network paths

- NAT traversal;
- route update;
- relay forward-only;
- Adaptive Defense.

### M5 — account/commercial control plane

- entitlement;
- max_devices;
- Free contact policy;
- PayPal/crypto payment adapters;
- payment attestation idempotency.

### M6 — safety/update plane

- EmergencyBulletin;
- SecurityPolicy;
- FreedomRelease;
- multi-source artifact verification.
