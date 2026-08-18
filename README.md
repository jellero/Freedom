# Freedom

Freedom is an experimental decentralized communication network for **end-to-end encrypted peer-to-peer messaging, media exchange, voice calls and video calls without a central messaging server**.

The core idea is to use a blockchain as a **verifiable control plane** for identity and durable network state, while keeping actual communication **off-chain** on a low-latency P2P overlay.

> Blockchain is not the packet transport. Putting chat messages, media packets or call frames on-chain would make realtime communication too slow, expensive and metadata-heavy. Freedom uses the chain to establish trust; peers carry the traffic.

## Goals

- No central service that can read or control conversations.
- End-to-end encryption by default.
- Direct P2P delivery whenever connectivity permits.
- Encrypted multi-hop relay when direct connectivity is impossible.
- Text messages, files, images, audio and video.
- Voice calls and video calls.
- Offline delivery without storing plaintext on infrastructure controlled by one party.
- Cryptographic user/device identities instead of server-owned accounts.
- Routing and peer records that can be authenticated against blockchain state.
- Open protocol, so independent clients and nodes can interoperate.

## Non-goals

- Storing message bodies or media on-chain.
- Publishing users' IP addresses permanently on-chain.
- Requiring every client to be a blockchain validator.
- Treating the blockchain as a replacement for a realtime transport protocol.

## Architectural principle

Freedom separates the system into two planes:

### 1. Control plane

Durable, verifiable state:

- identity/public-key registration;
- device/key rotation and revocation;
- optional relay/node staking or anti-Sybil mechanisms;
- hashes/commitments for signed network records;
- protocol-version and capability discovery.

This is the part that can be blockchain-backed.

### 2. Data plane

Ephemeral, high-throughput traffic:

- peer discovery;
- routing;
- encrypted chat envelopes;
- media transfer;
- call signaling;
- realtime audio/video packets;
- encrypted relay and store-and-forward traffic.

This runs on the P2P network and never requires consensus for each packet.

## High-level topology

```text
                  +-------------------------+
                  |      Blockchain         |
                  | identity / keys /       |
                  | durable commitments     |
                  +------------+------------+
                               |
                        verify / resolve
                               |
        +----------------------+----------------------+
        |                                             |
   +----v-----+       encrypted P2P overlay      +----v-----+
   |  Alice   | <------------------------------> |   Bob    |
   |  peer    |                                  |   peer   |
   +----+-----+                                  +----+-----+
        |                                             |
        | if direct path is unavailable               |
        v                                             v
   +-------------------------------------------------------+
   | volunteer / incentivized relay peers                  |
   | forward opaque ciphertext; no decryption capability  |
   +-------------------------------------------------------+
```

## Routing model

The first design rule is that **fast-changing routing tables should not live directly on-chain**. Network addresses change too quickly and publishing them permanently also creates a privacy problem.

Instead:

1. every node has a cryptographic `PeerID`;
2. long-lived identity keys are anchored to blockchain state;
3. nodes publish short-lived, signed peer/routing records into the P2P overlay;
4. receivers verify those records against the blockchain-anchored identity;
5. routing itself uses an ephemeral distributed routing structure such as a DHT plus local peer tables;
6. records expire quickly and can be rotated without blockchain writes for every network change.

The blockchain therefore authenticates routing information without becoming the routing hot path.

## Messaging

A message is an encrypted envelope addressed to a recipient device or group. The sender first attempts direct delivery. If the recipient is offline or unreachable, the ciphertext can be replicated to independent relay/cache peers with a TTL.

Relays know only what is required to forward or temporarily store an opaque object. Message plaintext and attachment keys remain end-to-end encrypted.

## Calls and video calls

Call signaling travels through the same encrypted messaging layer. Audio/video then uses the lowest-latency available path:

1. direct peer-to-peer path;
2. NAT traversal / hole punching;
3. encrypted relay peer when a direct path cannot be established.

Realtime media is never written to the blockchain.

## Important engineering constraint

"No central server" is achievable as **no mandatory trusted central communication server**, but a practical Internet P2P system still needs mechanisms for bootstrap, NAT traversal, offline delivery and relay. Freedom treats those functions as replaceable, permissionless peer roles rather than privileged central infrastructure.

Mobile operating systems introduce another constraint: background delivery, especially on iOS, may require platform push mechanisms for reliable wake-up. That requirement must be isolated from the cryptographic and routing trust model rather than allowed to become the message transport itself.

## Initial milestones

### M0 — Protocol design

- identity and device model;
- threat model;
- routing record format;
- encrypted session establishment;
- message envelope format;
- relay protocol;
- offline-delivery model.

### M1 — Two-node encrypted messaging

- local node identity;
- direct peer discovery by explicit address;
- authenticated encrypted session;
- bidirectional text messages;
- replay protection and sequence handling.

### M2 — Decentralized discovery and routing

- DHT/overlay routing;
- signed short-lived peer records;
- blockchain-backed identity verification;
- peer scoring and anti-Sybil controls.

### M3 — Offline messages and media

- encrypted store-and-forward;
- redundant temporary storage across peers;
- attachment chunking and integrity verification;
- TTL and garbage collection.

### M4 — Voice/video

- encrypted call signaling;
- NAT traversal;
- direct realtime media;
- fallback relay;
- adaptive bitrate and network recovery.

### M5 — Groups and production hardening

- group key management;
- multi-device synchronization;
- spam/abuse resistance without central moderation authority;
- metadata minimization;
- audits, fuzzing and protocol compatibility tests.

## Repository status

The project is currently in the protocol-design phase. No programming language, blockchain or transport library is fixed yet; those choices should follow the protocol requirements rather than determine them.

See:

- [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md)
- [`docs/PROTOCOL.md`](docs/PROTOCOL.md)
- [`docs/THREAT_MODEL.md`](docs/THREAT_MODEL.md)
