# Freedom — architettura implementata

## Componenti

```text
Android A                                      Android B
identity P-256                                identity P-256
NEAR Function-Call key                       NEAR Function-Call key
        |                                            |
        +---------- RPC NEAR sostituibili ---------+
                             |
               freedom-registry-jellero.testnet
                 registry contatti + mailbox TTL
```

Il percorso operativo dell'alpha è blockchain-only. Non richiede discovery LAN, IP peer, relay Freedom o server applicativo. Gli RPC sono trasporti sostituibili verso la stessa rete NEAR e non sono trust anchor crittografici.

## Identità e numero

Ogni installazione mantiene una chiave P-256 e un Device ID locali. Il contratto registra la chiave compressa e accetta un numero solo se coincide con:

```text
payload = SHA-256(compressed_P256_public_key) mod 10^19
number  = zero_pad(payload, 19) || luhn_check_digit(payload)
```

Questo impedisce la prenotazione arbitraria di un numero valido. La collisione resta teoricamente possibile perché un numero decimale non è un identificatore crittografico completo: il contatto conserva e verifica anche fingerprint e chiave identità.

## Bootstrap contatto

Il QR/link contiene soltanto materiale pubblico:

```text
network
number
display name
identity fingerprint
device_id
identity_public_key
mailbox_public_key
key_epoch
```

Nessuna private key o capability segreta viene condivisa. Un contatto importato viene verificato nuovamente contro lo stato finalizzato NEAR prima di essere considerato raggiungibile.

## Invio asincrono

1. Il mittente risolve la mailbox key corrente del destinatario.
2. Genera una chiave P-256 effimera.
3. Deriva una chiave con ECDH e HKDF-SHA256.
4. Cifra con AES-256-GCM, legando mittente, destinatario, message ID e scadenza come AAD.
5. Firma l'autorizzazione applicativa con la chiave identità.
6. Firma e invia la transazione NEAR con la Function-Call key.
7. Mostra il messaggio locale soltanto dopo un esito di esecuzione NEAR riuscito.

Il destinatario interroga la mailbox soltanto mentre l'Activity è in foreground. Prova la chiave mailbox corrente e quelle della breve finestra di rotazione, quindi salva il plaintext localmente in forma cifrata con Android Keystore.

## Retention e storage

I ciphertext hanno TTL di 24 ore. Le view ignorano i record scaduti. Al successivo invio verso una mailbox il contratto rimuove sia gli ID sia i record scaduti e restituisce il credito storage liberato ai pagatori. La funzione esplicita di cleanup resta disponibile per manutenzione, ma il client non deve possederne il permesso.

## Chiavi

- Identity key: persistente, protetta localmente; autorizza le operazioni Freedom.
- Mailbox key: ruota ogni giorno UTC; sono conservati corrente e due giorni precedenti.
- NEAR Function-Call key: importata dall'utente, cifrata con Android Keystore e limitata al contratto/metodi.
- Ephemeral message key: nuova per ogni messaggio.

La rotazione mailbox riduce l'impatto di un compromesso futuro, ma non equivale a un protocollo ratchet completo.

## Codice

`NearChainAdapter` esegue view call e fallback RPC. `NearDirectClient` valida la Function-Call key, serializza/firma transazioni e verifica gli outcome finali. `IdentityStore`, `MailboxKeyStore` ed `EncryptedJsonStore` gestiscono il materiale locale. Il contratto Rust applica binding numero/identità, nonce, firme, TTL, limiti e contabilità storage.

`MainActivity` contiene ancora troppo codice UI e orchestrazione: è debito tecnico noto, non una proprietà desiderata dell'architettura. La prossima estrazione dovrebbe separare un servizio di messaggistica NEAR e ViewModel lifecycle-aware senza cambiare il protocollo.
