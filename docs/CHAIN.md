# Freedom — layer NEAR

## Deploy Testnet

```text
network:  NEAR Testnet
contract: freedom-registry-jellero.testnet
protocol: 1
code:     0.4.0
```

Il client firma direttamente dal telefono. Non usa l'account `freedom-relayer-jellero.testnet` né un servizio Freedom. L'utente può cambiare la lista degli endpoint RPC; tutti devono puntare alla rete scelta.

## Stato

Il contratto conserva:

- device record: chiave P-256, epoch, nonce autorizzativo, stato;
- indice numero -> contatto e device -> numero;
- mailbox: message ID e record ciphertext con scadenza;
- credito storage prepagato per account;
- strutture rendezvous legacy, non usate dal client blockchain-only corrente.

Il numero è derivato dalla chiave compressa. `publish_contact` rifiuta qualunque numero checksummed che non corrisponda alla chiave già registrata. `get_contact_by_number` nasconde anche i vecchi record non conformi.

## API usata dall'app

View:

```text
get_config
get_device
get_contact_by_number
get_messages
storage_balance_of
```

Change methods autorizzati dalla Function-Call key:

```text
register_device
publish_contact
send_message
```

La lista `method_names` vuota di NEAR significa tutti i metodi del receiver ed è accettata, ma una lista esplicita con i tre metodi riduce meglio l'autorità. La chiave deve avere receiver `freedom-registry-jellero.testnet`; una Full Access key viene rifiutata.

## Costi

La Function-Call key paga gas dalla propria allowance/account ma non può allegare deposito. Per questo il credito storage viene versato una volta con wallet o CLI. Ogni scrittura scala il costo dei byte aggiunti dal saldo interno.

I messaggi scaduti sono rimossi automaticamente quando arriva un nuovo messaggio nella stessa mailbox. Il contratto misura i byte realmente liberati e riaccredita l'importo ai pagatori; se più pagatori hanno record scaduti nello stesso cleanup, il rimborso è distribuito proporzionalmente al numero di record.

## Outcome transazioni

Un hash non prova il successo. Il client aspetta `FINAL` e rifiuta:

- `final_execution_status = FAILURE`;
- `status.Failure` della transazione;
- un `receipts_outcome[].outcome.status.Failure`.

Soltanto dopo questa verifica l'invio appare nella cronologia locale.

## RPC

Un RPC può osservare IP e richieste, censurare, ritardare o restituire errori. Il fallback migliora la disponibilità, non crea verifica light-client. Il client dipende ancora dalle risposte finalizzate del provider per le view e deve dichiararlo chiaramente.

## Upgrade

La versione 0.4 mantiene il layout Borsh dello stato 0.3. Il campo rendezvous del record persistito resta vuoto per compatibilità ma non viene più accettato come argomento né restituito dalla API contatto. Un upgrade del codice non richiede modifiche alle access key esistenti.
