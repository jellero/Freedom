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
- tentare impersonation e replay;
- creare molti nodi/identità;
- controllare uno o più endpoint RPC;
- tentare di saturare relay, chain writes o risorse locali;
- tentare di ottenere reward Relay Contributor senza fornire capacità utile;
- tentare di usare un relay come proxy generico verso Internet;
- distribuire client modificati se compromette la supply chain o il sistema di aggiornamento.

Freedom non assume che relay, NAT observer, bootstrap node, fee relayer, RPC o provider commerciale siano fidati per autenticare una persona o un device.

## 2. Trust anchors

Le radici di fiducia sono limitate a:

- RootIdentity/private root material locale secondo ruolo;
- DeviceKey privata locale;
- autorizzazione verificabile della DeviceKey corrente;
- stato verificato del control-plane;
- primitive crittografiche standard;
- stato locale autenticato derivato da relazioni/sessioni precedenti.

Un IP, relay, RPC, fee relayer, risultato di discovery, payment provider o provider commerciale non è un trust anchor.

La funzione di registro/control-plane verificabile è fondamentale per il trust model attuale; una blockchain specifica come NEAR non deve esserlo.

## 3. Identity correlation

Freedom non usa un `DeviceID` globale nel network layer.

La separazione canonica è:

```text
RootIdentity             ownership/recovery
DeviceRecordCommitment   lookup/rotation/revocation opaco
PairwiseContactAlias     relazione specifica
TransportToken           circuito/route temporaneo
```

Questa separazione riduce la superficie di correlazione ma non la elimina.

Un osservatore può ancora tentare di correlare:

- timing di device activation/revocation;
- timing di rendezvous/recovery;
- uso dello stesso RPC/provider;
- dimensioni delle transazioni;
- indirizzi IP/ASN;
- ingress/egress dei relay;
- payment/entitlement events.

Mitigazioni:

- nessun global device identifier nei frame applicativi o relay;
- commitment opachi nel control-plane;
- alias differenti per relazioni differenti;
- slot rendezvous derivati da secret pairwise;
- token di trasporto temporanei;
- payload del rendezvous cifrato;
- riduzione e batching prudente delle write dove compatibile;
- provider/route diversity.

Un commitment stabile **non è automaticamente anonimo**. La metadata resistance deve essere misurata empiricamente.

## 4. Device impersonation

Attacco: Mallory tenta di presentare una DeviceKey non autorizzata come device corrente di Bob.

Difesa:

1. Alice possiede la RootIdentity/contact identity attesa per Bob;
2. verifica che la DeviceKey presentata sia autorizzata da quella RootIdentity e non revocata;
3. verifica `key_epoch`/freshness tramite `ChainAdapter` o cache verificata;
4. l'handshake richiede prova del possesso della DeviceKey;
5. il transcript lega pairwise aliases, device authorization proof, epoch ed ephemeral keys.

Senza Root authorization valida e private DeviceKey corrente, Mallory non deve completare l'handshake.

## 5. Man-in-the-middle

La semplice cifratura ECDH non basta se l'ephemeral exchange non è autenticato.

Il transcript deve includere entrambe le identità pairwise attese, autorizzazioni device, epoch, ephemeral key, nonce, versione e suite.

Qualsiasi sostituzione deve causare authentication failure.

## 6. Replay

### Rendezvous

Freshness verificata tramite:

- slot pairwise atteso;
- stato del registro sufficientemente verificato/finalizzato;
- `expires_at`;
- nonce del rendezvous;
- autenticazione/decifrabilità del payload;
- materiale effimero valido per il tentativo corrente.

### Session frames

Mitigato da sequence monotoni/finestra anti-replay autenticata con AEAD.

### Handshake

Mitigato da nonce casuali e nuove ephemeral key per connessione.

## 7. Stale routing

Un record vecchio ma autentico può indicare una route non più valida.

Difese:

- TTL corto;
- candidate expiration;
- preferenza per route update ricevuti in-session;
- read-before-write;
- control-plane usato solo dopo fallimento dei percorsi locali consentiti dalla policy.

## 8. Rendezvous metadata

Un registro pubblico può rendere osservabili tempi e pattern delle write.

Mitigazioni:

- slot pairwise opachi;
- capability casuali al primo contatto;
- pair secret dopo l'handshake;
- slot rotanti;
- payload cifrato;
- niente RootIdentity, DeviceRecordCommitment o IP in value leggibile quando evitabile;
- write solo dopo perdita completa del route;
- niente cancellazione on-chain dopo successo quando creerebbe segnali aggiuntivi.

Queste misure non eliminano completamente traffic analysis o correlazioni temporali.

## 9. Network identity leakage

Un indirizzo IP non è un'identità Freedom, ma direct path o partecipazione come relay possono esporre endpoint di rete a peer/hop adiacenti.

Requisiti:

- direct path non obbligatorio;
- relay/shielded/multi-hop quando la privacy di rete è prioritaria;
- RootIdentity e DeviceRecordCommitment non usati come routing identifiers;
- transport token/capability minimizzati e temporanei;
- log senza mapping persistenti identity/IP non necessari;
- `DEVICE_RELAY` deve informare l'utente dell'aumento di esposizione come nodo di rete.

Freedom non promette che un peer remoto non possa conoscere l'IP quando viene scelta una connessione diretta, né che un device relay sia invisibile ai nodi adiacenti.

## 10. Global traffic analysis

Un avversario globale può correlare:

- timing;
- volume;
- direzione;
- durata delle connessioni;
- pattern di rendezvous;
- ingressi/uscite relay.

E2EE protegge il contenuto ma non elimina automaticamente questi metadati.

Multi-hop, padding, batching o transport camouflage possono ridurre alcuni segnali, ma non sono garanzia di anonimato contro un global passive adversary.

## 11. Censura e IP blocking

Un avversario può bloccare:

- IP noti di relay;
- endpoint RPC;
- domini di bootstrap;
- provider specifici;
- firme di protocollo rilevabili;
- intere classi di trasporto.

Mitigazioni:

- provider RPC multipli;
- relay multipli e sostituibili;
- device/community relay temporanei;
- bootstrap multipli;
- path diversity;
- transport alternativi;
- direct disabilitabile;
- futuro supporto bridge/relay non facilmente enumerabili;
- nessun IP/dominio singolo come requisito permanente.

Freedom mira a resistere al blocco di componenti individuali, non garantisce comunicazione se l'avversario elimina ogni connettività disponibile.

## 12. Adaptive interference detection

Dopo perdita completa del percorso, i peer possono usare slot pairwise opachi per `RecoveryBeacon` cifrati e a TTL breve.

```text
connettività generale locale       OK
almeno un registry/RPC             OK
beacon recente del peer            OK
data path corrente                 FAIL
```

Questo può giustificare `INTERFERENCE_OR_ROUTE_FAILURE_SUSPECTED` e failover automatico.

Non prova:

- chi causi il blocco;
- che il blocco sia intenzionale;
- che esista sorveglianza passiva.

Mitigazioni dei beacon:

- solo dopo failure o policy esplicita;
- TTL breve;
- slot pairwise opachi/rotanti;
- payload cifrato;
- read-before-write;
- rate limit/backoff;
- soglie su più segnali;
- stop write appena la sessione è ristabilita.

Dettagli: [`ADAPTIVE_DEFENSE.md`](ADAPTIVE_DEFENSE.md).

## 13. Malicious RPC

Un RPC può mentire, omettere dati, rispondere stale o rifiutare richieste.

Difese progressive:

- provider multipli;
- fallback;
- chain finality awareness;
- proof/light-client verification dove implementata;
- cache verificata con freshness policy.

L'RPC non sostituisce mai la firma dell'endpoint.

## 14. Malicious fee relayer

Un fee relayer può rifiutare, ritardare, censurare richieste o osservare operazioni on-chain.

Non deve poter:

- ottenere Root private material o DeviceKey;
- firmare come endpoint;
- modificare silenziosamente un'operazione firmata;
- diventare requisito unico.

Difese: relayer multipli, richieste autenticate, rate limiting e meccanismi alternativi compatibili.

## 15. Malicious relay

Un relay — incluso un `DEVICE_RELAY` — può:

- droppare;
- ritardare;
- correlare timing/volume;
- rifiutare connessioni;
- tentare replay;
- modificare ciphertext;
- mentire sulla capacità/disponibilità.

Non deve poter:

- decifrare payload applicativi;
- generare messaggi autenticati validi;
- impersonare endpoint;
- derivare session key.

Difese:

- E2EE endpoint-to-endpoint;
- AEAD;
- sequence;
- capability/token di circuito;
- relay/path diversity;
- possibilità di cambiare relay.

Essere un dispositivo Freedom non rende il relay più fidato di un VPS anonimo.

## 16. Relay come open proxy

Il relay Freedom **non è un proxy Internet generico**.

Non deve consentire arbitrariamente:

```text
client -> relay -> qualsiasi IP:porta Internet
```

nel protocollo relay base.

Difese:

- packet format obbligatorio;
- capability di circuito;
- next-hop controllato dal fabric/protocollo;
- hop limit/TTL;
- nessun arbitrary TCP CONNECT generico;
- rate limit/circuit quotas.

Questo è particolarmente importante per `DEVICE_RELAY`: il proprietario non deve trasformare inconsapevolmente il telefono in un exit proxy.

Un eventuale futuro **Gateway/Internet egress** deve essere un ruolo separato, esplicitamente opt-in e con operatori `MANAGED/PRIVATE/EGRESS`, non una conseguenza automatica di `DEVICE_RELAY`.

## 17. Relay resource exhaustion

Ogni relay deve imporre:

- maximum frame size;
- maximum buffer per circuit;
- maximum total buffer;
- maximum concurrent circuits;
- rate limit;
- TTL;
- hop limit;
- timeout inattività;
- bandwidth quota;
- eventuali capability/quota.

Per `DEVICE_RELAY` si aggiungono batteria, charging-only opzionale, Wi-Fi only opzionale, rete metered, CPU/RAM, temperatura e background execution.

## 18. Relay Contributor farming

Attacchi:

```text
utente abilita relay senza contribuire
oppure
account controllati generano traffico artificiale
```

Mitigazioni:

- finestre di qualificazione bounded;
- combinazione disponibilità + forwarding utile;
- soglie minime e massime;
- receipt/commitment opachi aggregati;
- rate limit per RootIdentity/device epoch;
- pattern anti-farming senza social graph pubblico;
- scadenza periodica del benefit.

La prova non deve pubblicare peer serviti, contenuto o cronologia dettagliata.

## 19. Chain write spam

Difese:

- fee/storage economics;
- record bounded;
- slot aggiornabili;
- TTL;
- read-before-write;
- backoff;
- limiti contrattuali.

Recovery beacon e policy di resilienza devono rispettare gli stessi limiti.

## 20. Contact spam

Conoscere una RootIdentity o un alias non deve fornire capability illimitata di contatto.

Il contact bootstrap usa `contact_capability` casuale, temporanea o one-shot.

Il client può applicare contacts-only, request approval, local block list e rate limits.

Non esiste una blacklist globale necessaria al protocollo.

## 21. QR theft/copy

Copiare un contact QR non permette impersonation perché il QR non contiene private key. Può però fornire una bootstrap capability finché valida.

Un Recovery QR è materiale sensibile cifrato e richiede la policy separata del Recovery Kit.

Capability sensibili devono poter scadere/ruotare/essere one-shot.

## 22. Device theft

Se un attacker ottiene la DeviceKey può impersonare quel device finché il record non viene revocato/ruotato.

Mitigazioni:

- Android Keystore;
- Secure Enclave/Keychain quando applicabile;
- biometria/protezione schermo opzionale;
- key rotation;
- revocation;
- Recovery Kit/RootIdentity per autorizzare un nuovo device senza clonare la vecchia DeviceKey.

## 23. Client/supply-chain compromise

E2EE non protegge contro un client legittimamente firmato ma malevolo che legge plaintext o private key prima/dopo cifratura.

Mitigazioni:

- protezione signing key;
- review codice;
- build riproducibili dove praticabile;
- distribuzione verificabile;
- update firmati;
- release manifest verificabile;
- minimizzazione dipendenze privilegiate.

Il controllo di relay/RPC non equivale al controllo del client; supply-chain compromise è un livello di minaccia più grave.

## 24. Key compromise

Root keys, DeviceKey, session keys e media keys devono essere separate.

Ogni sessione usa nuovo materiale effimero. Una futura ratchet construction può migliorare forward secrecy/post-compromise properties; la scelta deve essere standard e reviewata.

## 25. Downgrade

Versione e suite sono parte del transcript autenticato. Un attacker non deve poter forzare suite/versione inferiore senza authentication failure.

## 26. Eclipse / peer isolation

Mitigazioni:

- fonti bootstrap multiple;
- peer/relay diversity;
- cache indipendenti;
- confronto informazioni;
- verifica control-plane separata dal routing;
- nessun provider unico richiesto.

## 27. Network reachability failures

Freedom deve degradare attraverso più classi di percorso consentite dalla policy:

```text
direct / NAT traversal / relay / shielded path -> rendezvous recovery
```

Nessuna tecnica garantisce comunicazione senza connettività sufficiente.

## 28. Offline recipient e synchronous delivery

Se il destinatario non è raggiungibile o non esiste una sessione autenticata attiva, il protocollo base non accoda il messaggio per consegna futura e non lo replica su peer casuali, relay persistenti o blockchain.

Questo limita retention, storage exhaustion e proliferazione di ciphertext. Il trade-off è intenzionale: la consegna richiede presenza contemporanea.

## 29. Live mode

Live può evitare la persistenza locale della cronologia e distruggere stato effimero quando termina la sessione.

Questa proprietà riguarda il client locale e non impedisce a peer remoto, OS compromesso, screenshot o registrazione di conservare contenuto ricevuto.

## 30. Logging

Production client e relay non devono loggare di default:

- plaintext;
- Root private material;
- DeviceKey privata;
- session/media keys;
- pair/rendezvous secret;
- intero contact graph;
- mapping identity/IP non necessari;
- contenuti Live destinati a non essere persistiti.

## 31. Non-goals

Freedom non pretende di:

- nascondere ogni metadato a un avversario globale;
- rendere un direct connection invisibile ai due peer;
- garantire disponibilità in assenza completa di connettività;
- impedire a un destinatario legittimo di copiare ciò che riceve;
- essere impossibile da censurare in senso assoluto;
- rilevare in modo affidabile sorveglianza passiva invisibile.

Obiettivo concreto:

> **nessun singolo server, relay, RPC, fee relayer, provider, IP, global DeviceID o percorso deve costituire da solo un punto unico dal quale controllare, correlare facilmente o interrompere Freedom.**
