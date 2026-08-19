# Freedom — Security & Trust Invariants

Status: **canonical / normative design rules**

Questo documento definisce proprietà che una implementazione Freedom compatibile **MUST** rispettare. In caso di conflitto con documenti più vecchi, queste invarianti prevalgono finché il documento in conflitto non viene riallineato.

## 1. Trust model

Freedom separa rigidamente:

```text
RootIdentity              -> ownership / recovery
DeviceCertificate         -> autorizzazione offline della DeviceKey
DeviceKey                 -> autenticazione operativa del device
DeviceRecordCommitment    -> handle opaco del control-plane
PairwiseContactAlias      -> identità specifica della relazione
TransportToken            -> route/circuito temporaneo
Session keys              -> E2EE effimera
EntitlementCommitment     -> capacità/licenza
PaymentBindingCommitment  -> binding economico separato
```

Nessuno di questi identificatori deve essere riutilizzato automaticamente come un altro.

## 2. Primitive vietate

Freedom Protocol **MUST NOT** introdurre:

- un identificatore globale user/device richiesto dal network layer;
- `RootIdentity` o `DeviceRecordCommitment` come routing identifier;
- messaggi, file, audio o video on-chain;
- mailbox on-chain;
- inbox persistente su relay;
- automatic offline delivery queue nel protocollo base;
- store-and-forward automatico per peer offline;
- social graph pubblico leggibile;
- mapping pubblico leggibile `RootIdentity -> devices[]`;
- mapping pubblico leggibile `identity -> IP/relay/route`;
- un server centrale di delivery obbligatorio;
- un singolo RPC/provider/relay/egress obbligatorio;
- una master decryption key;
- una singola production key con potere amministrativo totale;
- semantica `transaction hash == success`;
- downgrade silenzioso da una policy protetta a un path meno sicuro.

## 3. Synchronous communication invariant

```text
active authenticated session -> transmit now
no active authenticated session -> fail/discard now
```

Un endpoint può ritentare esplicitamente durante una nuova sessione, ma il protocollo non crea automaticamente una consegna futura.

## 4. Device authorization offline-verifiable

Ogni DeviceKey usata per nuovi handshake deve essere autorizzata dalla RootIdentity tramite un certificato verificabile offline:

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

Requisiti:

- il certificato è firmato dalla RootIdentity o da una chiave di autorizzazione derivata/ruotabile esplicitamente delegata;
- il peer può verificarne firma, network, epoch, expiry e binding alla relazione attesa senza interrogare un RPC nel packet hot path;
- revocation/rotation/freshness vengono ottenute dal control-plane o da cache verificata secondo policy;
- uno stato di revoca troppo vecchio può impedire **nuovi** handshake high-risk, ma non deve trasformare una singola RPC in un trust anchor;
- un relay non autentica il DeviceCertificate.

## 5. Handshake invariant

Il transcript di una sessione nuova deve legare almeno:

```text
network_id
protocol_version
expected pairwise relationship
local/remote pairwise aliases or commitments
local/remote DeviceCertificate hash/proof
local/remote key_epoch
ephemeral key material
nonces
negotiated crypto suite
session_id
```

L'handshake deve provare contemporaneamente:

1. che il peer è la RootIdentity/contact identity attesa per quella relazione;
2. che la DeviceKey corrente è autorizzata;
3. che il peer possiede la DeviceKey;
4. che il materiale effimero appartiene alla sessione corrente.

## 6. Forward secrecy e key lifetime

Freedom Communication **MUST** offrire forward secrecy tra sessioni.

La compromise di una DeviceKey a tempo `T` non deve consentire di derivare le session key di sessioni E2EE completate prima di `T`, salvo compromissione contemporanea degli endpoint/session state.

Per sessioni lunghe:

- le traffic key devono avere lifetime bounded per tempo/byte/frame;
- il protocollo deve supportare rekey prima del limite;
- messaging keys e media keys restano separate;
- una costruzione ratchet standard e reviewata è il target per post-compromise security;
- non inventare ratchet proprietari senza necessità e review.

`SESSION_REKEY_REQUIRED` è un errore/protocol event normativo, non opzionale.

## 7. Domain separation dei commitment

Una RootIdentity stabile non deve diventare un correlatore universale del control-plane.

Derivare commitment separati per dominio, per esempio:

```text
DeviceAuthorizationCommitment = H(root_secret/context, "device-auth", ...)
EntitlementCommitment         = H(root_secret/context, "entitlement", ...)
PaymentBindingCommitment      = H(root_secret/context, "payment", ...)
SponsorshipCommitment         = H(root_secret/context, "sponsorship", ...)
```

Requisiti:

- non riutilizzare lo stesso commitment stabile tra domini quando non necessario;
- pairwise rendezvous deriva da `PairRendezvousSecret`, non da commitment account-global;
- payment provider reference non contiene commitment Freedom globali in plaintext;
- eventuale enforcement privacy-preserving può usare slot, nullifier, blind commitment o prove ZK quando giustificato.

## 8. Verified control-plane finality

Un hash di transazione significa soltanto **submission**, non successo.

Per ogni mutazione security-sensitive:

```text
submit signed operation
 -> wait acceptable finality
 -> inspect execution outcome
 -> reject Failure / partial failure
 -> read/verify resulting state
 -> only then commit local state transition
```

Si applica almeno a:

- root/device activation;
- key rotation/revocation;
- entitlement changes;
- sponsorship state;
- payment attestation effects;
- SecurityPolicy;
- FreedomRelease / ReleaseStatus;
- rendezvous/recovery write quando il risultato influisce sulla state machine.

Una implementazione non deve mostrare `ACTIVE`, `VERIFIED`, `REVOKED`, `PAID` o equivalente finché lo stato risultante non è verificato.

## 9. Bounded control-plane state

Il control-plane non contiene mailbox o message history.

Ogni stato temporaneo deve avere:

- size bound;
- TTL/expiry o epoch;
- rate limit;
- ownership/authorization;
- reclaim/overwrite strategy quando possibile.

Rendezvous e RecoveryBeacon devono essere bounded, opachi, pairwise e read-before-write.

## 10. Production governance: no super-admin

Il claim **“Nessun super-admin”** richiede separazione crittografica dei poteri.

In production:

- release authorization **MUST** richiedere threshold/multi-key governance;
- global release revocation **MUST** richiedere threshold/multi-key governance;
- critical `SecurityPolicy` **MUST** richiedere threshold/multi-key governance;
- la rotazione della release root **MUST** richiedere una procedura threshold/recovery documentata;
- emergency keys, se esistono, devono avere scope ridotto, TTL breve e non poter autorizzare una nuova release arbitraria da sole;
- payment attestors non possono firmare release/policy;
- entitlement authorities non possono decifrare conversazioni;
- relay/egress operators non hanno potere sull'identità E2EE.

Target iniziale di governance production:

```text
ReleaseAuthorization    >= 3-of-5
ReleaseRevocation       >= 3-of-5
CriticalSecurityPolicy  >= 3-of-5
RootRotation            >= 3-of-5 + recovery procedure
Emergency advisory      separate scoped key/set + TTL
```

I numeri possono evolvere tramite una governance migration esplicita; non è consentito degradare silenziosamente a `1-of-1`.

## 11. Release authenticity e first-install root

La sorgente dei byte non è trust.

Una release installabile deve verificare:

```text
exact artifact SHA-256
+ canonical FreedomRelease threshold signatures
+ Android package signer / authorized lineage
+ ReleaseStatus != REVOKED
+ current-enough SecurityPolicy
+ anti-downgrade policy
```

Per il **primo sideload** il bootstrap verifier ufficiale deve incorporare/pinnare almeno:

```text
Freedom release root / threshold signer set commitment
expected package_id
expected Android signing root/lineage anchor
minimum verifier policy version
```

Peer/QR/mirror/store possono indicare dove trovare i byte, ma non possono ridefinire queste root.

## 12. Release schema single source of truth

Lo schema canonico è:

```text
FreedomRelease {
    manifest_version
    release_id
    version_code
    version_name
    package_id
    artifact_sha256
    artifact_size
    signing_cert_fingerprint
    signing_lineage_commitment?
    min_supported_version
    min_secure_version
    criticality
    release_locator_hash
    issued_at
    signer_set_epoch
    signatures[]
}
```

Gli altri documenti devono referenziare questo schema e non introdurre varianti incompatibili.

## 13. Security labels are derived state

Label UI come:

```text
VERIFIED
E2EE
ACTIVE
SHIELDED
SUSPECTED
REVOKED
```

devono derivare da verifiche o osservazioni implementate. Non sono stringhe marketing.

`SUSPECTED` rappresenta una inferenza di rete, non prova censura, sorveglianza o attribuzione dell'attore.

## 14. Fail closed / fail explicit

Failure di autenticazione, release verification, downgrade policy o stato di revoca non deve trasformarsi in successo silenzioso.

Per reachability, invece, Freedom può degradare tra path **solo entro la policy autorizzata dall'utente**. Se l'utente richiede Shield/strict/kill-switch, il client non può uscire direct silenziosamente.

## 15. Review gates

Prima dell'interoperabilità pubblica devono esistere:

- encoding canonici e test vector;
- negative handshake tests;
- replay/downgrade tests;
- parser/resource-limit tests;
- control-plane finality/failure tests;
- release/first-install verification tests;
- independent cryptographic/security review delle primitive normative;
- threat-model review separata per Gateway/Shield e per Freedom Communication.
