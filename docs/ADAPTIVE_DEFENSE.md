# Freedom — Adaptive Defense

Status: **canonical design draft**.

Normative security: [`SECURITY_INVARIANTS.md`](SECURITY_INVARIANTS.md).
Revocation/freshness: [`REVOCATION.md`](REVOCATION.md).
Network UI: [`NETWORK_STATUS_UI.md`](NETWORK_STATUS_UI.md).
Shield: [`SHIELD.md`](SHIELD.md).

## 1. Obiettivo

Adaptive Defense classifica failure/reachability e tenta automaticamente percorsi alternativi. Non è una prova di censura o sorveglianza.

## 2. Control-plane / data-plane

```text
CONTROL PLANE
verified identity/revocation/rendezvous/recovery state

DATA PLANE
messages/files/audio/video/session traffic
```

No continuous blockchain heartbeat durante sessione normale.

## 3. RecoveryBeacon

RecoveryBeacon è pairwise, cifrato, bounded e firmato con la one-time rendezvous write key derivata dal `PairRendezvousSecret`.

Osservare lo slot non concede overwrite authority.

## 4. Inference

Esempio:

```text
local connectivity OK
+ verified control-plane path OK
+ peer beacon recent
+ current data path repeatedly FAIL
 -> INTERFERENCE_OR_ROUTE_FAILURE_SUSPECTED
```

È inferenza, non attribution.

## 5. Adaptive state machine

```text
NORMAL
 -> VERIFYING_REACHABILITY
 -> INTERFERENCE_SUSPECTED
 -> ALTERNATIVE_PATH_SEARCH
 -> RECOVERED
```

Il motore può tentare:

```text
alternate direct/NAT
alternate relay
alternate provider
alternate transport
bridge
build Shield circuit
```

## 6. `SHIELDED` non è uno stato di severità

`SHIELDED` descrive una proprietà del **path** e può coesistere con una diagnosi network separata.

Esempio:

```text
Network inference: NORMAL
Protection: SHIELDED
```

oppure:

```text
Network inference: SUSPECTED
Protection: SHIELDED
```

La label `SHIELDED` è consentita solo dopo il gate di `SHIELD.md`.

## 7. Revocation freshness durante failure

Control-plane degraded non equivale automaticamente a peer revoked/non-revoked.

Se revocation state è stale, il motore espone `REVOCATION_STATE_STALE` e applica la freshness class prevista. Una existing authenticated session può avere una policy bounded diversa da un nuovo high-risk handshake.

## 8. Recovery write bounds

Beacon/recovery write usa read-before-write, generation monotonic, TTL/height bounds, backoff e concrete storage reclaim.

Stop recovery writes appena una valid session/path è ristabilita.

## 9. Free / paid boundary

Core Free riceve la stessa diagnosi tecnica fondamentale. Paid tiers possono comprare managed capacity/path diversity, non una classificazione più favorevole.

## 10. Claim boundary

Freedom può dire:

> **Interferenza o anomalia di rete sospetta; Freedom sta tentando/usando un percorso alternativo.**

Non può dire senza evidenza:

- “sei sorvegliato”;
- “il governo ti sta bloccando”;
- “passiamo ogni firewall”.
