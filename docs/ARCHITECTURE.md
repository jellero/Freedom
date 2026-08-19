# Freedom — Architecture

Status: **canonical design draft**

Normative security rules: [`SECURITY_INVARIANTS.md`](SECURITY_INVARIANTS.md).
Control-plane security: [`CONTROL_PLANE_SECURITY.md`](CONTROL_PLANE_SECURITY.md).
Shield: [`SHIELD.md`](SHIELD.md).

## 1. Definizione

Freedom separa esplicitamente:

1. **user-root recovery** — RootRecoveryKey / RootIdentity epoch;
2. **device authorization** — delegated DeviceAuthorizationKey + DeviceCertificate;
3. **verifiable control-plane** — checkpoint/state proof, revocation/rotation, entitlement, recovery, policy/release;
4. **pairwise identity/rendezvous** — relazione specifica tra due contatti;
5. **routing/transport fabric** — direct, NAT, relay, bridge, Shield, pluggable transport;
6. **Freedom Communication** — sessione E2EE live;
7. **Freedom Gateway** — percorso opzionale verso explicit Internet egress;
8. **verified distribution** — source bytes non fidata, release verificata.

NEAR è la prima implementazione `ChainAdapter`; chat, media, Gateway payload e APK restano off-chain.

## 2. Vista d'insieme

```text
                DISTRIBUTED / VERIFIABLE CONTROL PLANE
      +------------------------------------------------------+
      | finalized checkpoint / state proofs                  |
      | device revocation / rotation                         |
      | pairwise rendezvous / recovery                       |
      | entitlement / sponsorship / payment redemption       |
      | security policy / release / signer / contract state  |
      +--------------------------+---------------------------+
                                 |
                         only when needed
                                 |

+---------------------------+                 +---------------------------+
| Alice                     |                 | Bob                       |
| RootIdentity              |                 | RootIdentity              |
| DeviceAuthorization proof |                 | DeviceAuthorization proof |
| DeviceCertificate         |                 | DeviceCertificate         |
| DeviceKey                 |                 | DeviceKey                 |
| PairwiseContactAlias_AB   |                 | PairwiseContactAlias_BA   |
+-------------+-------------+                 +-------------+-------------+
              |                                             |
              +--------- authenticated relationship --------+
                                  |
                        path / transport selector
                                  |
              +-------------------+-------------------+
              |                                       |
              v                                       v
   FREEDOM COMMUNICATION                      FREEDOM GATEWAY
   authenticated E2EE live                   external app traffic
              |                                       |
 text / file / voice / video                 relay/bridge/Shield
                                                      |
                                              explicit Internet Egress
```

## 3. Trust model

```text
RootRecoveryKey                -> cold user recovery
RootIdentity                   -> ownership/root epoch
DeviceAuthorizationKey         -> delegated authorization epoch
DeviceCertificate              -> offline device authorization proof
DeviceKey                      -> device authentication
DeviceRecordCommitment         -> opaque control-plane handle
PairwiseContactAlias           -> relationship identity
TransportToken                 -> temporary path identity
Session keys                   -> E2EE traffic keys
EntitlementCommitment          -> commercial state
PaymentBindingCommitment       -> payment state
VerifiedControlPlaneCheckpoint -> verified state root/finality
```

Nessun livello viene riutilizzato automaticamente come un altro.

## 4. Communication security boundary

![Freedom Communication architecture](assets/freedom-communication.svg)

```text
Freedom endpoint A
      <==== authenticated E2EE ====>
Freedom endpoint B
```

Session keys restano agli endpoint. Relay/bridge/RPC/provider/path selector non autenticano il peer e non possiedono conversation keys.

## 5. Device authorization senza RPC nel packet hot path

```text
RootRecoveryKey
 -> DeviceAuthorizationDelegation
 -> DeviceCertificate
 -> DeviceKey possession
```

Il peer verifica delegation/certificate offline e applica revocation/freshness da cache/control-plane **crittograficamente verificato**.

Nessuna singola RPC diventa trust anchor.

## 6. Control-plane authenticity

Security-sensitive state:

```text
NetworkAnchor
 -> VerifiedControlPlaneCheckpoint
 -> state root
 -> inclusion/non-inclusion proof
 -> canonical object
```

Multi-RPC senza proof verification non basta per dichiarare stato production `VERIFIED`.

## 7. Device/account privacy

No global DeviceID.

Production target per device activation/multi-device usa anonymous authorization proof/slot nullifier per evitare una lista pubblica leggibile RootIdentity→devices.

Se Testnet resta linkabile, tale limite viene dichiarato esplicitamente.

## 8. Pairwise identity

```text
PairSecret_AB
PairwiseContactAlias_AB
PairRendezvousSecret_AB
```

Relazioni differenti producono alias differenti.

Questo riduce infrastructure correlation; non implica automaticamente unlinkability contro contatti colludenti che confrontano root/certificate material.

## 9. First contact

Prima del primo bootstrap un descriptor può essere sostituito.

Il client distingue:

```text
BOOTSTRAP_UNVERIFIED
CONTACT_VERIFIED
```

Safety code/fingerprint/out-of-band verification è disponibile quando serve assurance umana più forte.

## 10. Pairwise recovery

Pairwise state non è on-chain.

Recovery avviene tramite:

```text
surviving authorized device transfer
or
encrypted PairwiseRecoveryBundle
```

Se entrambi mancano, ownership torna ma i contatti richiedono re-bootstrap.

## 11. Route / transport fabric

```text
DIRECT
NAT_TRAVERSAL
RELAY
BRIDGE
SHIELDED
MULTI_HOP
PLUGGABLE / OBFUSCATED TRANSPORT
```

Ogni adapter dichiara semantica `RELIABLE_ORDERED_STREAM` e/o `UNRELIABLE_DATAGRAM`.

Text/control/rekey non dipendono dalla perdita di media datagram attraverso un sequence space unico.

## 12. Relay architecture

Relay può essere VPS/server/mini-PC/community/managed/private/device opt-in.

Invarianti:

- forward not store;
- no mailbox;
- resource bounds;
- no session keys;
- no implicit Internet egress;
- RelayDescriptor firmato;
- diversity non derivata solo da metadata self-declared;
- `N relay IDs != N independent operators`.

> **Qualsiasi macchina compatibile può inoltrare Freedom; nessuna macchina deve diventare Freedom.**

## 13. Shield

Freedom Shield forte richiede vero circuit protocol:

```text
Alice -> Hop A -> Hop B -> Bob
```

con per-hop keys, layered forwarding, provenance-aware path selection e no silent direct fallback.

Due proxy concatenati non bastano. Dettagli: [`SHIELD.md`](SHIELD.md).

## 14. Forward secrecy / anti-downgrade

Freedom Communication richiede:

```text
fresh ephemeral exchange
+ both-offer-set transcript binding
+ strongest-allowed negotiation
+ forward secrecy
+ bounded traffic-key lifetime
+ authenticated rekey
+ separate media/control keys
```

## 15. Synchronous semantics

```text
active authenticated session? yes -> transmit now
active authenticated session? no  -> fail/discard now
```

No mailbox/offline delivery queue/store-and-forward automatico.

## 16. Verified finality

```text
submit
 -> finality proof
 -> execution success
 -> resulting state proof
 -> expected transition
 -> local success
```

Transaction hash != success.

## 17. Active storage bounded

TTL da solo non basta. Temporary control-plane state usa overwrite/ring/prune/lease/reclaim concreto e converge a un bound di stato attivo.

La storia archiviale della chain resta osservabile.

## 18. Verified time

Expiry/freshness usa `VerifiedTimeAnchor`, height/epoch e monotonic local time. Wall clock locale non può riattivare stato vecchio.

## 19. User root compromise

`LOST_DEVICE` e `ROOT_COMPROMISE` sono distinti.

Root compromise usa `UserRootRotation` e nuovo root epoch; una root rubata non viene “recuperata” continuando a usarla.

## 20. No-super-admin governance

Production:

```text
ReleaseAuthorization   >= 3-of-5
ReleaseRevocation      >= 3-of-5
CriticalSecurityPolicy >= 3-of-5
ContractUpgrade        >= 3-of-5 + timelock
GovernanceRootRotation >= 3-of-5 + recovery
Emergency advisory     scoped + TTL
```

Signer-set transitions sono monotonic/cross-authorized; highest-seen state impedisce rollback.

Una singola Full Access key production che può sostituire il contratto viola il modello.

## 21. Gateway boundary

```text
external app
 -> local Gateway tunnel
 -> route/relay/bridge/Shield
 -> explicit MANAGED/PRIVATE/BUSINESS Egress
 -> Internet
```

Gateway protegge/diversifica il percorso, non trasforma protocolli esterni in Freedom E2EE.

## 22. Censorship resistance

Nessun singolo fingerprint/protocol/IP/domain/relay/RPC/provider/transport deve essere requisito permanente.

Freedom prova alternative quando esiste almeno un carrier utilizzabile; non promette universal bypass.

## 23. Adaptive Defense

`SUSPECTED` deriva da incoerenze osservate e resta inferenza, non prova di censura/sorveglianza.

## 24. Release / Share Freedom

```text
untrusted bytes
 -> exact hash
 -> signer-set transition/epoch
 -> threshold FreedomRelease
 -> Android signer lineage
 -> ReleaseStatus + SecurityPolicy proofs
 -> installer
```

First sideload usa pinned BootstrapTrustAnchor indipendente dalla source.

## 25. Payment privacy

Payment e entitlement preferiscono un one-time voucher/blind credential + redemption nullifier, evitando linkage diretto quando possibile. Timing correlation può restare.

## 26. Contact-slot policy

`10/20 contact slots` V1 è product policy del client ufficiale, non protocol-interoperability invariant. Il social graph non viene pubblicato per enforcement commerciale.

## 27. Primitive vietate

Lista normativa completa: [`SECURITY_INVARIANTS.md`](SECURITY_INVARIANTS.md).

In particolare: no global network ID, on-chain mailbox/messages, persistent relay inbox, public social graph, mandatory central delivery server, master decryption key, single-key super-admin, tx-hash-is-success, silent downgrade, unbounded temporary active state.

## 28. Control-plane / data-plane

```text
CONTROL PLANE
proof/checkpoint state
identity authorization/revocation
rendezvous/recovery
entitlement/payment redemption
security/release/contract governance

DATA PLANE
messages/files/voice/video
relay traffic
Gateway traffic
APK bytes
```

## 29. Principio finale

> **Nessun server centrale. Nessun super-admin. Niente di opaco. Fiducia nel protocollo. Sicurezza nell'architettura.**

Non significa assenza fisica di server/relay/RPC/egress: significa nessuna autorità assoluta o requisito permanente singolo.
