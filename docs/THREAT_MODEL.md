# Freedom Threat Model

Status: **initial design draft**

Freedom aims to provide private, authenticated communications over an untrusted decentralized network. This document defines what the system assumes, what it protects, and where availability or metadata privacy can still fail.

## 1. Security objectives

Freedom should provide:

- end-to-end confidentiality for messages, attachments, voice and video;
- endpoint authentication;
- forward secrecy for established conversations where the selected cryptographic construction supports it;
- replay resistance;
- integrity and origin authentication of protocol objects;
- independent device revocation;
- resistance to malicious relays altering content undetected;
- minimal permanent metadata exposure;
- no requirement to trust a single routing, relay, storage or blockchain-RPC operator.

## 2. Assets

Protected assets include:

- root identity private keys;
- device private keys;
- session keys;
- message plaintext;
- attachment plaintext and keys;
- voice/video media plaintext;
- contact graph;
- conversation membership;
- online/offline presence;
- IP/network location;
- message timing and volume metadata;
- blockchain identity state.

Not all metadata can be hidden from every network observer. Where confidentiality cannot be guaranteed, the protocol should minimize collection, permanence and linkability.

## 3. Adversaries

### A1 — Malicious network peer

Can join the public network, create connections, send malformed packets, lie about capabilities and disappear at any time.

### A2 — Malicious relay

Can observe packet timing/size for traffic it forwards, drop packets, delay them, reorder them, duplicate them, selectively refuse storage and return stale data.

It must not be able to decrypt end-to-end payloads or impersonate endpoints solely by being a relay.

### A3 — Malicious storage peer

Can inspect opaque stored objects, delete them early, refuse retrieval, duplicate them or claim capacity it does not actually provide.

### A4 — Routing attacker

Attempts eclipse attacks, route poisoning, stale-record replay, selective isolation or DHT manipulation.

### A5 — Sybil attacker

Creates many identities or peers to dominate routing tables, relay selection or reputation mechanisms.

### A6 — Passive network observer

Can observe network endpoints, timing, packet sizes and connection patterns on paths under its visibility.

### A7 — Active man-in-the-middle

Can intercept and modify unauthenticated network traffic and redirect discovery responses.

### A8 — Compromised device

Has obtained a device's private key and local plaintext. The protocol cannot protect information already accessible to a compromised endpoint, but should support revocation and limit compromise of other devices and past sessions.

### A9 — Compromised identity root key

Can authorize malicious devices and revoke legitimate ones. Root-key protection and recovery therefore require special design.

### A10 — Malicious or unavailable blockchain gateway

Returns stale, censored or fabricated chain responses, or becomes unavailable.

Correctness should be verifiable through proofs/light-client validation rather than trusting one RPC provider.

### A11 — Consensus-level blockchain adversary

Can affect chain finality according to the security assumptions of the selected blockchain. Freedom inherits those assumptions for identity state anchored to that chain.

## 4. Trust boundaries

### Trusted

For confidentiality, the trusted computing base includes:

- the local endpoint application;
- operating-system security relevant to secret storage;
- cryptographic implementation;
- authenticated state of intended remote endpoints.

### Untrusted

The following must be treated as untrusted:

- Internet transport;
- bootstrap peers;
- DHT peers;
- relays;
- temporary storage peers;
- media forwarding nodes;
- public blockchain gateways/RPC providers;
- user-supplied routing records until verified.

## 5. Threats and mitigations

## T1 — Message interception

**Attack:** an intermediary reads a message in transit or temporary storage.

**Required mitigation:** payload is end-to-end encrypted before leaving the sender endpoint. Relays receive ciphertext only.

## T2 — Message modification

**Attack:** an intermediary changes encrypted traffic.

**Required mitigation:** authenticated encryption and transcript-bound session authentication cause modified payloads to fail verification.

## T3 — Identity impersonation

**Attack:** attacker publishes a peer record claiming another user's identity.

**Required mitigation:** peer records must be signed by an authorized device key, whose authorization is verified against durable identity state.

## T4 — Stale peer-record replay

**Attack:** attacker republishes an old valid record to redirect traffic to a stale endpoint.

**Required mitigation:** expiry, monotonically increasing sequence values and device-revocation checks.

## T5 — Session replay

**Attack:** attacker re-injects a previously accepted encrypted frame.

**Required mitigation:** session-specific sequence/replay windows and fresh handshake state.

## T6 — Downgrade attack

**Attack:** attacker forces peers to use an obsolete protocol or cryptographic suite.

**Required mitigation:** version/suite negotiation must be cryptographically bound to the authenticated handshake and enforce local minimum-security policy.

## T7 — DHT poisoning

**Attack:** attacker floods discovery with forged or misleading records.

**Required mitigation:** discovery returns candidates, never trust. Every identity-bearing record is cryptographically authenticated. Clients diversify lookup paths and reject invalid/stale records.

## T8 — Eclipse attack

**Attack:** attacker surrounds a node's routing table so most discovery passes through attacker-controlled peers.

**Mitigation direction:** peer diversity, independent bootstrap sources, routing-table diversity rules, rotation, chain-verifiable identities where useful, and anti-Sybil policy.

No single mitigation is considered complete at this stage.

## T9 — Sybil domination

**Attack:** attacker cheaply creates many peers and gains disproportionate control over routing/relay selection.

**Mitigation direction:** diversity rules plus an explicit anti-Sybil mechanism. Potential mechanisms include scarce identities, stake, proof-of-work/resource, reputation or rate-limited admission.

The project must not claim Sybil resistance until a concrete mechanism is selected and analyzed.

## T10 — Relay traffic analysis

**Attack:** relay correlates sender/recipient by timing, size or repeated routing tokens.

**Mitigation direction:** rotating opaque routing identifiers, relay diversity, optional multi-hop paths, padding/batching where latency permits, and minimizing stable identifiers.

Freedom cannot promise global traffic-analysis resistance against an observer that sees both ends of every path without substantially stronger anonymity machinery.

## T11 — Offline mailbox enumeration

**Attack:** relay learns a stable mapping from mailbox key to user identity or contact graph.

**Required direction:** retrieval capabilities/tokens should rotate and be derived from secret session state rather than a stable public `UserID`.

## T12 — Storage exhaustion

**Attack:** attacker fills volunteer relay disks with arbitrary encrypted objects.

**Required mitigation:** strict quotas, TTL, bounded object size and an admission-control mechanism before expensive storage allocation.

## T13 — Bandwidth exhaustion

**Attack:** attacker causes relays or endpoints to forward unlimited traffic.

**Required mitigation:** per-peer/connection rate limits, authenticated resource allocation, traffic caps and early rejection before costly cryptographic/application work where possible.

## T14 — Parser/resource attacks

**Attack:** malformed frames trigger excessive memory allocation, CPU use or crashes.

**Required mitigation:** bounded lengths, incremental parsing, hard limits, fuzzing, timeouts and no allocations directly controlled by unchecked remote length fields.

## T15 — Malicious attachment

**Attack:** attacker sends a validly encrypted but dangerous file to a recipient.

**Required mitigation:** encryption does not imply content safety. Client must treat decoded attachments as untrusted files and integrate OS/content safety boundaries.

## T16 — Key compromise

**Attack:** device key is stolen.

**Required mitigation:** independent device revocation, fresh session keys, rekey after revocation and no requirement to reuse a single private key across devices.

A stolen live endpoint can expose local data. Protocol guarantees do not extend past endpoint compromise.

## T17 — Root-key compromise

**Attack:** attacker steals the user's identity root key.

**Mitigation direction:** keep root key out of routine transport operations, support hardware-backed storage where available, design social/multi-device recovery or delayed recovery mechanisms before production.

This is a critical unresolved area.

## T18 — Malicious blockchain RPC

**Attack:** a gateway lies about device authorization/revocation.

**Required mitigation:** verify chain proofs/light-client consensus state or query enough independently verifiable sources that correctness does not depend on trusting the gateway response.

## T19 — Blockchain unavailability

**Attack/failure:** chain data is temporarily unreachable.

**Required behavior:** existing already-authenticated sessions may continue according to explicit cache/revocation policy, but new identity decisions must not silently downgrade into unauthenticated mode.

Exact cache validity is a protocol decision.

## T20 — Blockchain reorganization/finality

**Attack/failure:** recent identity update is reorganized.

**Required mitigation:** identity operations must define required confirmation/finality policy based on the selected chain. Security-sensitive revocations may need different UX and policy than ordinary profile updates.

## T21 — Spam and unsolicited messaging

**Attack:** permissionless lookup enables arbitrary users to flood recipients.

**Mitigation direction:** contact capabilities, invitation tokens, sender quotas, local filtering and optional scarce-resource mechanisms.

Spam prevention must not require exposing message plaintext to a central moderation service.

## T22 — Group membership leakage

**Attack:** group routing/storage structures reveal who belongs to a group.

**Mitigation direction:** group metadata should be encrypted where forwarding semantics permit; long-lived public group membership lists should not be placed on-chain by default.

## T23 — Malicious media forwarder

**Attack:** group forwarding node records media, injects frames or selectively drops participants.

**Required mitigation:** media remains participant-E2EE above forwarding layer and frames are authenticated. Availability attacks remain possible and require path/forwarder replacement.

## T24 — Push-provider metadata

**Attack/limitation:** mobile platform push service observes that an application receives a wake event.

**Required mitigation:** push payload contains no message content or cryptographic secrets and is not authoritative delivery state. The app retrieves ciphertext through Freedom after wake-up.

Platform push can still reveal timing metadata to the platform provider; this is an explicit privacy limitation if that integration is used.

## 6. Availability is not equivalent to confidentiality

A decentralized network cannot prevent every peer from refusing service. Freedom's security target is:

- malicious intermediaries cannot silently read or alter protected content;
- clients can replace unavailable paths/relays;
- redundancy reduces reliance on any one node.

It is not possible to guarantee message delivery if all viable paths or storage peers are unavailable or adversarial.

## 7. Metadata limitations

Even with perfect payload encryption, the following may remain observable to some network participants:

- source/destination network addresses of adjacent hops;
- connection timing;
- packet sizes;
- relay usage;
- approximate session duration;
- traffic volume.

Stronger anonymity requires additional techniques and introduces bandwidth/latency tradeoffs. Freedom should not advertise anonymity properties beyond those actually implemented and measured.

## 8. Security requirements for M1

The first two-node prototype is not complete unless tests demonstrate:

- invalid signatures are rejected;
- wrong recipient identity causes handshake failure;
- modified ciphertext is rejected;
- replayed ciphertext is rejected;
- oversized/malformed frames fail safely;
- a reconnect creates new session key material;
- application logs do not print plaintext secrets by default;
- private key material is never transmitted.

## 9. Security work required before public production

Before describing Freedom as production-secure, the project should have:

- a frozen, reviewable protocol specification;
- concrete cryptographic primitives selected from established constructions;
- independent cryptographic/security review;
- protocol parser fuzzing;
- adversarial network simulation;
- key-compromise and recovery design;
- anti-Sybil design;
- spam/resource-abuse controls;
- mobile background-delivery privacy review;
- reproducible interoperability tests;
- an explicit vulnerability disclosure process.
