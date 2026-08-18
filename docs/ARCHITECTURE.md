# Freedom Architecture

## 1. System model

Freedom is a decentralized communications protocol composed of a **blockchain-backed control plane** and an **off-chain P2P data plane**.

The blockchain provides durable consensus only for state that benefits from global agreement. The P2P network handles state that is high-frequency, ephemeral or latency-sensitive.

This split is mandatory for the design to support realtime messaging and calls.

## 2. Design invariants

1. Message and media plaintext never leaves an endpoint.
2. Message bodies, attachments and realtime packets are never written on-chain.
3. A relay must not possess the keys required to decrypt forwarded payloads.
4. A blockchain validator is not automatically a communication relay.
5. A user's current IP address is not permanent blockchain state.
6. Realtime routing must continue without waiting for blockchain finality.
7. A compromised relay must not be sufficient to impersonate another user.
8. Long-lived identities and short-lived network locations are separate objects.
9. Every externally supplied routing or identity record is authenticated before use.
10. Protocol components must be replaceable without changing the user's cryptographic identity.

## 3. Logical components of a Freedom node

```text
+-------------------------------------------------------------+
|                         Freedom Node                        |
|                                                             |
|  +------------------+      +-----------------------------+  |
|  | Identity Manager |<---->| Chain / Identity Resolver   |  |
|  +--------+---------+      +-----------------------------+  |
|           |                                                 |
|  +--------v---------+      +-----------------------------+  |
|  | Session Security |<---->| P2P Overlay / Router        |  |
|  +--------+---------+      +-------------+---------------+  |
|           |                              |                  |
|  +--------v---------+      +-------------v---------------+  |
|  | Messaging Engine |      | Relay / Store-and-Forward   |  |
|  +--------+---------+      +-----------------------------+  |
|           |                                                 |
|  +--------v---------+      +-----------------------------+  |
|  | Attachment Store |      | Realtime Media Engine       |  |
|  +------------------+      +-----------------------------+  |
+-------------------------------------------------------------+
```

A node can expose only a subset of these capabilities. A mobile client, for example, may consume relay services without volunteering persistent storage.

## 4. Identity model

Freedom distinguishes at least three identifiers.

### User identity

A durable cryptographic identity. Its authoritative public state can be anchored on-chain.

A user record can contain or commit to:

- root public identity key;
- active device-key commitments;
- key rotation sequence;
- revocations;
- protocol capabilities;
- optional human-readable naming reference.

Private identity keys are never stored on-chain.

### Device identity

Each device receives a distinct key pair and `DeviceID`. Multi-device support therefore does not require sharing one private key among all devices.

A device can be independently revoked.

### Peer identity

A `PeerID` identifies a currently participating P2P endpoint. It can be derived from, or cryptographically bound to, a transport public key.

A peer identity can rotate more frequently than a user identity.

## 5. Signed peer records

Network location is represented by short-lived signed records rather than permanent blockchain entries.

Conceptually:

```text
PeerRecord {
    version
    user_id
    device_id
    peer_id
    transport_public_key
    reachable_addresses[]
    relay_hints[]
    capabilities[]
    sequence
    expires_at
    signature
}
```

The signature chains the record back to an authorized device identity. The blockchain is used to verify whether that device identity is still valid.

`reachable_addresses` are distributed only through the P2P discovery layer and are deliberately excluded from permanent chain state.

## 6. Discovery and routing

### 6.1 Bootstrap

A node with an empty routing table must discover at least one existing peer. This is a fundamental bootstrap requirement of any Internet P2P network.

Freedom must therefore support multiple interchangeable bootstrap mechanisms, for example:

- bundled bootstrap peer descriptors;
- peer descriptors obtained from another Freedom user;
- QR/deep-link peer import;
- community-operated bootstrap peers;
- blockchain-published commitments to rotating bootstrap sets.

Bootstrap peers are discovery aids, not trusted authorities. A malicious bootstrap peer must not be able to forge identities or decrypt sessions.

### 6.2 Overlay

After bootstrap, peers maintain an ephemeral distributed routing overlay. A DHT-style routing model is the initial architectural direction because it avoids requiring a central directory.

Routing keys should resolve to signed peer records rather than directly to unauthenticated IP addresses.

### 6.3 Churn

Peer records have short expiration times. Clients republish fresh signed records when network connectivity changes.

This avoids writing high-churn network state to the blockchain.

## 7. Session establishment

Before sending application data, two endpoints establish an authenticated encrypted session.

The handshake must provide:

- mutual authentication or authenticated recipient identity;
- forward secrecy;
- replay resistance;
- transcript binding;
- algorithm/version negotiation;
- key separation for control, messages, attachments and media signaling.

The exact cryptographic handshake suite is intentionally not fixed at architecture stage. The protocol document defines abstractions first so a reviewed construction can be selected explicitly.

## 8. Message delivery

### 8.1 Online recipient

```text
Alice
  |
  | resolve Bob identity
  | resolve signed Bob peer record
  | establish encrypted session
  v
Bob
```

The sender attempts a direct path first.

### 8.2 Unreachable or offline recipient

When Bob cannot be reached directly:

```text
Alice
  |
  | encrypt once for Bob/device session
  |
  +----> Relay A ---+
  +----> Relay B ---+----> Bob later retrieves ciphertext
  +----> Relay C ---+
```

The object is already end-to-end encrypted before it reaches a relay.

A store-and-forward object should include:

- opaque retrieval token;
- encrypted envelope;
- expiry/TTL;
- integrity identifier;
- optional proof/payment/anti-spam data.

The design should replicate across multiple independent relays so no single node becomes mandatory infrastructure.

## 9. Metadata minimization for offline mailboxes

A naive mailbox such as `mailbox/<UserID>` exposes communication metadata. Freedom should instead derive unlinkable or rotating retrieval tokens from cryptographic session material.

The desired property is that a relay can answer:

> "Do I have opaque objects for this retrieval token?"

without needing to know the recipient's stable public identity.

This requires dedicated protocol design and must be validated as part of the threat model before production use.

## 10. Attachments and media files

Large payloads are not embedded directly in chat control messages.

The sender:

1. generates an attachment encryption key;
2. encrypts the file locally;
3. chunks encrypted data;
4. computes integrity identifiers;
5. distributes chunks directly or through temporary storage peers;
6. sends the recipient an E2EE manifest containing the key and chunk descriptors.

Storage peers see ciphertext only.

Content should expire unless explicitly pinned under a future storage-policy mechanism.

## 11. Voice and video calls

Call setup is split into signaling and media.

### Signaling

Encrypted application messages carry:

- call invitation;
- capabilities;
- network candidates/path hints;
- accept/reject state;
- session identifiers;
- renegotiation and termination events.

### Media path

Path priority:

1. direct peer-to-peer connection;
2. NAT traversal / hole punching;
3. one or more untrusted relay peers forwarding encrypted packets.

Blockchain consensus is not involved in packet forwarding.

### Group calls

Small calls can use a peer mesh. Larger calls eventually require bandwidth aggregation. Freedom can support volunteer or incentivized forwarding nodes with an SFU-like role, but media must remain end-to-end encrypted above the forwarding layer so such a node cannot decrypt conference content.

## 12. Relay roles

A relay is a peer capability, not a privileged server role.

Possible capabilities:

- transient packet forwarding;
- NAT traversal assistance;
- temporary encrypted mailbox storage;
- encrypted attachment chunk storage;
- group-media forwarding.

A relay advertises signed capabilities and limits. Clients can choose relays using independent policy such as latency, availability, diversity, reputation and economic cost.

No relay is authoritative for user identity.

## 13. Blockchain responsibilities

Appropriate on-chain state:

- durable identity root;
- authorized-device commitments;
- key rotation and revocation;
- anti-Sybil/stake state if the protocol adopts it;
- protocol governance/version commitments if needed;
- durable commitments to node reputation or relay accounting if justified.

State that should remain off-chain:

- chat contents;
- attachment contents;
- call packets;
- presence;
- typing indicators;
- exact IP addresses;
- fast-changing routing tables;
- read receipts;
- contact graphs.

## 14. Blockchain access without a trusted RPC provider

Using a blockchain while depending on one hosted RPC endpoint would reintroduce a central dependency.

The target design should support a validating light client or equivalent proof-verification model. A client may query several gateways or peer nodes, but correctness must be cryptographically verifiable rather than based on trusting one provider.

## 15. NAT and hostile network environments

The Internet does not guarantee direct inbound connectivity. CGNAT, symmetric NAT, enterprise firewalls and mobile networks can prevent direct connections.

Freedom therefore cannot promise that every pair of peers communicates directly. What it can promise is that any fallback relay remains **cryptographically untrusted and replaceable**.

## 16. Mobile platform constraint

Reliable incoming communication on a suspended mobile application is not solely a networking problem. Mobile OS background policies can prevent an application from listening continuously.

Freedom's cryptographic protocol must remain independent of any platform wake-up channel. If a platform notification service is used, the notification should contain only an opaque wake-up hint; the message itself should still be fetched through and decrypted by the Freedom protocol.

## 17. Failure model

The protocol must remain safe when:

- a peer disappears during transfer;
- a relay drops packets;
- a relay stores but refuses retrieval;
- a routing record is stale;
- an attacker floods discovery records;
- the blockchain is temporarily unreachable;
- blockchain state reorganizes within its finality model;
- a device key is revoked;
- a user has several active devices;
- messages arrive duplicated or out of order.

Safety means that availability may degrade, but confidentiality and identity authentication must not silently degrade.

## 18. Initial implementation boundary

The first implementation should deliberately exclude the blockchain and complex routing from the first executable milestone.

M1 should prove the cryptographic data plane between two explicitly addressed peers. Once that path is testable, decentralized discovery and chain-backed identity can be added independently.

This order prevents consensus, routing and application bugs from being mixed together during the first security-sensitive implementation.
