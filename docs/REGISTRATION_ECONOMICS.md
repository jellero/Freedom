# Freedom — Registration Economics & Anti-Abuse

Status: **canonical design draft**

Normative security rules: [`SECURITY_INVARIANTS.md`](SECURITY_INVARIANTS.md).
Control-plane details: [`CONTROL_PLANE_SECURITY.md`](CONTROL_PLANE_SECURITY.md).

## 1. Obiettivo

Freedom deve sostenere una base Free ampia senza trasformare registration/sponsorship in storage/gas exhaustion.

> **il primo utilizzo deve restare gratuito per una persona; creare identity in massa deve avere un costo crescente per un aggressore.**

## 2. Nessuna write al semplice install

```text
install
 -> generate RootRecoveryKey / RootIdentity locally
 -> generate DeviceAuthorizationKey locally
 -> generate DeviceKey locally
 -> generate DeviceRecordCommitment locally
 -> Recovery Kit
 -> 0 mandatory chain writes
```

## 3. SponsorshipCommitment

```text
SponsorshipCommitment = H(sponsorship-specific context, domain)
```

Separato da device authorization, entitlement, payment e pairwise state.

## 4. Sponsored registration

```text
NEW OWNERSHIP CONTINUITY
 -> valid ownership/sponsorship proof?
 -> SponsorshipCommitment valid/unused?
 -> anti-abuse proof valid?
 -> relayer rate limit OK?
 -> global bounded budget OK?
 -> submit
 -> finality proof
 -> execution success
 -> resulting state proof
 -> REGISTERED
```

Transaction hash != success.

## 5. Privacy del proof

La registration production non deve richiedere di pubblicare inutilmente lo stesso global RootIdentity identifier insieme a device/payment/social state.

Quando serve dimostrare ownership continuity senza renderla un correlatore universale, usare commitment/credential/nullifier/proof separati per dominio.

Un Testnet proof linkabile deve essere dichiarato come limitazione temporanea.

## 6. Adaptive proof-of-work

Challenge può legare:

```text
challenge
sponsorship/ownership proof context
nonce
expiry
network/policy epoch
```

Difficoltà benchmarkata su device rappresentativi e adattabile sotto abuso.

## 7. Relayer multipli

Più fee relayer indipendenti; ogni relayer ha rate/budget bounds.

IP, telefono, payment account o DeviceRecordCommitment non diventano global Freedom identity.

## 8. Una sponsorship per ownership continuity

Il control-plane impedisce sponsorship iniziali illimitate per la stessa continuity senza pubblicare un global user identifier leggibile.

Reinstall/nuovo device usa Recovery Kit e continuity esistente.

## 9. Active storage bounded

Temporary state:

```text
rendezvous
RecoveryBeacon
PurchaseIntent
anti-abuse challenge/state
```

Ogni record ha:

- size bound;
- TTL/epoch;
- rate limit;
- authorization;
- **concrete reclaim/overwrite strategy**.

Consentiti: ring/bucket bounded, overwrite, `prune_expired`, lease/rent, refund/bounty bounded.

Vietato creare una nuova map key per ogni rinnovo senza reclaim.

La storia archiviale della chain resta osservabile; il requisito è bounded active state.

## 10. Costo cresce con eventi rari

```text
message            -> 0 chain writes
voice/video frame  -> 0 chain writes
active session     -> 0 heartbeat writes
normal route       -> 0 recovery writes
```

## 11. Contact slots

Il target `10 active contacts` è **product policy del client ufficiale**, non anti-Sybil primitive del protocollo.

Non pubblicare social graph per enforceare commercialmente la quota.

Un futuro enforcement anti-tamper richiede credential/nullifier/ZK dedicati e reviewati.

## 12. Budget dinamico

Durante abuso:

```text
proof difficulty     -> may increase
per-relayer rate     -> may decrease
sponsorship budget   -> bounded
existing users       -> continue communicating
```

Registration attack non interrompe utenti già attivi/recovery essenziale.

## 13. Metriche prima della mainnet

- bytes per active state object;
- storage convergence dopo migliaia/milioni logici di renewals;
- prune/refund/bounty behavior;
- registration/rotation/revocation cost;
- sponsorship correlation analysis;
- adaptive PoW benchmarks;
- Sybil simulations;
- malicious/stale RPC;
- failed transaction/state mismatch;
- device authorization privacy proof behavior.

## 14. Invarianti

- install = 0 mandatory writes;
- no global user identifier introdotto per anti-abuse;
- sponsorship domain-separated;
- no phone/SMS/payment identity requirement universale;
- multiple relayers;
- active temporary storage reclaimable/bounded;
- existing users independent from new-registration availability;
- no mailbox/message/media on-chain;
- contact quota non è social-graph enforcement V1;
- transaction hash != success.
