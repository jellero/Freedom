# Freedom — Network Status UI

Status: **canonical UX/security labeling rules**

Normative security rules: [`SECURITY_INVARIANTS.md`](SECURITY_INVARIANTS.md).
Shield label gate: [`SHIELD.md`](SHIELD.md).
Adaptive evidence: [`ADAPTIVE_DEFENSE.md`](ADAPTIVE_DEFENSE.md).

## 1. Obiettivo

La UI separa:

- fatti verificati/osservati;
- inferenze;
- contromisure;
- Communication vs Gateway.

> **Semplice quando tutto funziona. Trasparente quando qualcosa cerca di impedirti di comunicare.**

## 2. Stati

```text
NORMAL
SHIELDED
DEGRADED
SUSPECTED
UNAVAILABLE
```

Colore mai unico segnale.

## 3. Gate `SHIELDED`

`SHIELDED` può essere mostrato in production solo se il runtime ha completato il vero circuit protocol di `SHIELD.md`:

- circuit setup valido;
- per-hop keys separate;
- layered forwarding attivo;
- path conforme alla Shield policy;
- no silent direct fallback.

Due relay/proxy concatenati non autorizzano la label `SHIELDED`.

## 4. Communication

```text
COMMUNICATION
Peer assurance     Bootstrap / Verified
Session            Authenticated / Inactive
Encryption         E2EE ACTIVE only after handshake verification
Route              Direct / Relay / Bridge / Shielded
Interference       None / Suspected
```

`End-to-end encrypted by Freedom` appare solo dopo expected-contact authentication + valid DeviceCertificate/delegation + DeviceKey possession + session establishment.

## 5. Gateway

```text
GATEWAY
Mode               Selected apps / Whole device
Tunnel             Protected / Off
Egress             Active / Unavailable
Route              Relay / Bridge / Shielded / Direct egress
Filtering          None / Suspected
Managed quota      used / remaining
```

Gateway non mostra `End-to-end encrypted by Freedom` per traffico Internet generico.

Copy corretto:

> **Protected path to Freedom egress**

## 6. Control-plane evidence

Non mostrare:

```text
Peer activity RECENT
Control-plane VERIFIED
```

solo perché un RPC ha risposto.

Serve:

```text
VerifiedControlPlaneCheckpoint
+ valid state proof
+ fresh pairwise RecoveryBeacon
```

Se proof/freshness fallisce:

```text
Control-plane state  UNVERIFIED / STALE
```

non `peer active`.

## 7. Evidenza vs inferenza

Fatti:

- verified RecoveryBeacon freshness;
- verified control-plane checkpoint;
- route/transport connect result;
- authenticated handshake result;
- relay/bridge/egress reachability;
- packet loss/RTT;
- Shield circuit state;
- quota state.

Inferenze:

```text
INTERFERENCE_OR_ROUTE_FAILURE_SUSPECTED
PROTOCOL_BLOCK_SUSPECTED
DPI_OR_FILTERING_SUSPECTED
```

Non dichiarare `sei monitorato`, attribution a Stato/ISP o universal bypass.

## 8. First-contact assurance

Per contatti:

```text
BOOTSTRAP_UNVERIFIED
CONTACT_VERIFIED
```

`CONTACT_VERIFIED` richiede verifica indipendente/safety code/fingerprint secondo UX.

Una sessione può essere crittograficamente autenticata rispetto al descriptor ricevuto senza provare da sola che il descriptor appartenga alla persona fisica che l'utente intendeva contattare.

## 9. Vista semplice Communication

```text
FREEDOM COMMUNICATION

Status             Connected
Peer               Verified / Bootstrap only
Encryption         End-to-end
Route              Relay / Shielded
Interference       None / Suspected
```

## 10. Vista semplice Gateway

```text
FREEDOM GATEWAY

Status             Connected
Mode               3 selected apps
Path               Shielded / Relay / Bridge
Egress             CH / managed
Filtering          None
Managed capacity   82 / 100 MB today
```

Quota esaurita non diventa `SUSPECTED`/`UNAVAILABLE` se il problema è economico/capacity.

## 11. Vista tecnica

Campi possibili:

```text
verified checkpoint height
control-plane proof state
recovery beacon proof/freshness
pairwise/contact assurance
route generation
transport semantic class
relay descriptor/provenance class
Shield circuit epoch/hop count
last failure reason
fallback attempts
Gateway egress/DNS/leak state
quota state
```

Non mostrare secrets o global identifiers non necessari.

## 12. Core Free / Pro

Free e Pro vedono la stessa verità tecnica.

Pro può aumentare managed capacity, path diversity, prewarming, multi-hop/Shield resources e Gateway quota, ma non può ottenere label di sicurezza più favorevoli a parità di stato.

## 13. Anti-dark-pattern

Il client non deve:

- elevare packet loss a censura senza evidenza;
- usare `SUSPECTED` per vendere Pro;
- mostrare `SHIELDED` prima del vero circuit gate;
- mostrare `VERIFIED` da risposta RPC non provata;
- mostrare E2EE su Gateway generico;
- confondere quota Gateway con incidente security;
- nascondere egress trust boundary;
- promettere anonimato/universal bypass.

## 14. Notification policy

```text
INFO       route changed
NOTICE     degraded/fallback
WARNING    interference/route failure suspected
CRITICAL   no valid path
```

Deduplicare per incidente.

## 15. Invarianti UX

- labels derivano da runtime state verificato;
- `SHIELDED` richiede real Shield circuit;
- `VERIFIED` control-plane richiede proof;
- `CONTACT_VERIFIED` distingue human assurance da bootstrap;
- Communication/Gateway separati;
- facts/inference separati;
- no passive-surveillance detection claim;
- same meaningful diagnostics for Free/paid tiers.
