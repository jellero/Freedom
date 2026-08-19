# Freedom — Control-Plane Security & State Verification

Status: **canonical / normative design rules**.

Normative baseline: [`SECURITY_INVARIANTS.md`](SECURITY_INVARIANTS.md).
Revocation/freshness: [`REVOCATION.md`](REVOCATION.md).
Pairwise recovery: [`PAIRWISE_RECOVERY.md`](PAIRWISE_RECOVERY.md).
Schema: [`../spec/freedom.cddl`](../spec/freedom.cddl).
Crypto domains: [`../spec/crypto-domains.txt`](../spec/crypto-domains.txt).

## 1. RPC non è trust

Security-sensitive state:

```text
NetworkAnchor
 -> finalized checkpoint
 -> state root
 -> inclusion/non-inclusion proof
 -> canonical object
```

RPC JSON da solo non è `VERIFIED_STATE`.

## 2. Verified checkpoint / state proof

Il `ChainAdapter` verifica finality/consensus proof, state-root binding, inclusion/non-inclusion proof, canonical encoding, cryptographic domain, epoch e policy.

Per NEAR la prima implementazione deve usare primitive coerenti col modello reale di finality/state, non fidarsi del solo RPC response.

## 3. Cache / anti-rollback

Persistire per namespace rilevante:

```text
highest_verified_height
highest_seen_object_epoch
freshness class
monotonic observation time
```

Un valid proof più vecchio del highest-seen/floor rilevante viene rifiutato.

## 4. Fresh-install bootstrap floor

Fresh install usa `BootstrapFreshnessFloor` incorporato nella release/verifier corrente.

State sotto minimum checkpoint/signer/policy floor viene rifiutato.

Limite esplicito: un verifier autentico ma esso stesso molto obsoleto ottenuto soltanto da canali attacker-controlled non può sapere dal nulla che esista stato più recente. La freshness del verifier richiede independent bootstrap assurance.

## 5. Verified time

Expiry/freshness usa verified checkpoint time/height/epoch + monotonic local time. Wall clock locale non è authority esclusiva.

## 6. Opaque device state V1

V1 non richiede public RootIdentity→device mapping.

```text
DeviceRecordCommitment
DeviceKey
DeviceControlPublicKey
key_epoch
status
```

Peer authorization deriva dal DeviceCertificate/delegation, non dal contratto che conosce l'account owner.

Record creation/spam viene limitato con fee/sponsorship/anti-abuse. Device-count hard enforcement privacy-preserving è evoluzione futura, non blocker core V1.

## 7. DeviceControlKey / recovery revocation

DeviceControlKey è scoped a rotate/revoke del singolo opaque record.

La canonical revocation object consente anche una `RECOVERY_OR_SUCCESSOR` authorization proof quando la device control key è persa/indisponibile, purché la proof sia prevista dalla current verified identity/recovery policy.

## 8. Revocation

Semantica completa: `REVOCATION.md`.

`RPC not found != non-revoked`.

Adapter-specific test vector devono definire device key floor, authorization epoch floor, root transition e non-inclusion semantics.

## 9. Rendezvous write authorization

Per direction/epoch, `PairRendezvousSecret` deriva off-chain un one-time write keypair.

Il contract-visible slot è:

```text
slot_id = H("Freedom/RendezvousSlot" || network_id || write_public_key)
```

Il fixed HASH/KDF purpose è registrato in `spec/crypto-domains.txt`; il contratto non deve conoscere direction/epoch per verificare il binding.

Write valida soltanto con:

```text
slot/public-key binding
valid write signature
generation monotonic
size/expiry bounds
```

Osservare slot/public key non concede overwrite authority.

## 10. Active state bounded

Temporary state usa overwrite/ring/prune/lease/reclaim. TTL da solo non basta.

Acceptance test: dopo N renewals/expiries, active state converge al theoretical bound e payer/refund/bounty behavior resta bounded.

La chain history archiviale può restare osservabile.

## 11. RootControlState

Gli utenti che usano control-plane root continuity/recovery hanno un opaque `root_control_commitment`.

Schema: `root-control-state`.

Contiene current root epoch/commitment, optional recovery policy commitment e optional pending compromise-recovery hash.

`root_control_commitment` non è network/contact ID, ma può correlare gli eventi della stessa recovery lineage sul control-plane. Questo trade-off è esplicito.

## 12. UserRecoveryPolicy

Compromise recovery richiede independent recovery authority precommitted prima dell'incidente.

Schema: `user-recovery-policy`.

Policy validation MUST reject:

```text
< 2 recovery key commitments
duplicate recovery key commitments
threshold == 0
threshold > number of distinct recovery keys
```

Per un profilo production che dichiara independent compromise recovery, threshold >=2 e custody domains separati dalla active RootRecoveryKey/device environment sono il target operativo.

V1 policy è **sticky**:

```text
NORMAL root rotation
 -> same root_control_commitment
 -> same recovery_policy_commitment
```

La current root da sola non può rimuovere/sostituire la policy.

Policy mutation non è una V1 operation finché non viene specificata una transition separata autorizzata almeno dal current recovery quorum.

## 13. Compromise recovery race

`COMPROMISE_RECOVERY` usa independent quorum proof e configured delay.

Una valid request può recuperare la **latest current root state della stessa root_control_commitment lineage**, anche se l'attaccante ha già fatto una normal root rotation con la root rubata.

Quando accepted:

```text
RECOVERY_PENDING
```

fino all'activation height:

- block normal root rotations;
- block recovery-policy mutation;
- high-risk new device authorization può essere bloccata/pending;
- current root non può cancellare unilateralmente la recovery;
- cancellation/replacement richiede la independent recovery authority prevista dalla policy.

Questo impedisce alla root rubata di evadere dalla recovery policy tramite normal rotation race.

## 14. PairwiseRecoveryAnchor

Il full `PairwiseRecoveryBundle` resta off-chain su storage scelto dall'utente/deployment.

Per il profilo rollback-detectable dopo perdita totale dei device, il control-plane conserva soltanto un piccolo `pairwise-recovery-anchor`:

```text
root_control_commitment
anchor_epoch
latest_backup_generation
latest_bundle_hash
latest_state_commitment
recovery_key_epoch
updated_at_height
authorization_proof
```

Update valida soltanto se:

```text
anchor_epoch == previous.anchor_epoch + 1
latest_backup_generation > previous.latest_backup_generation
root_control_commitment unchanged for lineage
recovery_key_epoch >= previous.recovery_key_epoch
authorization proof valid under current verified recovery-state authority
```

L'anchor update è una mutazione security-sensitive e richiede finality + resulting-state proof.

La source che conserva i bundle non è trust e non può ridefinire l'anchor.

Trade-off: l'anchor non pubblica social graph/plaintext, ma rende osservabile il timing degli update della stessa opaque recovery lineage.

Senza anchor/surviving device, bundle integrity può essere verificata ma la freshness non è dimostrabile. Vedi `PAIRWISE_RECOVERY.md`.

## 15. Contract governance

Production sceglie immutable security core oppure threshold-governed upgrade.

Upgradeable core richiede code hash, migration program hash, activation height, timelock, accepted lineage e rollback floor.

Una singola Full Access key production viola il modello.

## 16. Governance quorum assumption

`3-of-5` elimina una singola key unilaterale, non quorum collusion.

Signer production devono usare per quanto praticabile custody/operator domains differenti, hardware/offline separation, no single secret-manager/account containing a quorum, public transition records e periodic custody audit.

Se un singolo soggetto controlla unilateralmente il quorum, il claim corretto è “nessuna singola chiave”, non “nessun singolo attore”.

## 17. Signer-set anti-rollback

```text
next_epoch = previous_epoch + 1
previous threshold authorizes
next threshold accepts
activation height monotonic
highest-seen persisted
old set cannot reactivate
```

Quorum-loss recovery è pre-pinned, stronger-threshold/timelocked, non una emergency key singola.

## 18. Chain migration

`ChainMigrationManifest` da solo non basta.

Serve `StateMigrationProof` che lega source finalized checkpoint/export root, migration program hash/input commitment e target imported root.

Governance autorizza la migration rule/version; non può firmare arbitrariamente uno state rewrite e chiamarlo migration.

## 19. Acceptance gates

- fresh-install stale checkpoint/floor;
- honest/stale/forked/malicious RPC proof vectors;
- revocation inclusion/non-inclusion/freshness;
- rendezvous overwrite/front-run/replay;
- storage convergence;
- DeviceControlKey + recovery revocation;
- UserRecoveryPolicy duplicate/threshold/custody validation;
- sticky UserRecoveryPolicy;
- compromise-recovery latest-lineage/race/timelock;
- pairwise backup anchor monotonic update + rollback/mismatch;
- signer custody/quorum assumptions;
- contract upgrade/rollback;
- deterministic StateMigrationProof.
