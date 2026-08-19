# Freedom — Registration Economics & Anti-Abuse

Status: **canonical design draft**.

Normative security: [`SECURITY_INVARIANTS.md`](SECURITY_INVARIANTS.md).
Control-plane: [`CONTROL_PLANE_SECURITY.md`](CONTROL_PLANE_SECURITY.md).

## 1. Obiettivo

Il primo utilizzo deve restare gratuito per una persona, mentre la creazione massiva di record/identity deve avere un costo crescente/bounded per un aggressore.

## 2. Nessuna write al semplice install

```text
install
 -> local RootRecoveryKey/RootIdentity
 -> local DeviceAuthorizationKey
 -> local DeviceKey
 -> local DeviceRecordCommitment/DeviceControlKey
 -> Recovery Kit
 -> 0 mandatory chain writes
```

## 3. Registration V1

Il device record può essere opaque e non richiede di pubblicare RootIdentity ownership.

```text
create opaque DeviceRecord
 -> sponsorship/fee/anti-abuse checks
 -> finalized verified state
 -> peer later authenticates record through DeviceCertificate
```

Un attacker può creare un proprio record, ma non trasformarlo in un device del contatto Alice senza la valid certificate/delegation chain attesa dal peer.

## 4. Anti-abuse

Possibili primitive:

- adaptive PoW;
- per-relayer rate/budget limits;
- storage deposit/lease;
- bounded sponsorship commitment;
- multiple fee relayers;
- per-operation size/rate limits.

No phone/SMS/payment identity requirement universale.

## 5. Sponsorship privacy

Sponsorship state è domain-separated e non viene riutilizzato come network/contact identity.

Timing/linkage resta un rischio da misurare.

## 6. Device/contact quotas

`1 device / 10 contacts` V1 è product/service policy, non anti-Sybil/security primitive del protocollo.

Non introdurre public social/device graph o una ZK construction non ancora scelta soltanto per hard-enforce monetization.

Future hard enforcement privacy-preserving richiede design/review separati.

## 7. Active storage bounded

Temporary state usa overwrite/ring/prune/lease/reclaim concreto. TTL alone non soddisfa il requisito.

Una new map key infinita per renewal/epoch è vietata.

## 8. Rendezvous anti-spam

Rendezvous write usa derived one-time write key + signed generation-monotonic record. Osservare uno slot non concede overwrite authority.

Rate/size/expiry bounds restano necessari anche con write authentication.

## 9. Existing-user resilience

Registration/sponsorship attack non deve interrompere sessioni già attive o recovery essenziale degli utenti esistenti.

## 10. Metriche prima della mainnet

- bytes/cost per opaque device record;
- storage convergence;
- sponsorship/PoW benchmark;
- malicious registration spam;
- prune/refund/bounty behavior;
- stale/malicious RPC;
- rendezvous overwrite/front-run;
- failed tx/state mismatch.

## 11. Invarianti

- install = 0 mandatory writes;
- no global user ID introdotto per anti-abuse;
- opaque record creation does not imply peer authorization;
- no public social/device graph for V1 quotas;
- active temporary storage bounded/reclaimable;
- tx hash != success;
- no mailbox/message/media state.
