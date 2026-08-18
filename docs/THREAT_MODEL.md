# Freedom — Threat Model

## 1. Assunzioni

Freedom assume una rete non fidata.

Un avversario può:

- osservare traffico e metadati di rete;
- controllare alcuni peer o relay;
- restituire informazioni di routing false;
- bloccare, ritardare, duplicare o riordinare pacchetti;
- bloccare IP, domini, RPC, relay o classi di trasporto;
- tentare traffic analysis e correlazione temporale;
- tentare impersonation;
- tentare replay;
- creare molti nodi;
- controllare uno o più endpoint RPC;
- tentare di saturare relay, chain writes o risorse locali;
- distribuire client modificati se compromette la supply chain o il sistema di aggiornamento.

Freedom non assume che relay, NAT observer, bootstrap node, fee relayer o RPC siano fidati per autenticare un DeviceID.

## 2. Trust anchors

Le radici di fiducia sono limitate a:

- private identity key locale del device;
- stato verificato del registro distribuito relativo al `DeviceID`;
- primitive crittografiche standard;
- stato locale autenticato derivato da sessioni precedenti.

Un IP, un relay, un RPC, un fee relayer, un risultato di discovery o un provider commerciale non è un trust anchor.

La funzione di registro verificabile è fondamentale per il trust model attuale; una blockchain specifica come NEAR non deve esserlo.

## 3. Device impersonation

Attacco:

```text
Mallory afferma di essere DeviceID_B
```

Difesa:

1. A risolve `DeviceID_B` tramite `ChainAdapter`;
2. ottiene current public key e key epoch;
3. l'handshake richiede una prova del possesso della private key;
4. il transcript lega DeviceID, epoch ed ephemeral keys.

Senza la private key corrente di B, Mallory non deve poter completare l'handshake.

## 4. Man-in-the-middle

La semplice cifratura ECDH non basta se l'ephemeral exchange non è autenticato.

Il transcript deve includere entrambe le identità e le chiavi effimere ed essere autenticato bilateralmente.

Qualsiasi sostituzione di DeviceID, key epoch, ephemeral key, nonce, versione o suite deve far fallire l'handshake.

## 5. Replay

### Rendezvous

Ogni rendezvous è indipendente e non usa una sequence storica.

La freshness viene verificata attraverso:

- slot atteso;
- stato del registro sufficientemente verificato/finalizzato;
- `expires_at`;
- nonce del rendezvous;
- autenticazione e decifrabilità del payload;
- materiale effimero valido per il tentativo corrente.

### Session frames

Mitigato da sequence monotoni/finestra anti-replay autenticata con AEAD.

### Handshake

Mitigato da nonce casuali e nuove ephemeral key per connessione.

## 6. Stale routing

Un record vecchio ma autentico può indicare una route non più valida.

Difese:

- TTL corto;
- candidate expiration;
- preferenza per route update ricevuti in-session;
- read-before-write;
- registro usato solo dopo fallimento dei percorsi locali consentiti dalla policy.

## 7. Rendezvous metadata

Un registro pubblico può rendere osservabili tempi e pattern delle scritture.

Mitigazioni:

- slot opachi;
- capability casuali al primo contatto;
- pair secret dopo l'handshake;
- slot rotanti;
- payload cifrato;
- niente DeviceID/IP nel value leggibile quando evitabile;
- scritture solo dopo perdita completa del route;
- niente cancellazione on-chain dopo successo.

Queste misure non eliminano completamente traffic analysis o correlazioni temporali.

## 8. Network identity leakage

Un indirizzo IP non è un'identità Freedom, ma un direct path può esporre l'endpoint di rete del peer.

Requisiti:

- il direct path non deve essere obbligatorio;
- il client deve poter usare relay o percorsi shielded/multi-hop quando la privacy di rete è prioritaria;
- `DeviceID` non deve essere usato come identificatore di routing pubblico quando non necessario;
- identificatori di trasporto e capability devono essere minimizzati, limitati nel tempo o nel contesto quando possibile;
- i log non devono creare mapping DeviceID/IP persistenti senza necessità tecnica.

Freedom non promette che un peer remoto non possa conoscere l'IP quando viene scelta una connessione diretta.

## 9. Global traffic analysis

Un avversario capace di osservare contemporaneamente grandi porzioni della rete può tentare correlazioni usando:

- timing;
- volume;
- direzione;
- durata delle connessioni;
- pattern di rendezvous;
- ingressi/uscite di relay.

E2EE protegge il contenuto ma non elimina automaticamente questi metadati.

Percorsi multi-hop, padding, batching o transport camouflage possono ridurre alcuni segnali, ma non devono essere presentati come garanzia di anonimato contro un global passive adversary.

## 10. Censura e IP blocking

Un avversario di rete può bloccare:

- IP noti di relay;
- endpoint RPC;
- domini di bootstrap;
- provider specifici;
- firme di protocollo rilevabili;
- intere classi di trasporto.

Mitigazioni architetturali:

- provider RPC multipli;
- relay multipli e sostituibili;
- bootstrap multipli;
- path diversity;
- transport alternativi;
- possibilità di disabilitare il direct path;
- supporto futuro a bridge/relay non facilmente enumerabili;
- nessun IP o dominio singolo come requisito permanente.

Freedom mira a essere resistente al blocco di componenti individuali, non a garantire comunicazione se l'avversario elimina ogni forma di connettività disponibile.

### 10.1 Adaptive interference detection

Il registro/rendezvous può ridurre l'ambiguità tra "peer offline" e "peer recentemente attivo ma data path non disponibile".

Dopo la perdita completa del percorso, i peer possono usare slot pairwise opachi per pubblicare `RecoveryBeacon` cifrati e a TTL breve.

Un beacon recente indica **attività recente sul control-plane**, non prova presenza perfetta in tempo reale.

Pattern di interesse:

```text
connettività generale locale       OK
almeno un registry/RPC             OK
beacon recente del peer            OK
data path corrente                 FAIL
```

Questo pattern può giustificare lo stato `INTERFERENCE_OR_ROUTE_FAILURE_SUSPECTED` e attivare failover automatico.

Non prova però:

- chi stia causando il blocco;
- che il blocco sia intenzionale;
- che il peer sia sotto sorveglianza;
- che un osservatore passivo esista.

Un global passive adversary può monitorare senza produrre un segnale rilevabile.

Rischi aggiuntivi introdotti dai beacon:

- pattern temporali osservabili on-chain/provider;
- chain write spam;
- battery/network cost;
- false positive se il data path è semplicemente instabile;
- stale beacon interpretati male.

Mitigazioni:

- beacon solo dopo failure o policy esplicita;
- TTL breve;
- slot pairwise opachi e rotanti;
- payload cifrato;
- read-before-write;
- rate limit/backoff;
- soglie su più segnali, non un singolo timeout;
- stop delle write appena una sessione viene ristabilita.

Dettagli: [`ADAPTIVE_DEFENSE.md`](ADAPTIVE_DEFENSE.md).

## 11. Malicious RPC

Un RPC può mentire, omettere dati, rispondere con dati stale o rifiutare richieste.

Difese progressive:

- provider multipli;
- fallback;
- chain finality awareness;
- proof/light-client verification dove implementata;
- cache verificata con policy di freshness.

L'RPC non sostituisce mai la firma dell'endpoint.

## 12. Malicious fee relayer

Un fee relayer può:

- rifiutare una richiesta;
- ritardarla;
- censurare alcuni utenti;
- osservare che una determinata richiesta on-chain viene inoltrata.

Non deve poter:

- ottenere la private identity key;
- firmare come DeviceID;
- modificare silenziosamente un'operazione firmata senza invalidarne l'autorizzazione;
- diventare requisito unico del protocollo.

Difese:

- più fee relayer indipendenti;
- formato di richiesta autenticato;
- rate limiting senza autorità sull'identità;
- possibilità di usare meccanismi di pagamento alternativi compatibili.

La private key del fee relayer non deve essere incorporata nel client distribuito.

## 13. Malicious relay

Un relay può:

- droppare;
- ritardare;
- correlare timing e volume;
- rifiutare connessioni;
- tentare replay;
- modificare ciphertext.

Non deve poter:

- decifrare payload applicativi;
- generare messaggi autenticati validi;
- impersonare gli endpoint;
- derivare le session key.

Difese:

- E2EE endpoint-to-endpoint;
- AEAD;
- sequence;
- possibilità di cambiare relay;
- relay/path diversity;
- limiti di fiducia espliciti.

## 14. Relay resource exhaustion

Freedom non offre storage persistente sui relay.

Ogni relay deve imporre:

- maximum frame size;
- maximum buffer per circuit;
- maximum concurrent circuits;
- rate limit;
- TTL;
- hop limit;
- timeout inattività;
- eventuali capability/quota.

Questo riduce l'utilità della rete come storage abuse target.

## 15. Chain write spam

Il rendezvous non deve diventare una primitive di scrittura illimitata.

Difese:

- fee/storage economics della chain;
- record bounded;
- slot aggiornabili;
- TTL;
- client read-before-write;
- backoff;
- limiti contrattuali compatibili con il modello scelto.

Recovery beacon e policy di resilienza devono rispettare gli stessi limiti e non trasformarsi in heartbeat continui.

## 16. Contact spam

Conoscere un DeviceID non deve necessariamente fornire capability illimitata di contatto.

Il contact bootstrap può usare `rendezvous_capability` casuale, temporanea o one-time.

Il client può inoltre applicare contacts-only, request approval, local block list e rate limits.

Non esiste una blacklist globale necessaria al protocollo.

## 17. QR theft/copy

Copiare un QR non permette impersonation perché il QR non contiene la private key.

Può tuttavia dare accesso alla capability di primo rendezvous finché valida.

Per questo le capability sensibili devono poter scadere, essere ruotate, essere one-shot o essere ignorate/revocate secondo la policy prevista.

## 18. Device theft

Se un attacker ottiene accesso alla private identity key può impersonare il device finché la chiave non viene revocata/ruotata.

Mitigazioni platform:

- Android Keystore;
- Secure Enclave/Keychain su Apple quando applicabile;
- protezione schermo/biometria opzionale;
- key rotation;
- revocation.

La recovery completa dell'identità è un sottoprogetto separato.

## 19. Client/supply-chain compromise

E2EE non protegge contro un client legittimamente firmato ma malevolo che legge plaintext o private key prima/dopo la cifratura.

Mitigazioni richieste/progressive:

- protezione forte delle signing key;
- review del codice;
- build riproducibili dove praticabile;
- distribuzione verificabile;
- aggiornamenti firmati;
- minimizzazione delle dipendenze privilegiate.

Il controllo di un relay o RPC non equivale al controllo del client; il compromesso della supply chain è invece un livello di minaccia separato e più grave.

## 20. Key compromise

Le identity key e le session key devono essere separate.

Ogni sessione usa nuovo materiale effimero. Le media key devono essere separate dalle messaging key.

Una futura ratchet construction può migliorare forward secrecy/post-compromise properties; la scelta definitiva deve essere standard e sottoposta a review crittografica.

## 21. Downgrade

Versione e suite sono parte del transcript autenticato.

Un attacker non deve poter forzare una suite o versione inferiore senza causare authentication failure.

## 22. Eclipse / peer isolation

Un attacker che controlla molte fonti di bootstrap/routing può tentare di isolare un device.

Mitigazioni:

- fonti bootstrap multiple;
- peer diversity;
- relay diversity;
- cache di peer indipendenti;
- confronto di informazioni;
- verifica del registro separata dal routing;
- nessun provider unico richiesto.

## 23. Network reachability failures

Freedom deve poter degradare attraverso più classi di percorso consentite dalla policy:

```text
direct / NAT traversal / relay / shielded path -> rendezvous recovery
```

Nessuna tecnica garantisce comunicazione se il dispositivo non dispone di connettività sufficiente.

## 24. Offline recipient e synchronous delivery

Freedom è sincrono by design.

Se il destinatario non è raggiungibile o non esiste una sessione autenticata attiva, il protocollo base non accoda il messaggio per consegna futura e non lo replica su peer casuali, relay persistenti o blockchain.

Questo limita storage exhaustion, retention non desiderata, proliferazione di ciphertext e responsabilità dei relay.

Il trade-off è intenzionale: la consegna richiede presenza contemporanea degli endpoint.

## 25. Live / ephemeral mode

In modalità Live il client può evitare la persistenza locale della cronologia e distruggere lo stato effimero previsto quando l'utente esce dalla chat o termina la sessione.

Questa proprietà riguarda il client locale e non impedisce a un peer remoto, sistema operativo compromesso, screenshot o registrazione di conservare il contenuto ricevuto.

## 26. Logging

Production client e relay non devono loggare di default:

- plaintext;
- private key;
- session key;
- rendezvous secret;
- attachment key;
- intero contact graph;
- correlazioni DeviceID/IP non necessarie;
- contenuti Live destinati a non essere persistiti.

## 27. Non-goals di sicurezza

Freedom non pretende di:

- nascondere ogni metadato a un avversario globale capace di osservare l'intera rete;
- rendere un direct connection invisibile ai due peer;
- garantire disponibilità in assenza completa di connettività;
- impedire a un destinatario legittimo di copiare ciò che riceve;
- essere impossibile da censurare in senso assoluto;
- rilevare in modo affidabile una sorveglianza passiva invisibile.

L'obiettivo è più concreto: **nessun singolo server, relay, RPC, fee relayer, provider, IP o percorso deve costituire da solo un punto unico dal quale controllare o interrompere Freedom.**
