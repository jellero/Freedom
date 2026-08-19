# Freedom — App Distribution / Share Freedom

Status: **canonical design draft**.

Normative security rules: [`SECURITY_INVARIANTS.md`](SECURITY_INVARIANTS.md).
Release governance: [`EMERGENCY_UPDATES.md`](EMERGENCY_UPDATES.md).
Control-plane verification: [`CONTROL_PLANE_SECURITY.md`](CONTROL_PLANE_SECURITY.md).
Canonical schema: [`../spec/freedom.cddl`](../spec/freedom.cddl).

## 1. Principio

```text
source of bytes          != release authority
download capability      != release signature
filename / URL           != authenticity
RPC response             != verified security state
first-install bootstrap  != peer trust
old valid checkpoint     != necessarily current checkpoint
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

Object shapes per `FreedomInstallDescriptor`, `FreedomRelease`, `ReleaseStatus`, `BootstrapTrustAnchor` e `BootstrapFreshnessFloor` devono essere allineati a `spec/freedom.cddl`; quando un oggetto non è ancora presente nel CDDL non è congelato per interoperabilità.

## 4. PeerTransferCapability

Una capability temporanea autorizza download di byte specifici, non release authority. Deve essere TTL/download/bandwidth bounded.

## 5. Verifica pre-install

Verifier MUST:

```text
1. compute exact artifact SHA-256
2. resolve canonical FreedomRelease
3. verify signer-set epoch/transition
4. verify threshold release signatures
5. verify ReleaseStatus as control-plane proof
6. reject status/policy/signer rollback
7. verify package/version/size/hash
8. verify Android signer/authorized lineage
9. verify SecurityPolicy as control-plane proof
10. enforce BootstrapFreshnessFloor
11. verify BootstrapTrustAnchor on first sideload
12. invoke installer
```

Mismatch -> fail closed.

## 6. Fresh-install freeze resistance

Un device nuovo non ha `highest_seen`. Il verifier incorpora un floor minimo di checkpoint/signer/policy.

Un attacker che controlla RPC/peer non può far scendere il client sotto quel floor.

Ma se **anche il verifier autentico è molto vecchio**, ottenuto soltanto da un canale attacker-controlled, nessun protocollo può dedurre da solo l'esistenza di stato più recente. L'utente deve ottenere il verifier/anchor da almeno un canale indipendente abbastanza recente per l'assurance desiderata.

## 7. First install

`BootstrapTrustAnchor` include package ID, release signer root, Android signing anchor, accepted control-plane anchor e bootstrap freshness floor.

QR/peer/mirror non può modificarli.

## 8. Anti-rollback locale

Persistire:

```text
highest_verified_checkpoint
highest_signer_set_epoch
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

## 12. Threats

Resistere a:

- modified bytes;
- old release replay;
- fresh-install old-checkpoint freeze rispetto al floor;
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
- fresh-install floor;
- independent first-install anchor;
- Android signer separately verified;
- tx hash != success;
- fail closed.
