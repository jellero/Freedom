# Freedom

Freedom è un client Android di messaggistica cifrata **blockchain-only** in sviluppo su NEAR Testnet. Non richiede un server Freedom, un relayer predefinito o l'inserimento di IP peer: registrazione, risoluzione contatti e mailbox asincrona passano dal contratto `freedom-registry-jellero.testnet`.

## Stato attuale

Il client `0.5.0-alpha` offre:

- identità P-256 locale e Device ID;
- numero Freedom a 20 cifre derivato dalla chiave pubblica compressa e protetto da checksum;
- contatti tramite numero o QR;
- cifratura end-to-end ECDH P-256 + HKDF-SHA256 + AES-256-GCM;
- mailbox NEAR con messaggi cifrati e TTL di 24 ore;
- firma diretta delle transazioni dal telefono con Function-Call key dedicata;
- chiave NEAR, contatti e cronologia locale cifrati tramite Android Keystore;
- rotazione giornaliera della chiave mailbox, conservando solo la finestra necessaria a leggere i messaggi non scaduti;
- test completo nelle impostazioni: RPC, permessi chiave, registrazione, scrittura, lettura e decifratura.

Il contratto `0.4.0` lega ogni numero alla chiave identità registrata. I record creati da versioni precedenti con un numero non derivato correttamente non vengono più risolti. I messaggi scaduti vengono rimossi fisicamente al successivo invio verso la mailbox e il relativo credito storage viene restituito ai pagatori.

## Cosa serve

1. Un account NEAR Testnet usato come pagatore.
2. Credito storage già versato al contratto.
3. Una Function-Call key limitata al receiver `freedom-registry-jellero.testnet` e ai metodi:

```text
register_device
publish_contact
send_message
```

La Full Access key viene rifiutata. L'endpoint RPC è soltanto il gateway HTTP verso NEAR: è sostituibile nelle impostazioni e non possiede identità o messaggi in chiaro.

## Flusso

```text
chiave identità locale
  -> numero Freedom deterministico
  -> registrazione/publish su NEAR
  -> lookup del contatto
  -> cifratura E2EE sul mittente
  -> ciphertext nella mailbox NEAR (TTL 24 h)
  -> lettura e decifratura sul destinatario
```

Non esiste un percorso di consegna LAN nascosto o un relayer Freedom. L'APK parla direttamente con RPC NEAR scelti dall'utente.

## Limiti di privacy da conoscere

Il contenuto è cifrato end-to-end, ma NEAR è una blockchain pubblica. Account pagatore, fee, orari, frequenza, dimensioni approssimative, Device ID mittente/destinatario e argomenti delle transazioni sono osservabili. Un endpoint RPC vede inoltre l'indirizzo IP del client e le richieste che inoltra. Freedom non promette anonimato di rete.

La rotazione giornaliera delle chiavi mailbox limita la decifrabilità storica, ma non è ancora un Double Ratchet sottoposto ad audit. Non usare questa alpha per dati o fondi reali.

## Build Android

Richiede JDK 17 e Android SDK 37:

```text
./gradlew testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest
```

La CI esegue anche i test strumentali Android Keystore in emulatore. Se sono configurati i secret di firma, produce inoltre un APK Testnet firmato stabilmente; questo evita che ogni build richieda disinstallazione e perdita dell'identità locale.

## Contratto

```text
cd contract
cargo test --locked
cargo near build non-reproducible-wasm
```

Il deploy di un upgrade non richiede e non autorizza la modifica delle access key dell'account.

## Documentazione

- [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md)
- [`docs/CHAIN.md`](docs/CHAIN.md)
- [`docs/THREAT_MODEL.md`](docs/THREAT_MODEL.md)
- [`docs/PROTOCOL.md`](docs/PROTOCOL.md)
- [`docs/STORE_COMPLIANCE.md`](docs/STORE_COMPLIANCE.md)
- [`ANDROID.md`](ANDROID.md)
