# Freedom — App Distribution / Share Freedom

Status: **canonical design draft**.

Normative security rules: [`SECURITY_INVARIANTS.md`](SECURITY_INVARIANTS.md).
Release governance: [`EMERGENCY_UPDATES.md`](EMERGENCY_UPDATES.md).
Control-plane verification: [`CONTROL_PLANE_SECURITY.md`](CONTROL_PLANE_SECURITY.md).
NetworkAnchor bootstrap/rotation: [`NETWORK_ANCHORS.md`](NETWORK_ANCHORS.md).
Canonical schema: [`../spec/freedom.cddl`](../spec/freedom.cddl).

## 1. Principio

```text
source of bytes          != release authority
download capability      != release signature
filename / URL           != authenticity
RPC response             != verified security state
first-install bootstrap  != peer trust
old valid checkpoint     != necessarily current checkpoint
governance signature     != chain consensus
```

## 2. Artifact esterno

```text
Alice genuine Freedom
 -> Share Freedom / Install QR
 -> Bob bootstrap verifier
 -> bytes from peer/relay/mirror/store
 -> verify
 -> Android installer
```

Source dei byte è non fidata.

## 3. Schema canonico

Object shapes per `FreedomInstallDescriptor`, `FreedomRelease`, `ReleaseStatus`, `BootstrapTrustAnchor`, `BootstrapFreshnessFloor` e `NetworkAnchor` devono essere allineati a `spec/freedom.cddl`; quando un oggetto non è ancora presente nel CDDL non è congelato per interoperabilità.

## 4. PeerTransferCapability

Una capability temporanea autorizza download di byte specifici, non release authority. Deve essere TTL/download/bandwidth bounded.

## 5. Verifica pre-install

Verifier MUST:

```text
1. compute exact artifact SHA-256
2. load/verify the applicable BootstrapTrustAnchor for first-install verification
3. resolve canonical FreedomRelease
4. verify signer-set epoch/transition
5. verify threshold release signatures
6. verify the initial canonical NetworkAnchor commitment pinned by BootstrapTrustAnchor
7. instantiate only the exact supported ChainAdapter/verifier profile from that anchor
8. verify ReleaseStatus as independently proved control-plane state
9. reject status/policy/signer/NetworkAnchor rollback
10. verify package/version/size/hash
11. verify Android signer/authorized lineage
12. verify SecurityPolicy as independently proved control-plane state
13. enforce BootstrapFreshnessFloor
14. invoke installer
```

Mismatch -> fail closed.

An RPC URL, peer or mirror cannot choose a different initial NetworkAnchor even if it supplies a syntactically valid or threshold-signed object.

## 6. Fresh-install freeze resistance

Un device nuovo non ha `highest_seen`. Il verifier incorpora un floor minimo di checkpoint/signer/policy e l'exact initial NetworkAnchor commitment del proprio bootstrap profile.

Un attacker che controlla RPC/peer non può far scendere il client sotto quel floor né sostituire l'anchor con uno non pinned.

Ma se **anche il verifier autentico è molto vecchio**, ottenuto soltanto da un canale attacker-controlled, nessun protocollo può dedurre da solo l'esistenza di stato più recente. L'utente deve ottenere il verifier/anchor da almeno un canale indipendente abbastanza recente per l'assurance desiderata.

## 7. First install

`BootstrapTrustAnchor` include package ID, release signer root, Android signing anchor, `accepted_contract_or_controlplane_anchor` e bootstrap freshness floor.

Per il profilo control-plane V1, `accepted_contract_or_controlplane_anchor` è l'exact `NetworkAnchorCommitmentV1` definito in `NETWORK_ANCHORS.md`. Esso impegna network, chain adapter/network, verifier profile/policy, checkpoint, adapter payload, signer-set commitment/epoch, activation e previous-anchor state prima delle signatures.

QR/peer/mirror/RPC non può modificarlo.

La concrete production signature suite del `NETWORK_ANCHOR` signer set deve essere esplicitamente pinned/reviewed prima dell'interoperabilità production; non viene inferita dalla source dei byte.

## 8. Anti-rollback locale

Persistire:

```text
highest_verified_checkpoint
current NetworkAnchorCommitment
highest NetworkAnchor anchor_epoch
highest NETWORK_ANCHOR signer_set_epoch
highest_policy_epoch
highest_release_status_epoch
accepted_contract_lineage
```

Vecchio stato validamente firmato non retrocede stato già osservato.

## 9. Android signing

Barriere indipendenti:

```text
threshold FreedomRelease
+ exact hash
+ Android signer/lineage
+ pinned/rotated NetworkAnchor trust
+ verified ReleaseStatus
+ verified SecurityPolicy
+ bootstrap trust/freshness
```

## 10. Decentralized Release Network

Sorgenti:

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

Content addressing usa exact artifact hash e canonical release manifest hash.

## 11. Offline / degraded

Cache verificata può essere usata entro policy. Release security-sensitive con state troppo stale fallisce esplicitamente o richiede refresh.

Un already-trusted NetworkAnchor/checkpoint può sostenere la verifica offline entro la freshness policy; una nuova anchor rotation non viene accettata soltanto perché una RPC non è raggiungibile o perché un mirror propone un replacement.

## 12. Threats

Resistere a:

- modified bytes;
- old release replay;
- fresh-install old-checkpoint freeze rispetto al floor;
- initial NetworkAnchor substitution;
- NetworkAnchor rollback o signer-set jump;
- threshold-valid NetworkAnchor senza valid chain consensus continuity;
- signer/policy/status rollback;
- malicious/stale/forked RPC;
- source poisoning;
- first-install malicious peer;
- revocation withholding;
- silent contract/control-plane substitution.

## 13. Invarianti

- APK off-chain;
- no per-install write;
- source/filename/locator not trust;
- release private key never client-side;
- canonical schema source in `spec/`;
- threshold governance;
- governance does not replace chain consensus;
- fresh-install floor;
- exact independently pinned initial NetworkAnchor;
- Android signer separately verified;
- tx hash != success;
- fail closed.
