# Freedom — Protocol Specification

Status: **canonical design draft**

Normative security rules: [`SECURITY_INVARIANTS.md`](SECURITY_INVARIANTS.md).
Identity details: [`IDENTITY_MODEL.md`](IDENTITY_MODEL.md).

Questa specifica descrive oggetti logici e flussi minimi. Gli encoding binari definitivi e le suite concrete devono essere congelati con test vector prima dell'interoperabilità pubblica.

## 1. Principi normativi

- ogni oggetto parsabile è versionato;
- RootIdentity, DeviceCertificate, DeviceKey, commitment, alias pairwise, transport token e session keys hanno ruoli separati;
- **non esiste un `DeviceID` globale richiesto dal wire protocol**;
- la blockchain/control-plane non trasporta contenuti applicativi o APK;
- relay/bridge inoltrano ciphertext, non creano mailbox;
- i contenuti applicativi vengono inviati soltanto dentro una sessione autenticata attiva;
- nessuna consegna offline automatica nel protocollo base;
- read-before-write è obbligatorio per rendezvous/recovery;
- una sessione attiva gestisce direttamente i propri route update;
- installare l'app non implica una write chain;
- entitlement/pagamento non alterano le primitive E2EE;
- un transaction hash non equivale a successo;
- forward secrecy tra sessioni è obbligatoria;
- traffic-key lifetime e rekey sono bounded/normativi;
- governance production critica non può essere `1-of-1`.

## 2. RootIdentity e commitment domain-separated

```text
RootIdentity {
    version
    root_public_key
    root_commitment
    root_epoch
}
```

La RootIdentity serve a ownership/recovery e autorizzazione. Non è message key, transport ID o payment reference.

Stato account/service stabile usa commitment separati:

```text
DeviceAuthorizationCommitment
EntitlementCommitment
PaymentBindingCommitment
SponsorshipCommitment
```

Il riuso indiscriminato dello stesso `root_commitment` tra domini è vietato quando evitabile.

## 3. Device record

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

`device_record_commitment` è un handle opaco del control-plane, non username/contact/routing ID.

## 4. DeviceCertificate

La DeviceKey corrente è autorizzata dalla RootIdentity tramite un certificato verificabile offline:

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

Un nuovo handshake deve poter verificare firma, binding alla RootIdentity attesa, epoch, network e expiry senza dipendere da una RPC nel packet hot path.

Il control-plane fornisce revocation/rotation/freshness; cache verificate possono essere usate secondo policy.

## 5. Device activation / rotation / revocation

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

```text
RotateDeviceKey {
    device_record_commitment
    old_epoch
    new_epoch
    new_public_key
    authorization_proof
}
```

Dopo activation/rotation finalizzata e verificata viene emesso/usato un `DeviceCertificate` coerente col nuovo stato.

Una revoca impedisce nuovi handshake con il certificato/epoch revocato secondo freshness policy.

## 6. Recovery Kit

```text
recover RootIdentity
 -> generate NEW DeviceKey
 -> generate NEW DeviceRecordCommitment
 -> ActivateDevice
 -> wait verified finality/state
 -> issue/use NEW DeviceCertificate
 -> resolve entitlement
```

Il restore non riusa automaticamente vecchie DeviceKey.

## 7. Contact descriptor

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

Il contatto logico rappresenta una persona/RootIdentity. La capability abilita bootstrap/rendezvous iniziale, non impersonation.

## 8. Pairwise identity state

```text
PairIdentityState {
    peer_root_identity_proof
    pair_secret
    pairwise_contact_alias
    rendezvous_secret
}
```

Alias e slot sono specifici della relazione e differenti tra coppie differenti.

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
    sender_device_certificate_hash_or_proof
    sender_key_epoch
    rendezvous_nonce
    route_candidates[]
    relay_candidates[]
    ephemeral_transport_public_key
}
```

Il record pubblico non espone mapping leggibile RootIdentity/device/route.

## 10. Read-before-write

```text
remote = read(remote_slot)
if remote usable -> try(remote), DO_NOT_WRITE
else:
  local = read(local_slot)
  if local usable -> WAIT/POLL
  else -> write(new independent record)
```

TTL/backoff e size bounds sono obbligatori.

## 11. RecoveryBeacon

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

Pairwise, opaco, cifrato, TTL breve; indica attività recente, non presence globale.

## 12. RouteCandidate

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

Classi: `LOCAL`, `OBSERVED`, `DIRECT`, `RELAY`, con estensioni future `BRIDGE`, `SHIELDED`, `OBFUSCATED`.

## 13. RelayCandidate

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

Classi: `DEDICATED`, `COMMUNITY`, `DEVICE`, `PRIVATE`, `MANAGED`.

La classe non è un trust signal crittografico.

## 14. Transport tokens

```text
TransportToken
RelayCircuitToken
NextHopToken
RouteCapability
```

Un relay non deve ricevere RootIdentity o DeviceRecordCommitment quando un token temporaneo è sufficiente.

## 15. RouteUpdate in-session

```text
RouteUpdate {
    sequence
    candidates[]
    relay_candidates[]
    issued_at
    expires_at
}
```

Viaggia E2EE nella sessione. Finché un path è valido, la chain non viene usata per ogni cambio route.

## 16. Handshake canonico

```text
SessionContext {
    session_id
    local_pairwise_alias
    remote_pairwise_alias
    local_device_certificate_hash_or_proof
    remote_device_certificate_hash_or_proof
    local_key_epoch
    remote_key_epoch
    negotiated_version
    negotiated_crypto_suite
    traffic_key_epoch
    tx_keys
    rx_keys
    created_at
}
```

Il transcript **MUST** legare almeno:

```text
network_id
protocol_version
expected pairwise relationship
pairwise aliases/commitments
local/remote DeviceCertificate hash/proof
key epochs
ephemeral key material
nonces
suite
session_id
```

Entrambi verificano:

1. RootIdentity/contact identity attesa;
2. DeviceCertificate valido;
3. possesso della DeviceKey;
4. freshness/revocation secondo policy;
5. transcript corrente.

È vietato accettare una chiave soltanto perché firma correttamente se non è legata al contatto atteso.

Il relay non partecipa come authority all'autenticazione endpoint-to-endpoint.

## 17. Forward secrecy, traffic-key epochs e rekey

Ogni nuova sessione usa ephemeral key exchange con forward secrecy rispetto alle chiavi statiche di identity/device.

La compromissione futura di RootIdentity/DeviceKey non deve permettere di derivare le session key di sessioni completate precedentemente.

Per sessioni lunghe:

```text
TrafficKeyEpoch {
    epoch
    activated_at
    frame_count
    byte_count
}
```

L'implementazione deve imporre limiti bounded per:

- tempo;
- frame;
- byte;
- policy di suite.

Prima del limite:

```text
RekeyInit
RekeyCommit
```

sono autenticati nella sessione corrente e producono nuove traffic keys. Se il rekey richiesto fallisce, la sessione termina con `SESSION_REKEY_REQUIRED`/failure esplicita.

Messaging keys e media keys sono separate. Una ratchet construction standard/reviewata è il target per post-compromise security.

## 18. Encrypted frame / replay

```text
EncryptedFrame {
    version
    session_hint
    traffic_key_epoch
    sequence
    ciphertext
}
```

Sequence monotona per direzione e traffic-key epoch; AEAD autentica epoch/sequence/session context. Replay e gap non validi vengono rifiutati con memoria bounded.

`session_hint` è temporaneo e non diventa identificatore globale stabile.

## 19. Text message

```text
ChatMessage {
    message_id
    logical_sequence
    sent_at
    body
    reply_to?
}
```

Valido solo dentro sessione autenticata attiva. Nessun device identifier globale nei frame se il session context basta.

## 20. ACK

```text
MessageAck {
    message_id
    ack_type
    logical_time
}
```

`RECEIVED`, `READ` opzionale. `RECEIVED` non implica persistenza su disco.

## 21. Synchronous / offline behavior

```text
SEND
  |
  +-- active authenticated session? -- no --> DISCARD / FAIL
  |
  `-- yes --> transmit --> ACK/session result
```

Nessun deposito su blockchain, relay persistente o mailbox locale futura.

## 22. Live / local ephemerality

Live mode può evitare cronologia persistente, backup/preview plaintext e distruggere session state/key al termine. Non può impedire al peer o a un OS compromesso di copiare il plaintext ricevuto.

## 23. RelayPacket

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

`ENDPOINT_CONTEXT` e `RELAY_CONTEXT` restano separati.

## 24. RelayContributionProof

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

Il semplice toggle non basta. La prova non contiene plaintext o lista dei peer serviti.

```text
EntitlementBenefit {
    benefit_type = RELAY_CONTRIBUTOR_CONTACTS
    value = 10
    expires_at
    proof_commitment
}
```

## 25. Attachment

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

Trasferimento solo durante sessione/route attiva; niente storage persistente automatico.

## 26. Call signaling

```text
CallInvite
CallAccept
CallCandidate
CallEnd
```

Signaling E2EE; media keys separate e soggette a rekey/lifetime bounded.

## 27. Presence

Presence è off-chain, opportunistica e preferibilmente pairwise. Nessun heartbeat blockchain continuo o global presence ID.

## 28. Entitlement

```text
FreedomEntitlement {
    version
    entitlement_commitment
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

La contact list resta locale/cifrata. L'entitlement commitment è domain-separated dall'identità/routing/payment state.

## 29. PurchaseIntent / PaymentAttestation

```text
PurchaseIntent {
    purchase_ref_hash
    payment_binding_commitment
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

Il callback client non è prova autoritativa. Payment reference e provider metadata non usano RootIdentity/DeviceRecordCommitment/pairwise alias in plaintext.

## 30. EmergencyBulletin / SecurityPolicy / FreedomRelease

```text
EmergencyBulletin {
    version
    bulletin_id
    severity
    issued_at
    expires_at
    geographic_scope?
    payload_hash
    issuer_set_epoch
    signatures[]
}
```

```text
SecurityPolicy {
    policy_epoch
    latest_version
    min_supported_version
    min_secure_version
    vulnerable_versions[]
    disabled_features[]
    severity
    reason_hash
    remediation_release
    issued_at
    expires_at?
    signer_set_epoch
    signatures[]
}
```

Schema **unico e canonico** della release:

```text
FreedomRelease {
    manifest_version
    release_id
    version_code
    version_name
    package_id
    artifact_sha256
    artifact_size
    signing_cert_fingerprint
    signing_lineage_commitment?
    min_supported_version
    min_secure_version
    criticality
    release_locator_hash
    issued_at
    signer_set_epoch
    signatures[]
}
```

L'APK resta off-chain. Critical SecurityPolicy, release authorization e global revocation richiedono threshold governance production secondo `SECURITY_INVARIANTS.md`.

## 31. Verified control-plane mutation

Un hash di transazione **non** è successo.

Per ogni mutazione security-sensitive:

```text
submit signed operation
 -> wait acceptable finality
 -> inspect execution result
 -> reject Failure / partial failure
 -> read/verify resulting state
 -> commit local transition
```

Si applica a activation/revocation/rotation, entitlement, sponsorship, payment effects, release/policy state e rendezvous/recovery quando influenzano la state machine.

## 32. Error classes

```text
MALFORMED
UNSUPPORTED_VERSION
CHAIN_IDENTITY_NOT_FOUND
CHAIN_IDENTITY_REVOKED
DEVICE_CERTIFICATE_INVALID
DEVICE_CERTIFICATE_EXPIRED
REVOCATION_STATE_STALE
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
CONTROL_PLANE_EXECUTION_FAILED
CONTROL_PLANE_STATE_MISMATCH
SECURITY_UPDATE_REQUIRED
```

## 33. Resource limits

Ogni implementazione limita frame/handshake size, route candidate, relay buffer, connessioni, handshake concorrenti, idle timeout, write frequency, rendezvous retry/backoff, recovery writes, sponsorship rate e temporary state.

Per `DEVICE_RELAY`: banda, CPU, RAM, batteria/temperatura e circuiti simultanei bounded.

## 34. ChainAdapter

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
    readReleaseStatus
    verifyState
    verifyFinalOutcome
}
```

NEAR Testnet è la prima implementazione; non è Freedom Protocol.

## 35. Primitive vietate

Freedom Protocol **MUST NOT** introdurre:

```text
global user/device network ID
on-chain messages/mailbox
persistent relay inbox
automatic offline delivery queue
RootIdentity as routing ID
DeviceRecordCommitment as contact ID
public readable social graph
mandatory central delivery server
mandatory single RPC/provider/relay
master decryption key
single production super-admin key
transaction-hash-is-success semantics
silent downgrade from strict/Shield policy
```

La lista normativa completa è in [`SECURITY_INVARIANTS.md`](SECURITY_INVARIANTS.md).

## 36. Interoperability gates

Prima dell'interoperabilità pubblica:

- encoding canonici;
- suite congelate e versionate;
- test vector handshake/rekey/frame;
- negative/replay/downgrade tests;
- control-plane finality/failure tests;
- bounded parser/resource tests;
- release/first-install trust tests;
- review crittografica e security review indipendente.
