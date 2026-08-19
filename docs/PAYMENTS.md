# Freedom — Payments

Status: **canonical design draft**

Normative security rules: [`SECURITY_INVARIANTS.md`](SECURITY_INVARIANTS.md).
Control-plane rules: [`CONTROL_PLANE_SECURITY.md`](CONTROL_PLANE_SECURITY.md).

## 1. Principio

Il pagamento abilita servizi/entitlement Freedom senza diventare identity trust anchor.

```text
PaymentAdapter
|- PayPal
|- native crypto
|- stablecoin
`- future providers
```

Nessun singolo provider è requisito permanente.

## 2. Separazione dei ruoli

```text
payment provider   -> prova economica
payment attestor   -> verifica provider
voucher issuer     -> one-time entitlement capability
control-plane      -> redemption/final entitlement state
client             -> UX + proof/state verification
```

Payment attestor non può firmare release, SecurityPolicy o device authorization.

## 3. PaymentBindingCommitment

Non riutilizzare `root_commitment`, DeviceRecordCommitment o pairwise alias come payment reference.

```text
PaymentBindingCommitment = H(payment context, random/context, domain)
```

Domain separation impedisce riuso diretto ma **non garantisce unlinkability** se payment ed entitlement sono collegati nella stessa transazione.

## 4. PurchaseIntent

```text
PurchaseIntent {
    version
    purchase_ref_hash
    payment_binding_commitment
    product
    amount
    currency_or_asset
    provider
    expires_at
    status
}
```

`purchase_ref` casuale ad alta entropia.

Merchant reference non contiene identity/network/social identifiers Freedom salvo necessità esplicita del provider.

## 5. PaymentDescriptor

```text
PaymentDescriptor {
    provider
    merchant/payment_target
    supported_products[]
    currencies/assets[]
    config_version
    expires_at?
    issuer_signature
}
```

Nessun merchant secret nell'APK.

## 6. Provider flow

```text
App -> PurchaseIntent / PaymentDescriptor
App -> hosted provider flow
User -> pays
Private Payment Worker -> provider verification outbound
Worker -> PaymentAttestation
Attestation -> one-time EntitlementVoucher issuance
Client -> voucher redemption
Control-plane -> verified entitlement transition
```

Il worker non è account server e può essere replicato/sostituito.

## 7. Callback != proof

Checkout `OK` può solo portare a `PAYMENT_PENDING`.

Entitlement nasce da prova economica verificata + voucher redemption + verified control-plane state.

## 8. PaymentAttestation

```text
PaymentAttestation {
    version
    provider
    provider_transaction_commitment
    purchase_ref_hash
    amount
    currency_or_asset
    status
    observed_at
    worker_id
    signature
}
```

Requisiti:

- idempotenza;
- amount/product/currency match;
- replay reject;
- worker key rotation/revocation;
- nessuna authority su release/identity/conversation.

`PaymentAttestation` non deve contenere direttamente `EntitlementCommitment` quando è evitabile.

## 9. EntitlementVoucher

Per ridurre linkage diretto payment→account:

```text
EntitlementVoucher {
    voucher_commitment
    product
    entitlement_delta
    issued_at
    expires_at
    issuer_epoch
    signature_or_blind_credential
}
```

Il voucher è one-time e non contiene RootIdentity/DeviceRecordCommitment/pairwise alias in plaintext.

Una implementazione più forte può usare blind signatures/anonymous credentials standard e reviewate.

## 10. EntitlementRedemption

```text
EntitlementRedemption {
    voucher_nullifier
    entitlement_commitment_or_proof
    redemption_epoch
    proof
}
```

Requisiti:

- nullifier impedisce doppia redemption;
- redemption non rivela payment provider transaction ID;
- entitlement transition è verificata dopo finality/state proof;
- voucher expired/revoked/replayed fallisce esplicitamente.

Timing correlation tra payment e redemption può restare possibile: non promettere unlinkability assoluta.

## 11. Verified finality

```text
submit/observe
 -> finality proof
 -> execution success
 -> resulting state proof
 -> expected entitlement epoch/tier/status
 -> PAID/ACTIVE
```

Hash tx o attestation pubblicata non equivalgono a entitlement attivo.

## 12. Crypto payments

```text
PurchaseIntent
 -> verified crypto transfer/contract call
 -> PaymentAttestation or directly verified economic proof
 -> voucher/redemption or equivalent privacy-preserving transition
 -> verified entitlement
```

## 13. Privacy invariants

- provider agnostic;
- merchant secret mai nell'APK;
- callback client != proof;
- payment binding domain-separated;
- payment attestation non contiene account identity quando evitabile;
- voucher/nullifier separano economic proof da entitlement redemption;
- payment state non entra nel packet hot path;
- timing correlation resta un limite da misurare;
- transaction hash != entitlement success.

## 14. Test gates

- duplicate provider transaction;
- callback forged;
- attestation replay;
- voucher double-spend;
- expired voucher;
- payment/entitlement transaction timing correlation analysis;
- malicious/stale RPC around redemption;
- finality/state mismatch;
- worker key rotation/revocation.
