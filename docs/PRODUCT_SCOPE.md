# Freedom — Product Scope

Status: **canonical product scope**

Normative security rules: [`SECURITY_INVARIANTS.md`](SECURITY_INVARIANTS.md).

## 1. Obiettivo

Freedom Communication deve dimostrare la proposta centrale di Freedom Protocol: **comunicazione privata live, autenticata E2EE, sincrona, senza mailbox centrale e senza dipendenza permanente da un singolo percorso/provider.**

> **Semplice quando tutto funziona. Trasparente quando qualcosa cerca di impedirti di comunicare.**

## 2. Launch scope — V1

La prima release pubblica è focalizzata sul **1:1**.

Funzioni essenziali:

- RootIdentity inizializzata localmente;
- DeviceKey + DeviceRecordCommitment opaco;
- **DeviceCertificate verificabile offline**;
- Recovery Kit esportabile;
- registrazione sponsorizzata quando serve, senza wallet NEAR obbligatorio;
- verified finality/state per operazioni control-plane;
- contatto-persona via QR/link;
- pairwise identity/rendezvous;
- expected-contact authenticated handshake;
- **forward secrecy tra sessioni**;
- **bounded key lifetime + rekey**;
- 1 active device Free;
- 10 active contacts Free;
- synchronous 1:1 text/media/file/voice/video;
- Live mode;
- relay forward-only;
- device relay opt-in;
- Relay Contributor +10 contacts;
- Adaptive Defense base;
- Network Indicator;
- Emergency Shield bounded;
- Share Freedom / Install QR;
- **BootstrapTrustAnchor pinned per first sideload**;
- threshold-verified release/security policy;
- block/report/store-compliance essentials.

L'utente non deve conoscere gas, RPC, NAT, commitment o primitive crittografiche nell'uso normale.

## 3. Identity / recovery

```text
RootIdentity
 -> DeviceAuthorizationCommitment
 -> DeviceRecordCommitment + DeviceKey
 -> verified activation
 -> DeviceCertificate
```

Restore:

```text
Recovery Kit
 -> RootIdentity
 -> NEW DeviceKey
 -> NEW DeviceRecordCommitment
 -> verified activation/finality
 -> NEW DeviceCertificate
 -> resolve domain-separated entitlement
```

Il restore non clona la vecchia DeviceKey.

## 4. Contact = persona

Un contatto rappresenta una persona/RootIdentity, non ogni device.

```text
FreedomContact
 -> expected RootIdentity/contact proof
 -> bootstrap DeviceCertificate optional
 -> first authenticated handshake
 -> PairSecret / PairwiseContactAlias / PairRendezvousSecret
```

Nessun global DeviceID network-facing.

## 5. Session security gate

Prima del V1 pubblico devono essere implementati:

```text
expected-contact authentication
DeviceCertificate validation
DeviceKey possession proof
revocation/freshness policy
forward secrecy
bounded traffic-key lifetime
rekey
replay protection
downgrade protection
```

Una chiave che si autofirma correttamente ma non appartiene al contatto atteso non è autenticata.

## 6. Synchronous semantics

```text
active authenticated session -> transmit now
no active authenticated session -> fail/discard
```

Vietato introdurre per comodità:

- mailbox on-chain;
- relay inbox persistente;
- automatic retry queue per peer offline;
- store-and-forward automatico.

## 7. Contatti Free / Relay Contributor

```text
FREE                     10 active contacts
FREE + RELAY CONTRIBUTOR 20 active contacts
```

Rubrica locale/cifrata; no social graph pubblico.

Benefit relay richiede contributo reale e privacy-preserving. Scadenza benefit non cancella contatti o sessioni.

## 8. Device Relay

Device relay è opt-in e resource-bounded.

Non concede plaintext/session keys e non diventa Internet egress.

```text
DEVICE_RELAY != INTERNET_EGRESS
```

## 9. Share Freedom / release security

```text
Alice -> Install QR
Bob -> bootstrap verifier
    -> peer/relay/mirror/store bytes
    -> exact SHA-256
    -> threshold FreedomRelease signatures
    -> Android signer lineage
    -> ReleaseStatus / SecurityPolicy
    -> install
```

First sideload usa root pinned indipendente dal peer/source:

```text
expected_package_id
release_signer_set_root_commitment
android_signing_root_or_lineage_anchor
minimum verifier policy
```

La source dei byte non è trust.

## 10. No super-admin gate

Prima della distribuzione production delle release:

```text
ReleaseAuthorization   >= 3-of-5
ReleaseRevocation      >= 3-of-5
CriticalSecurityPolicy >= 3-of-5
RootRotation           >= 3-of-5 + recovery
```

Emergency advisory keys sono separate, scoped e TTL-bounded.

Una singola production key non può autorizzare release arbitrarie o modificare ogni policy critica.

## 11. Verified control-plane gate

Nessuna operazione viene considerata riuscita dal solo transaction hash.

```text
submit
 -> acceptable finality
 -> execution success
 -> read resulting state
 -> exact state verification
 -> UX/local transition
```

Vale per identity/device, entitlement, sponsorship, payment, release/policy e recovery state.

## 12. Commitment privacy

Stato stabile usa commitment domain-separated:

```text
DeviceAuthorizationCommitment
EntitlementCommitment
PaymentBindingCommitment
SponsorshipCommitment
```

Non usare un `root_commitment` unico come global account correlator quando evitabile.

## 13. Adaptive Defense / Network Indicator

```text
NORMAL
SHIELDED
DEGRADED
SUSPECTED
UNAVAILABLE
```

Core Free riceve la stessa diagnosi tecnica fondamentale di Pro.

`SUSPECTED` = inferenza di rete, non prova di censura/sorveglianza.

## 14. Gateway — post-V1

Freedom Gateway non blocca V1 Communication.

```text
app -> local Gateway -> Freedom path -> explicit Egress -> Internet
```

Target Free iniziale quando disponibile:

```text
100 MB/day managed Gateway capacity
```

Separata da messaggi/chiamate Freedom ed Emergency Shield Communication.

`DEVICE_RELAY` non diventa egress.

## 15. Cosa NON blocca V1

Non sono prerequisiti:

- groups;
- group voice/video;
- public communities;
- bot/feed;
- mailbox offline;
- cloud history sync;
- full Maximum Resilience;
- complete non-public bridge pool;
- advanced padding;
- tokenized relay economy;
- embedded browser;
- whole-device Freedom Gateway.

## 16. V1.5 / V2

### Live Groups

Piccoli gruppi preservano semantica sincrona: offline member = no automatic deferred delivery.

### Multi-party media

Group voice/video richiede forwarding scalabile/SFU compatibile con il trust model e design E2EE multi-party reviewato separatamente.

## 17. Product roadmap

```text
V1
  RootIdentity / Recovery Kit
  DeviceCertificate + DeviceKey
  verified control-plane finality
  pairwise contact / rendezvous
  expected-contact authenticated handshake
  forward secrecy / rekey
  1:1 text/media/voice/video
  no mailbox/offline queue
  relay/device relay
  Relay Contributor
  Network Indicator / Adaptive Defense base
  Share Freedom + BootstrapTrustAnchor
  threshold release/security governance
  entitlement/payment foundations

V1.5
  Live Groups / small ephemeral rooms

V2
  scalable multi-party voice/video

Pro evolution
  Always-Shielded
  multi-hop
  Maximum Resilience

Post-V1 Gateway
  explicit egress
  selected-app / whole-device modes
  100 MB/day Free target
  transport/egress diversity
  Maximum Reachability
```

## 18. Launch blockers

Blocker prima del Creator Pilot:

- expected-contact authentication non verificata;
- DeviceCertificate assente/non validato;
- revocation/freshness behavior indefinito;
- forward secrecy non dimostrata;
- rekey/key-lifetime assente per sessioni lunghe;
- global DeviceID reintrodotto nel network layer;
- pairwise alias correlabili tra contatti;
- on-chain message/mailbox o offline queue reintrodotti;
- transaction Failure trattata come successo;
- state mismatch dopo finality non rilevato;
- single production super-admin key;
- first sideload senza pinned independent trust anchor;
- release non verificabile fail-closed;
- downgrade a release vulnerabile;
- Recovery Kit non verificato end-to-end;
- relay che persiste payload oltre bounds;
- Relay Contributor facilmente farmabile;
- Network Indicator con claim non derivati;
- merchant secret nell'APK;
- dipendenza hardcoded da singola infrastruttura.

Groups, Gateway e Shield avanzato non devono ritardare il V1 se questi gate sono soddisfatti.
