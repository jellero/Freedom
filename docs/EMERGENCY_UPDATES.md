# Freedom — Emergency Bulletins & Secure Updates

Status: **canonical design draft**

Normative security rules: [`SECURITY_INVARIANTS.md`](SECURITY_INVARIANTS.md).
Distribution details: [`APP_DISTRIBUTION.md`](APP_DISTRIBUTION.md).

## 1. Obiettivo

Freedom deve distribuire avvisi, policy e release verificabili senza introdurre un server centrale obbligatorio o una singola sorgente di download.

Il control-plane conserva **manifest, locator, signer-set state, revoche e policy piccoli, firmati e verificabili**. APK e altri artifact pesanti restano off-chain.

```text
chi fornisce i byte      != chi autorizza la release
artifact transport       != release authenticity
filename / URL           != proof of authenticity
```

![Freedom Release Network](assets/freedom-release-network.svg)

## 2. EmergencyBulletin

```text
EmergencyBulletin {
    version
    bulletin_id
    severity
    issued_at
    expires_at
    geographic_scope?
    topic?
    min_app_version?
    min_protocol_version?
    payload_hash
    issuer_set_epoch
    signatures[]
}
```

Il matching geografico avviene localmente. La posizione utente non viene scritta on-chain per il solo matching.

## 3. Wake/discovery

La blockchain non sveglia direttamente un'app sospesa.

Discovery ridondante:

```text
app open / periodic check
peer gossip / Freedom transport
optional platform push hint
```

Il push è solo hint; bulletin/policy sono sempre verificati crittograficamente.

## 4. Schema canonico FreedomRelease

Esiste **un solo schema normativo**:

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

La release authority firma il manifest canonico, non filename/URL/source descriptor.

## 5. FreedomReleaseLocator

```text
FreedomReleaseLocator {
    locator_version
    release_id
    release_nonce
    manifest_hash
    artifact_sha256
    package_id
    version_code
    channel
    issued_at
    expires_at?
    signer_set_epoch
    signatures[]
}
```

`release_nonce` è random ad alta entropia.

Un filename come:

```text
freedom-r42-454fjk4hfhsjhslllshlvhvru0ujwr8w.apk
```

è soltanto un locator/anti-enumeration hint. Non è trust.

Le private key di release non sono presenti nei client né nell'infrastruttura pubblica di distribuzione.

## 6. ReleaseStatus

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

Stati:

```text
ACTIVE
DEPRECATED
REVOKED
```

Una release non viene revocata perché qualcuno la installa. Non esiste write per singola installazione.

## 7. SignerSet e governance

“Nessun super-admin” richiede governance crittografica, non soltanto policy organizzativa.

```text
SignerSet {
    signer_set_epoch
    role
    public_keys[]
    threshold
    valid_from
    expires_at?
    previous_set_commitment?
    signatures[]
}
```

Ruoli separati:

```text
RELEASE_AUTHORIZATION
RELEASE_REVOCATION
CRITICAL_SECURITY_POLICY
ROOT_ROTATION
EMERGENCY_ADVISORY
```

In production:

```text
ReleaseAuthorization   >= 3-of-5
ReleaseRevocation      >= 3-of-5
CriticalSecurityPolicy >= 3-of-5
RootRotation           >= 3-of-5 + recovery procedure
```

Non è consentito degradare silenziosamente a `1-of-1`.

Una emergency key/set può avere threshold diverso soltanto con scope ridotto e TTL breve e **non può autorizzare una nuova release arbitraria da sola**.

Payment attestor, entitlement authority e relay/egress operator non hanno potere di release/security governance.

## 8. Decentralized Release Network

Artifact source possibili:

```text
STORE
PEER_LOCAL
PEER_NETWORK
COMMUNITY / UPDATE RELAY
MANAGED UPDATE NODE
PRIVATE / HTTPS MIRROR
future transport
```

Content addressing:

```text
artifact key = SHA-256(APK bytes)
manifest key = SHA-256(canonical FreedomRelease)
```

Qualunque nodo può servire i byte. Nessun nodo decide da solo se sono validi.

## 9. Verifica pre-install

```text
candidate artifact
 -> compute exact SHA-256
 -> resolve canonical FreedomRelease
 -> verify signer_set_epoch + threshold signatures
 -> verify locator when used
 -> verify ReleaseStatus != REVOKED
 -> verify package_id
 -> verify version / anti-downgrade
 -> verify artifact size/hash
 -> verify Android signing cert / authorized lineage
 -> verify current-enough SecurityPolicy
 -> INSTALL
```

Qualunque mismatch -> **fail closed**.

Una label `VERIFIED` può comparire soltanto dopo queste verifiche rilevanti.

## 10. Share Freedom

Un client genuino genera una capability temporanea di **trasferimento**, non una nuova release:

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

Il client non possiede la private release key.

Consumare la capability non modifica `ReleaseStatus` globale.

## 11. Android signing

La firma APK Android è una barriera indipendente:

```text
Freedom threshold release signatures
+ exact artifact hash
+ Android package signer / authorized lineage
+ ReleaseStatus
+ SecurityPolicy
```

Per rotazioni autorizzate si usa la signing lineage prevista dalla piattaforma e una policy di rotazione documentata.

## 12. First-install root of trust — decisione canonica

Per il primo sideload il **Freedom Bootstrap Verifier** ufficiale deve avere root pinned incorporate nel proprio artifact verificabile:

```text
BootstrapTrustAnchor {
    verifier_policy_version
    expected_package_id
    release_signer_set_root_commitment
    android_signing_root_or_lineage_anchor
    minimum_manifest_version
}
```

Il verifier può arrivare da store, pacchetto OS/OEM futuro o altro canale indipendente, ma le root sopra **non sono ridefinite dal QR/peer/mirror che serve l'APK**.

Il QR/descriptor può indicare:

```text
release_id
manifest_hash
source_hints
peer capability
```

ma non può cambiare il signer-set root o Android signer anchor.

Per maggiore assurance il client può mostrare fingerprint verificabili out-of-band, ma il bootstrap minimo non dipende da fidarsi del peer che distribuisce i byte.

## 13. Offline/control-plane degraded

La verifica deve restare resiliente:

- più RPC/control-plane provider;
- manifest/signature verificabili offline;
- ultima SecurityPolicy/ReleaseStatus cacheata;
- signer-set state cacheato e verificato;
- epoch/expiry/freshness bounded;
- provider failover.

Se la revocation view è troppo vecchia rispetto alla policy di freschezza, una release sensibile non viene installata ciecamente: il verifier richiede stato più recente o fallisce esplicitamente.

## 14. SecurityPolicy

```text
SecurityPolicy {
    policy_epoch
    latest_version
    min_supported_version
    min_secure_version
    vulnerable_versions[]
    disabled_features[]
    severity
    reason_hash
    remediation_release
    issued_at
    expires_at?
    signer_set_epoch
    signatures[]
}
```

Critical policy richiede threshold governance production.

## 15. Niente kill-switch commerciale

Una policy di sicurezza disabilita la superficie vulnerabile quando possibile, non spegne commercialmente Freedom.

```text
media vulnerability     -> disable vulnerable media path
transport vulnerability -> disable that transport
messaging vulnerability -> block unsafe mode
identity compromise     -> recovery/update safe mode
```

Recovery/export/update vengono preservati quando tecnicamente sicuri.

## 16. Verified control-plane state

Pubblicare una transazione di release/policy non significa che sia valida.

```text
submit
 -> acceptable finality
 -> execution success
 -> resulting state matches expected manifest/status/signer set
 -> only then display/persist ACTIVE/REVOKED/POLICY CURRENT
```

Un hash di transazione non è prova di successo.

## 17. UX

```text
Freedom Communication 1.4.2
Release signatures  VERIFIED 3/5
APK signer          VERIFIED
Artifact hash       VERIFIED
Release status      ACTIVE
Policy freshness    CURRENT
Source              PEER / RELAY / MIRROR / STORE
```

`Source` indica soltanto da dove sono arrivati i byte.

## 18. Invarianti

- niente APK on-chain;
- niente write per ogni installazione;
- release private keys mai nel client;
- filename/URL/source non sono trust;
- un solo schema `FreedomRelease` canonico;
- release authorization/revocation/security policy production threshold;
- nessun singolo super-admin production;
- exact artifact hash verificato;
- Android signer/lineage verificato separatamente;
- release `REVOKED` bloccata indipendentemente dalla source;
- first sideload usa BootstrapTrustAnchor pinned indipendente dalla source;
- transaction hash != success;
- peer/relay/mirror compromessi possono negare availability ma non produrre una Freedom valida;
- nessuna singola source/IP/store obbligatoria per Freedom Direct.
