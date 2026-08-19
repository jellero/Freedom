# Freedom — Monetization

Status: **canonical design draft**

Normative security rules: [`SECURITY_INVARIANTS.md`](SECURITY_INVARIANTS.md).

## 1. Principio

Freedom monetizza capacità, comodità e servizi professionali; non contenuto o fiducia crittografica.

- nessuna vendita di messaggi/conversation metadata;
- nessuna pubblicità basata sul contenuto E2EE;
- nessuna master key;
- nessun server centrale necessario per leggere/conservare/consegnare messaggi;
- pagare non rende Freedom Communication crittograficamente “più autentica”.

> **monetizzare capacità, comodità e servizi professionali; non la conversazione.**

> **la censura non deve diventare un paywall.**

## 2. Superfici economiche

```text
Freedom Communication -> core E2EE/live
Freedom Shield        -> capacità/path protection managed avanzata
Freedom Gateway       -> capacità rete/egress/anti-censura gestita
```

## 3. Core Free

Target iniziale client ufficiale:

- RootIdentity + Recovery Kit;
- DeviceKey/DeviceCertificate autorizzati;
- 1 device attivo;
- 10 contatti attivi come product policy locale;
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

## 4. Contact slots: product policy, non trust rule

```text
FREE                     10 contact slots
FREE + RELAY CONTRIBUTOR 20 contact slots
```

V1:

- la lista contatti resta locale/cifrata;
- il limite viene applicato dal client ufficiale come product/entitlement policy;
- non si pubblica il social graph solo per rendere il limite anti-tamper;
- un peer remoto non rifiuta una sessione perché il client mittente ha modificato la propria quota;
- un futuro enforcement resistente a client modificati richiede credential/nullifier/ZK privacy-preserving separati.

Quindi il limite contatti non è una security/interoperability primitive del Freedom Protocol.

## 5. Relay Contributor

Il +10 richiede contributo utile, bounded e privacy-preserving; il toggle non basta.

Se il benefit scade:

- contatti non cancellati;
- sessioni non terminate;
- client ufficiale impedisce nuove aggiunte sopra quota finché l'utente rientra o si riqualifica.

## 6. Freedom Plus / Shield

Può offrire:

- più contatti/device;
- più managed relay capacity;
- Always-Shielded;
- multi-hop quando il circuit protocol definito in `SHIELD.md` è realmente implementato;
- provider/path diversity;
- pre-warmed candidates;
- failover parallelo;
- transport rotation più aggressiva;
- Maximum Resilience;
- limiti file/media superiori;
- recovery/multi-device avanzati;
- Gateway quota/egress/regioni superiori.

Pagare non modifica l'identità del peer o la forza base dell'E2EE.

Prima del claim production `SHIELDED`, il vero circuit setup/per-hop key/layered forwarding deve superare i gate di [`SHIELD.md`](SHIELD.md).

## 7. Freedom Gateway Free

```text
FREEDOM GATEWAY FREE
managed capacity target = 100 MB / giorno
reset                   = daily
carry-over              = no salvo policy futura
priority                = standard
```

Target di prodotto, non parametro wire-protocol.

Non consuma automaticamente quota Gateway:

```text
Freedom Communication direct
Freedom Communication community/device relay
private relay/egress dell'utente/organizzazione
```

Emergency Shield Communication resta budget separato.

## 8. Gateway premium / Business

Plus/Shield può offrire quota superiore, egress/provider/region diversity, multi-hop Gateway, bridge/non-public pools e Maximum Reachability.

Business:

```text
PRIVATE_EGRESS
BUSINESS_EGRESS
custom quotas
private deployment
SLA
```

## 9. Entitlement privacy

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

`EntitlementCommitment` è domain-separated da device/payment/sponsorship/pairwise state.

`max_devices` può essere enforceato dal control-plane tramite privacy-preserving device-slot proof.

`base_contact_slots` V1 resta policy locale del client ufficiale.

## 10. Payment privacy

Payment flow preferito:

```text
provider payment
 -> verified PaymentAttestation
 -> one-time EntitlementVoucher / blind credential
 -> redemption nullifier
 -> verified entitlement state
```

Questo evita di mettere necessariamente payment transaction e `EntitlementCommitment` nello stesso oggetto pubblico.

Timing correlation può restare possibile e non viene negata.

Dettagli: [`PAYMENTS.md`](PAYMENTS.md).

## 11. NEAR / gas / treasury

L'utente compra Freedom, non NEAR.

Treasury/fee relayer possono sponsorizzare rare operazioni control-plane. Il costo non cresce con messaggi/chiamate/media frames.

## 12. Sponsored registration

```text
valid ownership continuity
 -> SponsorshipCommitment
 -> anti-abuse proof
 -> rate/budget
 -> finalized verified registration
```

Nessun SMS, carta, PayPal o telefono obbligatorio per identity Free.

## 13. Anti-dark-pattern

Freedom non deve:

- chiamare “censura” una normale perdita rete per vendere Pro;
- degradare route Free funzionanti;
- nascondere diagnostica fondamentale ai Free;
- usare paura/sorveglianza non dimostrata;
- mostrare paywall prima delle contromisure Free disponibili;
- cancellare contatti quando scade Relay Contributor;
- confondere quota Gateway con sicurezza Communication;
- bloccare Communication perché il Gateway è esaurito;
- presentare Shield come anonimato assoluto.

## 14. Vincolo di indipendenza

Monetizzazione non rende obbligatori un singolo payment provider, account server, store, RPC, relay, egress o soggetto commerciale.

Un client compatibile deve poter usare Freedom Protocol/Communication anche se i servizi commerciali ufficiali sono indisponibili.
