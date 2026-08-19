# Freedom — Identity Model

Status: **canonical design draft**.

Normative security rules: [`SECURITY_INVARIANTS.md`](SECURITY_INVARIANTS.md).
Control-plane: [`CONTROL_PLANE_SECURITY.md`](CONTROL_PLANE_SECURITY.md).
Revocation: [`REVOCATION.md`](REVOCATION.md).
Schema: [`../spec/freedom.cddl`](../spec/freedom.cddl).

## 1. Obiettivo

Freedom autentica persone e device, supporta rotation/revocation/recovery e separa identity, control-plane e routing senza richiedere un global `DeviceID` network-facing.

```text
RootRecoveryKey                 -> cold recovery / continuity
UserRecoveryPolicy              -> independent compromise-recovery authority
RootIdentity                    -> ownership identity / root epoch
DeviceAuthorizationKey          -> delegated authorization epoch
DeviceCertificate               -> offline DeviceKey authorization
DeviceKey                       -> operational device authentication
DeviceRecordCommitment          -> opaque control-plane handle
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
   |
   +-> UserRecoveryPolicy commitment
   |
   `-> DeviceAuthorizationDelegation
              |
              `-> DeviceCertificate
                        |
                        `-> DeviceKey
```

`RootRecoveryKey` non è una daily operational key.

## 3. RootIdentity

```text
RootIdentity {
    version
    root_public_key
    root_commitment
    root_epoch
    recovery_policy_commitment?
}
```

RootIdentity rappresenta continuity/ownership, non routing, message key, payment reference o global network identifier.

## 4. DeviceAuthorizationDelegation

Lo schema canonico è in `spec/freedom.cddl`.

La delegation definisce scope, epoch e expiry della authorization key. Un `DeviceCertificate`:

```text
capabilities subset-of delegation.capabilities
certificate expiry <= delegation expiry
same root_epoch
same authorization_epoch
```

Non è ammessa privilege escalation tramite certificato figlio.

## 5. Device record V1 — no public RootIdentity mapping required

Freedom V1 non richiede che il contratto provi pubblicamente quale RootIdentity possiede ogni device record.

```text
DeviceRecord {
    device_record_commitment
    device_public_key
    device_control_public_key
    key_epoch
    status
    protocol_version
}
```

Il record è utile a revocation/rotation/freshness. La **peer authentication** deriva dal DeviceCertificate verificato contro il contatto atteso.

Quindi un record arbitrario creato da un attacker non diventa automaticamente un device di Alice: manca la chain `RootIdentity -> delegation -> certificate -> DeviceKey possession` attesa dal peer.

Storage spam viene trattato con sponsorship/fee/anti-abuse, non pubblicando il social/device graph.

## 6. DeviceControlKey

Ogni record ha una control key scoped, distinta da DeviceKey.

Serve soltanto a operazioni come:

```text
rotate key epoch
revoke device record
narrow record update
```

La private DeviceControlKey deve essere protetta nell'authorization/recovery state e non usata per handshake/chat.

## 7. DeviceCertificate

Schema canonico: `device-certificate`.

Il peer verifica offline:

- deterministic encoding/signing domain;
- root/contact identity attesa;
- delegation chain;
- capability subset;
- expiry/epoch constraints;
- `device_record_commitment` binding;
- DeviceKey possession;
- revocation/freshness secondo `REVOCATION.md`.

Una RPC non è necessaria nel packet hot path di ogni frame.

## 8. Revocation

Tre superfici distinte:

```text
DeviceKey/device record revocation
authorization-epoch revocation
root epoch transition
```

Una risposta RPC `not found` non è una prova di non-revoca. Il peer usa proof/cache verificati e highest-seen epochs.

## 9. Device-count product policy

V1 non rende `max_devices` una security/interoperability primitive del protocollo.

Il client/servizio ufficiale può applicare la quota commerciale; un futuro hard enforcement privacy-preserving può usare credential/nullifier/ZK dopo review.

Questo evita di rendere una costruzione ZK non ancora scelta un blocker del core identity protocol.

## 10. UserRecoveryPolicy

Recovery da perdita e recovery da compromissione sono diverse.

Per rivendicare `ROOT_COMPROMISE` recovery deve esistere **prima dell'incidente** una authority indipendente dalla singola RootRecoveryKey.

Schema canonico: `user-recovery-policy`.

Esempio:

```text
recovery key/share commitments
threshold
recovery delay blocks
policy commitment
```

Le recovery keys/shares devono stare in fault/custody domains distinti per quanto praticabile.

## 11. UserRootRotation

Schema canonico: `user-root-rotation`.

```text
NORMAL
 -> old-root continuity proof

COMPROMISE_RECOVERY
 -> independent recovery quorum proof
 -> recovery delay
 -> new root epoch
```

Se l'utente ha una sola root secret e nessuna recovery authority indipendente, proprietario e ladro che conoscono la stessa secret non sono distinguibili crittograficamente. In quel profilo Freedom non promette root-compromise recovery.

## 12. Contact = persona / RootIdentity

La rubrica rappresenta una persona, non un singolo device.

```text
Bob
  |- Phone DeviceCertificate
  |- Tablet DeviceCertificate
  `- Desktop DeviceCertificate
```

Bootstrap descriptor: schema `freedom-contact` in CDDL.

## 13. First-contact substitution

Un attacker può sostituire l'intero descriptor prima del primo bootstrap e far autenticare perfettamente la propria identity.

Il client distingue:

```text
BOOTSTRAP_UNVERIFIED
CONTACT_VERIFIED
```

Safety code/fingerprint/out-of-band verification è disponibile per assurance umana più forte.

## 14. Pairwise identity

Dopo handshake autenticato:

```text
PairSecret_AB
PairwiseContactAlias_AB
PairRendezvousSecret_AB
```

Relazioni differenti producono alias differenti.

## 15. Claim boundary contro contatti colludenti

Pairwise alias/rendezvous riducono correlazione infrastrutturale. Se Bob e Carol vedono materiale root/certificate confrontabile, possono correlare Alice.

Non promettere colluding-contact unlinkability senza credenziali pairwise-scoped/anonymous specificamente implementate.

## 16. Rendezvous write authority

Ogni direction/epoch deriva dal `PairRendezvousSecret` un one-time `RendezvousWriteKeypair`.

```text
write_public_key
 -> slot_id = H(domain || write_public_key || epoch || direction)
```

Il record pubblico include firma/generation; osservare lo slot non concede overwrite authority.

## 17. Pairwise recovery lifecycle

`PairSecret` e `PairRendezvousSecret` non sono on-chain.

Recovery:

```text
A. surviving authorized device -> authenticated transfer
B. encrypted PairwiseRecoveryBundle -> user-chosen backup media/source
```

Schema canonico: `pairwise-recovery-bundle`.

Il bundle contiene solo ciphertext per metadata/state pairwise, con `state_epoch` e `recovery_key_epoch`.

Backup location/discovery è una scelta dell'utente/deployment:

- exported encrypted file/QR bundle where size permits;
- private cloud/file storage chosen by user;
- organization-managed backup;
- multiple redundant untrusted byte stores, purché il ciphertext sia verificato.

La source del backup non è trust.

Dopo restore da un bundle:

```text
restore state
 -> reject rollback below highest-known state if available
 -> re-authenticate peer
 -> rotate/re-derive future rendezvous/session state
 -> mark recovered relationship current
```

Una vecchia copia del backup non deve fissare indefinitamente future rendezvous secrets.

Se manca surviving device e manca backup valido, ownership torna ma i contatti richiedono re-bootstrap.

## 18. Handshake

Il transcript lega entrambi gli offer set, expected relationship, DeviceCertificate/delegation proofs, epochs, ephemeral material, selected suite/version/transport semantics, nonces e session ID.

Offer stripping sotto policy fallisce.

## 19. Forward secrecy

Static root/device keys autenticano; non ricostruiscono sessioni concluse.

Sessioni lunghe usano bounded traffic-key lifetime e la rekey state machine canonica di `PROTOCOL.md`.

## 20. Privacy invariants

- no global DeviceID network-facing;
- RootIdentity non è route ID;
- DeviceRecordCommitment non è contact ID;
- V1 non richiede public account→device proof;
- DeviceControlKey non è DeviceKey;
- pairwise aliases/rendezvous per relazione;
- no social graph on-chain;
- recovery backup resta ciphertext;
- root-compromise recovery richiede independent precommitment;
- colluding-contact unlinkability non viene promessa senza primitive dedicate.
