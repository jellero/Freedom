# Freedom — Launch Plan

Status: **canonical launch plan**

Normative security rules: [`SECURITY_INVARIANTS.md`](SECURITY_INVARIANTS.md).

## 1. Obiettivo

Il lancio non ottimizza subito per download. Prima deve dimostrare che Freedom Communication è comprensibile, utilizzabile e tecnicamente credibile.

> **Freedom Communication — Powered by Freedom Protocol**
>
> Comunicazione privata live, autenticata E2EE, sincrona, senza mailbox e senza dipendenza permanente da un singolo server/percorso.

Claim vietati senza evidenza: “impossibile da tracciare”, “incensurabile”, “passa ogni firewall”, “anonimato garantito”, “rileva la sorveglianza”.

## 2. Ordine narrativo

```text
comunicazione live privata
 -> no offline mailbox
 -> expected-contact authenticated E2EE
 -> DeviceCertificate verificabile offline
 -> forward secrecy + rekey
 -> pairwise identity / no global DeviceID
 -> path/relay sostituibili
 -> recovery pairwise
 -> verifiable control-plane
 -> threshold release/security governance
 -> NEAR come prima implementazione ChainAdapter
```

## 3. Prerequisiti security prima dei creator pubblici

Blocker:

- RootIdentity/Recovery Kit non verificati end-to-end;
- DeviceCertificate assente/non verificato;
- handshake che accetta una chiave non legata al contatto atteso;
- revocation/freshness policy indefinita;
- forward secrecy non dimostrata;
- sessioni lunghe senza bounded key lifetime/rekey;
- replay/downgrade failure non testati;
- global DeviceID reintrodotto nel network layer;
- pairwise alias correlabili per errore;
- mailbox/offline queue reintrodotta;
- transaction `Failure` trattata come successo;
- state mismatch dopo finality non rilevato;
- single production super-admin key;
- update/release non threshold-verified;
- first sideload senza BootstrapTrustAnchor pinned indipendente dalla source;
- release downgrade/revocation non fail-closed;
- relay che persiste payload oltre bounds;
- claim UI non derivati da stato verificato;
- merchant/infrastructure secret nel client.

## 4. Demo minima reale

```text
Alice                         Bob
  |                            |
  | Contact QR / capability    |
  |---------- bootstrap ------>|
  |                            |
  | expected-contact auth      |
  | DeviceCertificate verify   |
  |<======== E2EE live =======>|
  |                            |
  X session ends               X
```

La demo mostra:

1. contact bootstrap;
2. device authorization verificata;
3. session establishment;
4. live send/ACK;
5. peer offline -> nessuna delivery futura;
6. nuova sessione con nuove ephemeral keys;
7. route switch se implementato;
8. Share Freedom verificato se pronto.

## 5. Founder Cohort

Indicativamente 20–50 persone:

- sviluppatori;
- Android power users;
- privacy/security users;
- amministratori rete;
- piccoli creator tecnici.

Obiettivi:

- onboarding;
- expected-contact authentication;
- recovery;
- reti/NAT/device differenti;
- latency/reliability;
- relay/device relay;
- Network Indicator false-positive;
- Share Freedom/bootstrap trust;
- claim confusi o troppo forti.

## 6. Security & Privacy Reviewers

Materiale minimo:

- repository;
- `SECURITY_INVARIANTS.md`;
- `IDENTITY_MODEL.md`;
- `ARCHITECTURE.md`;
- `PROTOCOL.md`;
- `CHAIN.md`;
- `THREAT_MODEL.md`;
- `APP_DISTRIBUTION.md`;
- build/test vector quando disponibili;
- responsible disclosure.

I reviewer devono essere liberi di pubblicare critiche.

## 7. Creator Pilot

Target iniziale: 20–30 creator piccoli/medi pertinenti.

Possibile programma:

```text
Freedom Founding Creator — Pro Lifetime
```

Benefit commerciali non comprano recensione positiva e non danno privilegi crittografici/trust.

“Lifetime” non significa banda managed infinita.

## 8. Privacy del funnel

Misurare solo eventi minimali/aggregati/opt-in:

```text
install / first open
RootIdentity initialized
contact added
first authenticated session
first live message
reconnect/recovery success
```

Non raccogliere per marketing:

- plaintext;
- social graph;
- RootIdentity/account commitment persistente quando evitabile;
- DeviceRecordCommitment peer;
- PairwiseContactAlias;
- rendezvous content;
- conversation history;
- transport/circuit token;
- IP associato alla relazione quando evitabile.

## 9. Reliability / security metriche

- onboarding completion;
- first authenticated session success;
- expected-contact mismatch reject rate/test coverage;
- live send success;
- reconnect success;
- crash-free sessions;
- latency distribution;
- DeviceCertificate expiry/revocation behavior;
- rekey success/failure;
- control-plane Failure/state-mismatch detection;
- release/bootstrap verification success;
- relay fallback/recovery success;
- Network Indicator false positives.

## 10. Go / No-Go

Target iniziali da validare:

- onboarding completion >= 80%;
- first authenticated session success >= 85% in test prerequisites;
- crash-free sessions >= 99%;
- nessun bug critico aperto su identity/key/session/release handling;
- nessuna systematic multi-second latency su sessione stabile;
- nessuna offline queue implicita;
- nessun claim pubblico su feature non implementate.

## 11. Public Launch

Può includere:

- stable release;
- sito/documentazione;
- demo breve;
- repository/spec pubblici;
- responsible disclosure;
- comparison page basata su proprietà tecniche;
- Founder/Early Supporter program;
- Share Freedom solo quando verifier/bootstrap trust sono pronti.

La homepage spiega Freedom Communication prima di NEAR.

## 12. Monetizzazione nel lancio

### Free

- 1 device;
- 10 contacts;
- Communication E2EE/live;
- community/device relay;
- Network Indicator;
- Emergency Shield bounded;
- Gateway target 100 MB/day quando disponibile.

### Relay Contributor

```text
10 base + 10 bonus = 20 contacts
```

### Plus / Shield

Più contatti/device/capacity, Always-Shielded, multi-hop, Maximum Resilience/Reachability.

### Business

Private deployment, relay/egress pool, SDK/integrations, support/SLA.

Pagare non rende la E2EE base più forte.

## 13. Gateway nel lancio

Browser integrato non è V1.

Freedom Gateway è post-V1 e richiede threat model/egress/DNS-leak/VpnService/store review separati.

`DEVICE_RELAY` non diventa Internet exit.

## 14. Governance readiness

Prima di una release production distribuita come “ufficiale”:

```text
ReleaseAuthorization   >= 3-of-5
ReleaseRevocation      >= 3-of-5
CriticalSecurityPolicy >= 3-of-5
RootRotation           >= 3-of-5 + recovery
```

Signer-set rotation/recovery deve essere testata, non solo documentata.

## 15. North-star metric

> **numero di nuovi utenti che completano con successo almeno una sessione autenticata Freedom con un altro contatto.**

Misura comunicazione reale, non installazioni.

## 16. Sequenza di lancio

```text
Founder Cohort
 -> Security/Privacy reviewers
 -> Creator Pilot
 -> larger privacy/tech creators
 -> public launch
 -> scale only after reliability/security gates
```

> **prima rendere Freedom dimostrabile, poi raccontabile, infine grande.**
