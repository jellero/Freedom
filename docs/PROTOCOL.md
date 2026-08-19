# Freedom — Protocol Specification

Status: **canonical design draft**.

Normative security rules: [`SECURITY_INVARIANTS.md`](SECURITY_INVARIANTS.md).
Identity: [`IDENTITY_MODEL.md`](IDENTITY_MODEL.md).
Control-plane: [`CONTROL_PLANE_SECURITY.md`](CONTROL_PLANE_SECURITY.md).
Revocation: [`REVOCATION.md`](REVOCATION.md).
Shield: [`SHIELD.md`](SHIELD.md).
Canonical object schema: [`../spec/freedom.cddl`](../spec/freedom.cddl).

I Markdown descrivono semantica/state machine. I field name e shape degli oggetti firmati/parsi vengono da `spec/freedom.cddl`.

## 1. Principi normativi

- deterministic canonical encoding prima dell'interoperabilità pubblica;
- domain-separated signatures/hash per network/object/version;
- no global DeviceID network-facing;
- no message/media/APK on-chain;
- no mailbox/offline delivery queue;
- RootRecoveryKey, authorization, DeviceKey, record control, pairwise identity, routing e traffic keys separati;
- RPC non è trust;
- `tx hash != success`;
- revocation/non-revocation richiede proof semantics esplicite;
- forward secrecy + bounded rekey;
- both-offer-set anti-downgrade;
- transport semantics dichiarate;
- critical production governance non `1-of-1`.

## 2. Identity hierarchy

```text
RootRecoveryKey
 -> RootIdentity(root_epoch)
 -> DeviceAuthorizationDelegation(authorization_epoch)
 -> DeviceCertificate
 -> DeviceKey
```

Una independent `UserRecoveryPolicy` è necessaria per recovery da root compromise, non per il normale device loss.

## 3. Device record / control

V1 record:

```text
DeviceRecordCommitment
DeviceKey
DeviceControlPublicKey
key_epoch
status
```

Il control-plane non deve conoscere obbligatoriamente la RootIdentity proprietaria.

Peer validity deriva da certificate/delegation/DeviceKey proof. `DeviceControlKey` controlla soltanto rotation/revocation del record opaco.

## 4. Device quota

`max_devices` V1 è product/service policy, non interoperability/security invariant.

Un futuro hard enforcement privacy-preserving richiede credential/nullifier/ZK specificamente reviewati.

## 5. DeviceCertificate validation

Schema canonico: `device-certificate`.

Validation order:

```text
canonical parse
 -> signing domain
 -> expected RootIdentity/contact proof
 -> delegation signature/scope
 -> child capabilities subset
 -> child expiry <= parent expiry
 -> device_record_commitment binding
 -> DeviceKey possession
 -> highest-seen epoch checks
 -> current-enough revocation proof
 -> AUTHENTICATED DEVICE
```

## 6. Revocation

Oggetti canonici:

```text
device-revocation-record
authorization-revocation-record
user-root-rotation
```

Semantica completa: [`REVOCATION.md`](REVOCATION.md).

`RPC null/not-found` non è non-revocation proof.

## 7. Contact descriptor / assurance

Schema: `freedom-contact`.

Trust state:

```text
BOOTSTRAP_UNVERIFIED
CONTACT_VERIFIED
```

Safety code/fingerprint/out-of-band verification consente assurance umana indipendente.

## 8. Pairwise identity

```text
PairSecret
PairwiseContactAlias
PairRendezvousSecret
```

Alias/rendezvous sono relationship-scoped.

## 9. Rendezvous slot authorization

Ogni direction/epoch deriva un one-time write keypair:

```text
PairRendezvousSecret
 -> RendezvousWriteKeypair(direction, epoch)
 -> write_public_key
 -> slot_id = H(domain || write_public_key || epoch || direction)
```

Schema: `rendezvous-record`.

Write acceptance:

```text
slot derivation valid
write signature valid
generation monotonic
size/expiry valid
```

L'osservatore dello slot non ottiene overwrite authority.

## 10. RecoveryBeacon

Schema: `recovery-beacon`.

Stesse write-auth rules del RendezvousRecord. Pairwise, bounded, cifrato, short-lived, non presence globale.

## 11. Read-before-write

```text
remote = read(remote_slot)
if valid/usable -> try route, do not write
else:
  local = read(local_slot)
  if valid -> wait/poll
  else -> signed bounded write to own slot
```

## 12. Pairwise recovery

Schema: `pairwise-recovery-bundle`.

```text
surviving authorized device transfer
or
encrypted backup bytes from untrusted storage
```

Dopo restore:

```text
re-authenticate peer
 -> rotate/re-derive future rendezvous state
 -> establish fresh session keys
```

Old backup state non diventa permanent future authority.

## 13. Route / relay descriptors

Schema canonico include `route-candidate`, `relay-descriptor`, `relay-candidate`, `provenance-attestation`.

Self-declared metadata non è independence proof.

## 14. Transport semantic contract

```text
RELIABLE_ORDERED_STREAM
UNRELIABLE_DATAGRAM
```

Handshake/control/text/rekey richiedono reliable ordered semantics oppure reliability layer esplicito. Media può usare datagram e sequence space separato.

## 15. HandshakeOffer

Schema: `handshake-offer`.

Il transcript autentica almeno:

```text
network_id
expected pairwise relationship
local_offer_hash
remote_offer_hash
certificate/delegation hashes or proofs
root/authorization/key epochs
selected_version
selected_suite
selected_transport_semantics
ephemeral material
nonces
session_id
```

La scelta è deterministic/strongest-allowed secondo policy locale. Offer stripping sotto policy -> `NEGOTIATION_DOWNGRADE`.

## 16. Session establishment

```text
parse offers
 -> authenticate contact/certificate/delegation
 -> verify revocation freshness
 -> verify both offer sets
 -> fresh ephemeral exchange
 -> derive traffic schedule
 -> key confirmation
 -> E2EE ACTIVE
```

Relay/path non è authentication authority.

## 17. Forward secrecy

Ogni nuova sessione usa fresh ephemeral exchange. Future compromise di static DeviceKey/Root key non ricostruisce sessioni concluse, salvo endpoint/session-state compromise.

## 18. Rekey state machine

Oggetti canonici:

```text
rekey-init
rekey-commit
rekey-ack
```

States:

```text
STABLE(epoch N)
INIT_SENT / INIT_RECEIVED
COMMIT_ESTABLISHED
NEW_KEY_PENDING_ACK
STABLE(epoch N+1)
```

### Initiation

```text
RekeyInit.current_epoch == N
RekeyInit.next_epoch    == N+1
```

Include fresh ephemeral material e transcript hash.

### Simultaneous init

Se entrambi iniziano `N -> N+1`, il winner viene scelto deterministicamente usando una ordering derivata da `session_id` + fixed endpoint role/offer ordering. L'altro Init viene trattato come duplicate competing proposal, non crea un secondo epoch.

### Commit

Responder produce `RekeyCommit` legato all'Init hash e aggiunge fresh responder ephemeral material.

Entrambi possono derivare la next key schedule ma continuano a conservare le old keys finché non arriva conferma.

### Ack / key confirmation

Initiator invia `RekeyAck` autenticato nel nuovo key schedule con `key_confirmation`.

Dopo Ack verificato:

```text
send epoch -> N+1 only
old send key -> erase
old receive key -> bounded in-flight grace only
```

### Failure/replay

- duplicate Init/Commit/Ack: idempotent reject/ignore by object hash/state;
- wrong next epoch: reject;
- transcript mismatch: terminate;
- Ack timeout: terminate before traffic-key lifetime limit;
- route switch: does not reset rekey state/epoch;
- reconnect/new session: new session schedule, not implicit continuation of old rekey.

No silent split-brain key epochs.

## 19. Control/media frame spaces

Control/text/rekey e media usano sequence/replay windows indipendenti. Media loss non blocca control stream.

Exact frame schema può essere aggiunto in CDDL quando encoding viene congelato.

## 20. Synchronous application semantics

```text
active authenticated session? yes -> transmit now
active authenticated session? no  -> FAIL/DISCARD
```

No `StoreRequest` base.

## 21. RelayPacket / Shield

Relay inoltra ciphertext bounded. Shield forte richiede il vero circuit protocol di `SHIELD.md`, non proxy concatenati.

## 22. Verified control-plane checkpoint

Schema: `verified-control-plane-checkpoint`.

Security state da RPC senza checkpoint/proof non è `VERIFIED_STATE`.

## 23. BootstrapFreshnessFloor

Schema: `bootstrap-freshness-floor`.

Fresh install rifiuta state sotto il floor incorporato nella release/verifier corrente.

Non promettere protezione contro verifier autentico ma esso stesso molto obsoleto ottenuto solo da canali attacker-controlled.

## 24. Verified mutation

```text
submit
 -> finality proof
 -> execution success
 -> resulting state proof
 -> exact expected transition
 -> local commit
```

## 25. Release / SecurityPolicy

Object shapes sono soltanto in `spec/freedom.cddl`.

Markdown non deve mantenerne copie divergenti come source of truth.

## 26. Contract / signer governance

Signer transitions sono cross-authorized e monotonic. Contract upgrade è immutable-core oppure threshold/timelocked/code-hash pinned.

`3-of-5` elimina una singola key unilaterale, non il rischio di quorum collusion. Custody/operator independence è trust assumption documentata.

## 27. Chain migration

Una migration richiede:

```text
ChainMigrationManifest
+ StateMigrationProof
```

`StateMigrationProof` lega source checkpoint/export root, migration program hash e target imported state root.

Una threshold signature non autorizza da sola uno state rewrite arbitrario.

## 28. Payment / product quotas

Payment→entitlement può usare one-time voucher/nullifier per ridurre linkage.

Contact slots e device-count V1 sono product/service policy; non cambiano peer session acceptance.

## 29. Error classes

```text
MALFORMED
UNSUPPORTED_VERSION
NEGOTIATION_DOWNGRADE
DEVICE_CERTIFICATE_INVALID
DEVICE_CERTIFICATE_EXPIRED
REVOCATION_STATE_STALE
REVOCATION_PROOF_INVALID
CONTROL_PLANE_PROOF_INVALID
CONTROL_PLANE_ROLLBACK
CONTROL_PLANE_EXECUTION_FAILED
CONTROL_PLANE_STATE_MISMATCH
KEY_EPOCH_MISMATCH
AUTHENTICATION_FAILED
REPLAY_DETECTED
RENDEZVOUS_WRITE_UNAUTHORIZED
RENDEZVOUS_GENERATION_ROLLBACK
ROUTE_UNAVAILABLE
PEER_OFFLINE
SESSION_REKEY_REQUIRED
SESSION_REKEY_FAILED
ENTITLEMENT_INVALID
PAYMENT_PENDING
SECURITY_UPDATE_REQUIRED
GOVERNANCE_TRANSITION_INVALID
BOOTSTRAP_STATE_TOO_OLD
```

## 30. Interoperability gates

Prima dell'interoperabilità pubblica:

- deterministic-CBOR/signing-domain vectors;
- delegation capability/expiry negative tests;
- revocation/non-revocation/freshness vectors;
- fresh-install stale checkpoint tests;
- rendezvous overwrite/front-run/replay tests;
- handshake offer-stripping tests;
- rekey simultaneous/init-loss/commit-loss/ack-loss/duplicate/route-switch tests;
- stream/datagram semantics;
- control-plane proof/finality tests;
- storage convergence;
- pairwise backup rollback/post-restore rotation;
- root compromise recovery race/timelock;
- relay provenance/Sybil tests;
- signer/contract/migration rollback tests;
- independent security review.
