# Freedom — Store Compliance Architecture

Status: **canonical design draft**.

Normative security: [`SECURITY_INVARIANTS.md`](SECURITY_INVARIANTS.md).
Revocation: [`REVOCATION.md`](REVOCATION.md).
Canonical schema: [`../spec/freedom.cddl`](../spec/freedom.cddl).

## 1. Separazione

Store/platform policy non ridefinisce Freedom Protocol e non introduce mailbox, master key o central delivery server.

## 2. Identity privacy

```text
RootRecoveryKey
RootIdentity
DeviceAuthorizationDelegation
DeviceCertificate
DeviceKey
DeviceRecordCommitment
DeviceControlKey
PairwiseContactAlias
TransportToken
```

No global DeviceID network-facing. Contact/device commercial quotas V1 non giustificano public social/device graph.

## 3. Contact QR / Install QR

Contact descriptor e install descriptor sono oggetti distinti.

Contact bootstrap viene autenticato tramite expected-contact + delegation/certificate + DeviceKey possession + current-enough revocation proof.

Install descriptor non può ridefinire release signer root, Android signer anchor, control-plane anchor o bootstrap freshness floor.

## 4. First-contact assurance

UI può distinguere `BOOTSTRAP_UNVERIFIED` / `CONTACT_VERIFIED` e offrire safety code/fingerprint/out-of-band verification.

## 5. Account/recovery/deletion

Client può revocare device, eliminare key/dati locali e richiedere cancellazione di service-side data opzionali dove applicabile.

Root-compromise recovery viene presentato soltanto se esiste una independent `UserRecoveryPolicy` precommitted; normal device loss non viene confuso con root compromise.

## 6. Device relay

Opt-in, resource-bounded, no mailbox/plaintext/session keys, no implicit Internet egress.

## 7. Gateway

Gateway usa platform VPN/tunnel APIs quando consentito e mantiene trust boundary separata da Communication E2EE.

Store build rispetta disclosure/consenso/policy vigenti al momento della release.

## 8. Play / Direct separation

Play può usare store update path. Direct usa verified artifact distribution.

Nessun silent install/bypass store nella build che deve rispettare lo store.

## 9. Direct install verification

```text
exact artifact hash
threshold FreedomRelease
verified ReleaseStatus/SecurityPolicy
Android signer lineage
BootstrapTrustAnchor
BootstrapFreshnessFloor
```

Fresh install rifiuta state sotto il floor del verifier corrente.

Un verifier autentico ma obsoleto ottenuto solo da canali attacker-controlled non può dedurre magicamente che esista stato più recente; la freshness del verifier stesso richiede independent bootstrap assurance.

## 10. Governance

Threshold governance elimina la singola key unilaterale. Quorum-collusion/operator-custody è trust assumption esplicita e deve essere separata/auditata per quanto praticabile.

## 11. Store review mode

Procedura riproducibile per onboarding, Contact QR, revocation-aware expected-contact session, no offline queue, block/report, relay mode, Share Freedom verification e Gateway controls se inclusi.

## 12. Invarianti

- store non è protocol trust anchor;
- store compliance non introduce mailbox;
- `DEVICE_RELAY != INTERNET_EGRESS`;
- Gateway != Communication E2EE;
- Direct/Play separation possibile;
- first sideload trust + freshness indipendenti dalla byte source;
- transaction hash != security-state success.
