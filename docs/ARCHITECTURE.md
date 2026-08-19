# Freedom — Architecture

Status: **canonical design draft**

Normative security rules: [`SECURITY_INVARIANTS.md`](SECURITY_INVARIANTS.md).

## 1. Definizione

Freedom separa esplicitamente:

1. **ownership identity** — RootIdentity/recovery;
2. **offline-verifiable device authorization** — DeviceCertificate + DeviceKey;
3. **verifiable control-plane** — revocation/rotation, entitlement, recovery, policy e release;
4. **pairwise identity/rendezvous** — relazione specifica tra due contatti;
5. **routing/transport fabric** — direct, NAT, relay, bridge, Shield, pluggable transport;
6. **Freedom Communication** — sessione E2EE live;
7. **Freedom Gateway** — percorso opzionale per app esterne verso Internet egress;
8. **verified distribution** — source non fidata, release verificata.

La prima implementazione del control-plane usa NEAR tramite `ChainAdapter`; chat, media, Gateway payload e APK restano off-chain.

## 2. Vista d'insieme

```text
                  DISTRIBUTED / VERIFIABLE CONTROL PLANE
      +---------------------------------------------------------+
      | device revocation / rotation                           |
      | pairwise rendezvous / recovery                         |
      | entitlement / sponsorship                              |
      | security policy / release / signer sets                |
      +----------------------------+----------------------------+
                                   |
                          only when needed
                                   |

+--------------------------+                     +--------------------------+
| Alice                    |                     | Bob                      |
| RootIdentity             |                     | RootIdentity             |
| DeviceCertificate        |                     | DeviceCertificate        |
| DeviceKey                |                     | DeviceKey                |
| PairwiseContactAlias_AB  |                     | PairwiseContactAlias_BA  |
+------------+-------------+                     +------------+-------------+
             |                                                |
             +--------- pairwise authenticated state ---------+
                                   |
                         path / transport selector
                                   |
             +---------------------+---------------------+
             |                                           |
             v                                           v
   FREEDOM COMMUNICATION                         FREEDOM GATEWAY
   authenticated E2EE live                      external app traffic
             |                                           |
 text / file / voice / video                    relay/bridge/Shield
                                                         |
                                                 explicit Internet Egress
                                                         |
                                                      Internet
```

## 3. Trust model

```text
RootIdentity                    -> ownership / recovery
DeviceCertificate               -> offline authorization proof
DeviceKey                       -> device authentication
DeviceRecordCommitment          -> opaque control-plane handle
PairwiseContactAlias            -> relationship identity
TransportToken                  -> temporary path identity
Session keys                    -> E2EE traffic keys
EntitlementCommitment           -> domain-separated commercial state
PaymentBindingCommitment        -> domain-separated payment state
```

Nessun livello viene riutilizzato automaticamente come un altro.

## 4. Freedom Communication security boundary

![Freedom Communication architecture](assets/freedom-communication.svg)

```text
Freedom endpoint A
      <==== authenticated E2EE ====>
Freedom endpoint B
```

Le session keys restano agli endpoint.

Relay, bridge, RPC, provider e path selector non autenticano il peer e non possiedono le conversation keys.

Il path può cambiare senza cambiare identità pairwise o session semantics.

## 5. Device authorization senza RPC nel packet hot path

La RootIdentity autorizza DeviceKey tramite:

```text
DeviceCertificate {
    version
    network_id
    root_identity_commitment_or_proof
    device_public_key
    key_epoch
    protocol_version
    capabilities?
    issued_at
    expires_at
    certificate_id
    root_authorization_signature
}
```

Il peer verifica offline il certificato e usa control-plane/cache verificata per revocation/freshness.

Quindi:

```text
new handshake
 -> verify expected contact relationship
 -> verify DeviceCertificate offline
 -> verify DeviceKey possession
 -> apply cached/fresh revocation policy
 -> establish E2EE session
```

Nessuna singola RPC diventa necessaria per ogni handshake.

## 6. Pairwise identity / no global DeviceID

Freedom non usa un `DeviceID` globale come identity o routing identifier.

```text
Bob / RootIdentity
  |- Phone DeviceCertificate
  |- Tablet DeviceCertificate
  `- Desktop DeviceCertificate
```

Il contatto è la persona/RootIdentity.

Dopo bootstrap:

```text
PairSecret_AB
PairwiseContactAlias_AB
PairRendezvousSecret_AB
```

Relazioni differenti producono alias differenti.

## 7. Commitment domain separation

Un singolo `root_commitment` non deve diventare il nuovo global correlator.

```text
DeviceAuthorizationCommitment
EntitlementCommitment
PaymentBindingCommitment
SponsorshipCommitment
```

sono domain-separated.

Rendezvous usa `PairRendezvousSecret`, non account commitment globali.

## 8. Pairwise rendezvous / recovery

```text
known path -> try
alternate relay/bridge -> try
pairwise slot -> read
if usable -> connect, no write
else bounded read-before-write recovery
```

```text
RendezvousRecord {
    version
    expires_at
    ciphertext
}
```

Il record pubblico non contiene mapping leggibile identity/device/route.

## 9. Route / transport fabric

Classi:

```text
DIRECT
NAT_TRAVERSAL
RELAY
BRIDGE
SHIELDED
MULTI_HOP
PLUGGABLE / OBFUSCATED TRANSPORT
```

Transport identity usa capability temporanee:

```text
TransportToken
RelayCircuitToken
NextHopToken
RouteCapability
```

Un IP non rappresenta una identity Freedom.

## 10. Relay architecture

Un relay può essere VPS, server, mini-PC, community node, managed/private node o device opt-in.

Invarianti:

- forward ciphertext, not store;
- niente mailbox persistente;
- buffer/TTL/size/concurrency bounded;
- relay non autentica peer;
- relay non possiede session keys;
- `DEVICE_RELAY` non è Internet egress;
- endpoint context e relay context separati.

> **Qualsiasi macchina compatibile può inoltrare Freedom; nessuna macchina deve diventare Freedom.**

## 11. Forward secrecy / rekey

Freedom Communication richiede:

```text
ephemeral key exchange per sessione
+ forward secrecy tra sessioni
+ bounded traffic-key lifetime
+ authenticated rekey per sessioni lunghe
+ media keys separate
```

Una futura ratchet construction standard/reviewata è il target per post-compromise security.

La compromissione futura della DeviceKey non deve rendere decifrabili sessioni precedenti già concluse.

## 12. Synchronous semantics

```text
active authenticated session?
  yes -> transmit now
  no  -> fail/discard now
```

Nessuna mailbox, message queue o store-and-forward automatico nel protocollo base.

## 13. Verified control-plane finality

```text
submit signed operation
 -> acceptable finality
 -> execution success
 -> read resulting state
 -> verify expected transition
 -> local success
```

`transaction hash != success`.

Vale per activation/rotation/revocation, entitlement, sponsorship, payment effects, policy, release/status e recovery state transitions.

## 14. No super-admin governance

In production:

```text
ReleaseAuthorization   >= 3-of-5
ReleaseRevocation      >= 3-of-5
CriticalSecurityPolicy >= 3-of-5
RootRotation           >= 3-of-5 + recovery
Emergency advisory     scoped + TTL
```

Payment, entitlement, emergency e release authorities sono ruoli separati.

Una singola production key non può controllare l'intero sistema.

## 15. Freedom Gateway boundary

```text
external app
 -> local Gateway tunnel
 -> route/relay/bridge/Shield
 -> explicit MANAGED/PRIVATE/BUSINESS Egress
 -> Internet
```

Gateway protegge/diversifica il percorso, non trasforma protocolli esterni in Freedom E2EE.

`DEVICE_RELAY`/`COMMUNITY_RELAY` non diventano automaticamente Internet exit.

Dettagli: [`GATEWAY.md`](GATEWAY.md).

## 16. Censorship resistance

Invariante:

> **nessun singolo fingerprint, protocollo, IP, dominio, relay, RPC, provider o transport deve essere requisito permanente.**

Freedom tenta path/transport differenti quando esiste almeno un carrier utilizzabile.

Non promette universal firewall bypass o funzionamento senza connettività.

## 17. Adaptive Defense

Quando peer activity/control-plane restano disponibili ma il data path fallisce:

```text
INTERFERENCE_OR_ROUTE_FAILURE_SUSPECTED
 -> alternate relay/provider/transport/bridge/Shield
```

È un'inferenza di rete, non prova di censura o sorveglianza.

## 18. Release / Share Freedom

```text
peer / relay / mirror / store
        -> untrusted artifact bytes
        -> exact hash
        -> threshold FreedomRelease signatures
        -> Android signer/lineage
        -> ReleaseStatus / SecurityPolicy
        -> installer
```

First sideload usa `BootstrapTrustAnchor` pinned indipendente dalla source.

La source dei byte non può ridefinire release signer root, package ID o Android signing anchor.

## 19. Primitive vietate

Freedom Protocol **MUST NOT** introdurre:

- global user/device network ID;
- RootIdentity/DeviceRecordCommitment come routing/contact ID;
- on-chain messages/mailbox;
- persistent relay inbox;
- automatic offline delivery queue;
- public readable social graph;
- mandatory central delivery server;
- mandatory single RPC/provider/relay/egress;
- master decryption key;
- single production super-admin key;
- transaction-hash-is-success semantics;
- silent security downgrade.

Lista completa: [`SECURITY_INVARIANTS.md`](SECURITY_INVARIANTS.md).

## 20. Control-plane/data-plane split

```text
CONTROL PLANE
identity authorization / revocation
rendezvous / recovery
entitlement / payment attestation
security policy / release governance

DATA PLANE
messages
files
voice
video
relay traffic
Gateway traffic
APK artifact bytes
```

Il registro non è nel packet hot path.

## 21. Principio finale

> **Nessun server centrale. Nessun super-admin. Niente di opaco. Fiducia nel protocollo. Sicurezza nell'architettura.**

Questo non significa assenza fisica di server/relay/RPC/egress. Significa che nessuno di essi deve essere autorità assoluta, trust anchor unico o requisito permanente.
