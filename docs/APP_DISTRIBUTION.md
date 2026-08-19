# Freedom — App Distribution / Share Freedom

Status: **canonical design draft**

Normative security rules: [`SECURITY_INVARIANTS.md`](SECURITY_INVARIANTS.md).
Release/security governance: [`EMERGENCY_UPDATES.md`](EMERGENCY_UPDATES.md).

## 1. Principio

Freedom separa:

```text
source of bytes          != release authority
download capability      != release signature
filename / URL           != authenticity
first-install bootstrap  != peer trust
```

L'obiettivo non è impedire a un attaccante di distribuire APK falsi. L'obiettivo è impedire a un verifier conforme di confondere byte falsi con una release Freedom autorizzata.

## 2. Artifact esterno, non APK embedded

Il client non incorpora di default un secondo APK completo da distribuire.

`Share Freedom` usa un artifact standalone verificato, ottenibile da più sorgenti e opzionalmente seedabile da peer/relay/mirror.

```text
Alice genuine Freedom
 -> Share Freedom
 -> Install QR / descriptor
 -> Bob system camera/browser/bootstrap verifier
 -> resolve artifact from peer/relay/mirror/store
 -> verify
 -> Android system installer
```

## 3. Filename opaco

Esempio:

```text
freedom-r42-454fjk4hfhsjhslllshlvhvru0ujwr8w.apk
```

La parte opaca può aiutare discovery/anti-enumeration ma **non è trust**.

La release authority firma manifest canonici, non il filename.

## 4. PeerTransferCapability

Un client genuino può offrire byte di una release già verificata tramite capability temporanea:

```text
PeerTransferCapability {
    transfer_nonce
    release_id
    artifact_sha256
    source_endpoint
    expires_at
    max_downloads?
}
```

Può essere one-shot, TTL breve o bounded a N download.

La capability non concede autorità sulla release e il client non possiede le release private key.

Consumare la capability non revoca la release globale.

## 5. Install QR / descriptor

```text
FreedomInstallDescriptor {
    version
    channel
    package_id
    release_id
    release_locator_hash
    release_manifest_hash
    source_hints[]
    peer_transfer_capability?
    expires_at
}
```

Il descriptor è un locator. Non può ridefinire le root ufficiali.

Il destinatario può non avere Freedom installato; il QR deve essere utilizzabile da camera/browser/bootstrap verifier di sistema.

## 6. Decentralized Release Network

Sorgenti possibili:

```text
PEER_LOCAL
PEER_NETWORK
COMMUNITY / UPDATE RELAY
MANAGED UPDATE NODE
PRIVATE MIRROR
HTTPS MIRROR
STORE
future transport
```

Content addressing:

```text
artifact key = SHA-256(APK bytes)
manifest key = SHA-256(canonical FreedomRelease)
```

Più nodi possono seedare gli stessi byte verificati. Nessun nodo di distribuzione possiede release authority.

## 7. Schema canonico FreedomRelease

Questo documento usa lo stesso schema di `PROTOCOL.md`, `EMERGENCY_UPDATES.md` e `SECURITY_INVARIANTS.md`:

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

Non sono ammesse varianti incompatibili dello stesso oggetto tra documenti/implementazioni.

## 8. ReleaseStatus

```text
ReleaseStatus {
    release_id
    artifact_sha256
    status
    min_secure_version
    policy_epoch
    reason_hash?
    remediation_release?
    issued_at
    signer_set_epoch
    signatures[]
}
```

```text
ACTIVE
DEPRECATED
REVOKED
```

Nessuna write per singola installazione.

`REVOKED` deriva da governance di sicurezza threshold, non dal fatto che un utente abbia scaricato/installato la release.

## 9. Verifica prima dell'installazione

Il verifier **MUST** eseguire almeno:

```text
1. obtain candidate artifact from any source
2. compute SHA-256 over exact APK bytes
3. obtain canonical FreedomRelease
4. verify signer_set_epoch and threshold release signatures
5. verify ReleaseStatus != REVOKED
6. verify package_id
7. verify version_code / anti-downgrade
8. verify artifact_size + artifact_sha256
9. verify Android APK signer / authorized lineage
10. verify current-enough SecurityPolicy / min_secure_version
11. verify bootstrap trust anchor for first sideload
12. only then invoke Android system installer
```

Qualunque mismatch -> **fail closed**.

## 10. Android signing come barriera indipendente

```text
Freedom threshold signatures
        +
artifact content hash
        +
Android APK signer / lineage
        +
ReleaseStatus / SecurityPolicy
```

Nessuna barriera è trattata come sufficiente da sola.

## 11. First install — root of trust canonica

Un dispositivo vergine non può derivare matematicamente la “vera” release root da informazioni fornite tutte dallo stesso attaccante.

Per questo il primo sideload usa un **Freedom Bootstrap Verifier** con trust anchor pinned:

```text
BootstrapTrustAnchor {
    verifier_policy_version
    expected_package_id
    release_signer_set_root_commitment
    android_signing_root_or_lineage_anchor
    minimum_manifest_version
}
```

Il verifier può essere distribuito tramite store/canale indipendente e può mostrare fingerprint per verifica out-of-band.

Il peer/QR/mirror che fornisce l'APK **non può modificare**:

```text
release signer-set root
Android signer/lineage anchor
package_id expected
minimum verifier policy
```

Questa è la root of trust minima inevitabile del primo install.

## 12. Offline / control-plane degraded

La distribuzione continua anche se un singolo RPC è bloccato:

- più RPC/provider;
- manifest/signature verificabili offline;
- signer-set cacheato e verificato;
- ultima SecurityPolicy/ReleaseStatus valida cacheata;
- expiry/epoch/freshness bounded;
- source peer/relay/mirror non autoritative.

Se lo stato di revoca è oltre la freshness consentita per una release sensibile, il verifier richiede stato più recente o fallisce esplicitamente.

## 13. Peer-local transfer

```text
Alice Freedom
  -> selects current verified artifact
  -> opens temporary read-only endpoint
  -> creates PeerTransferCapability
  -> shows QR

Bob
  -> obtains descriptor
  -> downloads artifact
  -> verifies completely
  -> invokes installer
```

L'endpoint è temporaneo, capability-protected e bounded per tempo/download/banda.

## 14. Verified control-plane state

Una write che pubblica `FreedomRelease`, `ReleaseStatus`, signer-set o policy non è considerata valida soltanto perché esiste un transaction hash.

```text
submit
 -> acceptable finality
 -> execution success
 -> read resulting state
 -> verify exact expected object/hash/epoch
 -> only then accept
```

## 15. Threats

La distribuzione deve resistere a:

- peer/mirror che serve byte modificati;
- replay di release vecchie;
- downgrade;
- locator copiati;
- source poisoning;
- singolo signer compromesso;
- singola RPC malevola/stale;
- first-install peer malevolo;
- revocation withholding.

Non protegge contro compromissione simultanea delle threshold root richieste + Android signing root/lineage + verifier trust anchor.

## 16. Invarianti

- APK off-chain;
- source non è trust;
- filename/locator non è trust;
- release private key mai nel client;
- un solo schema FreedomRelease canonico;
- production release authorization/revocation threshold;
- first install con BootstrapTrustAnchor pinned indipendente dalla source;
- Android signer/lineage verificato separatamente;
- exact SHA-256 verificato;
- anti-downgrade;
- `REVOKED` blocca ogni source;
- nessuna write per installazione;
- transaction hash != success;
- fail closed su mismatch;
- nessuna singola source/IP/store obbligatoria per Freedom Direct.
