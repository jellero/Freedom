# Freedom — Relay Architecture

Status: **canonical design draft**

Normative security rules: [`SECURITY_INVARIANTS.md`](SECURITY_INVARIANTS.md).
Shield circuit details: [`SHIELD.md`](SHIELD.md).

## 1. Definizione

Un relay Freedom inoltra ciphertext tra endpoint/hop. Può essere VPS, server, mini-PC, community node, managed/private infrastructure o normale device Freedom opt-in.

Il relay non è account server, mailbox, identity authority o Internet egress implicito.

> **forward, not store.**

## 2. Classi

```text
DEDICATED
COMMUNITY
DEVICE
PRIVATE
MANAGED
```

La classe descrive ruolo operativo, non trust crittografico.

## 3. Device Relay

Un device può essere contemporaneamente:

```text
ENDPOINT
RELAY
```

con context/keys logicamente separati.

Opt-in policy:

```text
relay_enabled
wifi_only
charging_only
battery_minimum
metered_network_allowed
max_bandwidth
max_concurrent_circuits
max_memory
max_cpu
background_policy
```

## 4. Relay identity / descriptor

Un `relay_id` da solo non dimostra operator independence.

```text
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
```

```text
RelayCandidate {
    descriptor_hash
    capability_token?
    capacity_hint?
}
```

La relay key autentica il descriptor corrente; non è identity dell'utente che gestisce il relay.

## 5. Provenance classes

Il selector distingue:

```text
SELF_DECLARED
OBSERVED
VERIFIED_PROVENANCE
```

Esempi:

- geografia dichiarata dal relay = `SELF_DECLARED`;
- ASN/provider osservato dalla rete = `OBSERVED`;
- operator/provenance attestato da una source verificabile = `VERIFIED_PROVENANCE`.

Non trattare metadata self-declared come prova di diversity.

## 6. Sybil / eclipse

Un attacker può creare molti relay IDs, IP e capacity hints.

Difese target:

- più sorgenti di discovery indipendenti;
- relay public keys/descriptor firmati;
- provenance e observed network metadata;
- diversity per ASN/provider/source/operator quando disponibile;
- randomizzazione controllata;
- evitare circuiti costruiti interamente da una singola directory/provenance;
- bounded reputation/capability senza social graph;
- circuit rebuild su pattern anomali.

`N relay IDs` **non significa** `N operatori indipendenti`.

## 7. Reachability

Un `DEVICE` relay non richiede necessariamente public inbound port. Può essere raggiungibile tramite NAT mapping, transport compatibile, connessione outbound persistente o LAN/local transport.

## 8. Forwarding identity separation

```text
TransportToken
RelayCircuitToken
NextHopToken
```

Un relay non riceve RootIdentity/DeviceRecordCommitment quando un capability token basta.

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

## 9. Nessuna mailbox

Se next hop è irraggiungibile:

- bounded RAM buffering per forwarding immediato;
- timeout/retry breve del circuito corrente;
- poi drop/failure.

Vietati persistent inbox, conversation database, offline store-and-forward e replica per consegna futura.

## 10. Resource bounds

Ogni relay impone almeno:

```text
max_frame_size
max_buffer_per_circuit
max_total_buffer
max_concurrent_circuits
max_concurrent_handshakes
rate_limit
idle_timeout
packet_ttl
hop_limit
bandwidth_quota
```

Per `DEVICE_RELAY` anche CPU/RAM/battery/temperature/network/background policy.

## 11. Privacy single-hop

```text
Alice -> Relay A -> Bob
```

Relay A può osservare timing/volume e lati adiacenti. Un single-hop relay non garantisce anonimato contro il relay stesso.

## 12. Freedom Shield

Multi-hop forte:

```text
Alice -> Hop A -> Hop B -> Bob
```

richiede circuit setup, per-hop keys e layered forwarding secondo [`SHIELD.md`](SHIELD.md).

Concatenare due proxy TCP non soddisfa Shield.

## 13. Discovery

Relay descriptor/candidate possono provenire da:

- local verified cache;
- E2EE route update;
- pairwise rendezvous/recovery;
- multiple bootstrap sources;
- non-authoritative directories/pools;
- peer announcements;
- private/managed sources.

Nessuna directory autentica il destinatario.

## 14. Relay ≠ Internet egress

Vietato nel relay base:

```text
client -> DEVICE_RELAY -> arbitrary Internet IP:port
```

Gateway usa solo egress espliciti `MANAGED/PRIVATE/BUSINESS`.

Relay Contributor non trasforma un telefono in exit node.

## 15. Relay Contributor

Target prodotto:

```text
FREE                     10 contact slots
FREE + RELAY CONTRIBUTOR 20 contact slots
```

Il bonus è policy/entitlement del client ufficiale, non una regola di interoperabilità del protocollo.

Il semplice toggle non basta. Segnali possibili: availability window, accepted circuits, bounded forwarded traffic, opaque receipts/attestations.

Non pubblicare peer serviti, plaintext, social graph o detailed traffic history.

Scadenza benefit non cancella contatti o sessioni; limita nuove aggiunte nella policy client.

## 16. Security invariants

Un relay non deve poter:

- decrypt E2EE payload;
- impersonate endpoint;
- derive session keys;
- forge application ACK;
- become persistent mailbox;
- become mandatory route;
- become implicit Internet exit.

Può drop/ritardare/rifiutare/osservare metadata; per questo è non fidato e sacrificabile.

## 17. Test gates

- relay descriptor signature tests;
- self-declared provenance spoofing;
- thousands-of-Sybil-relay simulation;
- eclipse from one discovery source;
- same-ASN/same-provider diversity failure;
- handshake/circuit resource exhaustion;
- device battery/network policy;
- no-mailbox persistence test;
- Shield tests in `SHIELD.md`.

> **Qualsiasi macchina compatibile può inoltrare Freedom; nessuna macchina deve diventare Freedom.**
