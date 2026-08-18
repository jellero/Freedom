# Freedom Android — M1 test client

This is the first executable Freedom client. It is intentionally narrow: two Android devices communicate directly over a TCP socket on the same reachable IP network, establish a fresh authenticated encrypted session, exchange text messages, and return encrypted acknowledgements.

## What M1 implements

- Android device identity persisted in Android Keystore.
- P-256 ECDSA identity signatures.
- Ephemeral P-256 ECDH on every connection.
- Transcript-bound authenticated handshake.
- HKDF-SHA-256 session key derivation.
- AES-256-GCM encrypted frames.
- Independent keys for initiator -> responder and responder -> initiator.
- Monotonic sequence numbers and replay rejection.
- Bidirectional UTF-8 text messages.
- Encrypted message ACKs.
- Fresh session ID and keys after reconnect.
- Manual remote fingerprint comparison for M1 identity bootstrap.
- Android 17 local-network runtime permission handling.

## Not implemented yet

M1 deliberately does **not** implement blockchain identity, DHT discovery, NAT traversal, decentralized relays, offline queues, attachments, groups, calls, or video calls. Those layers come after the direct encrypted path is proven.

The public identity key exchanged during M1 is authenticated cryptographically inside the handshake, but it is not yet bound to a blockchain identity. Therefore the first connection uses manual fingerprint comparison / TOFU. Compare the fingerprint shown on each phone before treating a new peer as trusted.

## Build

The project currently targets Android API 37 and uses Android Gradle Plugin 9.3.1.

Requirements:

- Android Studio / JDK 17.
- Android SDK 37 installed.
- Gradle 9.5.0 when building from the command line without a wrapper.
- Android device or emulator with API 26+.

Open the repository root in Android Studio, sync Gradle, and run the `app` configuration.

A Gradle Wrapper binary is not committed yet. Generate it from a trusted local Gradle 9.5.0 installation before relying on `./gradlew`.

## Two-phone test

1. Install and open Freedom on phone A and phone B.
2. On Android 17, grant Freedom access to the local network when Android asks for it.
3. Connect both devices to a network where they can address each other directly (for example the same Wi-Fi without client isolation).
4. Each phone displays one or more local IPv4 addresses and listens on TCP port `45731`.
5. On phone A, enter phone B's displayed IPv4 address and tap **Connetti**.
6. After the handshake, compare the remote fingerprint shown on A with B's local fingerprint, and vice versa.
7. Send messages in both directions.
8. Verify that every delivered text message results in an `ACK` entry.
9. Disconnect and reconnect. The displayed session ID must change because fresh ephemeral ECDH keys are generated for every connection.

## Network notes

If the phones cannot connect even on the same Wi-Fi, check whether the access point enables client/AP isolation. Mobile carrier networks also commonly prevent direct inbound connections; NAT traversal and relays are future milestones.

## M1 wire shape

```text
Phone A (initiator)                  Phone B (responder)
        |                                     |
        | ClientHello                         |
        | identity pub + ephemeral pub + nonce|
        |------------------------------------>|
        |                                     |
        | ServerHello + identity signature    |
        |<------------------------------------|
        |                                     |
        | Client identity signature           |
        |------------------------------------>|
        |                                     |
        |   ECDH + HKDF => fresh tx/rx keys   |
        |                                     |
        | AES-GCM encrypted text frame        |
        |====================================>|
        | encrypted ACK                       |
        |<====================================|
```

Handshake public keys and nonces are visible by design; application plaintext and session keys are not transmitted. Later identity milestones replace manual fingerprint comparison with blockchain-backed authorization and peer records.
