# Freedom — Product Visuals

Status: **concept UI / product communication**.

Questi visual servono a rendere leggibile il prodotto senza sostituire la specifica tecnica. Una label come `VERIFIED`, `ACTIVE`, `E2EE`, `SHIELDED` o `SUSPECTED` deve comparire nel client reale **solo quando il relativo stato è effettivamente derivato da verifiche o osservazioni implementate**.

## Freedom Communication — la conversazione esiste adesso

![Freedom Communication product screens](assets/freedom-communication-screens.svg)

Freedom Communication è la superficie con la security boundary più forte del prodotto: peer autenticato, sessione E2EE live e chiavi della conversazione agli endpoint.

Gli screen mostrano tre idee UX che devono restare visibili ma semplici:

- **Live E2EE**: la conversazione viene trasmessa soltanto durante una sessione autenticata attiva;
- **percorso sostituibile**: Direct, relay, bridge o Shield possono cambiare senza cambiare l'identità del contatto o le chiavi applicative;
- **no mailbox**: un peer offline non crea automaticamente una coda di consegna futura.

La UI può mostrare route e Network Status in modo discreto durante il funzionamento normale e più dettagliato quando avviene un fallback.

Il visual non implica che un relay renda la conversazione più affidabile crittograficamente: il relay rimane trasporto non fidato di ciphertext.

## Freedom Gateway — il percorso di rete per le altre app

![Freedom Gateway product screens](assets/freedom-gateway-screens.svg)

Freedom Gateway è una superficie separata da Freedom Communication. Protegge e diversifica il percorso di rete di browser o altre applicazioni mediante un tunnel locale e un egress esplicito.

Gli screen rendono visibili:

- modalità **selected apps** o **whole device**;
- percorso attivo e latenza osservata;
- transport/bridge/relay/egress scelti dal motore adattivo;
- quota di **managed Gateway capacity**, con target Free iniziale `100 MB/day`;
- DNS/leak controls ed egress preference;
- policy `Fast`, `Balanced`, `Maximum Reachability` e `Private`.

La quota Gateway non è una quota messaggi Freedom. `DEVICE_RELAY` non diventa un Internet exit: il traffico Internet esce soltanto da nodi `MANAGED_EGRESS`, `PRIVATE_EGRESS`, `BUSINESS_EGRESS` o altri ruoli egress esplicitamente autorizzati.

Il Gateway non trasforma un protocollo esterno in Freedom E2EE: oltre l'egress continuano a valere le proprietà di HTTPS o del protocollo dell'app finale.

## Freedom Shield — rendere visibile la degradazione e reagire

![Freedom Shield product screens](assets/freedom-shield-screens.svg)

Freedom Shield raccoglie la parte di resilienza avanzata del percorso. Il punto UX non è mostrare allarmi spettacolari, ma distinguere **fatti osservati, inferenza e contromisura**.

Il Network Indicator usa gli stati:

```text
NORMAL
SHIELDED
DEGRADED
SUSPECTED
UNAVAILABLE
```

Uno stato `SUSPECTED` può derivare, per esempio, dalla combinazione:

```text
peer recently active
+ control-plane reachable
+ current data path repeatedly failing
```

Questo permette al client di tentare relay, bridge, transport alternativo o Shield. Non prova chi stia bloccando, non prova sorveglianza passiva e non deve mostrare frasi come "sei monitorato".

La stessa diagnosi tecnica deve essere disponibile a Free e Pro. I tier premium possono comprare maggiore capacità, multi-hop, pool gestiti e failover più aggressivo, non una diagnosi più onesta o una cifratura base più forte.

## Share Freedom — distribuzione aperta, installazione verificata

![Share Freedom product screens](assets/freedom-share-screens.svg)

Share Freedom separa **distribuzione dei byte** e **autorità sulla release**.

Un client genuino può mostrare un Install QR e offrire una capability temporanea per una release già verificata. Peer, relay, mirror e store possono servire l'APK, ma nessuna di queste sorgenti decide da sola che l'APK sia genuino.

Prima di installare, il verifier deve concordare almeno su:

```text
exact artifact SHA-256
FreedomRelease signatures
Android signer / authorized lineage
ReleaseStatus != REVOKED
SecurityPolicy / min secure version
package ID / version / anti-downgrade
```

Qualunque mismatch deve produrre **fail closed**.

Un filename opaco come:

```text
freedom-r42-454fjk4hfhsjhslllshlvhvru0ujwr8w.apk
```

può essere utile per lookup e anti-enumeration, ma non è una prova di autenticità. Il client che condivide una release non possiede le private key della release authority.

## Naming visuale

Il naming di prodotto deve restare coerente:

```text
Freedom Protocol
|- Freedom Communication
|- Freedom Gateway
`- Freedom Shield

Share Freedom = funzione di distribuzione verificabile del client
```

Principio comune:

> **Nessun server centrale. Nessun super-admin. Niente di opaco. Fiducia nel protocollo. Sicurezza nell'architettura.**

Questo significa componenti infrastrutturali sostituibili e verifiche esplicite, non assenza fisica di server, relay, RPC o egress.