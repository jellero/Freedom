# Stato dell'implementazione Android/NEAR

> Questo documento descrive il codice eseguibile nel branch Android corrente. La specifica canonica di Freedom Communication definisce invece l'obiettivo sincrono, effimero e pairwise. Le due cose non sono ancora equivalenti.

## Versioni

```text
Android: 0.5.0-alpha (versionCode 11)
NEAR contract: 0.4.0
Network: NEAR Testnet
Contract account: freedom-registry-jellero.testnet
Protocol domain: FREEDOM_REGISTRY_V1
```

## Cosa fa davvero l'APK

L'alpha corrente è **blockchain-only**: non usa IP peer, discovery LAN, un relayer Freedom o un server applicativo predefinito. Registrazione device, pubblicazione contatto, lookup e consegna asincrona usano NEAR Testnet tramite RPC configurabili.

Il client offre home, contatti, chat, QR/numero, import manuale o QR della Function-Call key e un test completo NEAR. Il test esegue health check, controllo permessi, registrazione/publish se necessari, auto-invio cifrato, lettura e decifratura.

## Numero Freedom

Il numero implementato contiene 19 cifre di payload e una cifra Luhn:

```text
payload = SHA-256(compressed_P256_identity_key) mod 10^19
number  = zero_pad(payload, 19) || luhn(payload)
```

Il contratto ricalcola il valore dalla chiave registrata. Non è quindi possibile prenotare liberamente un numero checksummed. I vecchi numeri a 12 cifre e i contatti legacy non conformi non sono compatibili con `0.4.0`.

## Cifratura e chiavi

- Identity proof: ECDSA P-256 raw low-S.
- Messaggio: ECDH P-256 effimero, HKDF-SHA256 e AES-256-GCM.
- AAD: mittente, destinatario, message ID e scadenza.
- Mailbox key: rotazione giornaliera UTC; corrente più due giorni precedenti.
- NEAR credential, contatti e cronologia: AES-GCM con chiavi Android Keystore non esportabili.
- APK Testnet: certificato stabile custodito fuori dal repository e in GitHub Actions secrets.

La rotazione mailbox limita la finestra di compromesso ma non è un Double Ratchet auditato.

## Stato e costi NEAR

La Function-Call key deve avere receiver `freedom-registry-jellero.testnet` e autorizzare:

```text
register_device
publish_contact
send_message
```

La Full Access key viene rifiutata. Poiché la Function-Call key non allega deposito, il credito storage viene versato separatamente. I ciphertext scadono; al successivo invio verso la mailbox il contratto elimina fisicamente i record scaduti e riaccredita lo storage liberato ai pagatori.

Il client considera riuscito un invio soltanto se transazione e receipt NEAR non contengono `Failure`.

## Privacy reale

Il plaintext è cifrato end-to-end, ma NEAR è pubblica. Account pagatore, fee, tempi, frequenza, dimensione approssimativa, Device ID mittente/destinatario, message ID, chiave effimera, nonce e scadenza sono osservabili. L'RPC vede inoltre IP e richieste del client.

Questa alpha non offre anonimato, sender hiding, social-graph hiding o verifica light-client delle risposte RPC.

## Differenza dalla specifica canonica

La specifica Freedom Communication su `main` richiede comunicazione sincrona, effimera, endpoint-to-endpoint, RootIdentity e alias pairwise senza mailbox offline o Device ID globale. Il codice Android/NEAR corrente usa invece Device ID globali e ciphertext asincroni on-chain.

Non bisogna dichiarare l'alpha conforme alla specifica canonica. La convergenza richiede una migrazione di protocollo separata, non un cambio silenzioso.

## Verifiche

- unit test Kotlin per firme, transazioni, QR, numero e failure outcome;
- lint Android;
- APK debug/release e test APK compilati;
- test strumentali Android Keystore in emulatore CI;
- unit test Rust e build WASM in CI con Rust 1.93 e `Cargo.lock`;
- vettore numero condiviso Android/Rust.

Prima di produzione servono audit indipendente, recovery, multi-device, rate limit/blocking, verifica RPC e protocollo forward-secret standard.
