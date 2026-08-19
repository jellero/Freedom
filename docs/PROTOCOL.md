# Freedom — protocollo alpha v1

## Encoding e primitive

- Device ID e message ID: 32 byte, hex lowercase.
- Identity/mailbox/ephemeral key: P-256 compressa, 33 byte.
- Firma identity: ECDSA P-256 raw `r || s`, low-S, 64 byte.
- Hash/KDF: SHA-256 e HKDF-SHA256.
- Payload: AES-256-GCM, nonce casuale 12 byte.
- Valori binari JSON: Base64 standard.
- Interi NEAR oltre il range JSON sicuro: stringhe decimali.

## Authorization envelope

Ogni operazione identity-authenticated firma:

```text
"FREEDOM_REGISTRY_V1\0"
contract_id_length || contract_id
operation
device_id
auth_nonce
key_epoch
protocol_version
key_material_length || key_material
```

Gli interi sono big-endian. Operazioni correnti: register `1`, rotate `2`, revoke `3`, publish contact `4`, send message `5`.

## Numero Freedom

```text
digest  = SHA-256(compressed_identity_public_key)
payload = unsigned_big_endian(digest) mod 10^19
number  = decimal(payload, width=19) || Luhn(payload)
```

App e contratto devono produrre lo stesso valore. Il checksum rileva errori di digitazione, non è una firma. Il contratto confronta sempre il numero con la chiave registrata.

## Registrazione

`register_device` registra Device ID, identity public key e versione. La prova usa nonce `0`, epoch `1` e la chiave pubblica come key material. Operazioni successive richiedono `stored_nonce + 1`.

## Contatto

`publish_contact` associa al numero derivato:

```text
device_id
identity_public_key (dal DeviceRecord)
mailbox_public_key
key_epoch
updated_at
```

Key material firmato: `UTF8(number) || mailbox_public_key`. Il QR può trasportare questi valori pubblici per bootstrap, ma il destinatario verifica lo stato NEAR corrente.

## Messaggio

Il mittente genera message ID, scadenza e chiave P-256 effimera. Deriva:

```text
shared = ECDH(ephemeral_private, recipient_mailbox_public)
salt   = SHA-256(sender_device_id || recipient_device_id || message_id)
key    = HKDF-SHA256(shared, salt, "Freedom on-chain message v1", 32)
aad    = sender_device_id || recipient_device_id || message_id || expires_at_ns
```

Il contratto riceve e conserva metadata, ephemeral public key, nonce e ciphertext. Key material della firma:

```text
message_id || recipient_device_id || expires_at_ns || ephemeral_public_key || nonce || SHA-256(ciphertext)
```

TTL valido: da 60 secondi a 7 giorni; il client usa 24 ore. Ciphertext massimo: 4096 byte. Mailbox massima: 100 messaggi attivi.

## Lettura e deduplicazione

`get_messages(device_id)` restituisce solo record non scaduti. Il client deduplica per message ID e associa il mittente a un contatto già verificato. I self-test non vengono mostrati come conversazioni.

## Rotazione mailbox

Il client pubblica la chiave del giorno corrente e conserva localmente le chiavi private della finestra breve. In assenza di un key ID nel record prova le chiavi dalla più recente finché AES-GCM autentica. Dopo l'eliminazione di una chiave, i ciphertext storici destinati a quella chiave non sono più decifrabili dal device.

## Versioning

Protocol version resta `1`; il contract version cambia per semantica/deploy. Qualunque modifica incompatibile a domain, encoding, AAD o key material richiede una nuova versione esplicita e vettori di test incrociati.
