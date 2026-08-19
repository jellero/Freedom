# Freedom Communication

**Powered by Freedom Protocol**

> **Nessun server centrale. Nessun super-admin. Niente di opaco. Fiducia nel protocollo. Sicurezza nell'architettura.**
>
> **Synchronous. Ephemeral. Endpoint-to-endpoint.**

Freedom Communication è un sistema di comunicazione privata live costruito su Freedom Protocol. La conversazione esiste quando i peer sono presenti nello stesso momento e riescono a stabilire una sessione autenticata.

```text
peer raggiungibile + sessione autenticata -> comunica adesso
peer non raggiungibile                    -> fail/discard, non accodare
```

Il protocollo base non crea mailbox offline, non deposita messaggi sulla blockchain e non usa relay come storage persistente.

Le regole normative di sicurezza sono in [`docs/SECURITY_INVARIANTS.md`](docs/SECURITY_INVARIANTS.md).

## Product family

```text
Freedom Protocol
|- Freedom Communication  -> E2EE live endpoint-to-endpoint
|- Freedom Gateway        -> percorso rete opzionale per altre app
`- Freedom Shield         -> resilienza/privacy avanzata del path

Share Freedom             -> distribuzione verificabile del client
```

## Due superfici, due garanzie

### Freedom Communication

```text
Alice
  |
  | authenticated E2EE live session
  | forward secrecy + bounded key lifetime
  v
Bob
```

Target:

- peer autenticato rispetto alla relazione attesa;
- `DeviceCertificate` verificabile offline;
- session keys agli endpoint;
- forward secrecy tra sessioni;
- rekey obbligatorio per sessioni lunghe;
- nessuna mailbox/offline queue;
- relay non fidati e forward-only;
- path sostituibili;
- identity/routing/transport separati;
- alias pairwise, nessun global DeviceID di rete.

### Freedom Gateway

```text
Chrome / Firefox / altra app
            |
            v
      Freedom Gateway
            |
      encrypted tunnel
            |
 relay / bridge / Shield / transport adattivo
            |
            v
       explicit Egress
            |
            v
          Internet
```

Gateway protegge/diversifica il percorso di rete. Non trasforma un protocollo esterno in Freedom E2EE.

`DEVICE_RELAY` e `COMMUNITY_RELAY` non diventano automaticamente Internet exit.

Dettagli: [`docs/GATEWAY.md`](docs/GATEWAY.md).

## Architettura

![Architettura del sistema Freedom](docs/assets/freedom-architecture.svg)

```text
RootIdentity
 -> offline-verifiable DeviceCertificate
 -> authorized DeviceKey / opaque device record
 -> pairwise identity
 -> verifiable control-plane when needed
 -> adaptive path / transport selector
      |- Freedom Communication -> authenticated E2EE live session
      `- Freedom Gateway       -> explicit egress -> Internet
```

Il control-plane non trasporta chat, file, audio, video, Gateway payload o APK.

**NEAR non è Freedom Protocol.** È la prima implementazione del `ChainAdapter`.

### Freedom Communication architecture

![Freedom Communication architecture](docs/assets/freedom-communication.svg)

### Freedom Gateway architecture

![Freedom Gateway architecture](docs/assets/freedom-gateway.svg)

## Identity model

```text
RootIdentity                    -> ownership / recovery
DeviceCertificate               -> autorizzazione offline DeviceKey
DeviceKey                       -> autenticazione operativa
DeviceRecordCommitment          -> handle opaco control-plane
PairwiseContactAlias            -> alias specifico della relazione
TransportToken                  -> route/circuito temporaneo
Session keys                    -> E2EE effimera
EntitlementCommitment           -> entitlement domain-separated
PaymentBindingCommitment        -> payment binding domain-separated
SponsorshipCommitment           -> anti-abuse domain-separated
```

Freedom **non usa un `DeviceID` globale** come identità pubblica o identificatore di trasporto.

Un contatto è una persona/RootIdentity, non ogni singolo device.

Dettagli: [`docs/IDENTITY_MODEL.md`](docs/IDENTITY_MODEL.md).

## Device authorization senza RPC obbligatoria per ogni handshake

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

Il peer verifica offline firma/binding/epoch/expiry. Chain/cache verificata serve per revocation/freshness, senza mettere una singola RPC nel packet hot path.

## Pairwise contact / rendezvous

Bootstrap intenzionale:

```text
FreedomContact {
    version
    network_id
    root_identity_proof
    contact_capability
    bootstrap_device_certificate?
    bootstrap_route_hints[]?
    expires_at?
}
```

Dopo handshake autenticato:

```text
PairSecret_AB
PairwiseContactAlias_AB
PairRendezvousSecret_AB
```

Relazioni differenti producono alias differenti. Rendezvous/recovery usa slot opachi derivati da secret pairwise, non da global DeviceID o account commitment.

## Forward secrecy e rekey

Freedom Communication richiede:

```text
ephemeral key exchange per sessione
+ forward secrecy tra sessioni
+ bounded traffic-key lifetime
+ authenticated rekey
+ separate messaging/media keys
```

La compromissione futura della DeviceKey non deve permettere di ricostruire session key di sessioni precedenti concluse.

Una ratchet construction standard/reviewata è il target per post-compromise security.

## Relay

Un relay Freedom può essere:

```text
VPS / VM
server dedicato
mini PC / Raspberry Pi
community node
managed/private node
telefono / tablet / desktop opt-in
```

Invarianti:

- forward ciphertext, not store;
- niente mailbox persistenti;
- buffer/TTL/concurrency bounded;
- relay non autentica i peer;
- relay non possiede session keys;
- `DEVICE_RELAY != INTERNET_EGRESS`.

> **Qualsiasi macchina compatibile può inoltrare Freedom; nessuna macchina deve diventare Freedom.**

Policy Free iniziale:

```text
FREE                      1 device / 10 contatti attivi
FREE + RELAY CONTRIBUTOR  1 device / 20 contatti attivi
```

Il +10 richiede contributo utile e privacy-preserving; il toggle non basta.

Dettagli: [`docs/RELAYS.md`](docs/RELAYS.md).

## Adaptive Defense / Shield

```text
peer recently active
+ at least one control-plane path reachable
+ current data path failing
 -> INTERFERENCE_OR_ROUTE_FAILURE_SUSPECTED
 -> alternate relay/provider/transport/bridge/Shield
```

`SUSPECTED` è inferenza, non prova di censura o sorveglianza.

Network Indicator:

```text
NORMAL
SHIELDED
DEGRADED
SUSPECTED
UNAVAILABLE
```

Dettagli: [`docs/ADAPTIVE_DEFENSE.md`](docs/ADAPTIVE_DEFENSE.md) e [`docs/NETWORK_STATUS_UI.md`](docs/NETWORK_STATUS_UI.md).

## Censorship resistance

Freedom non deve avere un singolo IP, dominio, protocollo, relay, RPC, provider, egress o transport la cui interdizione blocchi l'intero sistema.

```text
direct blocked       -> relay / altro path
relay A blocked      -> relay B / bridge
RPC A blocked        -> RPC B / verified cache
transport A filtered -> transport B
public pool blocked  -> non-public / pairwise bridge
normal path fails    -> Shield / alternate strategy
```

Freedom non promette di attraversare **ogni firewall**. Se non esiste alcun carrier utilizzabile o la rete consente solo una allowlist totale incompatibile, nessun protocollo IP può inventare connettività.

Obiettivo: **Maximum Reachability** tramite path/transport/provider diversity e failover automatico.

## Verified control-plane finality

Un transaction hash **non** equivale a successo.

```text
submit signed operation
 -> acceptable finality
 -> inspect execution outcome
 -> read resulting state
 -> verify expected transition
 -> only then local success
```

Vale per activation/rotation/revocation, entitlement, sponsorship, payment effects, release/status/policy e recovery state transitions.

Label come `ACTIVE`, `PAID`, `REVOKED`, `VERIFIED` non possono derivare dal solo tx hash.

## Nessun super-admin

In production la governance critica è threshold/multi-key:

```text
ReleaseAuthorization   >= 3-of-5
ReleaseRevocation      >= 3-of-5
CriticalSecurityPolicy >= 3-of-5
RootRotation           >= 3-of-5 + recovery
Emergency advisory     scoped + TTL
```

Payment attestor, entitlement authority, release signer, emergency signer e relay/egress operator hanno ruoli separati.

Una singola production key non deve poter controllare l'intero sistema.

## Share Freedom / decentralized release network

![Freedom Release Network](docs/assets/freedom-release-network.svg)

```text
peer / relay / mirror / store
        -> untrusted artifact bytes
        -> exact SHA-256
        -> threshold FreedomRelease signatures
        -> Android signer / lineage
        -> ReleaseStatus / SecurityPolicy
        -> installer
```

Schema canonico:

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

Il filename tipo:

```text
freedom-r42-454fjk4hfhsjhslllshlvhvru0ujwr8w.apk
```

è un locator, **non trust**.

### First sideload

Il `Freedom Bootstrap Verifier` usa un trust anchor pinned indipendente dalla source:

```text
expected_package_id
release_signer_set_root_commitment
android_signing_root_or_lineage_anchor
minimum verifier policy
```

Peer/QR/mirror possono indicare dove trovare i byte ma non ridefiniscono queste root.

Dettagli: [`docs/APP_DISTRIBUTION.md`](docs/APP_DISTRIBUTION.md) e [`docs/EMERGENCY_UPDATES.md`](docs/EMERGENCY_UPDATES.md).

## Product UI concept

Gli screen seguenti sono **concept UI**, non screenshot dell'attuale spike. Le label di sicurezza devono riflettere stato realmente verificato.

### Freedom Communication

![Freedom Communication product screens](docs/assets/freedom-communication-screens.svg)

Live E2EE, route sostituibile, no mailbox.

### Freedom Gateway

![Freedom Gateway product screens](docs/assets/freedom-gateway-screens.svg)

Selected apps/whole device, egress esplicito, quota managed separata dalla Communication.

### Freedom Shield

![Freedom Shield product screens](docs/assets/freedom-shield-screens.svg)

Fatti osservati, inferenza e contromisura separati.

### Share Freedom

![Share Freedom product screens](docs/assets/freedom-share-screens.svg)

Source libera, installazione fail-closed verificata.

Dettagli: [`docs/PRODUCT_VISUALS.md`](docs/PRODUCT_VISUALS.md).

## Monetizzazione

> **monetizzare capacità, comodità e servizi professionali; non la conversazione.**

> **la censura non deve diventare un paywall.**

### Free

- 1 device attivo;
- 10 contatti attivi;
- +10 con Relay Contributor qualificato;
- Freedom Communication E2EE/live;
- community/device relay;
- base Network Indicator/recovery;
- Emergency Shield bounded;
- **Freedom Gateway managed: target iniziale 100 MB/giorno**, quando disponibile.

I 100 MB/giorno riguardano esclusivamente managed Internet egress, non messaggi/chiamate Freedom.

### Plus / Shield

- più contatti/device;
- Always-Shielded;
- multi-hop;
- più provider/path diversity;
- più managed relay/Gateway capacity;
- Maximum Resilience / Maximum Reachability.

### Business

- SDK/integrations;
- private deployments;
- dedicated relay/Shield pools;
- private/business egress;
- custom quotas;
- support/SLA.

Entitlement/payment/sponsorship usano commitment **domain-separated**; non riutilizzano un global account ID come network identity.

Dettagli: [`docs/MONETIZATION.md`](docs/MONETIZATION.md) e [`docs/PAYMENTS.md`](docs/PAYMENTS.md).

## Primitive vietate

Freedom Protocol **MUST NOT** introdurre:

```text
global user/device network identifier
on-chain messages/mailbox
persistent relay inbox
automatic offline delivery queue
RootIdentity as routing identifier
DeviceRecordCommitment as contact identifier
public readable social graph
mandatory central delivery server
mandatory single RPC/provider/relay/egress
master decryption key
single production super-admin key
transaction-hash-is-success semantics
silent downgrade from strict/Shield policy
```

Lista normativa completa: [`docs/SECURITY_INVARIANTS.md`](docs/SECURITY_INVARIANTS.md).

## Confronto oggettivo

Freedom non viene presentato come “più sicuro” in assoluto.

- **Signal** — benchmark UX/E2EE production;
- **SimpleX** — benchmark metadata privacy/no global user IDs;
- **Session** — decentralizzazione/onion routing;
- **Briar** — transport resilience;
- **Tor** — bridge/pluggable transport/anti-censura;
- **Psiphon** — circumvention adattiva;
- **Tailscale** — overlay/exit nodes;
- **VPN multi-hop** — tunnel device-wide/server diversity.

La differenziazione Freedom è la **combinazione** di communication live-only, pairwise identity, relay forward-only, control-plane verificabile, replaceable paths, Adaptive Defense, Gateway opzionale e distribuzione verificata.

Dettagli: [`docs/COMPETITIVE_POSITIONING.md`](docs/COMPETITIVE_POSITIONING.md).

## Documentazione

- [`docs/SECURITY_INVARIANTS.md`](docs/SECURITY_INVARIANTS.md) — regole MUST/MUST NOT, governance, forward secrecy, finality, bootstrap trust.
- [`docs/IDENTITY_MODEL.md`](docs/IDENTITY_MODEL.md) — RootIdentity, DeviceCertificate, commitment e alias pairwise.
- [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) — architettura completa.
- [`docs/PROTOCOL.md`](docs/PROTOCOL.md) — oggetti/flussi normativi.
- [`docs/CHAIN.md`](docs/CHAIN.md) — control-plane e verified finality.
- [`docs/THREAT_MODEL.md`](docs/THREAT_MODEL.md) — minacce/limiti/mitigazioni.
- [`docs/RELAYS.md`](docs/RELAYS.md) — relay/device relay e resource bounds.
- [`docs/GATEWAY.md`](docs/GATEWAY.md) — Gateway/egress/anti-censura.
- [`docs/ADAPTIVE_DEFENSE.md`](docs/ADAPTIVE_DEFENSE.md) — recovery e interference inference.
- [`docs/NETWORK_STATUS_UI.md`](docs/NETWORK_STATUS_UI.md) — Network Indicator.
- [`docs/ACCOUNT_RECOVERY_LICENSES.md`](docs/ACCOUNT_RECOVERY_LICENSES.md) — recovery/multi-device.
- [`docs/PAYMENTS.md`](docs/PAYMENTS.md) — payment binding e attestation.
- [`docs/REGISTRATION_ECONOMICS.md`](docs/REGISTRATION_ECONOMICS.md) — sponsorship/anti-Sybil.
- [`docs/EMERGENCY_UPDATES.md`](docs/EMERGENCY_UPDATES.md) — threshold release/security governance.
- [`docs/APP_DISTRIBUTION.md`](docs/APP_DISTRIBUTION.md) — Release Network/first sideload.
- [`docs/MONETIZATION.md`](docs/MONETIZATION.md) — Free/Shield/Gateway/Business.
- [`docs/PRODUCT_VISUALS.md`](docs/PRODUCT_VISUALS.md) — concept UI.
- [`docs/PRODUCT_SCOPE.md`](docs/PRODUCT_SCOPE.md) — V1 e roadmap.
- [`docs/LAUNCH_PLAN.md`](docs/LAUNCH_PLAN.md) — validazione/lancio.
- [`docs/STORE_COMPLIANCE.md`](docs/STORE_COMPLIANCE.md) — platform/store separation.
- [`ANDROID.md`](ANDROID.md) — roadmap Android.

## Principio finale

Freedom non è definito da una blockchain, un relay specifico, una VPN o una singola app.

> **Nessun server centrale. Nessun super-admin. Niente di opaco. Fiducia nel protocollo. Sicurezza nell'architettura.**

Questo significa che server, relay, RPC, store ed egress possono esistere fisicamente, ma nessuno deve essere autorità assoluta, trust anchor unico o requisito permanente.
