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

Le regole degli store non devono introdurre una master key, un server centrale di messaggistica o modificare automaticamente il wire protocol.

## 2. Posizionamento del prodotto

Descrizione neutra consigliata:

> Freedom è un protocollo decentralizzato di comunicazione con identità crittografiche, percorsi sostituibili e cifratura end-to-end.

Evitare di presentare il client ufficiale come random chat, anonymous stranger matching, wallet/trading app o strumento con funzionalità core non dichiarate allo store.

I contatti vengono stabiliti esplicitamente tramite QR, link o altro scambio intenzionale. Freedom non richiede un `DeviceID` globale user-facing.

## 3. Identity e block/report

Il modello canonico è:

```text
RootIdentity             -> ownership/recovery
DeviceKey                -> device authorization
DeviceRecordCommitment   -> control-plane opaco
PairwiseContactAlias     -> relazione specifica
```

Il client deve permettere block/report del **contatto logico** o della relazione pairwise, non richiedere un global DeviceID.

Il blocco è locale al client e non crea una blacklist globale nel protocollo.

Dettagli: [`IDENTITY_MODEL.md`](IDENTITY_MODEL.md).

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

- pairwise contact/reference proof;
- current device authorization proof se rilevante;
- messaggi scelti dall'utente;
- timestamp/logical sequence;
- firme/prove applicabili;
- media scelti esplicitamente.

La normale conversazione rimane E2EE e non viene automaticamente inviata a un moderation server.

## 5. Blockchain e crypto policy

L'uso di NEAR è infrastrutturale:

```text
RootIdentity / opaque device record state
key rotation / revocation
pairwise fallback rendezvous
entitlement / security manifests
```

Il client ufficiale non deve aggiungere senza necessità trading, token portfolio, exchange, NFT, investimento o custodia di asset.

## 6. QR

Freedom usa QR per flussi espliciti e separati:

```text
Contact QR
  root identity proof
  contact capability
  optional bootstrap device/route proof

Install QR
  release/bootstrap descriptor per ottenere Freedom
```

Il Contact QR non è un meccanismo di pagamento.

L'Install QR deve essere utilizzabile da fotocamera/browser di sistema perché il destinatario può non avere Freedom installato.

Dettagli: [`APP_DISTRIBUTION.md`](APP_DISTRIBUTION.md).

## 7. Privacy by design

Il client ufficiale dovrebbe evitare per default:

- numero di telefono obbligatorio;
- email obbligatoria;
- upload automatico della rubrica;
- advertising ID;
- analytics non necessarie;
- logging del plaintext;
- centralizzazione del contact graph;
- global DeviceID usato come routing/contact identifier.

Qualsiasi telemetria futura deve essere separata dal protocollo, documentata e minimizzata.

## 8. Account e cancellazione

Freedom usa RootIdentity e device authorization, non necessariamente un account web tradizionale.

Il client deve fornire un flusso chiaro per:

```text
revoke current device record
remove local keys
remove local conversations
remove cached media
remove local contacts
request deletion of optional service-side data
```

La blockchain è immutabile; per questo non deve contenere PII evitabile. La revoca rende la DeviceKey/record non valido per nuovi handshake senza pretendere di cancellare retroattivamente la storia della chain.

## 9. Android

Il client Android deve:

- richiedere solo permessi necessari;
- dichiarare chiaramente accesso alla rete locale quando richiesto;
- usare foreground service soltanto per casi conformi e visibili;
- permettere di interrompere relay/background mode;
- non eseguire funzionalità di rete nascoste rispetto alla descrizione del prodotto.

## 10. Relay mode su mobile

Se il client permette al device di diventare relay:

- deve essere opt-in;
- deve mostrare stato e consumo;
- deve avere limiti configurabili;
- deve poter essere disattivato;
- non deve conservare messaggi;
- non deve diventare un exit proxy Internet automaticamente;
- deve rispettare limitazioni background/energia della piattaforma.

Il relay protocol resta interoperabile con nodi desktop/server/community non distribuiti tramite mobile store.

## 11. Google Play build: app distribution

La build Google Play deve trattare Google Play come percorso di install/update della build store.

`Share Freedom` può mostrare un QR che porta alla listing ufficiale o a un bootstrap web che seleziona lo store appropriato.

La build Play non deve basare il proprio normale funzionamento su APK Freedom duplicato negli asset per self-update, installazione silenziosa o bypass del meccanismo update dello store.

Comportamento predefinito:

```text
Share Freedom
 -> official Play listing / compliant bootstrap
```

Il protocollo Freedom resta indipendente da questa scelta del client store.

## 12. Freedom Direct build: peer/relay distribution

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

L'installazione deve rimanere user-driven. Non assumere silent install su normali dispositivi consumer.

## 13. Anti-fake app / signing

Prima dell'installazione Direct devono concordare:

```text
FreedomRelease signatures
artifact SHA-256
package ID
version code
signing certificate / authorized lineage
SecurityPolicy
```

Il primo sideload richiede un trust anchor indipendente dal peer/relay, perché un'app farlocca può essere firmata con una propria chiave e usare nome/icona simili.

Possibili trust anchor: store, bootstrap web autenticato, release-root fingerprint verificato out-of-band o verifier con root pinned.

Il QR non deve poter ridefinire silenziosamente la chiave ufficiale Freedom.

Dettagli: [`APP_DISTRIBUTION.md`](APP_DISTRIBUTION.md) e [`EMERGENCY_UPDATES.md`](EMERGENCY_UPDATES.md).

## 14. iOS background

iOS limita l'esecuzione di rete persistente in background.

Freedom non deve fingere che un iPhone possa essere sempre un peer/listener raggiungibile.

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

Il signaling applicativo resta dentro Freedom E2EE; le API Apple gestiscono lifecycle/presentazione, non autenticazione RootIdentity/DeviceKey.

## 16. Export compliance crittografia

Prima della distribuzione iOS va completata la classificazione/export compliance prevista dalla piattaforma per l'uso di crittografia.

Freedom deve usare algoritmi standard, documentati e implementazioni consolidate.

## 17. Store review mode

Per facilitare la review deve esistere una procedura riproducibile:

- contatto/root proof di test;
- Contact QR di test valido;
- peer di review raggiungibile;
- istruzioni per aggiungere il contatto;
- possibilità di provare messaggi;
- possibilità di provare block/report;
- descrizione chiara di cosa viene scritto on-chain;
- descrizione della differenza tra Contact QR e Install QR;
- per Play build, indicazione che il percorso install/update resta conforme allo store.

## 18. Protocol independence

Un cambiamento di policy Google o Apple può richiedere modifiche al client ufficiale, ma non deve cambiare automaticamente:

- RootIdentity / DeviceKey authorization model;
- pairwise aliases;
- autenticazione tra endpoint;
- E2EE;
- relay semantics;
- ChainAdapter interface;
- interoperabilità con client non-store;
- autenticità del `FreedomRelease`;
- possibilità per una Direct build di usare sorgenti artifact indipendenti compatibili con la piattaforma.

Questa separazione è un requisito architetturale, non soltanto organizzativo.
