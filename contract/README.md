# Freedom Registry

Contratto NEAR del registro identità e rendezvous Freedom.

## Proprietà

- identità P-256 compatibili con Android Keystore;
- registrazione self-signed e rotazione/revoca autorizzate dalla chiave corrente;
- nonce monotono contro replay e dominio legato all'account del contratto;
- chiavi P-256 compresse e firme canoniche raw `r || s` low-S;
- rendezvous opachi, temporanei e limitati a 2 KiB;
- deposito obbligatorio per ogni crescita dello storage;
- rimborso dello storage al pagatore quando un rendezvous scaduto viene rimosso;
- nessun messaggio, contatto, IP o numero Freedom memorizzato on-chain.

## Test e build

```text
cargo test --package freedom-registry
cargo near build non-reproducible-wasm --no-abi
```

Il contratto non contiene chiavi amministrative. Gli upgrade restano controllati dalle access key dell'account NEAR su cui viene distribuito.

