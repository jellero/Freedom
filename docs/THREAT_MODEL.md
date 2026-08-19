# Freedom — Threat Model

Status: **canonical design draft**

Normative security rules: [`SECURITY_INVARIANTS.md`](SECURITY_INVARIANTS.md).
Control-plane details: [`CONTROL_PLANE_SECURITY.md`](CONTROL_PLANE_SECURITY.md).
Shield details: [`SHIELD.md`](SHIELD.md).

## 1. Assunzioni

Freedom assume non fidati:

- rete e path;
- relay/bridge/egress;
- RPC/provider;
- peer;
- source di download;
- singoli signer/payment worker;
- wall clock locale;
- metadata self-declared dei relay.

Un avversario può osservare metadata, bloccare/ritardare/riordinare traffico, creare Sybil relay, servire stato stale/forked, tentare rollback/downgrade, sostituire Contact QR prima del bootstrap, compromettere device/root keys, saturare storage/risorse e distribuire artifact falsi.

## 2. Security boundary

### Freedom Communication

```text
Alice <==== authenticated E2EE ====> Bob
```

Session/traffic keys agli endpoint.

### Freedom Gateway

```text
app -> Freedom tunnel -> relay/Shield -> explicit Egress -> Internet
```

Egress è trust boundary distinta e può osservare destination metadata/plaintext di protocolli esterni non cifrati.

### Freedom Shield

```text
Alice -> Hop A -> Hop B -> Bob
```

Shield riduce la conoscenza del singolo hop solo dopo vero circuit protocol con per-hop keys/layered forwarding; non promette anonimato contro collusione completa/global observer.

## 3. Trust anchors

Trust anchor del core:

- RootRecoveryKey/RootIdentity secondo ruolo;
- DeviceAuthorizationDelegation + DeviceCertificate;
- DeviceKey possession;
- pairwise authenticated state;
- verificatore del control-plane + NetworkAnchor/checkpoint;
- BootstrapTrustAnchor/release signer root;
- primitive crittografiche standard.

Non sono trust anchor:

- IP;
- relay/bridge/egress;
- RPC;
- fee relayer;
- payment provider;
- download source;
- transaction hash;
- relay metadata self-declared.

## 4. Malicious / stale RPC

Un RPC può servire stato sintatticamente valido ma vecchio o appartenente a un fork non accettato.

Difesa normativa:

```text
NetworkAnchor
 -> VerifiedControlPlaneCheckpoint
 -> state root
 -> inclusion/non-inclusion proof
```

Per stato security-sensitive, multi-RPC senza proof verification non basta.

Highest-seen checkpoint/epoch impedisce rollback.

## 5. False-success transaction

```text
submit
 -> finality proof
 -> execution success
 -> resulting state proof
 -> expected transition
 -> local success
```

Hash tx non equivale a `ACTIVE/PAID/REVOKED/VERIFIED`.

## 6. Control-plane contract takeover

Rischio: una singola Full Access key/upgrade key sostituisce il contratto o cambia le regole.

Mitigazioni:

- immutable security core oppure threshold-governed upgrade;
- code-hash manifest;
- timelock;
- accepted contract lineage;
- rollback floor;
- no silent contract-address swap;
- emergency key non può installare codice arbitrario da sola.

## 7. Signer-set rollback / quorum loss

Rischi:

- vecchio signer set validamente firmato ripresentato;
- nuovo set auto-firmato senza authorization precedente;
- quorum perso e recovery trasformato in super-admin.

Mitigazioni:

```text
previous set threshold authorizes N->N+1
next set accepts
highest-seen epoch persisted
old set cannot reactivate
recovery set/manifest pinned in advance
stronger threshold/timelock for quorum recovery
```

## 8. Verified time attacks

Wall clock locale può essere spostato avanti/indietro.

Mitigazioni:

- `VerifiedTimeAnchor` da checkpoint finalizzato;
- height/epoch-based validity quando possibile;
- monotonic local clock;
- max skew policy;
- highest-seen anti-rollback.

Clock locale non può riattivare certificati/release policy scaduti.

## 9. Device authorization privacy

Rischio: una RootIdentity/root signature pubblica accanto alla DeviceKey ricrea `RootIdentity -> device`.

Production target usa anonymous authorization/membership proof + slot nullifier.

Se Testnet è linkabile, il claim privacy production è vietato fino alla migrazione.

## 10. Root key compromise

Rischio: RootRecoveryKey compromessa.

Mitigazioni:

- RootRecoveryKey cold;
- delegated DeviceAuthorizationKey;
- `UserRootRotation` per root compromise;
- root compromise distinto da device loss;
- re-authorization dei device dopo rotation.

Continuare a usare la stessa root rubata non è recovery.

## 11. Recovery Kit offline brute force

Rischio: furto QR/bundle e brute-force del codice umano.

Mitigazioni normative:

- >=128-bit recovery entropy generata casualmente;
- memory-hard KDF (`Argon2id` target o standard equivalente);
- random salt;
- AEAD;
- parametri KDF versionati/benchmarkati;
- checksum non sostituisce authentication.

## 12. Pairwise-state loss

Root recovery non implica automaticamente recupero di `PairSecret`/`PairRendezvousSecret`.

Mitigazioni:

- authenticated device-to-device transfer;
- encrypted PairwiseRecoveryBundle.

Se entrambi mancano, ownership torna ma i contatti devono essere re-bootstrapati.

## 13. First-contact substitution

Copiare un QR valido non concede le private key, ma sostituire **l'intero descriptor** prima del primo bootstrap può creare una relazione valida con Mallory.

Mitigazioni:

- `BOOTSTRAP_UNVERIFIED` vs `CONTACT_VERIFIED`;
- safety code/fingerprint;
- out-of-band verification per assurance alta.

La crittografia non può sapere da sola che una key appartiene alla persona fisica chiamata “Bob”.

## 14. Colluding contacts

Pairwise aliases riducono infrastructure correlation ma Bob e Carol possono confrontare root/certificate material se lo vedono.

Non promettere unlinkability contro contatti colludenti senza anonymous/pairwise-scoped identity credentials implementate.

## 15. Handshake downgrade / offer stripping

Autenticare solo la suite selezionata può essere insufficiente se un MITM rimuove offerte migliori prima della scelta.

Transcript lega gli offer set di entrambi i peer e la scelta deve rispettare una deterministic/strongest-allowed policy.

## 16. Forward secrecy / post-compromise

- fresh ephemeral exchange per sessione;
- FS tra sessioni;
- bounded traffic-key lifetime;
- authenticated rekey;
- media/control keys separate;
- standard/reviewed ratchet come target PCS.

Endpoint/OS compromesso può comunque leggere plaintext corrente.

## 17. Transport semantic confusion

Rischio: assumere reliable/ordered semantics su transport datagram/multipath e bloccare text/control per perdita media.

Mitigazioni:

```text
RELIABLE_ORDERED_STREAM
UNRELIABLE_DATAGRAM
```

dichiarati da ogni adapter; sequence/replay spaces separati per control e media.

## 18. Rendezvous / storage exhaustion

TTL logico non libera automaticamente active state.

Mitigazioni:

- overwrite/ring/bucket bounded;
- permissionless prune;
- lease/rent;
- bounded refund/bounty;
- active-state upper bound testato.

Una map key nuova per ogni epoch senza reclaim è vietata.

La chain history archiviale resta osservabile.

## 19. Rendezvous metadata

Timing delle write può correlare activity/recovery.

Mitigazioni: pairwise opaque slots, encrypted payload, read-before-write, TTL/backoff, no root/device/IP plaintext.

Non elimina global traffic analysis.

## 20. Network identity leakage

Direct espone IP ai peer. Single relay può vedere entrambi i lati adiacenti.

Mitigazioni: direct opzionale, relay/Shield, temporary transport tokens, log minimizzati.

## 21. Relay Sybil / eclipse

Un attacker può creare molti relay IDs/endpoint/capacity hints.

Mitigazioni:

- signed RelayDescriptor;
- distinguish self-declared/observed/verified provenance;
- diversity per source/ASN/provider/operator quando disponibile;
- evitare path interamente da una singola provenance;
- controlled randomization;
- simulation di eclipse.

`N relay IDs != N independent operators`.

## 22. Malicious relay

Può drop/ritardare/correlare/mentire su capacity ma non deve decrypt, impersonate, derive session keys o forge app ACK.

Resource bounds obbligatori: frame/buffer/circuits/handshakes/rate/idle/TTL/hop/bandwidth.

## 23. Relay open proxy

Vietato:

```text
DEVICE_RELAY -> arbitrary Internet IP:port
```

Internet egress solo Gateway esplicito.

## 24. Shield compromise / collusion

Single hop compromise non deve ottenere plaintext o entrambe le estremità finali.

Collusione di tutti gli hop o global timing observer può correlare traffico: nessun anonimato assoluto.

Due proxy concatenati non costituiscono Shield.

## 25. Censura / DPI / active probing

Attacker può bloccare IP/provider/RPC/DNS/UDP/TLS fingerprint/bridge/egress/transport classes.

Freedom usa provider/path/transport diversity, bridges e Adaptive Defense.

> Freedom massimizza la probabilità di trovare un carrier utilizzabile; non promette universal bypass.

## 26. Adaptive interference inference

```text
local connectivity OK
+ verified control-plane path OK
+ peer beacon recent
+ data path FAIL
 -> INTERFERENCE_OR_ROUTE_FAILURE_SUSPECTED
```

Non è prova di censura/sorveglianza né attribution.

## 27. Gateway egress compromise / leaks

Egress può vedere destination IP, timing/volume, DNS e plaintext esterno non cifrato.

Mitigazioni: HTTPS app, egress diversity, multi-hop, DNS-over-tunnel, strict/kill-switch, IPv4/IPv6 leak tests.

No silent direct fallback in strict/Shield mode.

## 28. Payment correlation

Domain separation non impedisce correlazione se payment e entitlement compaiono nello stesso flow pubblico.

Mitigazione preferita:

```text
payment -> one-time EntitlementVoucher/blind credential
 -> redemption nullifier -> entitlement
```

Timing correlation può restare possibile e va dichiarata.

## 29. Contact-slot privacy/enforcement

Il limite 10/20 contatti è product policy V1, non protocol-interoperability invariant.

Questo evita di pubblicare social graph solo per enforcement commerciale.

Un futuro enforcement resistente a modified clients richiede privacy-preserving credential/nullifier/ZK design separato.

## 30. Supply chain / first install

Source dei byte non è trust.

First sideload usa `BootstrapTrustAnchor` pinned. Release verification richiede exact hash + threshold release signature + Android signer lineage + non-revoked/current policy.

## 31. Single super-admin

Production governance minima:

```text
ReleaseAuthorization   >= 3-of-5
ReleaseRevocation      >= 3-of-5
CriticalSecurityPolicy >= 3-of-5
ContractUpgrade        >= 3-of-5 + timelock
GovernanceRootRotation >= 3-of-5 + recovery
Emergency advisory     scoped + TTL
```

Payment/entitlement/emergency/relay roles non condividono authority totale.

## 32. Anti-overclaim

Distinguere:

```text
SECURITY CLAIM
REACHABILITY CLAIM
INFERENCE
```

Claim vietati senza evidenza:

- “passa ogni firewall”;
- “impossibile da bloccare”;
- “non tracciabile”;
- “rileva la sorveglianza”;
- “pairwise identity rende unlinkable contro contatti colludenti”;
- “Gateway ha la stessa E2EE di Communication”.

Claim corretto:

> **Freedom Communication protegge la conversazione endpoint-to-endpoint; Shield/Gateway/Adaptive Defense modificano la resilienza e privacy del percorso entro limiti espliciti.**
