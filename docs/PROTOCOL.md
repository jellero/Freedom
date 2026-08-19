# Freedom — Protocol Specification

Status: **canonical design draft**

Normative security rules: [`SECURITY_INVARIANTS.md`](SECURITY_INVARIANTS.md).
Identity details: [`IDENTITY_MODEL.md`](IDENTITY_MODEL.md).
Control-plane security: [`CONTROL_PLANE_SECURITY.md`](CONTROL_PLANE_SECURITY.md).
Shield: [`SHIELD.md`](SHIELD.md).

Gli encoding binari e le suite concrete devono essere congelati con test vector prima dell'interoperabilità pubblica.

## 1. Principi normativi

- ogni oggetto parsabile è versionato;
- no global `DeviceID` nel wire protocol;
- no message/media/APK on-chain;
- relay/bridge forward ciphertext, non mailbox;
- contenuti solo in sessione autenticata attiva;
- no automatic offline delivery;
- RootRecoveryKey, device authorization, DeviceKey, pairwise identity, routing e traffic keys sono separati;
- RPC non è trust: security state richiede proof/checkpoint verificato;
- `transaction hash != success`;
- forward secrecy + bounded rekey sono obbligatori;
- handshake negotiation è anti-downgrade;
- transport semantics sono dichiarate, non implicitamente TCP;
- critical production governance non è `1-of-1`.

## 2. Root / authorization hierarchy

```text
RootRecoveryKey
 -> RootIdentity(root_epoch)
 -> DeviceAuthorizationDelegation(authorization_epoch)
 -> DeviceCertificate
 -> DeviceKey
```

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

## 3. Domain-separated state

```text
DeviceAuthorizationCommitment
EntitlementCommitment
PaymentBindingCommitment
SponsorshipCommitment
```

Non vengono riutilizzati come contact/routing identifiers.

## 4. Device record / authorization proof

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

Production target per activation/multi-device:

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

Il proof deve evitare un mapping pubblico leggibile RootIdentity→device. Se Testnet usa una prova linkabile, tale limite è esplicito.

## 5. DeviceCertificate

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

Il peer verifica offline delegation/certificate e DeviceKey possession; revocation/freshness deriva da cache/control-plane verificato.

## 6. Device rotation / user-root compromise

```text
RotateDeviceKey {
    device_record_commitment
    old_epoch
    new_epoch
    new_public_key
    authorization_proof
}
```

Per root compromise:

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

`LOST_DEVICE` e `ROOT_COMPROMISE` sono state machine differenti.

## 7. Recovery Kit / pairwise recovery

Ownership restore:

```text
recover RootIdentity
 -> NEW DeviceAuthorizationKey epoch if needed
 -> NEW DeviceKey
 -> NEW DeviceRecordCommitment
 -> verified activation
 -> NEW DeviceCertificate
```

Pairwise state non è on-chain.

```text
PairwiseRecoveryBundle {
    version
    state_epoch
    contacts_metadata_ciphertext
    pairwise_state_ciphertext
    integrity
}
```

Recovery path:

```text
existing device -> authenticated device transfer
or
Recovery Kit -> decrypt PairwiseRecoveryBundle
```

Se nessuno dei due esiste, ownership torna ma i contatti richiedono re-bootstrap.

## 8. Contact descriptor / assurance state

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

La capability non concede impersonation.

UI/trust state:

```text
BOOTSTRAP_UNVERIFIED
CONTACT_VERIFIED
```

Un descriptor sostituito prima del primo bootstrap può creare una relazione valida con l'attaccante; safety code/fingerprint/out-of-band verification è disponibile.

## 9. Pairwise identity

```text
PairIdentityState {
    peer_root_identity_proof
    pair_secret
    pairwise_contact_alias
    rendezvous_secret
}
```

Alias/rendezvous sono per-relazione.

Non promettere unlinkability contro contatti colludenti se il root proof/certificate material è confrontabile.

## 10. Rendezvous / RecoveryBeacon

```text
RendezvousRecord {
    version
    expires_at
    ciphertext
}

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

Read-before-write, bounded size, TTL e reclaim fisico sono obbligatori.

TTL senza prune/overwrite non soddisfa il requisito storage.

## 11. Route / Relay descriptors

```text
RouteCandidate {
    transport
    endpoint
    candidate_type
    priority
    observed_at
    expires_at
}

RelayDescriptor {
    relay_public_key
    relay_id
    relay_class
    endpoint
    transport
    capabilities
    self_declared_metadata?
    observed_metadata?
    provenance_metadata?
    expires_at
    signature
}

RelayCandidate {
    descriptor_hash
    capability_token?
    capacity_hint?
}
```

`relay_class`, geografia e provider self-declared non sono trust/diversity proof.

## 12. Transport semantic contract

Ogni `TransportAdapter` dichiara almeno:

```text
RELIABLE_ORDERED_STREAM
UNRELIABLE_DATAGRAM
```

Interfaccia concettuale:

```text
TransportAdapter {
    capabilities()
    connect(candidate, policy)
    send_stream(...)?
    send_datagram(...)?
    health()
    classify_failure()
    close()
}
```

Handshake, rekey, text/control richiedono reliable ordered semantics o reliability layer esplicito.

Media può usare stream/datagram separati. Packet loss media non blocca il control/text sequence space.

## 13. RouteUpdate

```text
RouteUpdate {
    sequence
    candidates[]
    relay_candidates[]
    issued_at
    expires_at
}
```

Viaggia E2EE. Identity del peer non cambia quando cambia route.

## 14. Handshake offers / anti-downgrade

```text
HandshakeOffer {
    network_id
    supported_versions[]
    supported_suites[]
    supported_transport_semantics[]
    certificate_hash_or_proof
    key_epoch
    ephemeral_public_key
    nonce
}
```

Transcript:

```text
network_id
expected pairwise relationship
local_offer_hash
remote_offer_hash
local/remote DeviceCertificate + delegation hash/proof
local/remote key_epoch
selected_version
selected_suite
selected_transport_semantics
ephemeral key material
nonces
session_id
```

La selezione è deterministica o verificabile come strongest-allowed common choice secondo policy locale.

Offer stripping/downgrade sotto policy causa failure.

## 15. SessionContext

```text
SessionContext {
    session_id
    local_pairwise_alias
    remote_pairwise_alias
    local_device_certificate_hash_or_proof
    remote_device_certificate_hash_or_proof
    negotiated_version
    negotiated_crypto_suite
    traffic_key_epoch
    created_at
}
```

## 16. Forward secrecy / rekey

Ogni sessione usa fresh ephemeral key exchange.

```text
TrafficKeyEpoch {
    epoch
    activated_at
    frame_count
    byte_count
}

RekeyInit
RekeyCommit
```

Tempo/frame/byte lifetime sono bounded. Fallimento rekey richiesto -> `SESSION_REKEY_REQUIRED`/session termination.

Messaging/control e media keys sono separate.

## 17. Frame spaces

Control/reliable stream:

```text
EncryptedControlFrame {
    version
    session_hint
    traffic_key_epoch
    stream_sequence
    ciphertext
}
```

Media/datagram:

```text
EncryptedMediaFrame {
    version
    session_hint
    media_stream_id
    media_key_epoch
    packet_sequence
    ciphertext
}
```

Replay window e reorder policy sono bounded e specifiche per classe.

## 18. Text / ACK / attachments

```text
ChatMessage {
    message_id
    logical_sequence
    sent_at
    body
    reply_to?
}

MessageAck {
    message_id
    ack_type
    logical_time
}

AttachmentManifest {
    attachment_id
    media_type
    plaintext_size
    chunk_size
    chunks[]
    integrity
}
```

Valido solo durante sessione/route attiva. No offline queue.

## 19. Calls

```text
CallInvite
CallAccept
CallCandidate
CallEnd
```

Signaling E2EE; media keys/rekey separati.

## 20. Synchronous behavior

```text
active authenticated session? yes -> transmit now
active authenticated session? no  -> FAIL/DISCARD
```

## 21. RelayPacket

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

No `StoreRequest` nel protocollo base.

## 22. ShieldCircuit

Il multi-hop forte è definito in [`SHIELD.md`](SHIELD.md).

Un path può essere marcato `SHIELDED` solo dopo vero circuit setup, per-hop keys e layered forwarding. Due proxy concatenati non bastano.

## 23. Relay contribution

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

Non contiene plaintext/peer list.

Il bonus contatti è product policy del client ufficiale; non modifica interoperabilità/session acceptance.

## 24. Entitlement

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

`max_devices` può essere control-plane enforced con privacy proof. `base_contact_slots` V1 non richiede social-graph enforcement on-chain.

## 25. Payment / voucher redemption

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

EntitlementVoucher {
    voucher_commitment
    product
    entitlement_delta
    expires_at
    issuer_epoch
    signature_or_blind_credential
}

EntitlementRedemption {
    voucher_nullifier
    entitlement_commitment_or_proof
    proof
}
```

Payment attestation non deve necessariamente contenere entitlement identity. One-time voucher/nullifier riduce linkage diretto; timing correlation può restare.

## 26. Control-plane verified state

```text
VerifiedControlPlaneCheckpoint {
    network_id
    chain_adapter_id
    finalized_height
    finalized_block_hash
    state_root
    finalized_time
    consensus_or_finality_proof
    verifier_version
}

VerifiedStateProof<T> {
    checkpoint
    key
    value_or_absence
    inclusion_or_non_inclusion_proof
    object_hash
}
```

RPC response senza proof non è `VERIFIED_STATE` per security-sensitive objects.

## 27. Verified time

```text
VerifiedTimeAnchor {
    finalized_height
    finalized_time
    observed_monotonic_time
    max_clock_skew
}
```

Certificate/policy/release freshness usa height/epoch quando possibile e highest-seen anti-rollback.

## 28. Verified mutation

```text
submit
 -> finality proof
 -> execution success
 -> resulting state proof
 -> exact transition match
 -> local success
```

## 29. Release / SecurityPolicy

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

## 30. SignerSetTransition / contract upgrade

```text
SignerSetTransition {
    role
    previous_epoch
    next_epoch
    previous_set_commitment
    next_set_commitment
    activation_height
    previous_set_threshold_signatures[]
    next_set_acceptance_signatures[]
}

ContractUpgradeManifest {
    governance_epoch
    current_code_hash
    new_code_hash
    migration_hash
    activation_height
    rollback_floor
    signatures[]
}
```

Old signer/policy/contract state non può fare rollback di highest-seen state.

## 31. Active storage invariant

Ogni temporary object implementa overwrite/ring/prune/lease/reclaim. Una nuova map key infinita per ogni epoch è vietata.

## 32. Error classes

```text
MALFORMED
UNSUPPORTED_VERSION
NEGOTIATION_DOWNGRADE
DEVICE_CERTIFICATE_INVALID
DEVICE_CERTIFICATE_EXPIRED
REVOCATION_STATE_STALE
CONTROL_PLANE_PROOF_INVALID
CONTROL_PLANE_ROLLBACK
CONTROL_PLANE_EXECUTION_FAILED
CONTROL_PLANE_STATE_MISMATCH
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
GOVERNANCE_TRANSITION_INVALID
```

## 33. Interoperability gates

Prima dell'interoperabilità pubblica:

- canonical encoding/suite vectors;
- delegation/certificate vectors;
- handshake offer-stripping/downgrade tests;
- control vs media sequence/reorder tests;
- rekey/replay tests;
- control-plane checkpoint/state-proof tests;
- malicious/stale/forked RPC tests;
- active-storage reclaim stress;
- UserRootRotation + pairwise recovery tests;
- first-contact substitution tests;
- RelayDescriptor/provenance/Sybil tests;
- Shield circuit tests prima di claim `SHIELDED`;
- signer-set/contract-upgrade rollback tests;
- payment voucher/nullifier tests se il flow è abilitato;
- independent security review.
