# Freedom Android

## Implementazione corrente

`0.5.0-alpha` è un client Android nativo programmatico per la mailbox cifrata NEAR Testnet. Le schermate principali sono chat, contatti, identità/QR e impostazioni. Non usa IP peer o discovery LAN.

## Storage locale

- identity key e mailbox key private vengono cifrate con AES-GCM usando chiavi Android Keystore;
- credenziale NEAR viene cifrata con IV casuale persistito insieme al ciphertext;
- contatti e cronologia vengono migrati automaticamente da SharedPreferences plaintext a envelope AES-GCM autenticati;
- backup Android è disabilitato;
- i log diagnostici usano breadcrumb senza contenuti o identificatori utente.

La mailbox key ruota ogni giorno UTC. Il keyring conserva il giorno corrente e i due precedenti, sufficiente rispetto al TTL on-chain di 24 ore e a un margine di rotazione.

## Rete

L'app usa soltanto `INTERNET` e `ACCESS_NETWORK_STATE`. Gli endpoint RPC NEAR sono configurabili e provati in fallback. Nessuna Function-Call key o private identity key viene inviata a un server Freedom: la transazione viene serializzata e firmata sul device.

Il polling della mailbox parte in `onStart` e viene rimosso in `onStop`; non continua in background. Il composer applica gli inset della tastiera e della navigation bar.

## Verifica della chiave NEAR

L'indicatore verde richiede che la access key esista, sia Function-Call, abbia il receiver del contratto e autorizzi almeno `register_device`, `publish_contact`, `send_message`. Salva e verifica non esegue una scrittura. Il test completo esegue invece health check, registrazione/publish se necessari, auto-invio cifrato, lettura e decifratura.

Il client controlla gli outcome finali NEAR. Un transaction hash con `status.Failure` non viene più presentato come messaggio inviato.

## Build

```text
./gradlew testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest
```

Test JVM coprono serializzazione, firme, QR, numeri e gestione delle risposte RPC. Test strumentali coprono Android Keystore per credenziali e archivi JSON. La CI li esegue su emulatore API 35 e compila contro SDK 37.

La build release legge esclusivamente variabili d'ambiente:

```text
FREEDOM_KEYSTORE_PATH
FREEDOM_KEYSTORE_PASSWORD
FREEDOM_KEY_ALIAS
FREEDOM_KEY_PASSWORD
```

Il keystore non deve essere committato. In CI viene ricostruito da un secret Base64 e produce un APK Testnet con firma stabile.

## Debito tecnico

`MainActivity` resta troppo grande e va separata in ViewModel, renderer e servizio NEAR. Questa refactor è necessaria prima di una beta, ma non deve cambiare i formati crittografici o il contratto senza una migrazione esplicita.

Altri requisiti prima di produzione: audit indipendente, Double Ratchet standard, proof/light-client RPC, rate limit/blocking, recovery identità, gestione multi-device, accessibilità completa e test end-to-end su due dispositivi reali.
