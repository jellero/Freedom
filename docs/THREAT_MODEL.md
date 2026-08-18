# Freedom — Threat Model

## 1. Assunzioni

Freedom assume una rete non fidata.

Un avversario può:

- osservare traffico;
- controllare alcuni peer o relay;
- restituire informazioni di routing false;
- bloccare, ritardare, duplicare o riordinare pacchetti;
- tentare impersonation;
- tentare replay;
- creare molti nodi;
- controllare uno o più endpoint RPC;
- tentare di saturare relay, chain writes o risorse locali.

Freedom non assume che relay, NAT observer, bootstrap node o RPC siano fidati per autenticare un DeviceID.

## 2. Trust anchors

Le radici di fiducia sono limitate a:

- private key locale del device;
- stato blockchain verificato del `DeviceID`;
- primitive crittografiche standard;
- stato locale autenticato derivato da sessioni precedenti.

Un IP, un relay o un risultato di discovery non è un trust anchor.

## 3. Device impersonation

Attacco:

```text
Mallory afferma di essere DeviceID_B
```

Difesa:

1. A risolve `DeviceID_B` tramite chain;
2. ottiene la current public key e key epoch;
3. l'handshake richiede una prova firmata/derivata dal possesso della private key;
4. il transcript lega DeviceID, epoch ed ephemeral keys.

Senza la private key corrente B, Mallory non deve poter completare l'handshake.

## 4. Man-in-the-middle

La semplice cifratura ECDH non basta se l'ephemeral exchange non è autenticato.

Il transcript deve includere entrambe le identità e le chiavi effimere ed essere autenticato bilateralmente.

Qualsiasi sostituzione di:

- DeviceID;
- key epoch;
- ephemeral key;
- nonce;
- version;
- suite;

fa fallire l'handshake.

## 5. Replay

Livelli di replay:

### Rendezvous

Mitigato da:

- `sequence`;
- `expires_at`;
- slot rotanti;
- nonce nel payload.

### Session frames

Mitigato da sequence monotoni/finestra anti-replay autenticata con AEAD.

### Handshake

Mitigato da nonce casuali e nuove ephemeral key per connessione.

## 6. Stale routing

Un record vecchio ma autentico può indicare un route non più valido.

Difese:

- TTL corto;
- sequence;
- candidate expiration;
- preferenza per route update ricevuti in-session;
- chain usata solo dopo fallimento dei percorsi locali.

## 7. Rendezvous metadata

Una blockchain pubblica può rendere osservabili tempi e pattern delle scritture.

Mitigazioni:

- slot opachi;
- capability casuali al primo contatto;
- pair secret dopo l'handshake;
- slot rotanti;
- payload cifrato;
- niente DeviceID/IP nel value leggibile quando evitabile;
- scritture solo dopo perdita completa del route;
- niente cancellazione on-chain dopo successo.

Non si assume che queste misure eliminino completamente traffic analysis o correlazioni temporali.

## 8. Malicious RPC

Un RPC può:

- mentire sullo stato;
- omettere dati;
- rispondere con dati stale;
- rifiutare richieste.

Difese progressive:

- provider multipli;
- fallback;
- chain finality awareness;
- proof/light-client verification dove implementata;
- cache verificata con policy di freshness.

L'RPC non viene mai usato come sostituto della firma dell'endpoint.

## 9. Relay malicious

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
- multipath futuro;
- limiti di fiducia espliciti.

## 10. Relay resource exhaustion

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

## 11. Chain write spam

Il rendezvous non deve diventare una primitive di scrittura illimitata.

Difese:

- fee/storage economics della chain;
- record bounded;
- slot aggiornabili;
- TTL;
- client read-before-write;
- backoff;
- limiti contrattuali compatibili con il modello scelto.

## 12. Contact spam

Conoscere un DeviceID non deve necessariamente fornire capability illimitata di contatto.

Il contact bootstrap può usare:

```text
rendezvous_capability
```

casuale, temporanea o one-time.

Il client può inoltre applicare:

- contacts-only;
- request approval;
- local block list;
- rate limits.

Non esiste una blacklist globale necessaria al protocollo.

## 13. QR theft/copy

Copiare un QR non permette impersonation perché il QR non contiene la private key.

Può tuttavia dare accesso alla capability di primo rendezvous finché valida.

Per questo le capability sensibili devono poter:

- scadere;
- essere ruotate;
- essere one-shot;
- essere revocate localmente/ignorate.

## 14. Device theft

Se un attacker ottiene accesso alla private identity key può impersonare il device finché la chiave non viene revocata/ruotata.

Mitigazioni platform:

- Android Keystore;
- Secure Enclave/Keychain su Apple quando applicabile;
- protezione schermo/biometria opzionale;
- key rotation;
- revocation.

La recovery completa dell'identità è un sottoprogetto separato e non deve essere improvvisata.

## 15. Key compromise

Le identity key e le session key devono essere separate.

Ogni sessione usa nuovo materiale effimero. Le media key devono essere separate dalle messaging key.

Una futura ratchet construction può migliorare forward secrecy/post-compromise properties; la scelta definitiva deve essere standard e sottoposta a review crittografica.

## 16. Downgrade

Versione e suite sono parte del transcript autenticato.

Un attacker non deve poter forzare una suite o versione inferiore senza causare authentication failure.

## 17. Eclipse / peer isolation

Un attacker che controlla molte fonti di bootstrap/routing può tentare di isolare un device.

Mitigazioni future:

- fonti bootstrap multiple;
- peer diversity;
- relay diversity;
- cache di peer indipendenti;
- confronto di informazioni;
- chain verification separata dal routing.

## 18. Network reachability failures

Freedom deve degradare attraverso più classi di percorso:

```text
direct -> NAT traversal -> relay -> rendezvous recovery
```

Nessuna di queste tecniche garantisce comunicazione se il dispositivo non dispone di connettività di rete sufficiente.

## 19. Offline recipient

Se il destinatario è offline, Freedom non replica messaggi su peer casuali.

Questo limita:

- storage exhaustion;
- retention non desiderata;
- proliferazione di ciphertext;
- responsabilità dei relay.

Il trade-off è esplicito: la consegna richiede che entrambi gli endpoint siano online contemporaneamente.

## 20. Logging

Production client e relay non devono loggare di default:

- plaintext;
- private key;
- session key;
- rendezvous secret;
- attachment key;
- intero contact graph;
- correlazioni DeviceID/IP non necessarie.

## 21. Non-goals di sicurezza

Freedom non pretende di nascondere ogni metadato a un avversario globale capace di osservare l'intera rete.

Non può garantire disponibilità in assenza completa di connettività.

Non può proteggere il plaintext dopo che un endpoint legittimo lo ha volontariamente esportato, copiato o segnalato.

Questi limiti devono essere documentati e non mascherati da claim assoluti.
