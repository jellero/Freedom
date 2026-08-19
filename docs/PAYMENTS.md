# Freedom — Payments

Status: **canonical design draft**

Normative security rules: [`SECURITY_INVARIANTS.md`](SECURITY_INVARIANTS.md).

## 1. Principio

Il pagamento abilita servizi/entitlement Freedom. L'utente non deve possedere obbligatoriamente NEAR, wallet NEAR o saldo NEAR per usare il protocollo base.

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
payment provider -> prova economica
Freedom control-plane -> attestation / entitlement state
client -> UX + verifica stato
```

Il provider payment non è root of trust dell'identità Freedom.

Il ruolo payment attestor non può firmare release, SecurityPolicy o identity/device authorization.

## 3. PaymentBindingCommitment

Freedom **MUST NOT** riutilizzare `root_commitment`, `DeviceRecordCommitment` o `PairwiseContactAlias` come riferimento economico globale.

Usare un commitment domain-separated:

```text
PaymentBindingCommitment = H(root/payment context, "payment", random/context)
```

Requisiti:

- diverso da entitlement/device/sponsorship commitment;
- non usato come routing/contact identifier;
- non copiato in plaintext come merchant reference se non necessario;
- più acquisti possono usare binding/nullifier specifici quando utile a ridurre correlazione.

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

`purchase_ref` è casuale ad alta entropia.

Non usare in merchant/provider reference plaintext:

- email/telefono salvo necessità del provider;
- RootIdentity/root_commitment;
- DeviceRecordCommitment;
- pairwise alias;
- social metadata.

## 5. PaymentDescriptor

```text
PaymentDescriptor {
    provider
    merchant/payment target
    supported_products[]
    currencies/assets[]
    config_version
    expires_at?
    issuer_signature
}
```

Nessun merchant secret viene distribuito nell'APK.

## 6. PayPal senza server pubblico

```text
App -> reads PurchaseIntent/PaymentDescriptor
App -> hosted PayPal flow
User -> completes payment
Private Payment Worker -> queries PayPal outbound
Worker -> reconciles purchase_ref / amount / status
Worker -> publishes PaymentAttestation
Control-plane -> applies entitlement transition
App -> verifies final state
```

Il worker:

- non espone API pubblica all'app;
- può stare dietro NAT/firewall;
- custodisce credenziali merchant fuori dal client;
- non è account server Freedom;
- può essere replicato/sostituito.

## 7. Callback client != prova economica

Il ritorno `OK` del checkout serve solo alla UX e può portare a `PAYMENT_PENDING`.

Un client modificato può simulare il callback; l'entitlement nasce soltanto da prova verificata e stato control-plane verificato.

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
- worker key ruotabili/revocabili;
- possibilità futura quorum/threshold tra worker indipendenti;
- nessuna autorità del worker su release, identity o conversazioni.

## 9. Verified finality

`PaymentAttestation` pubblicata o transaction hash ricevuto **non** significa automaticamente entitlement attivo.

```text
submit/observe attestation
 -> acceptable finality
 -> execution success
 -> resolve entitlement
 -> verify expected entitlement_epoch/tier/status
 -> only then show PAID/ACTIVE
```

Failure o state mismatch -> `PAYMENT_PENDING`/errore esplicito, mai successo silenzioso.

## 10. Crypto payments

```text
PurchaseIntent
 -> crypto transfer / contract call
 -> on-chain verification
 -> finalized state verification
 -> entitlement
```

Stablecoin possono essere preferibili per prezzi commerciali stabili; native token può usare quote a validità breve.

## 11. NEAR è infrastruttura, non prodotto

L'utente compra servizi Freedom, non NEAR.

Gas/storage possono essere sponsorizzati da treasury/fee relayer e finanziati dai ricavi.

## 12. Privacy invariants

- provider agnostic;
- merchant secret mai nell'APK;
- callback client != proof;
- payment binding domain-separated;
- provider reference non contiene identity/network/social identifiers Freedom in plaintext salvo necessità esplicita;
- dati personali non copiati inutilmente on-chain;
- payment state non entra nel packet hot path;
- provider payment non diventa identity trust anchor;
- transaction hash != entitlement success.
