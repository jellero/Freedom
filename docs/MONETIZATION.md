# Freedom — Monetization

## 1. Principio

Freedom non monetizza il contenuto delle conversazioni e non richiede una mailbox centrale per generare ricavi.

Il modello economico deve restare coerente con il trust model del protocollo:

- nessuna vendita di messaggi o metadati di conversazione;
- nessuna pubblicità basata sul contenuto E2EE;
- nessuna master key;
- nessun server centrale necessario per leggere, conservare o consegnare i messaggi;
- nessun relayer di pagamento o relay di rete deve diventare autorità sull'identità dell'utente.

Principio: **monetizzare capacità, comodità e servizi professionali; non la conversazione.**

Principio commerciale obbligatorio:

> **la censura non deve diventare un paywall.**

## 2. Core gratuito

Policy iniziale Free:

- identità Freedom e Recovery Kit;
- **1 device attivo**;
- fino a **10 contatti attivi**;
- sessioni E2EE;
- messaggistica sincrona;
- modalità Live/effimera;
- comunicazione diretta quando disponibile;
- chiamate base;
- relay community/best-effort quando disponibili;
- rilevamento base di route failure;
- fallback tra RPC/provider, relay e route disponibili;
- recovery rendezvous;
- Freedom Network Indicator;
- quota limitata di **Emergency Shield** gestita quando necessaria e disponibile;
- operazioni chain essenziali sponsorizzabili secondo policy anti-abuso.

Il limite di 10 contatti riguarda contatti **attivi simultaneamente**, non il numero totale di persone mai aggiunte. Eliminare/disattivare un contatto libera uno slot.

La rubrica resta locale e cifrata. Se serve enforcement resistente a client modificati, usare commitment/slot opachi senza pubblicare `Account -> DeviceID[]`.

## 3. Freedom Plus / Shield

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
- funzioni recovery/multi-device avanzate.

Il piano Pro non compra una cifratura più forte, una diagnosi tecnica più onesta o il diritto esclusivo al recovery di base.

## 4. Entitlement e recovery

La licenza appartiene alla `RootIdentity`, non al singolo APK/device.

```text
FreedomEntitlement {
    account_commitment
    tier
    entitlement_epoch
    max_devices
    expires_at?
    status
}
```

Dopo reset/nuovo telefono, Recovery Kit -> RootIdentity -> nuova DeviceKey -> restore entitlement.

La chain fa rispettare `active_devices <= max_devices`; il restore non deve trasformarsi in clonazione illimitata della licenza.

Dettagli: [`ACCOUNT_RECOVERY_LICENSES.md`](ACCOUNT_RECOVERY_LICENSES.md).

## 5. Emergency Shield Free

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

## 6. Pagamenti provider-agnostic

L'utente compra **Freedom**, non NEAR.

Metodi previsti:

```text
PayPal
crypto native
stablecoin
future providers
```

PayPal non richiede un server Freedom pubblico: il checkout può essere aperto dall'app, mentre worker privati outbound-only verificano il provider e pubblicano una `PaymentAttestation` on-chain.

Il callback/OK nell'app non è sufficiente come prova economica autoritativa.

Per crypto verificabile on-chain, l'entitlement può essere attivato direttamente dal pagamento verificato dal contratto/adaptor.

Nessun merchant `client_secret` deve essere distribuito nell'APK.

Dettagli: [`PAYMENTS.md`](PAYMENTS.md).

## 7. NEAR, gas e treasury

L'utente Free non deve possedere obbligatoriamente NEAR.

Treasury e fee relayer indipendenti possono sponsorizzare registrazione e rare operazioni essenziali. Ricavi Pro/Business/crypto possono finanziare:

- gas;
- storage staking;
- relay/Shield bandwidth;
- media infrastructure;
- security/update infrastructure.

Il costo on-chain non cresce con i messaggi: messaggi, ACK, file, audio, video e route update restano off-chain.

## 8. Sponsored registration e anti-abuse

La registrazione iniziale Free può essere sponsorizzata ma non illimitata.

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

## 9. Anti-dark-pattern commerciale

Freedom non deve:

- chiamare "censura" una normale perdita di rete per vendere Pro;
- aumentare artificialmente la severità quando una quota Free finisce;
- nascondere diagnostica fondamentale ai Free;
- degradare route Free funzionanti;
- usare paura o sorveglianza non dimostrata come leva commerciale;
- mostrare un paywall prima delle contromisure Free disponibili durante un incidente critico.

## 10. Freedom Business

Possibili servizi:

- SDK e integrazioni;
- deployment aziendali;
- relay/Shield pool dedicati;
- supporto e SLA;
- policy/amministrazione locale;
- infrastruttura privata compatibile con Freedom Protocol.

Pagare per infrastruttura non concede accesso al plaintext E2EE.

## 11. Relay economy

```text
DIRECT                 -> nessun relay
COMMUNITY RELAY        -> best effort
EMERGENCY SHIELD FREE  -> capacità gestita limitata
MANAGED RELAY          -> servizio opzionale
SHIELDED / MULTI-HOP   -> capacità privacy premium
MAXIMUM RESILIENCE     -> path diversity/failover premium
```

Il pagamento compra capacità, non fiducia crittografica.

## 12. Vincolo di indipendenza

La monetizzazione non deve rendere obbligatori un singolo payment provider, account server, store, RPC, relay o soggetto commerciale.

Un client compatibile deve continuare a usare Freedom Protocol anche se i servizi commerciali ufficiali sono indisponibili.

Riferimenti:

- [`ACCOUNT_RECOVERY_LICENSES.md`](ACCOUNT_RECOVERY_LICENSES.md)
- [`PAYMENTS.md`](PAYMENTS.md)
- [`REGISTRATION_ECONOMICS.md`](REGISTRATION_ECONOMICS.md)
- [`ADAPTIVE_DEFENSE.md`](ADAPTIVE_DEFENSE.md)
- [`NETWORK_STATUS_UI.md`](NETWORK_STATUS_UI.md)
