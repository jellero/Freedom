# Freedom — Store Compliance Architecture

## 1. Separazione fondamentale

Freedom Protocol e i client distribuiti sugli store sono livelli distinti.

```text
Freedom Protocol
  identity / rendezvous / routing / E2EE
        |
        +-- Android client -> Google Play compliance
        |
        +-- iOS client     -> App Store compliance
```

Le regole degli store non devono introdurre una master key, un server centrale di messaggistica o un requisito di moderazione nel wire protocol.

## 2. Posizionamento del prodotto

Descrizione neutra consigliata:

> Freedom è un protocollo decentralizzato di comunicazione con identità crittografiche e cifratura end-to-end.

Evitare di presentare il client ufficiale come:

- random chat;
- anonymous stranger matching;
- wallet/trading application;
- strumento il cui valore principale dipende da funzioni non dichiarate allo store.

I contatti vengono stabiliti esplicitamente tramite DeviceID, QR, link o altro scambio intenzionale.

## 3. UGC e comunicazioni 1:1

Il client ufficiale deve prevedere almeno:

- blocco di un DeviceID;
- segnalazione;
- termini d'uso;
- privacy policy;
- canale di supporto/contatto;
- gestione delle richieste di contatto.

Il blocco è locale al client e non crea una blacklist globale nel protocollo.

## 4. Reporting compatibile con E2EE

Freedom non possiede una chiave globale di decifratura.

Se un utente decide di segnalare una conversazione:

```text
user selects content
     |
decrypt locally
     |
create explicit report package
     |
send only selected evidence
```

Il report può includere, se utile e tecnicamente verificabile:

- DeviceID segnalato;
- messaggi scelti dall'utente;
- timestamp/logical sequence;
- firme/prove applicabili;
- media scelti esplicitamente.

La normale conversazione rimane E2EE e non viene automaticamente inviata a un moderation server.

## 5. Blockchain e crypto policy

L'uso di NEAR è infrastrutturale:

```text
DeviceID registry
key rotation/revocation
fallback rendezvous
```

Il client ufficiale non deve aggiungere senza necessità:

- trading;
- token portfolio;
- exchange;
- NFT;
- investimento;
- custodia di asset per conto dell'utente.

L'obiettivo è evitare che il client di comunicazione venga classificato come prodotto finanziario/crypto diverso dal suo scopo reale.

Il modello di fee mainnet verrà progettato separatamente.

## 6. QR

Freedom usa QR per più flussi espliciti e separati:

```text
Contact QR
  network_id / device_id / rendezvous_capability

Install QR
  release/bootstrap descriptor per ottenere Freedom
```

Il Contact QR non sblocca contenuti digitali acquistati fuori dallo store e non è un meccanismo di pagamento.

L'Install QR deve essere utilizzabile da una fotocamera/browser di sistema perché il destinatario può non avere ancora Freedom installato.

Dettagli: [`APP_DISTRIBUTION.md`](APP_DISTRIBUTION.md).

## 7. Privacy by design

Il client ufficiale dovrebbe evitare per default:

- numero di telefono obbligatorio;
- email obbligatoria;
- upload automatico della rubrica;
- advertising ID;
- analytics non necessarie;
- logging del plaintext;
- centralizzazione del contact graph.

Qualsiasi telemetria futura deve essere separata dal protocollo, documentata e minimizzata.

## 8. Account e cancellazione

Freedom usa DeviceID, non necessariamente un account web tradizionale.

Il client deve comunque fornire un flusso chiaro per:

```text
revoke device identity
remove local keys
remove local conversations
remove cached media
remove local contacts
request deletion of optional service-side data
```

La blockchain è immutabile; per questo non deve contenere PII evitabile.

La revoca rende il DeviceID non valido per nuovi handshake senza pretendere di cancellare retroattivamente la storia della chain.

## 9. Android

Il client Android deve:

- richiedere solo permessi necessari;
- dichiarare chiaramente accesso alla rete locale quando richiesto dalla piattaforma;
- usare foreground service soltanto per casi conformi e visibili all'utente;
- permettere all'utente di interrompere modalità relay/background quando presente;
- non eseguire funzionalità di rete nascoste rispetto alla descrizione del prodotto.

## 10. Relay mode su mobile

Se il client permette al device di diventare relay:

- deve essere opt-in;
- deve mostrare stato e consumo;
- deve avere limiti configurabili;
- deve poter essere disattivato;
- non deve conservare messaggi;
- deve rispettare le limitazioni background/energia della piattaforma.

Il relay protocol resta interoperabile anche con nodi desktop/server/community non distribuiti tramite mobile store.

## 11. Google Play build: app distribution

La build Google Play deve trattare Google Play come percorso di install/update della build store.

`Share Freedom` può mostrare un QR che porta alla listing ufficiale o a un bootstrap web che seleziona lo store appropriato.

La build Play non deve basare il proprio normale funzionamento su:

- APK Freedom duplicato negli asset per redistribuzione/self-update;
- installazione silenziosa;
- bypass del meccanismo update dello store;
- `REQUEST_INSTALL_PACKAGES` usato come semplice meccanismo ordinario di self-update o peer app distribution se il caso d'uso non soddisfa le policy vigenti.

La policy Google Play su `REQUEST_INSTALL_PACKAGES` è restrittiva: l'installazione deve essere esplicitamente avviata dall'utente e l'uso del permesso deve essere direttamente collegato a una funzionalità core ammessa.

Quindi, per evitare che la distribuzione P2P del client diventi un rischio di review, il comportamento predefinito della **Play build** è:

```text
Share Freedom
 -> official Play listing / compliant bootstrap
```

Il protocollo Freedom resta indipendente da questa scelta del client store.

## 12. Freedom Direct build: peer/relay distribution

La build Direct può supportare il flusso completo:

```text
existing Freedom client
 -> Share Freedom
 -> Install QR
 -> peer-local / relay / mirror download
 -> release verification
 -> Android system installer
```

L'APK resta un artifact esterno verificato, non una seconda copia obbligatoria dentro l'app.

Il client Direct può mantenere una cache di release standalone verificate e servirle tramite endpoint temporanei/capability-protected.

L'installazione deve rimanere user-driven. Su Android moderni il client deve verificare se è autorizzato a richiedere installazioni da sorgenti esterne e, quando necessario, indirizzare l'utente alle impostazioni di sistema per autorizzare quella sorgente.

Non assumere silent install su normali dispositivi consumer.

## 13. Anti-fake app / signing

Il download da peer o relay non rende quella sorgente affidabile.

Prima dell'installazione Direct devono concordare:

```text
FreedomRelease signatures
artifact SHA-256
package ID
version code
signing certificate / authorized lineage
SecurityPolicy
```

Per app già installate, la continuità del certificato Android fornisce un'ulteriore barriera agli update firmati con chiavi incompatibili.

Il **primo sideload** richiede però un trust anchor indipendente dal peer/relay, perché un'app farlocca può essere firmata con una propria chiave e usare nome/icona simili.

Possibili trust anchor: store, bootstrap web autenticato, release-root fingerprint verificato out-of-band, verifier con root pinned o peer già considerato genuino.

Il QR non deve poter ridefinire silenziosamente la chiave ufficiale Freedom.

Dettagli: [`APP_DISTRIBUTION.md`](APP_DISTRIBUTION.md) e [`EMERGENCY_UPDATES.md`](EMERGENCY_UPDATES.md).

## 14. iOS background

iOS limita l'esecuzione di rete persistente in background.

Freedom non deve fingere che un iPhone possa essere sempre un peer/listener raggiungibile.

Eventuali servizi di wake della piattaforma vengono trattati come ottimizzazione del client:

```text
platform wake hint
      |
app wakes when permitted
      |
Freedom protocol reconnects
```

Il wake provider non riceve plaintext né diventa identity authority.

## 15. Chiamate iOS

Il client iOS integra le API di sistema previste per VoIP/call UX quando richiesto.

Il signaling applicativo resta dentro Freedom E2EE; le API Apple gestiscono lifecycle e presentazione della chiamata, non autenticazione del DeviceID.

## 16. Export compliance crittografia

Prima della distribuzione iOS va completata la classificazione/export compliance prevista dalla piattaforma per l'uso di crittografia.

Freedom deve usare algoritmi standard, documentati e implementazioni consolidate, evitando primitive proprietarie non necessarie.

## 17. Store review mode

Per facilitare la review deve esistere una procedura riproducibile:

- DeviceID di test;
- QR di test valido;
- peer di review raggiungibile;
- istruzioni per aggiungere il contatto;
- possibilità di provare messaggi;
- possibilità di provare block/report;
- descrizione chiara di cosa viene scritto on-chain;
- descrizione chiara della differenza tra Contact QR e Install QR;
- per Play build, indicazione che il percorso di install/update resta conforme allo store.

## 18. Protocol independence

Un cambiamento di policy Google o Apple può richiedere modifiche al client ufficiale, ma non deve cambiare automaticamente:

- formato DeviceID;
- autenticazione tra endpoint;
- E2EE;
- relay semantics;
- ChainAdapter interface;
- interoperabilità con client non-store;
- autenticità del `FreedomRelease`;
- possibilità per una Direct build di usare sorgenti artifact indipendenti compatibili con la piattaforma.

Questa separazione è un requisito architetturale, non soltanto organizzativo.
