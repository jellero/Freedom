# Freedom — threat model dell'alpha blockchain-only

## Proprietà offerte

- Il numero pubblicato è legato alla chiave identità P-256 registrata.
- Ogni change method applicativa richiede firma identità, nonce monotono ed epoch corrente.
- Il contenuto del messaggio è cifrato end-to-end con chiave effimera del mittente.
- La chiave NEAR, contatti e cronologia sul telefono sono cifrati tramite chiavi non esportabili Android Keystore.
- La chiave mailbox ruota giornalmente e le vecchie chiavi vengono eliminate dopo la finestra di consegna.
- La Function-Call key NEAR non può operare su altri receiver e non deve essere Full Access.
- Record e mailbox hanno limiti di dimensione, quantità e TTL; lo storage scaduto viene recuperato.

## Metadati pubblici inevitabili

NEAR Testnet è pubblica. Anche con ciphertext sicuro un osservatore può vedere o correlare:

- account NEAR che paga e relativa public key;
- receiver, metodo, gas, fee, block height e timestamp;
- Device ID mittente e destinatario negli argomenti/stato;
- message ID, scadenza, chiave effimera, nonce e dimensione ciphertext;
- frequenza delle conversazioni e polling visibile all'RPC;
- indirizzo IP del client visto dall'RPC.

Il contratto non nasconde il social graph a un osservatore della cronologia. Freedom non offre anonimato, mixnet, sender hiding o resistenza a un avversario globale.

## Attacchi e mitigazioni

### Prenotazione del numero

Un attaccante non può scegliere il numero della vittima: contratto e app derivano lo stesso valore da `SHA-256(compressed_public_key)`. La sicurezza è però limitata alle 19 cifre di payload; fingerprint/chiave restano necessari contro collisioni.

### Impersonazione del device

Richiede la private identity key corrente. Device ID, nonce, epoch, operation e materiale specifico sono legati alla firma. Un client deve rifiutare chiavi o fingerprint diversi da quelli salvati per un contatto.

### RPC malevolo

Può censurare, osservare o mentire. Il fallback limita il single point of failure ma non verifica proof/light-client. La disponibilità e la freschezza delle view dipendono ancora dai provider configurati.

### Replay e doppio invio

Il nonce identità è monotono e ogni message ID deve essere unico. AES-GCM autentica AAD con mittente, destinatario, ID e scadenza. Due transazioni concorrenti dallo stesso device possono collidere sul nonce; il client serializza le operazioni in un executor singolo ma un altro processo/telefono con la stessa identità resta un caso non supportato.

### Compromesso chiavi

- NEAR Function-Call key: consente solo i metodi autorizzati finché allowance e access key restano valide; non consente trasferimenti generici.
- Identity key: permette impersonazione Freedom finché non viene ruotata/revocata.
- Mailbox key corrente: permette decifrare i ciphertext indirizzati a quella chiave ancora disponibili o archiviati da un osservatore. La cancellazione delle chiavi vecchie limita la finestra, ma non esiste Double Ratchet.
- Telefono sbloccato/compromesso: Android Keystore non protegge il plaintext mentre l'app lo usa né da malware con privilegi sufficienti.

### Saturazione e costi

L'attaccante deve possedere un device registrato e credito storage per inviare. Mailbox e ciphertext sono bounded. Una mailbox piena può comunque causare denial of service fino alla scadenza; rate limit per sender e block list on-chain non sono ancora implementati.

### Contratto o supply chain compromessi

Un upgrade malevolo del contratto può censurare o modificare il protocollo e leggere tutti i metadati già pubblici, ma non possiede le mailbox private key. Il repository non è ancora auditato; dipendenze, toolchain, APK e WASM devono essere firmati/verificati prima di qualunque uso reale.

## Logging

I report diagnostici non devono includere private key, seed phrase, QR, numeri, nomi contatti o plaintext. Gli errori RPC/contratto mostrati vengono limitati; prima di condividere log resta comunque necessario controllarne il contenuto.

## Non-goal e debito aperto

- nessun anonimato di rete o dei metadati chain;
- nessun protocollo Double Ratchet auditato;
- nessuna recovery sicura dell'identità;
- nessuna verifica light-client delle risposte RPC;
- nessun multi-device per una singola identità;
- nessun audit indipendente di contratto, crittografia o client;
- nessuna garanzia di disponibilità o consegna entro il TTL.

Questi limiti sono parte del prodotto corrente e non vanno mascherati da claim come “nessun messaggio sulla blockchain”: i ciphertext sono deliberatamente memorizzati su NEAR per la consegna asincrona.
