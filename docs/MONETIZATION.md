# Freedom — Monetization

Status: **canonical design draft**.

Normative security rules: [`SECURITY_INVARIANTS.md`](SECURITY_INVARIANTS.md).

## 1. Principio

Freedom monetizza capacità, comodità e servizi professionali; non contenuto, social graph o fiducia crittografica.

> **monetizzare capacità, comodità e servizi professionali; non la conversazione.**

> **la censura non deve diventare un paywall.**

Pagare non rende la E2EE di Freedom Communication “più forte”.

## 2. Superfici economiche

```text
Freedom Communication -> core live E2EE
Freedom Gateway       -> managed egress/network capacity
Freedom Shield        -> managed multi-hop/path capacity
Business              -> private deployment/egress/relay/support
```

## 3. Product quotas V1

Target UI/commerciali iniziali possono restare:

```text
FREE 1 device
FREE 10 contacts
Relay Contributor +10 contacts
```

Ma V1 li tratta come **product/service policy**, non protocol security invariant.

Un client open-source modificato può aggirare una quota locale. Il business model quindi **MUST NOT dipendere esclusivamente** da contact/device limits locali.

Non pubblicare social/device graph soltanto per far rispettare monetizzazione.

## 4. Relay Contributor

Il bonus può essere utile come incentivo UX/community, purché:

- richieda contributo reale;
- non premi volume illimitato;
- non pubblichi peer serviti;
- non venga presentato come anti-tamper guarantee.

Scadenza bonus non cancella contatti o sessioni.

## 5. Managed capacity — superficie enforceable

Managed Gateway/Shield/relay/egress capacity può essere contabilizzata dal servizio che realmente fornisce banda/infrastruttura senza entrare nel plaintext E2EE.

Questa è una superficie commerciale più robusta perché l'enforcement avviene sul servizio opzionale fornito, non sul social graph locale.

## 6. Gateway Free

Target iniziale:

```text
managed Gateway capacity = 100 MB/day
reset                    = daily
priority                 = standard
```

È un target di prodotto da ricalibrare su costi/abuso/geografia/transport overhead.

Non consuma automaticamente quota Gateway:

```text
Freedom Communication direct
Freedom Communication community/device relay
private relay/egress funded by user/org
```

Emergency Shield Communication resta separato.

## 7. Plus / Shield

Può offrire:

- più managed relay/egress capacity;
- higher Gateway quota;
- Always-Shielded quando vero Shield è implementato;
- multi-hop capacity;
- provider/egress diversity;
- pre-warmed alternatives;
- Maximum Reachability resource budget;
- larger media/file service limits dove esistono costi managed.

Non compra una identity/authentication rule diversa.

## 8. Business

```text
PRIVATE_EGRESS
BUSINESS_EGRESS
private relay/Shield pools
custom quotas
private deployment
SLA/support
```

## 9. Entitlement privacy

Entitlement/payment/sponsorship restano domain-separated. Payment→entitlement può usare one-time voucher/nullifier per ridurre linkage.

Merchant/provider identity non diventa Freedom network identity.

## 10. Anti-dark-pattern

Freedom non deve:

- degradare route Free funzionanti per vendere Pro;
- classificare normali failure come censorship per vendere capacità;
- nascondere diagnostica fondamentale ai Free;
- bloccare Communication perché Gateway quota è esaurita;
- chiamare una quota locale “security enforcement” se un modified client può bypassarla;
- usare un claim `SHIELDED` se il vero circuit protocol non è attivo.

## 11. Vincolo di indipendenza

Servizi commerciali ufficiali possono essere opzionali/sostituibili. Freedom Protocol/Freedom Communication non deve richiedere permanentemente un singolo payment provider, store, RPC, relay, egress o account server commerciale.
