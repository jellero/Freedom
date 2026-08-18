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

## 2. Core gratuito

Il client ufficiale deve poter offrire gratuitamente il nucleo di Freedom:

- identità Freedom;
- contatti espliciti tramite DeviceID/QR;
- sessioni E2EE;
- messaggistica sincrona;
- modalità Live/effimera;
- comunicazione diretta quando disponibile;
- chiamate base;
- accesso a relay community/best-effort quando disponibili.

Il protocollo non deve introdurre una dipendenza tecnica da un abbonamento per poter stabilire una sessione tra due peer compatibili.

## 3. Freedom Plus

Possibili funzionalità premium del client ufficiale:

- maggiore capacità o priorità sui relay gestiti;
- percorsi privacy/multi-hop gestiti;
- trasferimenti file con limiti superiori;
- qualità o capacità media superiori dove l'infrastruttura comporta un costo;
- multi-device e strumenti di migrazione/recovery avanzati;
- personalizzazioni e funzionalità client non necessarie all'interoperabilità di base.

Le funzioni premium non devono indebolire la cifratura o creare un protocollo incompatibile con i client base.

## 4. Freedom Business

Possibili servizi professionali:

- SDK e integrazioni;
- deployment aziendali;
- relay dedicati o gestiti;
- supporto e SLA;
- policy e amministrazione locale dei client aziendali;
- infrastruttura privata compatibile con Freedom Protocol.

Un'organizzazione può pagare per infrastruttura e supporto senza ottenere accesso al plaintext delle comunicazioni E2EE.

## 5. Relay economy

Il traffico diretto endpoint-to-endpoint non richiede infrastruttura Freedom nel data path.

Quando un utente sceglie o necessita relay, possono coesistere:

```text
DIRECT                 -> nessun relay
COMMUNITY RELAY        -> best effort
MANAGED RELAY          -> servizio a pagamento opzionale
SHIELDED / MULTI-HOP   -> capacità privacy opzionale
```

Il pagamento di un relay compra capacità di rete, non fiducia crittografica. Il relay resta non fidato e non possiede le chiavi E2EE.

## 6. Gas e blockchain

Le operazioni on-chain rare — registrazione identità, key rotation/revocation e rendezvous write quando necessario — possono richiedere fee della chain.

Freedom può supportare relayer di fee indipendenti che sponsorizzano il gas, ma:

- la private identity key resta sul dispositivo;
- la private key del relayer non deve essere distribuita nel client;
- un relayer non deve poter firmare come DeviceID;
- devono poter esistere più relayer intercambiabili;
- l'utente o un'organizzazione deve poter usare un proprio meccanismo di pagamento compatibile;
- il blocco di un singolo relayer non deve rendere Freedom inutilizzabile.

Il costo on-chain non cresce con il numero di messaggi: messaggi, ACK, file, audio, video e route update in-session restano off-chain.

## 7. Vincolo di indipendenza

La monetizzazione non deve trasformare Freedom in un servizio che dipende da un singolo account, provider, store, RPC, relay o soggetto commerciale.

Un client compatibile deve poter continuare a usare Freedom Protocol anche se i servizi commerciali ufficiali non sono disponibili.
