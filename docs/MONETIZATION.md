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

Un secondo principio commerciale è obbligatorio:

> **la censura non deve diventare un paywall.**

Freedom può vendere capacità Shield superiore, multi-hop e resilienza preventiva, ma non deve rilevare una probabile interferenza e lasciare deliberatamente offline un utente Free solo per spingerlo verso Pro.

## 2. Core gratuito

Il client ufficiale deve poter offrire gratuitamente il nucleo di Freedom:

- identità Freedom;
- fino a **10 contatti attivi** per RootIdentity/entitlement Free;
- contatti espliciti tramite DeviceID/QR;
- sessioni E2EE;
- messaggistica sincrona;
- modalità Live/effimera;
- comunicazione diretta quando disponibile;
- chiamate base;
- accesso a relay community/best-effort quando disponibili;
- rilevamento base di route failure;
- fallback tra RPC/provider, relay e route disponibili;
- recovery rendezvous quando il peer risulta recentemente attivo sul control-plane ma il data-plane non è disponibile;
- Freedom Network Indicator e spiegazione degli eventi di rete significativi;
- quota limitata di capacità **Emergency Shield** gestita quando necessaria e disponibile.

La resilienza minima del protocollo non deve essere rimossa dal piano gratuito. Il protocollo non deve introdurre una dipendenza tecnica da un abbonamento per poter stabilire o recuperare una sessione tra due peer compatibili quando esiste un percorso disponibile.

Free e Pro devono ricevere la stessa classificazione tecnica onesta degli eventi di rete. Non deve esistere una versione "meno trasparente" del pannello Network per gli utenti gratuiti.

### 2.1 Limite contatti Free

Il limite Free riguarda **contatti attivi simultaneamente**, non il numero totale di persone mai aggiunte.

Esempio:

```text
Free contact slots: 10

Alice   active
Bob     active
...
10 / 10
```

L'utente può eliminare/disattivare un contatto e liberare immediatamente uno slot per un altro contatto.

La rubrica e il social graph non devono essere pubblicati in chiaro sulla blockchain. La lista dei contatti resta locale e cifrata.

Se in futuro è necessario impedire che un client modificato aggiri il limite, l'enforcement deve usare **slot/commitment opachi** o una primitive equivalente che consenta di contare gli slot senza pubblicare una relazione leggibile `Account -> DeviceID[]`.

Il design deve minimizzare anche i metadati derivabili da numero, timing e rotazione degli slot. Il limite commerciale non giustifica la pubblicazione del social graph.

## 3. Emergency Shield Free

Relay gestiti, multi-hop, voce e video shielded consumano banda e infrastruttura. È quindi legittimo limitare la quantità di capacità commerciale gratuita.

Il limite definitivo deve essere determinato da misure reali di:

- costo bandwidth;
- costo relay;
- mix testo/media/voce/video;
- abuso;
- geografia/provider diversity;
- costo delle eventuali chain write di recovery.

Possibili unità interne:

```text
managed relay bytes / day
shielded minutes / day
emergency sessions / day
capacity tokens
weighted traffic budget
```

Il client può esporre una metrica più semplice, ad esempio **messaggi/sessioni di emergenza disponibili**, purché la contabilità interna non tratti un messaggio di testo e un video come equivalenti in costo.

Non fissare un numero permanente prima di aver raccolto dati reali.

Quando il budget Free è disponibile, Freedom deve tentare il bypass prima di mostrare qualsiasi proposta commerciale aggressiva.

Quando il budget è quasi esaurito, il client può informare l'utente in modo neutro e spiegare quali alternative community/self-hosted restano disponibili.

## 4. Freedom Plus / Shield

Possibili funzionalità premium del client ufficiale:

- contatti illimitati o un limite molto superiore al Free, secondo la policy commerciale;
- maggiore capacità o priorità sui relay gestiti;
- budget Shield molto superiore al Free;
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

Il piano Pro monetizza infrastruttura, banda, path diversity, capacità e automazione più costose. Non compra una cifratura più forte, non compra una classificazione più onesta degli eventi e non deve diventare requisito per autenticare una sessione Freedom.

Le funzioni premium non devono creare un protocollo incompatibile con i client base.

## 5. Anti-dark-pattern commerciale

Freedom non deve:

- chiamare "censura" una normale perdita di rete per vendere Pro;
- aumentare artificialmente la severità di un evento quando la quota Free finisce;
- nascondere informazioni diagnostiche fondamentali agli utenti Free;
- degradare deliberatamente route Free funzionanti;
- usare paura, sorveglianza non dimostrata o claim assoluti come leva commerciale;
- mostrare un paywall prima di aver tentato le contromisure Free disponibili durante un incidente critico.

Il tier commerciale può determinare **quanta infrastruttura gestita e quanta capacità prodotto** viene consumata, non reinterpretare i segnali tecnici.

## 6. Freedom Business

Possibili servizi professionali:

- SDK e integrazioni;
- deployment aziendali;
- relay dedicati o gestiti;
- pool Shield privati/gestiti;
- supporto e SLA;
- policy e amministrazione locale dei client aziendali;
- infrastruttura privata compatibile con Freedom Protocol.

Un'organizzazione può pagare per infrastruttura e supporto senza ottenere accesso al plaintext delle comunicazioni E2EE.

## 7. Relay economy

Il traffico diretto endpoint-to-endpoint non richiede infrastruttura Freedom nel data path.

Quando un utente sceglie o necessita relay, possono coesistere:

```text
DIRECT                 -> nessun relay
COMMUNITY RELAY        -> best effort
EMERGENCY SHIELD FREE  -> capacità gestita limitata
MANAGED RELAY          -> servizio a pagamento opzionale
SHIELDED / MULTI-HOP   -> capacità privacy opzionale
MAXIMUM RESILIENCE     -> path diversity e failover premium
```

Il pagamento di un relay compra capacità di rete, non fiducia crittografica. Il relay resta non fidato e non possiede le chiavi E2EE.

## 8. Gas e blockchain

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

## 9. Vincolo di indipendenza

La monetizzazione non deve trasformare Freedom in un servizio che dipende da un singolo account, provider, store, RPC, relay o soggetto commerciale.

Un client compatibile deve poter continuare a usare Freedom Protocol anche se i servizi commerciali ufficiali non sono disponibili.

Dettagli del rilevamento/failover: [`ADAPTIVE_DEFENSE.md`](ADAPTIVE_DEFENSE.md).

UX di stato rete e Emergency Shield: [`NETWORK_STATUS_UI.md`](NETWORK_STATUS_UI.md).
