# Freedom — Network Status UI

Status: **product/security UX specification**.

Normative security: [`SECURITY_INVARIANTS.md`](SECURITY_INVARIANTS.md).
Revocation: [`REVOCATION.md`](REVOCATION.md).
Shield: [`SHIELD.md`](SHIELD.md).

## 1. Principio

La UI distingue sempre:

```text
OBSERVED FACT
DERIVED SECURITY STATE
NETWORK INFERENCE
COMMERCIAL CAPACITY STATE
```

Non usare una label più forte dello stato realmente verificato.

## 2. Communication labels

Possibili campi:

```text
Peer assurance      BOOTSTRAP_UNVERIFIED / CONTACT_VERIFIED
Device certificate  VERIFIED / INVALID
Revocation state    CURRENT / STALE / INVALID
Session             E2EE ACTIVE / INACTIVE
Route               DIRECT / RELAY / BRIDGE / SHIELDED
Network inference   NORMAL / DEGRADED / SUSPECTED / UNAVAILABLE
```

`CONTACT_VERIFIED` richiede independent safety-code/fingerprint/out-of-band assurance.

`Device certificate VERIFIED` richiede delegation/certificate/key proof + current-enough revocation state.

## 3. `SHIELDED` gate

Non basta usare un relay o due proxy.

`SHIELDED` compare soltanto quando:

```text
authenticated Shield circuit active
per-hop independent keys active
layered forwarding active
requested Shield policy satisfied
circuit not expired/degraded below policy
no silent direct fallback
```

Durante la costruzione usare copy come `Building protected path`, non `SHIELDED`.

## 4. `SUSPECTED`

`SUSPECTED` è inferenza da evidenze come route failures selettive, peer activity recente e alternative disponibili.

Non dichiarare:

- “sei monitorato”;
- “il governo ti sta bloccando”;
- “questo firewall non può fermarci”.

## 5. Revocation stale

Se revocation freshness è insufficiente:

```text
Peer identity known
Device authorization state STALE
```

Non mostrare `VERIFIED` come se fosse current.

Per sessione esistente la policy può consentire degraded continuation bounded; per nuovo high-risk handshake può richiedere refresh/failure.

## 6. Fresh install / release

Update/install UI può mostrare:

```text
Release signatures  VERIFIED
Artifact hash       VERIFIED
APK signer          VERIFIED
Policy              CURRENT
Checkpoint floor    SATISFIED
```

`Checkpoint floor SATISFIED` significa che lo stato verificato non è sotto il `BootstrapFreshnessFloor` del verifier corrente.

## 7. Gateway boundary

Gateway non mostra `End-to-end encrypted by Freedom` per generic Internet traffic.

Copy corretto:

```text
Protected path to Freedom egress
Shielded network path active   # only if actual Shield gate satisfied
```

Quota Gateway è capacity state, non security/network incident state.

## 8. Network Indicator

```text
NORMAL       route working
DEGRADED     fallback/degradation
SUSPECTED    selective interference/route failure suspected
UNAVAILABLE  no valid path found
SHIELDED     orthogonal protection/path state, only after Shield gate
```

`SHIELDED` non dovrebbe essere trattato come “severity level” equivalente a NORMAL/DEGRADED; è una proprietà del path corrente.

## 9. Anti-dark-pattern

Free e paid tiers ricevono la stessa verità tecnica. Premium può aumentare capacity/path diversity, non cambiare la diagnosi né rendere `VERIFIED` uno stato non verificato.

## 10. Accessibility

Colore non è mai l'unico segnale. Ogni stato ha testo/icona/description accessibile.
