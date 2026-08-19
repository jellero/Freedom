# Freedom — Monetization

## 1. Principio

Freedom non monetizza il contenuto delle conversazioni e non richiede una mailbox centrale per generare ricavi.

Il modello economico deve restare coerente con il trust model:

- nessuna vendita di messaggi o metadati di conversazione;
- nessuna pubblicità basata sul contenuto E2EE;
- nessuna master key;
- nessun server centrale necessario per leggere, conservare o consegnare messaggi;
- nessun relayer di pagamento o relay di rete deve diventare autorità sull'identità dell'utente.

> **monetizzare capacità, comodità e servizi professionali; non la conversazione.**

> **la censura non deve diventare un paywall.**

Freedom distingue economicamente due superfici:

```text
Freedom Communication -> core E2EE/live, sicurezza della comunicazione
Freedom Gateway       -> capacità di rete gestita / egress / anti-censura
```

Pagare non rende la crittografia del messenger più forte. Può invece comprare più capacità infrastrutturale, più egress, più path diversity e resilienza Gateway/Shield.

## 2. Core gratuito

Policy iniziale Free:

- RootIdentity e Recovery Kit;
- DeviceKey autorizzata tramite record opaco, senza global DeviceID di rete;
- **1 device attivo**;
- fino a **10 contatti attivi**;
- sessioni E2EE;
- messaggistica sincrona;
- modalità Live/effimera;
- comunicazione diretta quando disponibile;
- possibilità opt-in di contribuire come `DEVICE_RELAY`;
- chiamate base;
- relay community/best-effort quando disponibili;
- route/RPC/provider fallback;
- recovery rendezvous pairwise;
- Freedom Network Indicator;
- quota limitata di **Emergency Shield** gestita quando necessaria e disponibile;
- operazioni chain essenziali sponsorizzabili secondo policy anti-abuso;
- quando Freedom Gateway sarà disponibile: **target iniziale 100 MB/giorno di managed Gateway capacity**.

Il limite di 10 contatti riguarda persone/RootIdentity attive nella rubrica, non device e non contatti lifetime. Eliminare/disattivare un contatto libera uno slot.

La rubrica resta locale e cifrata. Se serve enforcement resistente a client modificati, usare commitment/slot opachi senza pubblicare social graph o mapping leggibili `RootIdentity -> devices[]`.

## 3. Relay Contributor

Un utente Free che mette a disposizione un dispositivo come relay Freedom utile alla rete riceve **10 slot contatto attivi aggiuntivi**.

```text
FREE                     10 contatti attivi
FREE + RELAY CONTRIBUTOR 20 contatti attivi
```

Il bonus non equivale a Pro e non compra capacità Shield/Gateway premium.

Il reward deve dipendere da partecipazione relay realmente utile, non dal semplice toggle. La policy può usare finestre di disponibilità, circuiti accettati, traffico inoltrato bounded o receipt/commitment opachi, evitando social graph e dettagli dei circuiti.

Se il contributo cessa, il bonus può scadere dopo una grace period. I contatti sopra il limite base non vengono cancellati automaticamente; l'utente non può aggiungerne di nuovi finché non torna entro quota o riqualifica il relay.

Dettagli: [`RELAYS.md`](RELAYS.md).

## 4. Freedom Plus / Shield

Il piano premium può offrire:

- contatti illimitati o limite molto superiore;
- più device attivi secondo `max_devices` del tier;
- maggiore capacità/priorità relay;
- budget Shield molto superiore;
- **Always-Shielded** senza direct IP;
- multi-hop gestito;
- pool relay più ampio e provider/geographic diversity;
- pre-warming candidate;
- failover parallelo;
- transport rotation aggressiva;
- bridge/non-public relay pool quando disponibile;
- padding/metadata protection opzionale;
- **Maximum Resilience**;
- limiti file/media superiori;
- funzioni recovery/multi-device avanzate;
- **Freedom Gateway managed con quota molto superiore al Free**;
- maggiore egress/provider diversity;
- multi-hop Gateway e `MAXIMUM_REACHABILITY` con resource budget superiore.

Il piano Pro non compra una cifratura più forte, una diagnosi tecnica più onesta o il diritto esclusivo al recovery di base.

I numeri Gateway premium definitivi devono essere fissati solo dopo misure reali di costo/abuso; non vengono codificati nel protocollo.

## 5. Freedom Gateway Free

Il Gateway è una risorsa di rete opzionale post-V1. La capacità gestita consuma egress bandwidth, relay, IP, anti-abuse e infrastruttura regionale, quindi può essere quotata separatamente dal messenger.

Target iniziale:

```text
FREEDOM GATEWAY FREE
managed capacity = 100 MB / giorno
reset            = giornaliero
carry-over       = no, salvo futura policy esplicita
priority         = standard
```

I 100 MB/giorno sono un **target di prodotto iniziale**, non un limite immutabile. Va ricalibrato dopo misure reali di:

- costo egress per regione/provider;
- overhead dei transport anti-censura;
- percentuale di multi-hop/Shield;
- browsing vs app traffic;
- abuso/bot;
- disponibilità infrastrutturale.

La quota riguarda esclusivamente **managed Gateway capacity**.

Non deve essere conteggiato automaticamente dentro questa quota:

```text
Freedom Communication direct
Freedom Communication tramite community/device relay
private relay/egress finanziato o gestito dall'utente/organizzazione
```

La quota **Emergency Shield di Freedom Communication è separata** dalla quota Gateway. Un utente sotto interferenza non deve perdere la possibilità di comunicare con un contatto Freedom solo perché ha consumato i 100 MB del Gateway Internet.

Dettagli: [`GATEWAY.md`](GATEWAY.md).

## 6. Freedom Gateway premium

Plus/Shield può monetizzare capacità gestita, non fiducia crittografica.

Possibili benefici:

- quota managed Gateway molto superiore;
- egress multipli/regioni aggiuntive;
- provider diversity;
- multi-hop Gateway;
- pool bridge/non-public più ampio;
- transport rotation aggressiva;
- candidate pre-warmed;
- failover parallelo;
- `MAXIMUM_REACHABILITY` con budget batteria/banda più alto;
- supporto a policy selected-app/whole-device più avanzate.

Business può offrire:

```text
PRIVATE_EGRESS
BUSINESS_EGRESS
custom quotas
region/policy dedicated pools
private deployment
SLA
```

Un private/business egress può avere una politica economica indipendente dalla quota Free gestita da Freedom.

## 7. Entitlement e recovery

La licenza appartiene alla `RootIdentity`, non al singolo APK/device.

```text
FreedomEntitlement {
    root_commitment
    tier
    entitlement_epoch
    max_devices
    expires_at?
    status
}
```

Dopo reset/nuovo telefono:

```text
Recovery Kit -> RootIdentity -> nuova DeviceKey -> nuovo DeviceRecordCommitment -> restore entitlement
```

La chain fa rispettare `active_devices <= max_devices`; il restore non deve trasformarsi in clonazione illimitata della licenza.

Benefit temporanei come `Relay Contributor` possono modificare la quota contatti senza trasformarsi in licenze Pro permanenti.

La quota Gateway è una capacity policy dell'entitlement/servizio gestito e non deve diventare parte dell'identità crittografica.

Dettagli: [`ACCOUNT_RECOVERY_LICENSES.md`](ACCOUNT_RECOVERY_LICENSES.md).

## 8. Emergency Shield Free

Relay gestiti, multi-hop, voce/video shielded consumano banda e infrastruttura. È legittimo limitare la capacità commerciale gratuita.

Il limite definitivo va determinato da dati reali di bandwidth, relay cost, mix testo/media/voce/video, abuso e geografia.

Possibili unità interne:

```text
managed relay bytes/day
shielded minutes/day
emergency sessions/day
capacity tokens
weighted traffic budget
```

Freedom deve tentare i bypass Free disponibili prima di mostrare proposte commerciali aggressive durante un incidente.

**Emergency Shield Communication e Gateway Free sono budget distinti.**

## 9. Pagamenti provider-agnostic

L'utente compra **Freedom**, non NEAR.

Metodi previsti:

```text
PayPal
crypto native
stablecoin
future providers
```

PayPal non richiede un server Freedom pubblico: il checkout può essere aperto dall'app, mentre worker privati outbound-only verificano il provider e pubblicano una `PaymentAttestation` on-chain.

Il callback/OK nell'app non è sufficiente come prova economica autoritativa. Per crypto verificabile on-chain, l'entitlement può essere attivato direttamente dal pagamento verificato dal contratto/adaptor.

Nessun merchant `client_secret` deve essere distribuito nell'APK.

Dettagli: [`PAYMENTS.md`](PAYMENTS.md).

## 10. NEAR, gas e treasury

L'utente Free non deve possedere obbligatoriamente NEAR.

Treasury e fee relayer indipendenti possono sponsorizzare registrazione e rare operazioni essenziali. Ricavi Pro/Business/crypto possono finanziare:

- gas;
- storage staking;
- relay/Shield bandwidth;
- Gateway egress bandwidth;
- bridge/transport infrastructure;
- media infrastructure;
- security/update infrastructure.

Il costo on-chain non cresce con i messaggi: messaggi, ACK, file, audio, video e route update restano off-chain.

I device/community relay possono ridurre la dipendenza da capacità relay managed, ma non devono essere trattati come infrastruttura garantita né come Internet egress impliciti.

## 11. Sponsored registration e anti-abuse

```text
valid RootIdentity
 -> anti-abuse proof / adaptive PoW
 -> sponsorship not consumed
 -> relayer rate limit
 -> global bounded budget
 -> register
```

Più relayer devono poter coesistere. Non usare PayPal, carta, SMS o numero telefonico come requisito universale per ottenere un'identità Free.

L'installazione locale non produce automaticamente storage on-chain.

Dettagli: [`REGISTRATION_ECONOMICS.md`](REGISTRATION_ECONOMICS.md).

## 12. Anti-dark-pattern commerciale

Freedom non deve:

- chiamare "censura" una normale perdita di rete per vendere Pro;
- aumentare artificialmente la severità quando una quota Free finisce;
- nascondere diagnostica fondamentale ai Free;
- degradare route Free funzionanti;
- usare paura o sorveglianza non dimostrata come leva commerciale;
- mostrare un paywall prima delle contromisure Free disponibili durante un incidente critico;
- cancellare contatti come punizione immediata quando scade un benefit Relay Contributor;
- confondere l'esaurimento dei 100 MB Gateway con un problema di sicurezza della comunicazione;
- bloccare Freedom Communication perché la quota Gateway Internet è terminata.

## 13. Freedom Business

Possibili servizi:

- SDK e integrazioni;
- deployment aziendali;
- relay/Shield pool dedicati;
- private/business Gateway egress;
- custom Gateway quotas;
- supporto e SLA;
- policy/amministrazione locale;
- infrastruttura privata compatibile con Freedom Protocol.

Pagare per infrastruttura non concede accesso al plaintext E2EE di Freedom Communication.

## 14. Relay/Gateway economy

```text
DIRECT                    -> nessun relay
DEVICE / COMMUNITY RELAY  -> best effort + incentivo Relay Contributor
EMERGENCY SHIELD FREE     -> capacità Communication gestita limitata
MANAGED RELAY             -> servizio opzionale
SHIELDED / MULTI-HOP      -> capacità privacy premium
MAXIMUM RESILIENCE        -> path diversity/failover Communication premium
GATEWAY FREE              -> target 100 MB/day managed egress
GATEWAY PREMIUM           -> quota/egress/path diversity superiori
PRIVATE/BUSINESS EGRESS   -> capacità dedicata/custom
```

Il pagamento compra capacità, non fiducia crittografica. Il contributo relay compra un beneficio di prodotto limitato, non autorità o accesso al contenuto.

## 15. Vincolo di indipendenza

La monetizzazione non deve rendere obbligatori un singolo payment provider, account server, store, RPC, relay, egress o soggetto commerciale.

Un client compatibile deve continuare a usare Freedom Protocol e Freedom Communication anche se i servizi commerciali Gateway/Shield ufficiali sono indisponibili.

Riferimenti:

- [`IDENTITY_MODEL.md`](IDENTITY_MODEL.md)
- [`ACCOUNT_RECOVERY_LICENSES.md`](ACCOUNT_RECOVERY_LICENSES.md)
- [`PAYMENTS.md`](PAYMENTS.md)
- [`REGISTRATION_ECONOMICS.md`](REGISTRATION_ECONOMICS.md)
- [`RELAYS.md`](RELAYS.md)
- [`GATEWAY.md`](GATEWAY.md)
- [`ADAPTIVE_DEFENSE.md`](ADAPTIVE_DEFENSE.md)
- [`NETWORK_STATUS_UI.md`](NETWORK_STATUS_UI.md)