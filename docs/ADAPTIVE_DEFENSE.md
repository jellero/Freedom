# Freedom — Adaptive Defense

Status: **canonical design draft**

Normative security rules: [`SECURITY_INVARIANTS.md`](SECURITY_INVARIANTS.md).
Control-plane proof model: [`CONTROL_PLANE_SECURITY.md`](CONTROL_PLANE_SECURITY.md).
Advanced test lab: [`ADVANCED_DEVELOPMENT.md`](ADVANCED_DEVELOPMENT.md).

## 1. Obiettivo

Freedom distingue, per quanto possibile:

- peer offline;
- normale route/NAT failure;
- relay/provider/control-plane failure;
- probabile filtering/interference del data path.

Non dichiara di rilevare sorveglianza passiva invisibile.

> **peer activity verificata + data path indisponibile può giustificare `INTERFERENCE_OR_ROUTE_FAILURE_SUSPECTED`, non attribuzione.**

## 2. Control-plane signal deve essere verificato

Non basta:

```text
RPC returned a beacon
```

Serve:

```text
VerifiedControlPlaneCheckpoint
+ state proof
+ fresh pairwise RecoveryBeacon
```

Un RPC malevolo non deve poter fabbricare `SUSPECTED` facendo credere che il peer sia recentemente attivo.

## 3. RecoveryBeacon

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

- pairwise;
- encrypted/authenticated;
- opaque slot;
- short TTL;
- no root/device/IP plaintext when avoidable;
- no continuous heartbeat.

## 4. Bounded active state

TTL logico non basta. Beacon/rendezvous devono usare overwrite/ring/prune/lease/reclaim concreto e convergere a un active-state bound.

Il recovery engine smette di scrivere appena una sessione valida viene ristabilita.

## 5. Detection condition

```text
local connectivity                    OK
verified control-plane checkpoint     OK
fresh peer beacon proof               OK
current data path                     FAIL
independent path/transport evidence    optional/additional
```

può produrre:

```text
PEER_RECENTLY_ACTIVE
DATA_PATH_UNAVAILABLE
INTERFERENCE_OR_ROUTE_FAILURE_SUSPECTED
```

Un singolo timeout non basta.

## 6. State machine

```text
NORMAL
 -> VERIFYING_REACHABILITY
 -> INTERFERENCE_SUSPECTED
 -> ALTERNATIVE_PATH_SEARCH
 -> RECOVERED
```

Alternative:

```text
different endpoint
relay with different provenance
provider/RPC path
transport family
bridge
Shield circuit
```

Retry/probing/backoff sono bounded.

## 7. Failure classes

```text
PEER_OFFLINE_PROBABLE
PATH_FAILURE
CONTROL_PLANE_PROVIDER_FAILURE
CONTROL_PLANE_PROOF_FAILURE
PROTOCOL_BLOCK_SUSPECTED
DPI_OR_FILTERING_SUSPECTED
BRIDGE_UNREACHABLE
SHIELD_PATH_FAILURE
```

`CONTROL_PLANE_PROOF_FAILURE` non viene trasformato in “peer online/offline”: significa che il signal non è verificabile.

## 8. RPC failure

```text
RPC A unavailable -> try B
RPC A stale       -> reject rollback/proof mismatch
RPC A lies        -> proof verification fails
```

Provider rotation è availability; state proof è authenticity.

## 9. Network Indicator

```text
NORMAL
SHIELDED
DEGRADED
SUSPECTED
UNAVAILABLE
```

`SHIELDED` richiede vero circuit state secondo `SHIELD.md`, non semplicemente due proxy/relay.

`SUSPECTED` = inference. Il client mostra fatti osservati separati dall'inferenza.

## 10. NAT / route dynamics

Adaptive Defense deve distinguere NAT rebinding/handover da censorship inference quando possibile.

Scenario minimi:

- Wi-Fi -> mobile;
- mobile IP change;
- NAT mapping change;
- relay failure;
- transport-specific blocking;
- control-plane provider block;
- verified peer activity with all current data paths failing.

## 11. Privacy trade-off

Recovery writes possono produrre timing metadata osservabili.

Mitigazioni:

- no global presence;
- pairwise rotating slots;
- encrypted payload;
- read-before-write;
- bounded frequency;
- no identity/IP plaintext;
- stop writes after recovery.

Non elimina traffic analysis.

## 12. Core / premium

Core Free mantiene:

- meaningful route health;
- provider/relay fallback;
- pairwise recovery;
- same diagnostic truth;
- Free alternatives before commercial prompt.

Premium può comprare capacità/path diversity più costosa, non una classificazione tecnica più favorevole.

## 13. Test lab

Automatizzare in Docker/scenario simulator:

```text
NAT rebinding
relay ban/block
provider block
stale RPC
fabricated RPC state with invalid proof
packet loss/reorder
UDP/QUIC block
DNS/SNI filter
bridge probing
clock skew
Shield hop failure
```

Dettagli: [`ADVANCED_DEVELOPMENT.md`](ADVANCED_DEVELOPMENT.md).

## 14. Invarianti

- no messages/media on control-plane;
- no global presence;
- peer-activity signal security-sensitive deve essere proof-verified;
- no single RPC trust;
- no continuous heartbeat;
- no censorship/surveillance attribution without evidence;
- recovery state physically reclaimable/bounded;
- `SHIELDED` only after actual Shield circuit gate;
- same core diagnostics for Free/paid tiers.
