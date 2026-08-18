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
- accesso a relay community/best-effort quando disponibili;
- rilevamento base di route failure;
- fallback tra RPC/provider, relay e route disponibili;
- recovery rendezvous quando il peer risulta recentemente attivo sul control-plane ma il data-plane non è disponibile.

La resilienza minima del protocollo non deve essere rimossa dal piano gratuito. Il protocollo non deve introdurre una dipendenza tecnica da un abbonamento per poter stabilire o recuperare una sessione tra due peer compatibili quando esiste un percorso disponibile.

## 3. Freedom Plus / Shield

Possibili funzionalità premium del client ufficiale:

- maggiore capacità o priorità sui relay gestiti;
- **Always-Shielded mode** senza direct IP;
- percorsi privacy/multi-hop gestiti;
- pool relay più ampio e geograficamente/provider-diverso;
- pre-warming di candidate alternativi;
- failover parallelo più rapido;
- transport rotation più aggressiva;
- bridge/non-public relay pool quando disponibile;
- padding/metadata protection opzionale quando implementato;
- modalità **Maximum Resilience** con più percorsi indipendenti pronti prima del failure;
- trasferimenti file con limiti superiori;
- qualità o capacità media superiori dove l'infrastruttura comporta un costo;
- multi-device e strumenti di migrazione/recovery avanzati;
- personalizzazioni e funzionalità client non necessarie all'interoperabilità di base.

Il piano Pro monetizza infrastruttura, banda, path diversity e automazione più costose. Non compra una cifratura più forte e non deve diventare requisito per autenticare una sessione Freedom.

Le funzioni premium non devono creare un protocollo incompatibile con i client base.

## 4. Freedom Business

Possibili servizi professionali:

- SDK e integrazioni;
- deployment aziendali;
- relay dedicati o gestiti;
- pool Shield privati/gestiti;
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
MAXIMUM RESILIENCE     -> path diversity e failover premium
```

Il pagamento di un relay compra capacità di rete, non fiducia crittografica. Il relay resta non fidato e non possiede le chiavi E2EE.

## 6. Gas e blockchain

Le operazioni on-chain rare — registrazione identità, key rotation/revocation, rendezvous write e recovery beacon quando necessario — possono richiedere fee della chain.

Freedom può supportare relayer di fee indipendenti che sponsorizzano il gas, ma:

- la private identity key resta sul dispositivo;
- la private key del relayer non deve essere distribuita nel client;
- un relayer non deve poter firmare come DeviceID;
- devono poter esistere più relayer intercambiabili;
- l'utente o un'organizzazione deve poter usare un proprio meccanismo di pagamento compatibile;
- il blocco di un singolo relayer non deve rendere Freedom inutilizzabile.

Il costo on-chain non cresce con il numero di messaggi: messaggi, ACK, file, audio, video e route update in-session restano off-chain.

I recovery beacon devono essere eccezionali, a TTL breve e attivati solo dopo perdita del data path o in modalità di resilienza esplicitamente configurata; non devono trasformarsi in heartbeat blockchain continui.

## 7. Vincolo di indipendenza

La monetizzazione non deve trasformare Freedom in un servizio che dipende da un singolo account, provider, store, RPC, relay o soggetto commerciale.

Un client compatibile deve poter continuare a usare Freedom Protocol anche se i servizi commerciali ufficiali non sono disponibili.

Dettagli del rilevamento/failover: [`ADAPTIVE_DEFENSE.md`](ADAPTIVE_DEFENSE.md).
