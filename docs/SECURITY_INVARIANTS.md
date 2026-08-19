# Freedom — Security & Trust Invariants

Status: **canonical / normative design rules**.

Questo documento definisce proprietà che una implementazione Freedom compatibile **MUST** rispettare. In caso di conflitto, queste invarianti prevalgono.

Schema canonico: [`../spec/freedom.cddl`](../spec/freedom.cddl).
Schema/signing rules: [`../spec/README.md`](../spec/README.md).
Control-plane: [`CONTROL_PLANE_SECURITY.md`](CONTROL_PLANE_SECURITY.md).
Revocation/freshness: [`REVOCATION.md`](REVOCATION.md).

## 1. Trust model

Freedom separa rigidamente:

```text
RootRecoveryKey                 -> cold recovery / user-root continuity
UserRecoveryPolicy              -> independent precommitted compromise recovery
DeviceAuthorizationKey          -> delegated device authorization epoch
DeviceCertificate               -> offline authorization of DeviceKey
DeviceKey                       -> operational device authentication
DeviceRecordCommitment          -> opaque control-plane handle
DeviceControlKey                -> scoped rotation/revocation of one opaque record
PairwiseContactAlias            -> relationship-specific identity
PairRendezvousSecret            -> pairwise rendezvous/recovery authority
TransportToken                  -> temporary path/circuit identity
Session keys                    -> ephemeral E2EE traffic
EntitlementCommitment           -> product/service capacity
PaymentBindingCommitment        -> separate economic binding
SponsorshipCommitment           -> separate anti-abuse state
VerifiedControlPlaneCheckpoint  -> cryptographically verified control-plane state root
```

Nessun elemento viene automaticamente riutilizzato come un altro.

## 2. Primitive vietate

Freedom Protocol **MUST NOT** introdurre:

- global user/device identifier richiesto dal network layer;
- RootIdentity o DeviceRecordCommitment come routing/contact ID;
- messages/files/audio/video on-chain;
- mailbox on-chain;
- persistent relay inbox;
- automatic offline delivery queue/store-and-forward nel protocollo base;
- public readable social graph;
- public readable `RootIdentity -> devices[]` mapping come requisito V1;
- mandatory central delivery server;
- mandatory single RPC/provider/relay/egress;
- master decryption key;
- single production credential con potere amministrativo totale;
- single Full Access key capace di sostituire silenziosamente il security core production;
- `transaction hash == success`;
- silent downgrade da strict/Shield policy;
- temporary on-chain state che cresce senza reclaim/overwrite bounded;
- unsigned/undomain-separated security objects;
- `RPC not found == non-revoked` semantics.

## 3. Synchronous communication

```text
active authenticated session -> transmit now
no active authenticated session -> fail/discard now
```

Il protocollo non crea automaticamente consegna futura.

## 4. Canonical schema / deterministic signing

I field name e shape degli oggetti sono definiti in `spec/freedom.cddl`.

Ogni firma security-sensitive **MUST** essere domain-separated almeno per:

```text
Freedom protocol domain
network_id
object_type
schema/object version
canonical deterministic bytes
```

Non firmare JSON ad-hoc, dump di oggetti language-specific o mappe non deterministiche.

Un child authority non può ampliare il parent:

```text
DeviceCertificate.capabilities subset-of DeviceAuthorizationDelegation.capabilities
DeviceCertificate.expires_after_height <= delegation.expires_after_height
certificate.root_epoch == delegation.root_epoch
certificate.authorization_epoch == delegation.authorization_epoch
```

## 5. Device authorization without public account mapping

V1 non richiede una ZK construction per dimostrare `active_devices <= max_devices` on-chain.

Il control-plane può conservare un record opaco indipendente dalla RootIdentity:

```text
DeviceRecordCommitment
DeviceKey
DeviceControlPublicKey
status / key_epoch
```

La **legittimità per il peer** deriva da:

```text
RootRecoveryKey
 -> DeviceAuthorizationDelegation
 -> DeviceCertificate
 -> DeviceKey possession
```

Il contratto non deve conoscere necessariamente quale RootIdentity possiede il record per rendere autenticabile il peer.

Creazione/spam dei record viene limitata tramite fee/sponsorship/anti-abuse; rotation/revocation usa `DeviceControlKey` scoped.

Hard enforcement privacy-preserving di `max_devices` può essere aggiunto successivamente con credential/nullifier/ZK reviewati. Finché non esiste, il device-count commerciale è policy del client/servizio ufficiale, non security/interoperability invariant.

## 6. Revocation / freshness

Revocation è definita in [`REVOCATION.md`](REVOCATION.md).

Un nuovo handshake verifica almeno:

```text
certificate/delegation validity
DeviceKey possession
root / authorization / key epochs
current-enough revocation state proof
highest-seen anti-rollback
```

Una RPC che risponde `not found` non prova non-revoca.

Stato stale produce `REVOCATION_STATE_STALE`; non viene mascherato da `VERIFIED`.

## 7. Control-plane authenticity

Per security-sensitive state:

```text
NetworkAnchor
 -> VerifiedControlPlaneCheckpoint
 -> state root
 -> inclusion/non-inclusion proof
 -> canonical object
```

Una risposta RPC non provata non è `VERIFIED_STATE`.

## 8. Bootstrap freshness

Un nuovo device non possiede highest-seen locale. First-install verifier e release recenti includono un `BootstrapFreshnessFloor` canonico:

```text
minimum_checkpoint_height
minimum_checkpoint_hash?
minimum_signer_set_epoch
minimum_policy_epoch
issued_in_release_id
```

Il client rifiuta bootstrap state sotto il floor incorporato nel verifier/release che sta eseguendo.

Limite esplicito: se anche il verifier autentico è una copia molto vecchia ottenuta esclusivamente da canali controllati dall'attaccante, Freedom non può dedurre dal nulla che esista uno stato più recente. La freshness del verifier stesso richiede un anchor/canale indipendente.

## 9. Verified finality — tx hash != success

```text
submit signed operation
 -> acceptable finality proof
 -> execution success
 -> resulting state proof
 -> exact expected transition
 -> local success
```

Vale per device/root state, revocation, entitlement, sponsorship, payment redemption, policy, release, signer-set, contract upgrade/migration e recovery state.

## 10. Bounded active state

TTL logico non basta.

Temporary state richiede:

- size bound;
- rate limit;
- authorization;
- expiry/epoch;
- concrete overwrite/ring/prune/lease/reclaim;
- upper bound derivabile dello stato attivo.

La blockchain history archiviale può restare osservabile; Freedom non la descrive come cancellata.

## 11. Rendezvous write authorization

Uno slot pubblico appena usato non può diventare liberamente sovrascrivibile.

Per ogni direzione/epoch:

```text
PairRendezvousSecret
 -> deterministic one-time RendezvousWriteKeypair
 -> write_public_key
 -> slot_id = H(domain || write_public_key || epoch || direction)
```

`RendezvousRecord`/`RecoveryBeacon` includono `write_public_key`, `generation`, expiry e `write_signature`.

Il control-plane accetta create/update solo se:

```text
slot derivation matches
write signature valid
generation monotonic
record within size/expiry bounds
```

Osservare slot/public key non consente overwrite senza la private write key.

## 12. Verified time

Expiry/freshness usa `VerifiedTimeAnchor`, height/epoch e monotonic local time. Wall clock locale non può riattivare stato vecchio o fare rollback di highest-seen state.

## 13. Root compromise requires independent precommitment

`ROOT_COMPROMISE` non è risolvibile se proprietario e attaccante possiedono esattamente la stessa unica RootRecoveryKey e non esiste una seconda authority precommitted.

Per rivendicare recovery da root compromessa Freedom richiede un `UserRecoveryPolicy` registrato/pinned **prima dell'incidente**, per esempio un threshold di recovery keys/shares indipendenti.

```text
UserRecoveryPolicy
 -> recovery key commitments
 -> threshold
 -> recovery delay
 -> policy commitment
```

`UserRootRotation` distingue:

```text
NORMAL              -> old-root continuity proof
COMPROMISE_RECOVERY -> independent recovery quorum proof + delay
```

Se l'utente non ha configurato una recovery authority indipendente, il progetto deve dichiarare che può recuperare perdita del device/backup ma **non può distinguere proprietario e ladro dopo compromissione completa della root secret**.

## 14. Pairwise recovery lifecycle

`PairSecret`/`PairRendezvousSecret` non sono on-chain.

Recovery path:

```text
A. authenticated surviving-device transfer
B. encrypted PairwiseRecoveryBundle stored/exported on user-chosen backup media/source
```

Lo schema canonico è in `spec/freedom.cddl` e usa `contacts_metadata_ciphertext`, non una lista contatti plaintext.

Il bundle deve avere version/state epoch, anti-rollback e discovery/backup semantics esplicite. Dopo restore da backup, i peer devono re-authenticate e ruotare/re-derive future rendezvous/session state prima di considerare pienamente corrente lo stato recuperato.

Se non esiste surviving device né backup valido, ownership torna ma i contatti richiedono re-bootstrap.

## 15. Pairwise privacy claim boundary

Alias/rendezvous pairwise riducono infrastructure correlation. Non promettere unlinkability contro contatti colludenti se confrontano root/certificate material.

## 16. First-contact substitution

La E2EE autentica il descriptor ricevuto; non può sapere da sola che esso appartenga umanamente a “Bob”.

Il client distingue almeno:

```text
BOOTSTRAP_UNVERIFIED
CONTACT_VERIFIED
```

Safety code/fingerprint/out-of-band verification deve essere disponibile.

## 17. Handshake anti-downgrade

Il transcript lega entrambi gli offer set, selected version/suite/transport semantics, certificate/delegation proofs, epochs, ephemeral keys, nonces e session ID.

La scelta è deterministic/strongest-allowed secondo policy. Offer stripping sotto policy causa failure.

## 18. Forward secrecy / rekey state machine

Freedom Communication richiede fresh ephemeral exchange, FS tra sessioni, separate control/media keys e bounded traffic-key lifetime.

Rekey usa gli oggetti canonici `RekeyInit`, `RekeyCommit`, `RekeyAck`.

State machine minima:

```text
STABLE
 -> INIT_SENT or INIT_RECEIVED
 -> COMMIT_ESTABLISHED
 -> NEW_KEY_PENDING_ACK
 -> STABLE(next_epoch)
```

Regole:

- `next_epoch == current_epoch + 1`;
- simultaneous init per lo stesso epoch viene risolta deterministicamente tramite session role/session_id ordering;
- responder deriva la next key ma non cancella subito la old key;
- `RekeyAck` contiene key confirmation sotto il nuovo key schedule;
- dopo ACK nessun nuovo frame viene inviato con old key;
- old receive key può restare solo per una bounded in-flight grace window;
- duplicate/replayed Init/Commit/Ack sono idempotent reject/ignore secondo transcript hash;
- timeout/mismatch -> session termination, mai split-brain silenzioso;
- route switch non resetta il key epoch.

## 19. Transport semantic contract

Ogni TransportAdapter dichiara `RELIABLE_ORDERED_STREAM` e/o `UNRELIABLE_DATAGRAM`.

Handshake/control/text/rekey richiedono reliable ordered semantics o reliability layer esplicito. Media usa sequence/replay spaces separati.

## 20. Production governance / trust assumption

Production minima:

```text
ReleaseAuthorization    >= 3-of-5
ReleaseRevocation       >= 3-of-5
CriticalSecurityPolicy  >= 3-of-5
ContractUpgrade         >= 3-of-5 + timelock
GovernanceRootRotation  >= 3-of-5 + recovery
Emergency advisory      scoped + TTL
```

Questa proprietà significa **nessuna singola chiave o credential tecnica unilaterale**.

Non equivale magicamente a nessun rischio organizzativo: collusione/compromissione del quorum è una trust assumption esplicita.

Production governance deve quindi usare custody/operator domains indipendenti per quanto praticabile, con audit/transparency delle transizioni. Se lo stesso soggetto controlla unilateralmente abbastanza signer per raggiungere il quorum, non può essere descritto come assenza di un singolo attore amministrativo.

## 21. Signer-set / contract / migration anti-rollback

Signer-set transition è cross-authorized e monotona. Contract upgrade è immutable-core oppure threshold/timelocked/code-hash pinned.

Chain migration richiede non solo un manifest firmato ma anche un `StateMigrationProof`:

```text
source finalized checkpoint
source export root
migration program hash
migration input commitment
target imported state root
verification artifact
```

Il target state deve essere verificabilmente derivato dalla source secondo la migration rule; il quorum non può semplicemente firmare uno state rewrite arbitrario e chiamarlo migrazione.

## 22. Release / first-install trust

Release installabile verifica exact hash + threshold FreedomRelease + Android signer lineage + verified ReleaseStatus + SecurityPolicy + anti-rollback.

First sideload usa `BootstrapTrustAnchor` con `BootstrapFreshnessFloor` indipendente dalla source dei byte.

## 23. Payment privacy

Payment/provider identity non viene riutilizzata come Freedom identity. Dove serve ridurre linkage payment→entitlement usare one-time voucher/blind credential + redemption nullifier. Timing correlation può restare.

## 24. Product quota boundaries

`10/20 contact slots` e, in V1, `max_devices` commerciale sono **product/service policy**, non security/interoperability invariants del protocollo.

Un client open-source modificato può aggirare una policy locale. Il modello commerciale non deve dipendere esclusivamente da tali limiti. Managed Gateway/Shield/egress/capacity sono superfici più enforceable senza pubblicare social/device graph.

## 25. Relay provenance / Shield diversity

`N relay IDs != N independent operators`.

Provenance attestations hanno issuer, claim type/value, expiry e signature canonici. Il selector distingue self-declared, observed e independently attested metadata.

Una provenance attestation non dimostra da sola operator independence; diversity forte richiede più issuer/source domains e resta una probabilistic trust signal, non una prova matematica.

## 26. Security labels are derived state

`VERIFIED`, `E2EE`, `ACTIVE`, `SHIELDED`, `SUSPECTED`, `REVOKED` derivano da verifiche/osservazioni implementate.

`SUSPECTED` resta inferenza. `SHIELDED` richiede il circuit protocol di `SHIELD.md`.

## 27. Fail closed / fail explicit

Failure di authentication, proof verification, revocation, downgrade, release verification o governance transition non diventa successo silenzioso.

## 28. Human review boundary for normative specification

Codex/agent possono **proporre** modifiche a:

```text
docs/SECURITY_INVARIANTS.md
docs/CONTROL_PLANE_SECURITY.md
docs/REVOCATION.md
docs/IDENTITY_MODEL.md
docs/PROTOCOL.md
spec/freedom.cddl
```

ma una modifica che indebolisce/rimuove un MUST/MUST NOT, cambia un trust assumption, un signing domain, una security state machine o uno schema firmato richiede human review esplicita prima di diventare canonical/main.

Un test failing non giustifica automaticamente la modifica della requirement.

## 29. Review gates

Prima dell'interoperabilità pubblica:

- canonical deterministic encoding + signing-domain test vectors;
- delegation/certificate scope/expiry negative vectors;
- revocation inclusion/non-inclusion/freshness vectors;
- bootstrap stale-checkpoint tests su fresh install;
- rendezvous overwrite/front-run tests;
- handshake offer-stripping/downgrade tests;
- rekey simultaneous/loss/duplicate/route-switch tests;
- control/media transport semantic tests;
- control-plane checkpoint/state-proof/finality tests;
- stale/forked/malicious RPC tests;
- storage reclaim convergence tests;
- independent-recovery `UserRootRotation` race/timelock tests;
- pairwise backup rollback/post-restore rotation tests;
- first-contact substitution tests;
- signer quorum/custody assumptions documentate;
- signer-set/contract/migration proof rollback tests;
- release/first-install freshness tests;
- Relay provenance/Sybil/eclipse tests;
- independent cryptographic/security review.
