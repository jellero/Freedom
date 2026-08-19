# Freedom — Emergency Bulletins & Secure Updates

Status: **canonical design draft**

Normative security rules: [`SECURITY_INVARIANTS.md`](SECURITY_INVARIANTS.md).
Control-plane governance: [`CONTROL_PLANE_SECURITY.md`](CONTROL_PLANE_SECURITY.md).
Distribution details: [`APP_DISTRIBUTION.md`](APP_DISTRIBUTION.md).

## 1. Obiettivo

Freedom distribuisce avvisi, policy e release verificabili senza una singola source obbligatoria.

Il control-plane conserva manifest, locator, signer-set state, revoche e policy piccoli/verificabili. APK/artifact pesanti restano off-chain.

```text
source of bytes != release authority
filename/URL    != authenticity
RPC response    != verified state
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

Matching geografico locale; posizione utente non scritta on-chain per il solo matching.

## 3. Wake/discovery

```text
app open / periodic check
peer gossip / Freedom transport
optional platform push hint
```

Push è solo hint; bulletin/policy sono verificati crittograficamente.

## 4. FreedomRelease canonico

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

Un solo schema normativo.

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

Filename/nonce sono locator/anti-enumeration hint, non trust.

## 6. ReleaseStatus

```text
ReleaseStatus {
    release_id
    artifact_sha256
    status
    status_epoch
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

## 7. SignerSet

```text
SignerSet {
    signer_set_epoch
    role
    public_keys[]
    threshold
    valid_from_height
    expires_after_height?
    previous_set_commitment?
    governance_recovery_set_commitment?
}
```

Ruoli separati:

```text
RELEASE_AUTHORIZATION
RELEASE_REVOCATION
CRITICAL_SECURITY_POLICY
CONTRACT_UPGRADE
GOVERNANCE_ROOT_ROTATION
EMERGENCY_ADVISORY
```

Production minima:

```text
ReleaseAuthorization   >= 3-of-5
ReleaseRevocation      >= 3-of-5
CriticalSecurityPolicy >= 3-of-5
ContractUpgrade        >= 3-of-5 + timelock
GovernanceRootRotation >= 3-of-5 + recovery procedure
```

## 8. SignerSetTransition

Un nuovo signer set non è valido perché auto-firmato.

```text
SignerSetTransition {
    role
    previous_epoch
    next_epoch
    previous_set_commitment
    next_set_commitment
    activation_height
    previous_set_threshold_signatures[]
    next_set_acceptance_signatures[]
}
```

Regole:

```text
next_epoch = previous_epoch + 1
previous set authorizes
next set accepts
activation height monotonic
highest-seen epoch persisted
old set cannot reactivate itself
```

## 9. Quorum-loss recovery

Recovery governance deve essere pinned prima dell'incidente:

```text
GovernanceRecoveryManifest {
    role
    failed_signer_set_epoch
    recovery_set_commitment
    next_set_commitment
    activation_height
    recovery_timelock
    recovery_threshold_signatures[]
}
```

Richiede threshold/timelock più forte e non equivale a una singola emergency key.

## 10. Anti-rollback

Verifier conserva almeno:

```text
highest_signer_set_epoch
highest_policy_epoch
highest_release_status_epoch
highest_verified_checkpoint
accepted_contract_lineage
```

Una policy/status/signer set validamente firmata ma più vecchia non sovrascrive stato più recente già osservato.

## 11. Contract upgrade governance

L'upgrade del security/control-plane core fa parte di “nessun super-admin”.

```text
ContractUpgradeManifest {
    governance_epoch
    current_code_hash
    new_code_hash
    migration_hash
    activation_height
    rollback_floor
    signatures[]
}
```

Requisiti:

- threshold >= CriticalSecurityPolicy;
- timelock non-emergency;
- code hash verificabile;
- migration versionata;
- client accepted lineage;
- no silent contract-address swap;
- emergency key non installa codice arbitrario da sola.

Una singola Full Access key production capace di cambiare il contratto viola il modello.

## 12. Decentralized Release Network

```text
STORE
PEER_LOCAL
PEER_NETWORK
COMMUNITY / UPDATE RELAY
MANAGED UPDATE NODE
PRIVATE / HTTPS MIRROR
future transport
```

```text
artifact key = SHA-256(APK bytes)
manifest key = SHA-256(canonical FreedomRelease)
```

Qualunque source può servire byte; nessuna source decide validità.

## 13. Verifica pre-install

```text
candidate bytes
 -> exact SHA-256
 -> canonical FreedomRelease
 -> verified signer-set transition/epoch
 -> threshold release signatures
 -> ReleaseStatus proof + anti-rollback
 -> package_id/version/size/hash
 -> Android signer/lineage
 -> SecurityPolicy proof + freshness/anti-rollback
 -> INSTALL
```

Mismatch -> fail closed.

## 14. Control-plane proof requirement

Per `SignerSet`, `ReleaseStatus` e `SecurityPolicy`, una risposta RPC non provata non basta.

```text
VerifiedControlPlaneCheckpoint
+ inclusion/non-inclusion proof
+ canonical object
```

sono richiesti per lo stato production security-sensitive.

## 15. Share Freedom

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

Capability riguarda il trasferimento, non la release authority.

## 16. Android signing

Barriere indipendenti:

```text
Freedom threshold release signatures
+ exact artifact hash
+ Android package signer / authorized lineage
+ ReleaseStatus
+ SecurityPolicy
```

## 17. First-install trust

```text
BootstrapTrustAnchor {
    verifier_policy_version
    expected_package_id
    release_signer_set_root_commitment
    governance_recovery_set_commitment?
    android_signing_root_or_lineage_anchor
    minimum_manifest_version
    accepted_contract_or_controlplane_anchor
}
```

Peer/QR/mirror non può ridefinire queste root.

## 18. Offline/control-plane degraded

Cache verificata conserva object + checkpoint + highest-seen epochs.

Se freshness/revocation è troppo stale per la policy corrente, install/update sensibile fallisce esplicitamente o richiede stato più recente.

Wall clock locale non è authority esclusiva: usare `VerifiedTimeAnchor`, height/epoch e monotonic time.

## 19. SecurityPolicy

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

Critical policy richiede threshold governance.

## 20. Niente kill-switch commerciale

Disabilitare superficie vulnerabile quando possibile, preservando recovery/export/update se tecnicamente sicuri.

Emergency authority ha scope limitato/TTL e non può creare una nuova release arbitraria.

## 21. Verified control-plane mutation

```text
submit
 -> finality proof
 -> execution success
 -> resulting state proof
 -> exact expected transition
 -> ACTIVE/REVOKED/CURRENT
```

Transaction hash != success.

## 22. UX

```text
Freedom Communication 1.4.2
Release signatures  VERIFIED 3/5
Signer set epoch     VERIFIED
APK signer           VERIFIED
Artifact hash        VERIFIED
Release status       ACTIVE
Policy freshness     CURRENT
Source               PEER / RELAY / MIRROR / STORE
```

## 23. Invarianti

- APK off-chain;
- no write per install;
- release keys mai nel client;
- source/filename/URL non sono trust;
- one canonical FreedomRelease schema;
- signer-set transitions monotonic/cross-authorized;
- old signer set/policy/status non può rollback highest-seen state;
- quorum recovery pinned/threshold/timelocked;
- contract upgrade è threshold-governed o security core immutable;
- first install usa pinned trust anchors;
- security-sensitive control-plane objects richiedono verified state proof;
- transaction hash != success.
