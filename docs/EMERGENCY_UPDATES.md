# Freedom — Emergency Bulletins & Secure Updates

Status: **canonical design draft**.

Normative security rules: [`SECURITY_INVARIANTS.md`](SECURITY_INVARIANTS.md).
Control-plane governance: [`CONTROL_PLANE_SECURITY.md`](CONTROL_PLANE_SECURITY.md).
NetworkAnchor bootstrap/rotation: [`NETWORK_ANCHORS.md`](NETWORK_ANCHORS.md).
Distribution: [`APP_DISTRIBUTION.md`](APP_DISTRIBUTION.md).
Canonical schema: [`../spec/freedom.cddl`](../spec/freedom.cddl).

## 1. Principio

```text
source of bytes       != release authority
filename/URL          != authenticity
RPC response          != verified state
old valid state       != necessarily current state
governance quorum     != chain consensus
```

APK/artifact restano off-chain.

![Freedom Release Network](assets/freedom-release-network.svg)

## 2. Schema

`FreedomRelease`, `ReleaseStatus`, `SecurityPolicy`, `SignerSetTransition`, `NetworkAnchor`, `BootstrapTrustAnchor` e `BootstrapFreshnessFloor` usano i field name canonici di `spec/freedom.cddl`.

I Markdown non sono una seconda source of truth per i field name.

## 3. Release verification

```text
candidate bytes
 -> exact SHA-256
 -> canonical FreedomRelease
 -> signer-set epoch/transition verified
 -> threshold release signatures
 -> pinned/verified NetworkAnchor
 -> ReleaseStatus proof + anti-rollback
 -> SecurityPolicy proof + anti-rollback
 -> package/version/size/hash
 -> Android signer/authorized lineage
 -> bootstrap freshness floor
 -> INSTALL
```

Mismatch -> fail closed.

## 4. BootstrapFreshnessFloor

Ogni verifier/release recente incorpora almeno:

```text
minimum_checkpoint_height
minimum_checkpoint_hash?
minimum_signer_set_epoch
minimum_policy_epoch
issued_in_release_id
```

Un fresh install rifiuta control-plane state sotto il floor della propria release/verifier.

Questo impedisce freeze su stato vecchio rispetto al verifier corrente.

Limite esplicito: se il verifier stesso è una copia autentica ma molto obsoleta ottenuta soltanto tramite canali attacker-controlled, non può sapere dal nulla che esista una release/floor più recente. First-install freshness richiede un canale/bootstrap anchor indipendente per il verifier stesso.

## 5. BootstrapTrustAnchor / initial NetworkAnchor

`BootstrapTrustAnchor` contiene almeno:

```text
expected_package_id
release_signer_set_root_commitment
governance_recovery_set_commitment?
android_signing_root_or_lineage_anchor
minimum_manifest_version
accepted_contract_or_controlplane_anchor
bootstrap_freshness_floor
```

Per il profilo control-plane V1:

```text
accepted_contract_or_controlplane_anchor
    ==
NetworkAnchorCommitmentV1(initial canonical NetworkAnchor)
```

Peer/QR/mirror/RPC non può ridefinire queste root/floor né scegliere un anchor alternativo.

Il commitment iniziale viene verificato prima che il payload adapter-specific possa inizializzare il consensus verifier.

## 6. Signer-set governance

Production minima:

```text
ReleaseAuthorization       >= 3-of-5
ReleaseRevocation          >= 3-of-5
CriticalSecurityPolicy     >= 3-of-5
NetworkAnchorAuthorization >= 3-of-5
ContractUpgrade            >= 3-of-5 + timelock
GovernanceRootRotation     >= 3-of-5 + recovery
```

Signer-set transition è cross-authorized e monotonic; old set non può riattivarsi.

Il ruolo `NETWORK_ANCHOR` è scoped. Può autorizzare un candidate anchor package, ma non può:

- autorizzare una release;
- abbassare `BootstrapFreshnessFloor`;
- cambiare `SecurityPolicy`;
- cambiare chain/profile tramite ordinary anchor rotation;
- sovrascrivere consenso/finality della chain;
- chiamare arbitrario state replacement “migration”.

Dopo bootstrap, una valid threshold authorization del NetworkAnchor **non basta**: serve anche consensus continuity dalla state già trusted.

## 7. Quorum trust assumption

`3-of-5` significa che nessuna **singola chiave** basta. Non dimostra automaticamente indipendenza organizzativa.

Production deve separare custody/operator domains per quanto praticabile:

- signer hardware/offline distinti;
- account/secret-manager differenti;
- nessuna singola credential in grado di estrarre un quorum;
- public transition/transparency records;
- periodic custody audit.

Se un singolo soggetto controlla unilateralmente tre signer, il sistema conserva threshold crittografico ma non può descriverlo come “nessun singolo attore amministrativo”.

Per `NETWORK_ANCHOR`, anche una collusione del Freedom quorum non deve poter fabbricare ordinary same-chain history senza una chain-consensus continuity proof valida.

## 8. Quorum-loss recovery

Recovery governance è pinned prima dell'incidente, threshold/timelocked e distinta dalle emergency advisory keys.

Una singola emergency key non può installare codice, autorizzare nuove release arbitrarie, sostituire il NetworkAnchor trust root o disabilitare consensus verification.

Quorum-loss recovery di un `NETWORK_ANCHOR` signer set segue la canonical governance recovery path; non crea un bypass al requisito di chain continuity.

## 9. Anti-rollback

Persistire almeno:

```text
highest_verified_checkpoint
current NetworkAnchorCommitment
highest NetworkAnchor anchor_epoch
highest NETWORK_ANCHOR signer_set_epoch
highest_policy_epoch
highest_release_status_epoch
accepted_contract_lineage
```

Oggetti validamente firmati ma inferiori al floor/highest-seen rilevante sono rifiutati.

## 10. Contract upgrade

Immutable security core oppure threshold-governed upgrade con code hash, migration hash, timelock, activation height, accepted lineage e rollback floor.

No silent contract swap.

## 11. Chain migration

`ChainMigrationManifest` da solo non basta. Serve `StateMigrationProof` che renda verificabile la derivazione del target imported root dal source finalized state secondo una migration rule/code hash deterministico.

Un ordinary `NetworkAnchor` non può essere usato come scorciatoia per cambiare chain adapter/network o verifier semantics.

## 12. Share Freedom

`PeerTransferCapability` autorizza solo il trasferimento dei byte di una release già identificata, non release authority né NetworkAnchor authority.

Source può negare availability ma non creare una release o un anchor valido.

## 13. Android signing

Barriere indipendenti:

```text
Freedom threshold signatures
+ exact artifact hash
+ Android signer/lineage
+ exact initial / monotonic rotated NetworkAnchor
+ verified ReleaseStatus
+ verified SecurityPolicy
+ bootstrap freshness/anchor
```

## 14. Offline/degraded

Cache verificata può sostenere funzionamento degradato entro freshness policy. Se una release security-sensitive richiede state più recente, fail explicit invece di installare ciecamente.

Offline/unavailable RPC non autorizza un replacement NetworkAnchor e non trasforma stale state in current state.

## 15. UX

```text
Freedom Communication 1.4.2
Release signatures  VERIFIED 3/5
Signer set epoch     VERIFIED
Artifact hash        VERIFIED
APK signer           VERIFIED
Network anchor       VERIFIED
Release status       ACTIVE
Policy freshness     CURRENT
Checkpoint floor     SATISFIED
Source               PEER / RELAY / MIRROR / STORE
```

Label solo se derivate da verifiche implementate.

## 16. Invarianti

- APK off-chain;
- no write per install;
- source/filename non trust;
- canonical schema in CDDL;
- threshold governance + explicit quorum trust assumption;
- NetworkAnchor signer authority è scoped e non sostituisce chain consensus;
- exact independently pinned initial NetworkAnchor;
- signer/policy/status/anchor anti-rollback;
- fresh install bootstrap floor;
- verifier-staleness limit dichiarato;
- Android signer separate verification;
- exact hash;
- security-sensitive state proof-verified;
- tx hash != success.
