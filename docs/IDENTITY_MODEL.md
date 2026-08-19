# Freedom — Identity Model

Status: **canonical design draft**.

Normative security: [`SECURITY_INVARIANTS.md`](SECURITY_INVARIANTS.md).
Control-plane: [`CONTROL_PLANE_SECURITY.md`](CONTROL_PLANE_SECURITY.md).
Revocation: [`REVOCATION.md`](REVOCATION.md).
Schema: [`../spec/freedom.cddl`](../spec/freedom.cddl).

## 1. Obiettivo

Freedom autentica persone e device, supporta rotation/revocation/recovery e separa identity, control-plane e routing senza richiedere un global `DeviceID` network-facing.

```text
RootRecoveryKey                 -> cold recovery / continuity
UserRecoveryPolicy              -> independent compromise-recovery authority
RootIdentity                    -> ownership identity / root epoch
RootControlCommitment           -> opaque control-plane continuity handle
DeviceAuthorizationKey          -> delegated authorization epoch
DeviceCertificate               -> offline DeviceKey authorization
DeviceKey                       -> operational device authentication
DeviceRecordCommitment          -> opaque device-state handle
DeviceControlKey                -> scoped control of one device record
PairwiseContactAlias            -> relationship alias
PairRendezvousSecret            -> relationship rendezvous authority
TransportToken                  -> temporary route/circuit token
Session keys                    -> ephemeral E2EE
```

## 2. Root hierarchy

```text
RootRecoveryKey
   |
   +-> RootIdentity(root_epoch)
   +-> RootControlCommitment / optional UserRecoveryPolicy
   `-> DeviceAuthorizationDelegation
              |
              `-> DeviceCertificate
                        |
                        `-> DeviceKey
```

`RootRecoveryKey` non è una daily operational key.

## 3. Root control state

Gli utenti che registrano continuity/recovery control-plane usano un handle opaco `root_control_commitment`, non un routing/contact ID.

Schema canonico: `root-control-state`.

```text
root_control_commitment
current root_epoch / root commitment
recovery_policy_commitment?
pending_compromise_recovery_hash?
```

Trade-off: un handle stabile di recovery può rendere correlabili **gli eventi control-plane della stessa recovery lineage**. Non viene inviato come network identity e non rivela automaticamente social graph/IP/route, ma questa correlazione temporale non viene negata.

## 4. DeviceAuthorizationDelegation

Schema canonico: `device-authorization-delegation`.

Un child certificate non amplifica il parent:

```text
certificate.capabilities subset-of delegation.capabilities
certificate.expires_after_height <= delegation.expires_after_height
certificate.root_epoch == delegation.root_epoch
certificate.authorization_epoch == delegation.authorization_epoch
```

## 5. Opaque DeviceRecord V1

V1 non richiede che il contratto conosca quale RootIdentity possiede ogni device record.

Schema canonico: `device-record`.

```text
DeviceRecordCommitment
DeviceKey
DeviceControlPublicKey
key_epoch
status
```

La peer authentication deriva da `RootIdentity -> DeviceAuthorizationDelegation -> DeviceCertificate -> DeviceKey possession`, non dalla presenza del record da sola.

## 6. DeviceControlKey

Scoped a rotation/revocation del singolo opaque record. Non firma sessioni/chat e non autorizza altri device.

La revoca può essere autorizzata dalla DeviceControlKey oppure da una recovery/successor proof prevista dal modello canonico quando la control key non è disponibile.

## 7. DeviceCertificate

Schema canonico: `device-certificate`.

Il peer verifica:

- canonical encoding + signing domain;
- expected contact/root proof;
- delegation chain e scope;
- DeviceRecordCommitment binding;
- DeviceKey possession;
- root/authorization/key epochs;
- current-enough revocation state.

## 8. Revocation

Tre superfici:

```text
DeviceKey / device record
authorization epoch
root epoch / lineage transition
```

`RPC not found` non è una non-revocation proof. Vedi `REVOCATION.md`.

## 9. Device-count policy V1

`max_devices` è product/service policy, non security/interoperability primitive. Future hard enforcement privacy-preserving può usare credential/nullifier/ZK dopo review.

## 10. UserRecoveryPolicy

Per recovery da root compromise deve esistere prima dell'incidente una independent recovery authority.

Schema canonico: `user-recovery-policy`.

```text
root_control_commitment
recovery key/share commitments
threshold
recovery delay
policy commitment
```

Se esiste una sola RootRecoveryKey e nessuna authority indipendente, proprietario e attacker con la stessa secret sono indistinguibili. Freedom non promette compromise recovery in quel profilo.

## 11. Sticky recovery policy V1

La recovery policy è **sticky** attraverso le normali root rotation:

```text
NORMAL root rotation
 -> same root_control_commitment
 -> same recovery_policy_commitment
```

La RootRecoveryKey corrente da sola non può rimuovere/sostituire la recovery policy.

V1 non supporta mutation arbitraria della recovery policy. Una futura policy transition richiede un oggetto/state machine separato e almeno l'autorità del recovery quorum corrente.

## 12. UserRootRotation / compromise race

Schema canonico: `user-root-rotation`.

```text
NORMAL
 -> old/current-root continuity proof
 -> inherits current recovery policy

COMPROMISE_RECOVERY
 -> independent recovery quorum proof
 -> configured delay
 -> new root epoch
```

Una compromise-recovery request lega il `root_control_commitment`, il current root state osservato e la recovery policy precommitted.

Quando una valid recovery request è pending:

```text
RECOVERY_PENDING
```

fino alla activation height:

- normal root rotations sono bloccate;
- recovery-policy mutation è bloccata;
- high-risk new device authorization può essere bloccata/pending secondo policy;
- la current/compromised root non può cancellare da sola la recovery request;
- cancellazione/sostituzione richiede l'independent recovery authority prevista dalla policy.

Se l'attaccante ha fatto una normal rotation **prima** dell'apertura della recovery request, il recovery quorum può comunque recuperare la stessa `root_control_commitment` lineage contro il latest current root state verificato. La root rubata non può “scappare” dalla recovery policy ruotando normalmente.

## 13. Contact = persona / RootIdentity

Un contatto rappresenta una persona/continuity relationship, non un singolo device.

Bootstrap object: `freedom-contact`.

## 14. First-contact substitution

Un descriptor sostituito prima del bootstrap può autenticare perfettamente l'attaccante. UI:

```text
BOOTSTRAP_UNVERIFIED
CONTACT_VERIFIED
```

Safety code/fingerprint/out-of-band verification fornisce assurance indipendente.

## 15. Pairwise identity

Dopo handshake autenticato:

```text
PairSecret
PairwiseContactAlias
PairRendezvousSecret
```

Different relationships -> different aliases/rendezvous state.

Questo non promette unlinkability contro contatti colludenti che confrontano root/certificate material.

## 16. Rendezvous write authority

`PairRendezvousSecret` deriva off-chain un fresh write keypair per direction/epoch.

Il public slot è:

```text
slot_id = H("Freedom/RendezvousSlot" || network_id || write_public_key)
```

Direction/epoch restano nella derivazione segreta della write key e non devono essere esposti al contratto per verificare il slot binding.

Record firmato + generation monotonic impediscono overwrite a chi osserva soltanto lo slot/public key.

## 17. Pairwise recovery lifecycle

Recovery tramite surviving-device transfer o encrypted `PairwiseRecoveryBundle` su source scelta dall'utente/deployment.

La source è non fidata; il bundle resta ciphertext.

Dopo restore:

```text
reject detectable rollback
 -> re-authenticate peer
 -> rotate/re-derive future rendezvous state
 -> establish fresh session state
```

Se manca device sopravvissuto e backup valido, ownership torna ma i contatti richiedono re-bootstrap.

## 18. Handshake / session

Il transcript lega both offer sets, expected relationship, certificate/delegation proof, epochs, selected suite/version/transport semantics, ephemeral material, nonces e session ID.

Forward secrecy e complete rekey state machine sono obbligatorie secondo `PROTOCOL.md`.

## 19. Privacy invariants

- no global DeviceID network-facing;
- RootIdentity/RootControlCommitment non sono routing IDs;
- RootControlCommitment può correlare eventi della recovery lineage e questo trade-off è esplicito;
- DeviceRecordCommitment non è contact ID;
- V1 non richiede public RootIdentity→device mapping;
- DeviceControlKey != DeviceKey;
- pairwise alias/rendezvous relationship-scoped;
- social graph non è on-chain;
- root-compromise recovery richiede independent precommitment;
- current root alone cannot remove sticky recovery policy;
- colluding-contact unlinkability non viene promessa senza primitive dedicate.
