# Freedom — Relay Architecture

Status: **canonical design draft**.

Normative security rules: [`SECURITY_INVARIANTS.md`](SECURITY_INVARIANTS.md).
Shield details: [`SHIELD.md`](SHIELD.md).
Schema: [`../spec/freedom.cddl`](../spec/freedom.cddl).

## 1. Definizione

Un relay Freedom inoltra ciphertext tra endpoint o hop successivi. Può essere VPS/VM, dedicated server, mini-PC, community node, managed/private node o normale device opt-in.

Il relay non è account server, mailbox, trust anchor o implicit Internet exit.

> **forward, not store.**

## 2. Relay classes

```text
DEDICATED_RELAY
COMMUNITY_RELAY
DEVICE_RELAY
PRIVATE_RELAY
MANAGED_RELAY
```

La classe non è un trust signal crittografico.

## 3. Device Relay

`DEVICE_RELAY` è opt-in e resource-bounded:

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

Endpoint context e relay context restano separati.

## 4. RelayDescriptor

Schema canonico: `relay-descriptor`.

Un descriptor firmato prova soltanto possesso della relay key e integrità dei campi firmati. Non prova che operator/geography/provider self-declared siano veri.

## 5. Provenance

`provenance-attestation` può attestare claim bounded/expiring come network/provider observation o managed ownership.

Il selector distingue:

```text
SELF_DECLARED
OBSERVED
ATTESTED_PROVENANCE
```

Una attestation singola non dimostra operator independence. Più attestazioni dello stesso issuer/custody domain non contano automaticamente come issuer indipendenti.

## 6. Diversity / Sybil

```text
N relay IDs != N independent operators
```

Preferire diversity tra source/directory, observed ASN/provider, provenance issuer, relay class e transport quando disponibile.

La diversity resta probabilistica; un adversary può creare Sybil endpoints e colludere con attestatori compromessi.

## 7. Forwarding identity separation

Relay usa capability temporanee:

```text
TransportToken
RelayCircuitToken
NextHopToken
```

RootIdentity, DeviceRecordCommitment e pairwise alias non vengono inseriti nei relay header quando non necessari.

## 8. No mailbox

Consentiti solo buffer RAM bounded, timeout brevi e local retry limitato per il circuito corrente.

Vietati inbox persistente, conversation database e store-and-forward offline.

## 9. Resource bounds

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

## 10. Relay != Gateway egress

```text
DEVICE_RELAY / COMMUNITY_RELAY
  Freedom circuit -> Freedom circuit
  NO arbitrary Internet egress
```

Freedom Gateway usa Egress espliciti `MANAGED/PRIVATE/BUSINESS` o altre classi future autorizzate.

## 11. Shield

Un singolo relay può osservare entrambi i lati adiacenti di un path single-hop. Privacy multi-hop forte richiede il circuit protocol di `SHIELD.md` con per-hop keys/layered forwarding.

## 12. Relay Contributor

Target prodotto:

```text
FREE                     10 product contact slots
FREE + RELAY CONTRIBUTOR 20 product contact slots
```

Il bonus richiede contributo utile, bounded e privacy-preserving; il toggle non basta.

### Limite esplicito

Il bonus è **product/UX policy del client ufficiale**, non una security primitive anti-tamper del Freedom Protocol.

Un client open-source modificato può ignorare una quota locale. Per questo:

- peers non rifiutano sessioni E2EE valide in base alla quota remota;
- il protocollo non pubblica social graph per enforceare il bonus;
- il modello economico non deve dipendere esclusivamente da `+10 contacts`;
- managed relay/Shield/Gateway/egress capacity è una superficie commerciale più enforceable.

## 13. Contribution proof

Future qualification può usare availability/forwarding receipts aggregate/opache, senza pubblicare peer serviti o content metadata.

Non premiare volume illimitato e non creare incentivi al traffic farming.

## 14. Invarianti

Relay non può decrypt, impersonate, forge valid endpoint ACK, persist mailbox o diventare implicit Internet exit.

> **Qualsiasi macchina compatibile può inoltrare Freedom; nessuna macchina deve diventare Freedom.**
