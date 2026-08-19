# Freedom — Store Compliance Architecture

Status: **canonical design draft**

Normative security rules: [`SECURITY_INVARIANTS.md`](SECURITY_INVARIANTS.md).

## 1. Separazione fondamentale

```text
Freedom Protocol
  identity / pairwise rendezvous / routing / E2EE
        |
        +-- Freedom Communication
        +-- Freedom Gateway optional
        |
        +-- Android client -> store compliance
        `-- iOS client     -> store compliance
```

Le regole degli store non introducono master key, mailbox o server centrale di delivery e non ridefiniscono automaticamente il wire protocol.

## 2. Identity privacy

```text
RootIdentity
DeviceCertificate
DeviceKey
DeviceRecordCommitment
PairwiseContactAlias
TransportToken
```

Nessun global DeviceID network-facing.

Evitare per default numero/email obbligatori, upload rubrica, advertising ID, analytics non necessari, plaintext logging e centralizzazione social graph.

## 3. Contact QR / Install QR

```text
Contact QR
 -> identity/bootstrap capability

Install QR
 -> release locator / source hints / transfer capability
```

Sono flussi distinti.

Il Contact QR non autentica da solo una sessione: l'handshake verifica expected RootIdentity/contact + DeviceCertificate + DeviceKey possession.

L'Install QR non può ridefinire release signer root o Android signer anchor.

## 4. Account/recovery/deletion

Il client deve poter revocare il device, eliminare key/dati locali e richiedere cancellazione di eventuali dati service-side opzionali.

La revoca invalida il device per nuovi handshake secondo freshness policy; non pretende di cancellare la storia immutabile del control-plane.

## 5. Android relay mode

`DEVICE_RELAY`:

- opt-in;
- visibile/disattivabile;
- resource-bounded;
- no mailbox;
- no plaintext/session keys;
- no Internet egress implicito.

Relay Contributor non trasforma il telefono in open proxy Internet.

## 6. Freedom Gateway su Android

```text
selected apps / whole device
 -> Android VpnService
 -> Freedom encrypted tunnel
 -> relay / bridge / Shield
 -> explicit egress
 -> Internet
```

La build store deve rispettare le policy vigenti della piattaforma al momento della release, inclusi disclosure/consenso/dichiarazioni richieste.

Se necessario, Freedom può separare:

```text
Freedom Play
Freedom Direct
Freedom Gateway companion
```

Una restrizione dello store non deve eliminare il protocollo o la Direct build dove legalmente/tecnicamente consentita.

## 7. Gateway transparency

Quando attivo mostra almeno:

- ON/OFF;
- selected-app vs whole-device;
- egress/path corrente in advanced UI;
- split tunneling;
- kill-switch/strict mode;
- quota managed separata;
- privacy/trust boundary distinta da Freedom Communication.

Non mostrare “E2EE by Freedom” per traffico Gateway generico.

## 8. Google Play build / Direct build

### Play

Usa percorso install/update conforme allo store.

Non basare self-update su APK duplicato negli asset, silent install o bypass store.

### Direct

```text
existing Freedom
 -> Share Freedom
 -> Install QR
 -> peer / relay / mirror
 -> bootstrap verifier
 -> verified artifact
 -> Android installer
```

L'APK è artifact standalone verificato.

## 9. Anti-fake / first sideload

Prima dell'installazione Direct:

```text
exact artifact SHA-256
threshold FreedomRelease signatures
package_id
version / anti-downgrade
Android signer / lineage
ReleaseStatus
SecurityPolicy
```

Per il primo sideload il `Freedom Bootstrap Verifier` usa root pinned:

```text
expected_package_id
release_signer_set_root_commitment
android_signing_root_or_lineage_anchor
minimum verifier policy
```

Il peer/relay/QR/source non può ridefinire queste root.

## 10. Release governance

Production release authorization/revocation/critical SecurityPolicy usa threshold/multi-key secondo `SECURITY_INVARIANTS.md`.

Una singola private key online non deve diventare super-admin della supply chain.

## 11. iOS/background

Freedom non presume che iOS consenta listener/relay persistenti in background. Wake hint non è identity authority.

Gateway iOS richiede design/API/entitlement specifici della piattaforma e non viene promesso sulla base del solo Android design.

## 12. Store review mode

Procedura riproducibile per:

- onboarding;
- Contact QR;
- DeviceCertificate/expected-contact session;
- E2EE live;
- peer offline -> no delivery queue;
- block/report;
- Relay mode;
- Share Freedom/install verification;
- Gateway controls se inclusi;
- spiegazione control-plane/data-plane;
- relay vs egress.

## 13. Protocol independence

Store policy non cambia automaticamente:

- RootIdentity/DeviceCertificate/pairwise model;
- authenticated E2EE;
- forward-secrecy/rekey requirements;
- synchronous semantics;
- relay semantics;
- ChainAdapter abstraction;
- threshold release authenticity;
- Direct distribution trust model;
- transport adapter model.

## 14. Invarianti

- store compliance non introduce central delivery server/mailbox;
- store non è trust anchor del protocollo;
- `DEVICE_RELAY != INTERNET_EGRESS`;
- Gateway opt-in/visibile;
- Gateway != Communication E2EE boundary;
- Direct/Play separation possibile;
- first sideload root pinned indipendente dalla source;
- production release governance threshold;
- transaction hash non determina da solo stato security-critical.
