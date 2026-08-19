# Freedom — Monetization

Status: **canonical design draft**

Normative security rules: [`SECURITY_INVARIANTS.md`](SECURITY_INVARIANTS.md).

## 1. Principio

Freedom monetizza capacità, comodità e servizi professionali; non contenuto o fiducia crittografica.

- nessuna vendita di messaggi o conversation metadata;
- nessuna pubblicità basata sul contenuto E2EE;
- nessuna master key;
- nessun server centrale necessario per leggere/conservare/consegnare messaggi;
- pagare non rende la crittografia di Freedom Communication più forte.

> **monetizzare capacità, comodità e servizi professionali; non la conversazione.**

> **la censura non deve diventare un paywall.**

## 2. Due superfici economiche

```text
Freedom Communication -> core E2EE/live
Freedom Gateway       -> capacità rete/egress/anti-censura gestita
```

## 3. Core Free

Target iniziale:

- RootIdentity + Recovery Kit;
- DeviceKey/DeviceCertificate autorizzati;
- 1 device attivo;
- 10 contatti attivi;
- sessioni E2EE live;
- no mailbox/offline delivery;
- direct + community/device relay quando disponibili;
- base calls;
- route/provider fallback;
- recovery pairwise;
- Network Indicator;
- Emergency Shield bounded;
- operazioni chain essenziali sponsorizzabili;
- Gateway managed: target 100 MB/giorno quando disponibile.

La lista contatti resta locale/cifrata.

## 4. Relay Contributor

```text
FREE                     10 contatti attivi
FREE + RELAY CONTRIBUTOR 20 contatti attivi
```

Il +10 richiede contributo utile, bounded e privacy-preserving; il toggle non basta.

Se il benefit scade, i contatti non vengono cancellati e le sessioni non vengono terminate: si blocca solo l'aggiunta di nuovi contatti finché l'utente rientra nella quota o si riqualifica.

## 5. Freedom Plus / Shield

Può offrire:

- più contatti/device;
- più relay capacity/priorità;
- Always-Shielded;
- multi-hop;
- provider/path diversity;
- pre-warmed candidates;
- failover parallelo;
- transport rotation più aggressiva;
- Maximum Resilience;
- limiti file/media superiori;
- recovery/multi-device avanzati;
- Gateway quota/egress/regioni superiori;
- Maximum Reachability con resource budget più alto.

Non compra una E2EE “più forte” o una diagnosi più onesta.

## 6. Freedom Gateway Free

```text
FREEDOM GATEWAY FREE
managed capacity target = 100 MB / giorno
reset                   = daily
carry-over              = no salvo policy futura
priority                = standard
```

È un target di prodotto, non un limite wire-protocol immutabile.

Va ricalibrato su costo egress, geografia, overhead anti-censura, abuso e multi-hop.

Non consuma automaticamente quota Gateway:

```text
Freedom Communication direct
Freedom Communication community/device relay
private relay/egress dell'utente/organizzazione
```

Emergency Shield Communication resta un budget separato.

## 7. Freedom Gateway premium / Business

Plus/Shield può offrire:

- quota managed molto superiore;
- egress/provider/region diversity;
- multi-hop Gateway;
- bridge/non-public pools;
- transport rotation/failover più aggressivi;
- candidate pre-warmed;
- Maximum Reachability.

Business:

```text
PRIVATE_EGRESS
BUSINESS_EGRESS
custom quotas
region/policy dedicated pools
private deployment
SLA
```

## 8. Entitlement privacy

La licenza appartiene alla continuità della RootIdentity ma il control-plane non deve riutilizzare `root_commitment` come identificatore universale.

Usare:

```text
FreedomEntitlement {
    entitlement_commitment
    tier
    entitlement_epoch
    max_devices
    base_contact_slots
    expires_at?
    status
}
```

`EntitlementCommitment` è domain-separated da:

```text
DeviceAuthorizationCommitment
PaymentBindingCommitment
SponsorshipCommitment
pairwise identity/rendezvous
```

Il restore segue:

```text
Recovery Kit
 -> RootIdentity
 -> new DeviceKey/DeviceRecordCommitment
 -> verified activation
 -> DeviceCertificate
 -> resolve EntitlementCommitment proof/state
```

## 9. Pagamenti provider-agnostic

Metodi previsti:

```text
PayPal
native crypto
stablecoin
future providers
```

Il payment binding usa `PaymentBindingCommitment`, non RootIdentity/device/pairwise identifiers in plaintext.

Callback client != prova economica. L'entitlement viene mostrato attivo solo dopo finalità/esecuzione/state verification.

Dettagli: [`PAYMENTS.md`](PAYMENTS.md).

## 10. NEAR / gas / treasury

L'utente compra Freedom, non NEAR.

Treasury/fee relayer possono sponsorizzare rare operazioni control-plane. Il costo non cresce con messaggi/chiamate/frame media.

## 11. Sponsored registration

```text
valid RootIdentity
 -> SponsorshipCommitment domain-separated
 -> anti-abuse proof
 -> rate limit/budget
 -> finalized verified registration
```

Nessun SMS, carta, PayPal o numero telefonico obbligatorio per identità Free.

## 12. Anti-dark-pattern

Freedom non deve:

- chiamare “censura” una normale perdita rete per vendere Pro;
- degradare route Free funzionanti;
- nascondere diagnostica fondamentale ai Free;
- usare paura/sorveglianza non dimostrata come leva commerciale;
- mostrare paywall prima delle contromisure Free disponibili in incidente;
- cancellare contatti quando scade Relay Contributor;
- confondere quota Gateway esaurita con sicurezza Communication;
- bloccare Freedom Communication perché il Gateway Internet è esaurito.

## 13. Relay/Gateway economy

```text
DIRECT                    -> nessun relay
DEVICE / COMMUNITY RELAY  -> best effort + Relay Contributor
EMERGENCY SHIELD FREE     -> capacità Communication managed bounded
MANAGED RELAY             -> servizio opzionale
SHIELDED / MULTI-HOP      -> capacità privacy/resilience premium
MAXIMUM RESILIENCE        -> Communication path diversity premium
GATEWAY FREE              -> target 100 MB/day managed egress
GATEWAY PREMIUM           -> quota/path/egress superiori
PRIVATE/BUSINESS EGRESS   -> capacità dedicata/custom
```

## 14. Vincolo di indipendenza

La monetizzazione non rende obbligatori un singolo payment provider, account server, store, RPC, relay, egress o soggetto commerciale.

Un client compatibile deve poter usare Freedom Protocol/Freedom Communication anche se i servizi commerciali ufficiali sono indisponibili.
