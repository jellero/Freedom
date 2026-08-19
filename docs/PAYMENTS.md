# Freedom — Payments

## 1. Principio

Il pagamento abilita servizi/entitlement Freedom. L'utente non deve essere obbligato a possedere NEAR, un wallet NEAR o saldo NEAR per usare il protocollo base.

Freedom deve essere payment-provider agnostic:

```text
PaymentAdapter
|- PayPal
|- native crypto
|- stablecoin
|- future providers
```

Nessun singolo provider di pagamento deve essere requisito permanente per acquistare i servizi Freedom.

## 2. Separazione dei ruoli

```text
payment provider -> prova economica
Freedom chain    -> PurchaseIntent / attestation / entitlement
client           -> UX di acquisto e verifica entitlement
```

Il metodo di pagamento non deve diventare una root of trust dell'identità Freedom.

## 3. Purchase intent

Prima del pagamento il client crea/ottiene un riferimento opaco:

```text
PurchaseIntent {
    version
    purchase_ref_hash
    account_commitment
    product
    amount
    currency_or_asset
    provider
    expires_at
    status
}
```

`purchase_ref` deve essere casuale ad alta entropia. Non usare in chiaro email, DeviceID, RootAccountID o social metadata come riferimento del pagamento.

## 4. Payment descriptor on-chain

La chain può pubblicare una configurazione verificabile/versionata per ciascun provider:

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

Il target può essere cifrato o comunque non necessario in chiaro quando il provider/UX lo consente, ma il sistema non deve promettere di nascondere al provider o al pagatore informazioni che il provider è legalmente/tecnicamente tenuto a mostrare.

Nessun `client_secret` PayPal o altra credenziale merchant segreta deve essere inserita nell'APK.

## 5. PayPal senza server pubblico

Il modello Freedom non richiede un payment server con endpoint pubblico.

Flusso desiderato:

```text
App -> legge PaymentDescriptor/PurchaseIntent dalla chain
App -> apre il flusso PayPal ospitato/in-app
User -> completa il pagamento su PayPal
Private Payment Worker -> interroga PayPal outbound
Private Payment Worker -> riconcilia purchase_ref/importo/stato
Private Payment Worker -> scrive PaymentAttestation on-chain
Chain -> attiva entitlement
App -> osserva entitlement attivo
```

Il worker:

- non espone una API pubblica all'app;
- non necessita di porta inbound pubblica;
- può stare dietro NAT/firewall;
- usa connessioni outbound verso PayPal e blockchain/RPC;
- custodisce le credenziali merchant fuori dal client;
- non è un account server Freedom;
- può essere replicato/sostituito.

La frequenza di polling è una policy operativa; interrogare frequentemente non garantisce che il provider renda immediatamente visibile una transazione. Il client deve mostrare uno stato `PAYMENT_PENDING` fino a prova verificata.

## 6. Callback PayPal nell'app

Il ritorno/OK del checkout PayPal nell'app è utile per UX ma **non costituisce da solo prova autoritativa di pagamento**, perché un client modificato potrebbe simulare il callback.

Il callback può portare l'UX a `PAYMENT_PENDING`; l'entitlement viene emesso soltanto dopo verifica indipendente del provider tramite worker o altra attestazione sicura.

## 7. Payment attestation

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

- idempotenza: una transazione provider non può attivare più entitlement incompatibili;
- importo/prodotto/currency devono corrispondere al PurchaseIntent;
- replay rifiutato;
- worker key ruotabili/revocabili;
- possibilità futura di quorum/threshold tra più worker indipendenti.

## 8. Crypto payments

Per asset verificabili direttamente dalla chain, il pagamento può evitare il worker esterno:

```text
PurchaseIntent
 -> crypto transfer / contract call
 -> on-chain verification
 -> entitlement
```

Stablecoin sono preferibili per prezzi commerciali stabili; il token nativo può essere accettato con quote a validità breve.

Il sistema deve poter accettare crypto anche quando PayPal non è disponibile, desiderato o accessibile all'utente.

## 9. NEAR è infrastruttura, non prodotto

L'utente compra:

- Freedom Pro/Shield;
- capacità relay;
- multi-device;
- Maximum Resilience;
- altre feature commerciali.

Non compra "NEAR" come requisito applicativo.

Gas/storage necessari alle operazioni Freedom possono essere sponsorizzati da treasury/fee relayer e finanziati dai ricavi. Pagamenti in NEAR possono contribuire direttamente al treasury, ma il tier resta un entitlement Freedom.

## 10. Invarianti

- nessun payment provider obbligatorio;
- nessun merchant secret nell'APK;
- nessun server pubblico Freedom necessario al flusso PayPal;
- callback client != prova economica;
- PayPal/crypto convergono nello stesso modello di entitlement;
- dati personali/payment metadata non vengono copiati inutilmente on-chain;
- dopo l'emissione, il provider di pagamento non è nel percorso quotidiano dell'app.
