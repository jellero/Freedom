# Freedom — App Distribution / Share Freedom

Status: **canonical design draft**

Normative security rules: [`SECURITY_INVARIANTS.md`](SECURITY_INVARIANTS.md).
Release/security governance: [`EMERGENCY_UPDATES.md`](EMERGENCY_UPDATES.md).
Control-plane verification: [`CONTROL_PLANE_SECURITY.md`](CONTROL_PLANE_SECURITY.md).

## 1. Principio

```text
source of bytes          != release authority
download capability      != release signature
filename / URL           != authenticity
RPC response             != verified security state
first-install bootstrap  != peer trust
```

L'obiettivo è impedire a un verifier conforme di confondere byte falsi/stale con una release Freedom autorizzata corrente.

## 2. Artifact esterno

Il client non incorpora di default un secondo APK completo.

```text
Alice genuine Freedom
 -> Share Freedom / Install QR
 -> Bob bootstrap verifier
 -> artifact from peer/relay/mirror/store
 -> verify
 -> Android installer
```

## 3. Filename / locator

```text
freedom-r42-<opaque-locator>.apk
```

Locator/filename può aiutare discovery ma non è trust.

## 4. PeerTransferCapability

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

Capability autorizza trasferimento, non release.

## 5. Install descriptor

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

Non può ridefinire signer root, Android signer anchor o control-plane anchor.

## 6. Decentralized Release Network

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

```text
artifact key = SHA-256(APK bytes)
manifest key = SHA-256(canonical FreedomRelease)
```

## 7. FreedomRelease canonico

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

Un solo schema.

## 8. ReleaseStatus

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

`ACTIVE / DEPRECATED / REVOKED`.

Nessuna write per installazione.

## 9. Verifica pre-install

Verifier **MUST**:

```text
1. compute exact artifact SHA-256
2. obtain canonical FreedomRelease
3. verify current signer-set transition/epoch
4. verify threshold release signatures
5. obtain ReleaseStatus as verified control-plane state proof
6. reject rollback below highest-seen status/policy/signer epochs
7. verify ReleaseStatus != REVOKED
8. verify package_id/version/size/hash
9. verify Android signer / authorized lineage
10. obtain SecurityPolicy as verified state proof
11. verify current-enough policy + anti-downgrade
12. verify BootstrapTrustAnchor on first sideload
13. invoke installer
```

Mismatch -> fail closed.

## 10. Control-plane proof

`ReleaseStatus`, `SecurityPolicy`, `SignerSet` e transition non diventano `VERIFIED` perché restituiti da un RPC.

```text
VerifiedControlPlaneCheckpoint
+ inclusion/non-inclusion proof
+ canonical object
```

sono richiesti nel modello production.

## 11. Anti-rollback local state

Verifier conserva:

```text
highest_verified_checkpoint
highest_signer_set_epoch
highest_policy_epoch
highest_release_status_epoch
accepted_contract_lineage
```

Una release/policy/status vecchia ma validamente firmata non può retrocedere stato già osservato.

## 12. Android signing

Barriere indipendenti:

```text
threshold FreedomRelease
+ exact hash
+ Android signer/lineage
+ verified ReleaseStatus
+ verified SecurityPolicy
```

## 13. First install

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

Source/QR/mirror non può modificarlo.

Un device vergine non può derivare la vera root se tutto gli arriva da un singolo attacker: l'anchor indipendente è inevitabile.

## 14. Offline / degraded

Cache verificata conserva signer/status/policy + checkpoint/epoch.

Se freshness è troppo vecchia per una release sensibile, verifier richiede stato più recente o fallisce esplicitamente.

Wall clock locale non è unico time authority; usare verified height/time anchor.

## 15. Peer-local transfer

Endpoint temporaneo, read-only, capability-protected, bounded per tempo/download/banda.

Source compromise causa availability failure, non valid release creation.

## 16. Verified mutation

Pubblicare manifest/status/policy/transition:

```text
submit
 -> finality proof
 -> execution success
 -> resulting state proof
 -> exact expected hash/epoch
 -> accepted
```

Tx hash != success.

## 17. Threats

Resistere a:

- modified bytes;
- old release replay;
- signer-set rollback;
- policy/status rollback;
- locator copying;
- source poisoning;
- single signer compromise;
- malicious/stale/forked RPC;
- malicious first-install peer;
- revocation withholding;
- silent contract/control-plane anchor substitution.

## 18. Invarianti

- APK off-chain;
- source/filename/locator non è trust;
- release private key mai nel client;
- canonical release schema;
- threshold release/revocation governance;
- anti-rollback signer/policy/status state;
- control-plane security objects proof-verified;
- first install con pinned independent anchor;
- Android signer separate verification;
- exact hash;
- no per-install chain write;
- tx hash != success;
- fail closed.
