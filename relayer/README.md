# Freedom NEAR Relayer

Gateway minimale che riceve operazioni già firmate dal dispositivo e paga gas/storage su NEAR Testnet. Non possiede né riceve la chiave P-256 del dispositivo.

## Account separato

Usare un account dedicato con saldo limitato, ad esempio `freedom-relayer-jellero.testnet`. Non usare mai la chiave dell'account del contratto.

## Configurazione

Le variabili richieste sono elencate in `.env.example`. La private key del relayer deve esistere soltanto nel secret store del servizio di hosting; non va inserita in Git, nell'APK, nei log o in chat.

## Avvio

```text
pnpm install --frozen-lockfile
pnpm test
pnpm start
```

Endpoint:

- `GET /health`
- `POST /v1/devices/register`
- `POST /v1/devices/rotate`
- `POST /v1/devices/revoke`
- `POST /v1/rendezvous`

Il servizio applica validazione stretta, limite body, quota per origine e depositi massimi fissi. Per Mainnet serviranno rate limiting persistente, Play Integrity/onboarding tickets, monitoraggio del budget e almeno due relayer indipendenti.
