# Freedom — Threat Model

Status: **canonical design draft**.

Normative baseline: [`SECURITY_INVARIANTS.md`](SECURITY_INVARIANTS.md).
Control-plane: [`CONTROL_PLANE_SECURITY.md`](CONTROL_PLANE_SECURITY.md).
Revocation: [`REVOCATION.md`](REVOCATION.md).
Shield: [`SHIELD.md`](SHIELD.md).
Schema: [`../spec/freedom.cddl`](../spec/freedom.cddl).

## 1. Assunzioni avversarie

Freedom assume non fidati rete/path, relay/bridge/egress, RPC/provider, peer, source di download, wall clock locale, self-declared relay metadata e singoli signer/payment worker.

Un avversario può:

- osservare timing/volume/address metadata;
- block/drop/delay/reorder/duplicate traffic;
- creare Sybil relay;
- servire stale/forked state;
- tentare rollback/downgrade;
- front-run/overwrite public control-plane slots;
- sostituire Contact QR prima del bootstrap;
- rubare device/root secrets;
- saturare storage/resources;
- distribuire artifact falsi;
- compromettere un signer o, nel worst case, colludere con un quorum.

## 2. Freedom Communication boundary

```text
Alice <==== authenticated E2EE ====> Bob
```

Session/traffic keys agli endpoint. Relay/path non è authentication authority.

## 3. Gateway / Shield boundaries

Gateway egress è trust boundary separata. Shield riduce la conoscenza del singolo hop solo con vero circuit protocol; non promette anonimato contro collusione completa/global observer.

## 4. Malicious/stale RPC

Security state richiede checkpoint/finality + state proof. Multi-RPC senza proof non basta.

## 5. Fresh-install freeze

Un fresh install non ha highest-seen locale.

Mitigazione:

```text
BootstrapTrustAnchor
+ BootstrapFreshnessFloor
```

Un verifier recente rifiuta checkpoint/signer/policy sotto il proprio floor.

Limite: un verifier autentico ma molto vecchio ottenuto soltanto da canali attacker-controlled può essere congelato nel proprio passato. Freshness del verifier stesso richiede un canale/bootstrap anchor indipendente.

## 6. False-success transaction

```text
submit -> finality proof -> execution success -> resulting-state proof -> local success
```

Tx hash non equivale a success.

## 7. Revocation ambiguity

Rischio: RPC `not found`, stale cache o namespace ambiguo viene interpretato come non-revoca.

Difesa:

- adapter-specific inclusion/non-inclusion proof semantics;
- monotonic revocation epochs/floors;
- freshness classes;
- highest-seen root/authorization/key epochs;
- fail explicit `REVOCATION_STATE_STALE`.

## 8. Root compromise

Una sola RootRecoveryKey rubata rende proprietario e attacker indistinguibili se non esiste una seconda authority.

Mitigazione per claim `ROOT_COMPROMISE` recovery:

- `UserRecoveryPolicy` precommitted;
- independent recovery keys/shares;
- threshold;
- recovery delay;
- compromise-mode `UserRootRotation`.

Senza independent precommitment Freedom non promette compromise recovery.

## 9. Rendezvous overwrite/front-running

Rischio: dopo la prima public write uno slot diventa osservabile e può essere sovrascritto/spammato.

Mitigazione:

```text
PairRendezvousSecret
 -> one-time RendezvousWriteKeypair
 -> slot derived from write public key
 -> signed generation-monotonic record
```

Osservare public key/slot non concede private write authority.

## 10. Pairwise backup rollback

Un old `PairwiseRecoveryBundle` rubato/restored può contenere state superato.

Mitigazioni:

- state/recovery-key epochs;
- highest-known rollback checks quando disponibili;
- re-authentication del peer;
- rotate/re-derive future rendezvous/session state dopo restore;
- untrusted backup source.

## 11. First-contact substitution

Un descriptor interamente sostituito prima del bootstrap può creare una relazione valida con Mallory.

Mitigazioni: `BOOTSTRAP_UNVERIFIED`, safety code/fingerprint/out-of-band verification, `CONTACT_VERIFIED` solo dopo independent assurance.

## 12. Colluding contacts

Pairwise alias riduce infrastructure correlation, non garantisce unlinkability contro contatti che confrontano root/certificate material.

## 13. Handshake downgrade

Transcript lega entrambi gli offer set; selection strongest-allowed/deterministic. Offer stripping sotto policy fallisce.

## 14. Signature cross-domain substitution

Rischio: una firma valida per un oggetto/rete viene riusata come un altro oggetto/rete.

Difesa: deterministic canonical encoding + signing domain che lega network, object type e schema version.

Child certificate scope/expiry non può superare la delegation parent.

## 15. Forward secrecy / rekey split-brain

Rekey può fallire per simultaneous init, lost commit/ack, duplicate/replay o route switch.

La state machine canonica risolve simultaneous init deterministicamente, usa key confirmation, old-key grace bounded e termina la sessione su mismatch/timeout prima del lifetime limit.

## 16. Transport semantic confusion

Adapters dichiarano reliable stream/datagram semantics. Control/media sequence spaces sono separati.

## 17. Storage exhaustion

TTL non basta: active state deve convergere a upper bound tramite overwrite/ring/prune/lease/reclaim.

## 18. Device/account privacy

V1 evita di rendere necessaria una public RootIdentity→device proof. Peer validity deriva dal DeviceCertificate; record spam è anti-abuse problem.

`max_devices` hard enforcement privacy-preserving è futuro; non si introduce un public device graph solo per monetizzazione.

## 19. Relay Sybil / provenance

`N relay IDs != N operators`.

Signed descriptors e provenance attestations distinguono self-declared/observed/attested metadata, ma una attestation non prova magicamente operator independence.

Diversity forte è probabilistica e migliora con issuer/source/custody domains differenti.

## 20. Malicious relay

Può drop/delay/correlare/mentire; non deve decrypt/impersonate/forge valid app ACK. Resource bounds obbligatori.

## 21. Shield collusion

Single hop compromise non ottiene plaintext/session identity authority. Collusion di tutti gli hop/global timing observer resta limite esplicito.

## 22. Censura / DPI / active probing

Path/provider/transport diversity e bridges aumentano reachability. Freedom non promette universal firewall bypass.

## 23. Adaptive inference

`SUSPECTED` deriva da osservazioni incoerenti; non prova censura/sorveglianza/attribution.

## 24. Gateway egress/leaks

Egress può vedere destination/timing/DNS/plaintext esterno non cifrato. Strict/Shield mode vieta silent direct fallback. DNS/IPv4/IPv6 leak tests necessari.

## 25. Payment correlation

Domain separation non basta se payment ed entitlement sono nella stessa public flow. Voucher/blind credential + nullifier riduce linkage; timing correlation può restare.

## 26. Product quota tampering

Contact/device-count V1 sono product/service policy. Un client modificato può aggirare local quota.

Questo non è un compromise E2EE; implica che il business model non deve dipendere esclusivamente da tali limiti.

## 27. Governance quorum compromise

Threshold governance elimina una singola key unilaterale ma **non** elimina il rischio di quorum collusion/compromise.

Trust assumption:

- abbastanza signer onesti/indipendenti restano fuori dal controllo dell'attaccante;
- custody/operator domains sono separati per quanto praticabile;
- no single secret-manager/account contiene un quorum.

Se un singolo soggetto controlla unilateralmente il threshold, il claim corretto è “nessuna singola chiave”, non “nessun singolo attore”.

## 28. Contract/state migration takeover

Un quorum non deve poter chiamare “migration” uno state rewrite arbitrario.

`StateMigrationProof` lega source finalized state + migration code hash + target imported root e deve essere verificabile.

## 29. Supply chain / first install

Source bytes non è trust. Verification richiede exact hash, threshold release, Android signer, current verified status/policy, bootstrap trust e freshness floor.

## 30. Anti-overclaim

Claim vietati senza evidenza:

- universal firewall bypass;
- impossible to track/block;
- surveillance detection;
- colluding-contact unlinkability;
- Shield anonymity against global observer;
- root-compromise recovery senza independent recovery policy;
- “nessun singolo attore” se un singolo operator controlla il governance quorum.

Claim corretto:

> **Freedom Communication protegge la conversazione endpoint-to-endpoint; control-plane, Shield, Gateway e recovery aggiungono proprietà verificabili entro trust assumptions e limiti espliciti.**
