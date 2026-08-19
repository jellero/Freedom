# Freedom — Emergency Bulletins & Secure Updates

## 1. Obiettivo

Freedom deve poter distribuire avvisi di emergenza, policy di sicurezza e release verificabili senza introdurre un server centrale obbligatorio o una singola sorgente di download.

La blockchain/control-plane conserva **manifest, locator, revoche e policy piccoli, firmati e verificabili**. File pesanti come APK non vengono memorizzati on-chain.

La distribuzione dell'app separa sempre:

```text
chi fornisce i byte      != chi autorizza la release
artifact transport       != release authenticity
filename / URL           != proof of authenticity
```

Dettagli: [`APP_DISTRIBUTION.md`](APP_DISTRIBUTION.md).

![Freedom Release Network](assets/freedom-release-network.svg)

## 2. Emergency Bulletin

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
    issuer_set
    signatures[]
}
```

Categorie possibili:

- security advisory;
- network interference advisory;
- transport/relay migration;
- regional connectivity emergency;
- critical protocol incompatibility;
- recovery instruction.

## 3. Geolocalizzazione privacy-preserving

Il matching geografico avviene localmente. La posizione utente non deve essere scritta on-chain per il solo matching di un bulletin.

```text
bulletin scope -> region/cells
local location -> local only
match          -> show notification
```

## 4. Wake/discovery

La blockchain non sveglia direttamente un'app sospesa. Discovery ridondante:

```text
app open / periodic check
peer gossip / Freedom transport
optional platform push hint
```

Il push è solo hint; bulletin e policy vengono sempre verificati crittograficamente.

## 5. FreedomRelease

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
    signatures[]
}
```

La release authority firma il manifest canonico, non il filename.

## 6. FreedomReleaseLocator

Un locator opaco rende possibile distribuire release senza URL permanenti:

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
    signatures[]
}
```

`release_nonce` è random ad alta entropia. Le firme dimostrano che il locator appartiene a una release autorizzata.

Il file può essere chiamato, per esempio:

```text
freedom-r42-454fjk4hfhsjhslllshlvhvru0ujwr8w.apk
```

ma **il filename è soltanto un locator leggibile/anti-enumeration hint**. Un attacker può copiare o rinominare un file; la sicurezza deriva dalla verifica delle firme e del contenuto.

La chiave privata di release non deve essere presente nei client installati.

## 7. ReleaseStatus / revocation

Il control-plane mantiene lo stato globale della release senza registrare ogni installazione:

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
    signatures[]
}
```

Stati:

```text
ACTIVE
DEPRECATED
REVOKED
```

Una release non viene revocata perché qualcuno l'ha installata. `REVOKED` è una decisione di sicurezza firmata per una release compromessa/vulnerabile/non più autorizzata.

Questo evita:

- write on-chain per installazione;
- leakage del numero/timing degli install;
- il paradosso in cui il primo install rende inutilizzabile la release per gli altri.

## 8. Decentralized Release Network

L'APK può arrivare da:

```text
STORE
PEER_LOCAL
PEER_NETWORK
COMMUNITY / UPDATE RELAY
MANAGED UPDATE NODE
PRIVATE / HTTPS MIRROR
future transport
```

La rete può usare content addressing:

```text
artifact key = SHA-256(APK bytes)
manifest key = SHA-256(canonical FreedomRelease)
```

Qualunque nodo può servire i byte. Nessun nodo di distribuzione decide se i byte sono validi.

## 9. Verifica pre-install

Prima di invocare l'installer Android:

```text
candidate artifact
 -> SHA-256 exact match
 -> FreedomRelease signatures valid
 -> FreedomReleaseLocator valid when used
 -> ReleaseStatus != REVOKED
 -> package_id exact
 -> version/anti-downgrade valid
 -> APK signing cert / authorized lineage valid
 -> SecurityPolicy / min_secure_version valid
 -> INSTALL
```

Mismatch -> fail closed.

## 10. Share Freedom

Un client già genuino può creare una capability di trasferimento locale temporanea, **non una nuova release**:

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

Il QR contiene/risolve il descriptor e la capability di source. Il client non usa e non possiede la private release key.

La capability può essere one-shot o TTL ma il suo consumo non cambia `ReleaseStatus` globale.

## 11. Android signing

La firma APK Android costituisce una barriera distinta dalla governance Freedom.

Per update di un'app già installata, il sistema verifica la continuità del signer/lineage del package. Freedom aggiunge:

```text
signed FreedomRelease
+ exact artifact hash
+ Android signer/lineage
+ ReleaseStatus
+ SecurityPolicy
```

Per supportare rotazioni autorizzate, usare la signing lineage prevista dalla piattaforma e documentare la policy di rotazione.

## 12. First-install authenticity

Il primo sideload richiede una root of trust indipendente dalla sorgente che distribuisce l'APK.

Possibili anchor:

```text
pinned release root in bootstrap verifier
verified store
release-root fingerprint verified out-of-band
multiple independent channels confirming same root
trusted existing Freedom client + independent verifier
```

Un fake peer non deve poter fornire sia i byte sia la definizione arbitraria di quale chiave sia "Freedom".

## 13. Offline/control-plane degraded

Per mantenere distribuzione e update resilienti:

- più RPC/control-plane provider;
- manifest firmati verificabili offline;
- ultima SecurityPolicy/ReleaseStatus cacheata;
- epoch/expiry/freshness bounded;
- failover provider.

Se una revocation view è troppo vecchia rispetto alla policy di freschezza, il client può richiedere un check più recente prima di installare una release sensibile.

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
    signatures[]
}
```

## 15. Niente kill-switch arbitrario

La policy deve disabilitare la superficie vulnerabile quando possibile, non spegnere commercialmente l'app.

```text
media vulnerability     -> disable vulnerable media path
transport vulnerability -> disable that transport
messaging vulnerability -> block unsafe mode
identity compromise     -> recovery/update safe mode
```

Preservare recovery/export/update quando tecnicamente sicuri.

## 16. Threshold governance

Release manifest, locator root, revoche e policy production dovrebbero preferire governance multi-key/threshold, per esempio `2-of-3` o `3-of-5`, invece di una sola chiave online.

Le private key di release devono restare fuori dai client e, idealmente, fuori dall'infrastruttura di distribuzione pubblica.

## 17. UX

```text
Freedom Communication 1.4.2
Release signer  VERIFIED
APK signer      VERIFIED
Artifact hash   VERIFIED
Release status  ACTIVE
Policy freshness CURRENT
Source          PEER / RELAY / MIRROR / STORE
```

Source indica soltanto da dove sono arrivati i byte.

## 18. Invarianti

- niente APK on-chain;
- niente write on-chain per ogni installazione;
- chiave privata release mai nel client;
- filename/URL non sono prove di autenticità;
- locator/manifest/revoca/policy firmati;
- artifact content-addressed e verificato;
- Android signer/lineage verificato separatamente;
- release `REVOKED` bloccata indipendentemente dalla sorgente;
- capability one-shot riguarda il trasferimento, non la validità globale della release;
- peer/relay/mirror compromessi possono causare availability failure ma non devono poter produrre una Freedom valida;
- first install richiede una root of trust indipendente;
- nessuna singola sorgente/IP/store obbligatoria per Freedom Direct.
