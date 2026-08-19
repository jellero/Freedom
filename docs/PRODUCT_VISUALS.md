# Freedom — Product Visuals

Status: **concept UI / product communication**.

Questi visual rendono leggibile il prodotto senza sostituire la specifica tecnica. Label come `VERIFIED`, `ACTIVE`, `E2EE`, `SHIELDED`, `CONTACT_VERIFIED` o `SUSPECTED` devono comparire nel client reale soltanto quando il relativo stato deriva da verifiche/osservazioni implementate.

## Freedom Communication — comunicazione live autenticata

![Freedom Communication product screens](assets/freedom-communication-screens.svg)

La security boundary è endpoint-to-endpoint: expected contact, DeviceCertificate/delegation validi, revocation freshness sufficiente, sessione E2EE live e chiavi della conversazione agli endpoint.

Gli screen rappresentano:

- Live E2EE;
- percorso sostituibile;
- no mailbox/offline delivery;
- route/network status visibile senza confondere transport e identity.

## Freedom Gateway — percorso rete per app esterne

![Freedom Gateway product screens](assets/freedom-gateway-screens.svg)

Gateway usa un tunnel locale e un egress esplicito. Può mostrare selected apps/whole device, path/egress, quota managed, DNS/leak controls e Maximum Reachability.

Il Gateway non trasforma protocolli esterni in Freedom E2EE e `DEVICE_RELAY` non diventa automaticamente Internet egress.

## Adaptive Defense / Network Status — osservazione e reazione

![Adaptive Defense and Shield activation concept](assets/freedom-shield-screens.svg)

Questo visual rappresenta **principalmente Adaptive Defense e Network Status**, non costituisce da solo la definizione crittografica di Freedom Shield.

Stati:

```text
NORMAL
DEGRADED
SUSPECTED
UNAVAILABLE
```

`SUSPECTED` deriva da fatti osservati + inferenza e non prova censura/sorveglianza.

La UI può mostrare che Freedom sta tentando relay/bridge/transport alternativo o costruendo un path Shield.

### Quando può apparire `SHIELDED`

`SHIELDED` è una label separata e più forte. Può comparire soltanto quando il runtime ha completato i gate di [`SHIELD.md`](SHIELD.md):

```text
authenticated circuit setup
+ independent per-hop keys
+ layered forwarding
+ current non-expired Shield policy satisfied
+ no silent direct fallback
```

Due proxy concatenati o un semplice fallback relay non autorizzano la label `SHIELDED`.

## Share Freedom — distribuzione aperta, installazione verificata

![Share Freedom product screens](assets/freedom-share-screens.svg)

Peer/relay/mirror/store possono fornire byte, ma la validità deriva da exact artifact hash, threshold release authorization, Android signer lineage, verified ReleaseStatus/SecurityPolicy e bootstrap trust/freshness.

Un fresh install deve inoltre soddisfare il `BootstrapFreshnessFloor` del verifier/release corrente; un checkpoint vecchio ma validamente provato non basta se è sotto quel floor.

## Identity / contact labels

La UI distingue:

```text
BOOTSTRAP_UNVERIFIED
CONTACT_VERIFIED
```

`CONTACT_VERIFIED` richiede un independent assurance step come safety code/fingerprint/out-of-band verification quando previsto dalla UX.

## Naming visuale

```text
Freedom Protocol
|- Freedom Communication
|- Freedom Gateway
`- Freedom Shield

Adaptive Defense / Network Status = detection/failover surface
Share Freedom = verified distribution function
```

Principio comune:

> **Nessun server centrale. Nessun super-admin. Niente di opaco. Fiducia nel protocollo. Sicurezza nell'architettura.**

“Nessun super-admin” significa nessuna singola production credential unilaterale; threshold quorum/custody assumptions restano esplicite.
