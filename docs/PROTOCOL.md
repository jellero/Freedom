# Freedom Protocol — Initial Specification

Status: **design draft**

This document defines protocol objects and flows without selecting a final programming language, blockchain, serialization format or cryptographic suite.

## 1. Protocol layers

```text
Application
  messages / groups / calls / attachments
        |
Secure session
  authentication / key schedule / replay protection
        |
Freedom transport
  envelopes / streams / relay / store-forward
        |
P2P overlay
  discovery / DHT / path selection
        |
Network transports
  direct datagram/stream / browser transport / relayed path
```

Blockchain verification is consumed by the identity layer and is not inserted in the packet hot path.

## 2. Versioning

Every independently parsed protocol object begins with a version and type discriminator.

Conceptually:

```text
Header {
    protocol_version
    object_type
}
```

Unknown mandatory versions must fail closed. Optional capabilities are negotiated separately so peers do not need to pretend support for features they do not implement.

## 3. Cryptographic abstraction

Before concrete algorithms are selected, the protocol requires these primitives:

- digital signature;
- authenticated key agreement / handshake;
- cryptographic hash;
- KDF;
- AEAD encryption;
- secure random generation.

The wire format must identify the selected suite so future migration is possible. Algorithm agility must not allow downgrade to an insecure suite.

## 4. Stable identity record

The durable identity state is represented conceptually as:

```text
IdentityRecord {
    version
    user_id
    root_public_key
    identity_sequence
    devices[]
    revoked_devices[]
    capabilities
    updated_at
}
```

The canonical or committed representation of this state is blockchain-specific and therefore deliberately left outside the base wire protocol.

## 5. Device authorization

```text
DeviceAuthorization {
    user_id
    device_id
    device_public_key
    not_before
    expires_at?
    capabilities
    sequence
    root_signature
}
```

The root signature proves that the device is authorized by the user identity.

Revocation must take precedence over an older authorization.

## 6. Peer record

A device advertises a short-lived network record:

```text
PeerRecord {
    version
    user_id
    device_id
    peer_id
    transport_public_key
    addresses[]
    relay_hints[]
    capabilities[]
    sequence
    issued_at
    expires_at
    signature
}
```

Validation steps:

1. parse using the declared protocol version;
2. reject expired records;
3. verify record signature;
4. resolve the corresponding user/device authorization;
5. reject revoked devices;
6. reject a record sequence older than the newest valid record already observed;
7. apply local network-address policy before dialing.

Peer records belong to the P2P discovery layer and should not be permanent chain entries.

## 7. Session establishment

A successful session handshake yields a `SessionContext`:

```text
SessionContext {
    session_id
    local_user_id
    local_device_id
    remote_user_id
    remote_device_id
    negotiated_version
    negotiated_crypto_suite
    tx_keys
    rx_keys
    created_at
    rekey_state
}
```

The handshake transcript must bind:

- both peer identities;
- device identities;
- ephemeral handshake keys;
- negotiated protocol version;
- negotiated cipher suite;
- session identifier.

An active attacker must not be able to alter these fields without handshake failure.

## 8. Encrypted frame

Once a session exists, transport frames are encrypted.

The external framing should expose only what a forwarding hop requires.

Conceptually:

```text
EncryptedFrame {
    version
    routing_class
    opaque_destination?
    session_hint?
    ciphertext
}
```

The authenticated encrypted content contains:

```text
InnerFrame {
    frame_type
    session_id
    sequence
    timestamp_or_logical_time
    payload
}
```

The exact split between visible and encrypted fields must be driven by metadata-minimization analysis.

## 9. Replay protection

Each secure session maintains a monotonic receive window or equivalent anti-replay structure.

Requirements:

- reject an already accepted sequence value;
- tolerate bounded out-of-order arrival;
- prevent unbounded memory growth from forged sequence numbers;
- reset state only through an authenticated session transition.

## 10. Text message object

```text
ChatMessage {
    message_id
    conversation_id
    sender_device_id
    logical_sequence
    sent_at
    content_type
    body
    reply_to?
    attachment_manifests[]
}
```

This entire object is protected inside the end-to-end encrypted session/message layer.

`message_id` must be unpredictable or collision-resistant enough to avoid ambiguity across devices.

## 11. Delivery acknowledgement

```text
MessageAck {
    message_id
    ack_type
    receiver_device_id
    logical_time
}
```

Possible semantic classes:

- received by endpoint;
- persisted by endpoint;
- displayed/read, if the user enables that feature.

Read state is application metadata and must never require blockchain publication.

## 12. Attachment manifest

```text
AttachmentManifest {
    attachment_id
    media_type
    plaintext_size
    encrypted_size
    chunk_size
    chunks[]
    encryption_parameters
    content_integrity
    expires_at?
}
```

Each chunk descriptor contains an opaque retrieval identifier and integrity commitment.

Attachment encryption keys are carried only inside E2EE application data.

## 13. Relay forwarding

A forwarding relay receives an opaque packet with a next-hop or retrieval token that is sufficient for forwarding but insufficient for application decryption.

```text
RelayPacket {
    version
    relay_mode
    next_hop_token
    ttl_or_hop_limit
    packet_id
    ciphertext
    relay_auth?
}
```

Relay nodes must enforce local resource limits before allocating memory, disk or bandwidth.

## 14. Offline store-and-forward

### Store request

```text
StoreRequest {
    storage_token
    object_id
    expires_at
    encrypted_object
    resource_proof?
}
```

### Fetch request

```text
FetchRequest {
    retrieval_token
    cursor?
    authorization_proof?
}
```

### Fetch response

```text
FetchResponse {
    objects[]
    next_cursor?
}
```

`storage_token` and `retrieval_token` must not trivially reveal a stable user identifier.

The sender should replicate important offline objects across more than one independently selected relay.

## 15. Contact establishment

The first contact between two users needs an authenticated identity bootstrap.

Candidate mechanisms include:

- QR code containing a signed contact descriptor;
- direct link containing a public identity descriptor;
- lookup through an optional naming layer whose result is verified against chain state;
- out-of-band fingerprint comparison.

The protocol must distinguish "identifier found" from "identity verified".

## 16. Presence

Presence is ephemeral and must remain off-chain.

A presence announcement can be a short-lived signed or session-authenticated object with states such as reachable/unreachable plus capability hints.

The system should avoid globally broadcasting a user's precise online state unless explicitly required.

## 17. Call signaling

Call control runs over the encrypted messaging/session channel.

```text
CallInvite {
    call_id
    media_capabilities
    transport_capabilities
    ephemeral_call_key_material
}

CallAccept {
    call_id
    selected_capabilities
    path_candidates[]
}

CallCandidate {
    call_id
    candidate
    priority
}

CallEnd {
    call_id
    reason
}
```

These are logical objects; exact transport candidate syntax is not fixed yet.

## 18. Media encryption boundary

Media confidentiality must terminate only at participant endpoints.

If a packet relay or group forwarding node is used, that node must be able to route or selectively forward encoded frames without obtaining the media decryption key.

Call key material must be distinct from ordinary chat/session traffic keys.

## 19. Routing resolution flow

A sender resolving a remote user follows this logical path:

```text
UserID
  |
  +--> blockchain/light-client verification
  |       -> valid DeviceIDs / device public keys
  |
  +--> overlay lookup
          -> candidate signed PeerRecords
                 |
                 +--> validate device authorization
                 +--> validate expiry + sequence
                 +--> dial best viable path
```

A DHT result alone never authenticates a user.

## 20. Path selection

Candidate paths can be scored locally using:

- directness;
- measured RTT;
- transport compatibility;
- recent success rate;
- relay independence/diversity;
- relay policy/cost;
- privacy preference.

No globally trusted path-selection authority is required.

## 21. Resource and spam controls

A completely permissionless message store is vulnerable to storage and bandwidth exhaustion.

The protocol therefore needs pluggable admission controls for expensive operations. Candidate mechanisms can include:

- sender authorization/contact state;
- per-peer quotas;
- computational proof;
- stake/reputation;
- micropayment/accounting;
- recipient-issued mailbox capability tokens.

No mechanism is selected yet. Security, privacy and denial-of-service tradeoffs must be evaluated before implementation.

## 22. Error handling

Protocol errors should distinguish at least:

- malformed object;
- unsupported version;
- authentication failure;
- expired record;
- revoked device;
- replay detected;
- route unavailable;
- relay refused/resource exhausted;
- storage object expired;
- session rekey required.

Remote error text must never be trusted as safe UI content.

## 23. Privacy rules for logs

Production clients and nodes must not log by default:

- message plaintext;
- attachment keys;
- session keys;
- private keys;
- complete contact graphs;
- stable identity + IP correlations unless explicitly enabled for debugging.

Debug builds must clearly separate sensitive trace logging from normal operation.

## 24. First executable protocol milestone

The smallest useful implementation is deliberately narrow:

```text
Peer A                         Peer B
  |                              |
  | explicit address            |
  |----------------------------->|
  | authenticated handshake     |
  |<============================>|
  | encrypted text frame        |
  |----------------------------->|
  | authenticated ACK           |
  |<-----------------------------|
```

M1 acceptance criteria:

- two independently generated device identities;
- authenticated encrypted connection;
- bidirectional text messages;
- tampering causes rejection;
- replayed frames are rejected;
- reconnect creates a fresh secure session;
- no plaintext is observable on the network path.

Blockchain identity, DHT routing, relays, media and groups are intentionally subsequent milestones.
