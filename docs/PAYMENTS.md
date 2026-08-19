# Freedom — Payments

Status: **canonical design draft**.

Normative security: [`SECURITY_INVARIANTS.md`](SECURITY_INVARIANTS.md).
Control-plane: [`CONTROL_PLANE_SECURITY.md`](CONTROL_PLANE_SECURITY.md).
Canonical schema: [`../spec/freedom.cddl`](../spec/freedom.cddl).

## 1. Principio

Payment abilita servizi/entitlement senza diventare identity trust anchor.

```text
PaymentAdapter
|- PayPal
|- native crypto
|- stablecoin
`- future providers
```

Nessun provider è requisito permanente.

## 2. Ruoli

```text
payment provider -> economic proof
payment attestor -> provider verification
voucher issuer   -> one-time entitlement capability
control-plane    -> redemption/final entitlement state
client           -> UX + proof verification
```

Payment attestor non firma identity/release/SecurityPolicy.

## 3. Canonical objects

Field name/object shape per:

```text
purchase-intent
payment-descriptor
payment-attestation
entitlement-voucher
entitlement-redemption
freedom-entitlement
```

sono definiti soltanto in `spec/freedom.cddl`.

I Markdown non mantengono una seconda struct incompatibile.

## 4. Privacy boundary

Non riutilizzare RootIdentity, DeviceRecordCommitment o PairwiseContactAlias come merchant/payment reference.

Domain separation riduce riuso diretto ma non garantisce unlinkability se economic proof e entitlement vengono linkati nella stessa public flow.

## 5. Provider flow

```text
App -> PurchaseIntent / PaymentDescriptor
App -> hosted provider flow
User -> pays
Private Payment Worker -> provider verification outbound
Worker -> PaymentAttestation
Attestation -> one-time EntitlementVoucher
Client -> EntitlementRedemption
Control-plane -> verified entitlement transition
```

Merchant secrets never ship in APK.

## 6. Callback != proof

Checkout callback/client success è UX hint e può soltanto portare a `PAYMENT_PENDING`.

Entitlement attivo richiede proof economica verificata + voucher redemption + verified resulting state.

## 7. Voucher/nullifier

Voucher è one-time e non contiene identity/network/social identifiers Freedom in plaintext.

Redemption usa nullifier anti-double-spend e non deve rivelare provider transaction ID.

Blind credential/anonymous credential standard può essere usata per unlinkability più forte dopo review.

Timing correlation può restare e non viene negata.

## 8. Verified finality

```text
submit/observe
 -> finality proof
 -> execution success
 -> resulting-state proof
 -> expected entitlement transition
 -> PAID/ACTIVE
```

Tx hash/attestation publication non equivale a entitlement success.

## 9. Worker key security

Payment worker keys sono scoped, rotabili/revocabili e non possiedono release, governance, identity o conversation authority.

## 10. Test gates

- forged callback;
- duplicate provider transaction;
- attestation replay;
- voucher double-spend;
- expired voucher;
- malicious/stale RPC around redemption;
- failed/partial transaction;
- state mismatch;
- worker-key rotation/revocation;
- payment/redemption timing-correlation analysis.
