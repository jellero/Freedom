# Freedom — Registration Economics & Anti-Abuse

Status: **canonical design draft**

Normative security rules: [`SECURITY_INVARIANTS.md`](SECURITY_INVARIANTS.md).

## 1. Obiettivo

Freedom deve sostenere una base Free ampia senza trasformare la registrazione in un vettore di storage/gas exhaustion.

> **il primo utilizzo deve restare gratuito per una persona; creare identity in massa deve avere un costo crescente per un aggressore.**

## 2. Nessuna write al semplice install

```text
install
 -> generate RootIdentity locally
 -> generate DeviceKey locally
 -> generate DeviceRecordCommitment locally
 -> Recovery Kit
 -> 0 mandatory chain writes
```

Una installazione abbandonata non consuma storage permanente.

## 3. SponsorshipCommitment domain-separated

La sponsorship non usa `root_commitment` come identificatore globale riutilizzato.

Derivare un commitment dedicato:

```text
SponsorshipCommitment = H(root/sponsorship context, "sponsorship", ...)
```

Deve essere distinto da:

```text
DeviceAuthorizationCommitment
EntitlementCommitment
PaymentBindingCommitment
PairwiseContactAlias / PairRendezvousSecret
```

## 4. Sponsored registration

```text
NEW ROOT IDENTITY
 -> valid root proof/signature?
 -> SponsorshipCommitment valid?
 -> anti-abuse proof valid?
 -> sponsorship already consumed?
 -> relayer rate limit OK?
 -> global bounded budget OK?
 -> submit registration
 -> verify finality/execution/state
 -> REGISTERED
```

Un transaction hash non equivale a registrazione riuscita.

## 5. Adaptive proof-of-work

Una challenge può legare:

```text
challenge
root_public_key_or_commitment
nonce
expiry/context
```

La difficoltà deve essere bassa per un singolo utente su hardware economico ma rendere costosa la creazione massiva automatizzata.

Valori concreti solo dopo benchmark reali su device rappresentativi.

## 6. Relayer multipli e rate limit

Più fee relayer indipendenti possono sponsorizzare registration/rare operations.

Ogni relayer impone limiti bounded per tempo/budget.

Il blocco di un singolo relayer non deve diventare censura globale.

IP, numero di telefono, account commerciale o DeviceRecordCommitment non diventano identità pubblica Freedom.

## 7. Una sponsorship per ownership continuity

Il control-plane deve impedire che la stessa ownership continuity consumi sponsorship iniziali illimitati senza pubblicare un global user identifier leggibile.

Reinstall/nuovo telefono usa Recovery Kit e RootIdentity esistente; non crea automaticamente una nuova RootIdentity solo per ottenere nuova sponsorship.

Nuove DeviceKey sono soggette a `max_devices` dell'entitlement.

## 8. Budget globale/dinamico

Durante abuso:

```text
proof difficulty     -> può aumentare
per-relayer rate     -> può ridursi
sponsorship budget   -> resta bounded
existing users       -> continuano a comunicare
```

Un attacco alla registration non deve interrompere utenti già attivi o recovery essenziale.

## 9. Storage minimale e bounded

Permanent state solo quando realmente necessario:

```text
root/ownership commitment state
opaque current device records
key epoch / revocation status
entitlement summary/commitment
signer/policy state
```

Temporary state:

```text
rendezvous
RecoveryBeacon
PurchaseIntent
route hints / bounded recovery state
```

Ogni stato temporaneo ha:

- size bound;
- TTL/epoch;
- rate limit;
- authorization;
- overwrite/reclaim strategy quando possibile.

Vietato memorizzare:

- messages/mailbox;
- media/history;
- readable social graph;
- pairwise alias globalmente leggibili;
- continuous presence.

## 10. Costo cresce con eventi rari

```text
message            -> 0 chain writes
voice/video frame  -> 0 chain writes
active session     -> 0 heartbeat writes
normal route       -> 0 recovery writes
```

Chain writes solo per eventi rari/bounded: registration, device activation/rotation/revocation, recovery/rendezvous, entitlement/payment/policy/release.

## 11. Free tier anti-abuse

Target iniziale:

- 1 ownership continuity sponsorizzabile;
- 1 active device Free;
- 10 active contact-person slots;
- core messaging/calls non tariffati per messaggio;
- Emergency Shield bounded su capacity reale;
- recovery essenziale non usato come leva commerciale.

## 12. Metriche prima della mainnet

- byte per root/device/entitlement record;
- costo registration/rotation/revocation;
- storage per 100k / 1M active identities;
- costo/latenza RecoveryBeacon;
- correlabilità activation/revocation/rendezvous/sponsorship;
- benchmark PoW su hardware reale;
- attack simulation su relayer/budget;
- failed-tx/state-mismatch behavior;
- storage-exhaustion simulation.

Non fissare parametri permanenti prima delle misure.

## 13. Invarianti

- install locale = 0 mandatory writes;
- SponsorshipCommitment domain-separated;
- nessun SMS/PayPal/telefono obbligatorio per identity Free;
- più relayer possibili;
- budget/rate/storage bounded;
- existing users non dipendono dal successo delle nuove registration;
- no mailbox/message/media on-chain;
- transaction hash != success;
- stato registrato solo dopo finalità/esecuzione/state verification.
