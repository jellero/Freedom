# Freedom — Store Compliance Architecture

## 1. Separazione fondamentale

Freedom Protocol e i client distribuiti sugli store sono livelli distinti.

```text
Freedom Protocol
  identity / rendezvous / routing / E2EE
        |
        +-- Freedom Communication
        +-- Freedom Gateway optional
        |
        +-- Android client -> Google Play compliance
        `-- iOS client     -> App Store compliance
```

Le regole degli store non devono introdurre una master key, un server centrale di messaggistica o modificare automaticamente il wire protocol.

## 2. Posizionamento del prodotto

Freedom non deve essere descritto come "messenger blockchain".

Descrizione tecnica neutra:

> Freedom è un sistema di comunicazione E2EE live con routing/relay sostituibili e un control-plane verificabile; una capacità Gateway opzionale può usare lo stesso fabric per proteggere e rendere più resiliente il traffico di rete del dispositivo.

Freedom Communication e Gateway devono essere descritti come security boundary differenti.

## 3. Contatti e reporting

I contatti vengono stabiliti esplicitamente tramite QR/link/capability pairwise.

Il client deve prevedere almeno:

- blocco contatto;
- gestione richieste;
- termini/privacy policy;
- supporto;
- reporting esplicito quando richiesto dalle policy applicabili.

Un report di contenuto E2EE deve essere creato localmente solo su azione esplicita dell'utente e includere esclusivamente l'evidenza scelta.

## 4. Identity privacy

Il client non usa un global `DeviceID` come identità di rete.

```text
RootIdentity
DeviceKey
DeviceRecordCommitment
PairwiseContactAlias
TransportToken
```

Evitare per default:

- numero telefono obbligatorio;
- email obbligatoria;
- upload automatico rubrica;
- advertising ID;
- analytics non necessari;
- logging plaintext;
- centralizzazione del contact graph.

## 5. Blockchain e crypto policy

NEAR è infrastruttura/control-plane, non il prodotto venduto all'utente.

Il client di comunicazione non deve aggiungere senza necessità:

- trading;
- portfolio;
- exchange;
- NFT;
- investimento;
- custodia asset.

La blockchain non trasporta chat, media, Gateway payload o APK.

## 6. Contact QR e Install QR

```text
Contact QR
  pairwise/bootstrap identity capability

Install QR
  release/bootstrap descriptor
```

I due flussi sono separati.

L'Install QR deve essere utilizzabile anche da un dispositivo che non ha ancora Freedom.

Dettagli: [`APP_DISTRIBUTION.md`](APP_DISTRIBUTION.md).

## 7. Account/recovery/deletion

Il client deve poter:

```text
revoke current device
remove local keys
remove local conversations
remove cached media
remove local contacts
request deletion of optional service-side data
```

La chain non deve contenere PII evitabile. La revoca invalida device/authorization per nuovi handshake senza pretendere di cancellare la storia immutabile del registro.

## 8. Android relay mode

`DEVICE_RELAY` mobile:

- opt-in;
- stato visibile;
- limiti configurabili;
- disattivabile;
- nessuna mailbox;
- resource bounded;
- non nascosto rispetto alla descrizione del prodotto;
- non è un Internet egress.

Un Relay Contributor non deve trasformare il telefono in un open proxy Internet.

## 9. Freedom Gateway su Android

Freedom Gateway è separato dal relay messenger.

```text
selected apps / whole device
        |
Android VpnService
        |
Freedom encrypted tunnel
        |
relay / bridge / Shield
        |
explicit egress
        |
Internet
```

Android/Google Play consentono l'uso di `VpnService` solo entro le categorie e condizioni previste dalla policy vigente. La build Play deve quindi:

- dichiarare chiaramente l'uso di `VpnService` nella listing;
- mostrare informativa/consenso quando richiesto;
- cifrare i dati dal device all'endpoint del tunnel;
- non usare il traffico di altre app per monetizzazione pubblicitaria/manipolazione;
- presentare Gateway come funzionalità coerente con lo scopo dichiarato dell'app;
- completare la dichiarazione Play Console richiesta;
- essere pronta a separare Gateway in una build/companion differente se la review store lo richiede.

Una restrizione Play non deve rimuovere la capacità dal protocollo/Direct build.

Dettagli tecnici: [`GATEWAY.md`](GATEWAY.md).

## 10. Gateway transparency

Quando Gateway è attivo il client deve mostrare chiaramente:

- stato ON/OFF;
- selected-app vs whole-device;
- egress/path corrente almeno in advanced UI;
- eventuale split tunneling;
- kill-switch/strict mode;
- dati che il servizio tratta secondo la privacy policy.

Non deve dichiarare che il Gateway offre la stessa E2EE endpoint-to-endpoint della chat Freedom.

## 11. Google Play build: app distribution

La build Google Play usa il percorso store conforme per install/update della build store.

```text
Share Freedom
 -> official Play listing / compliant bootstrap
```

Non basare il normale funzionamento della Play build su:

- APK duplicato negli asset per self-update;
- silent install;
- bypass update store;
- uso improprio di `REQUEST_INSTALL_PACKAGES`.

## 12. Freedom Direct build: peer/relay distribution

```text
existing Freedom client
 -> Share Freedom
 -> Install QR
 -> peer-local / relay / mirror
 -> release verification
 -> Android system installer
```

L'APK resta un artifact standalone verificato.

Il client Direct può mantenerne una cache verificata e servirlo tramite endpoint temporaneo/capability.

## 13. Anti-fake app

Prima dell'installazione Direct devono concordare:

```text
FreedomRelease signatures
artifact SHA-256
package ID
version code
signing certificate / lineage
SecurityPolicy
```

Il primo sideload richiede un trust anchor indipendente dal peer/relay.

Il QR non può ridefinire silenziosamente la signing root ufficiale.

## 14. iOS/background

iOS limita network execution persistente in background.

Freedom non deve fingere che un iPhone sia sempre un listener/relay raggiungibile.

Wake hint della piattaforma può essere un'ottimizzazione, non identity authority.

Gateway iOS deve essere progettato separatamente usando le API e entitlement previsti dalla piattaforma e non va promesso sulla base del solo design Android.

## 15. Store review mode

Per review deve esistere una procedura riproducibile per:

- onboarding;
- Contact QR;
- sessione E2EE;
- block/report;
- Relay mode se incluso nella build;
- Install QR;
- Gateway ON/OFF e selected-app/whole-device se incluso;
- spiegazione di cosa è on-chain;
- spiegazione di cosa il Gateway può e non può vedere;
- distinzione relay vs Internet egress.

## 16. Protocol independence

Una policy Google/Apple può cambiare la build ufficiale ma non deve cambiare automaticamente:

- RootIdentity/pairwise identity model;
- autenticazione E2EE;
- synchronous delivery semantics;
- relay semantics;
- ChainAdapter;
- FreedomRelease authenticity;
- Direct build architecture;
- transport adapter model;
- possibilità di egress/private Gateway su piattaforme che lo consentono.

## 17. Invarianti

- store compliance non introduce un server centrale di delivery;
- Play/iOS policy non diventa trust anchor del protocollo;
- `DEVICE_RELAY` non è Internet egress;
- Gateway è opt-in e visibile;
- Gateway non viene presentato come equivalente alla chat E2EE;
- la Direct build resta tecnicamente separata dalla Play build dove necessario;
- ogni uso di VPN/platform networking deve rispettare la policy vigente al momento della release.
