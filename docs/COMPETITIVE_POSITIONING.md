# Freedom — Competitive Positioning

Status: **product/architecture positioning draft**

Ultima verifica delle fonti concorrenti: 2026-08-19.

Freedom non deve essere valutato con una singola classifica assoluta. Il progetto combina due superfici differenti:

```text
Freedom Communication
  private live communication
  authenticated E2EE
  no offline mailbox

Freedom Gateway
  optional device/app network path
  adaptive relay/bridge/transport fabric
  censorship-oriented reachability
```

Altri prodotti sono benchmark migliori per proprietà differenti.

## 1. Freedom Communication

Proprietà centrali target:

1. comunicazione sincrona/live;
2. autenticazione E2EE endpoint-to-endpoint;
3. RootIdentity separata da DeviceKey/routing;
4. nessun global DeviceID necessario nel network layer;
5. alias pairwise e device commitment opachi;
6. relay forward-only e sostituibili;
7. no offline delivery queue nel protocollo base;
8. Adaptive Defense / pairwise recovery control-plane.

Formula:

> **Synchronous. Ephemeral. Endpoint-to-endpoint.**

## 2. Freedom Gateway

Gateway è un'evoluzione post-V1:

```text
browser / app / device
 -> encrypted tunnel
 -> Freedom path selector
 -> relay / bridge / Shield / pluggable transport
 -> explicit Internet egress
 -> Internet
```

Il Gateway non ha automaticamente la stessa trust boundary del messenger: l'egress può vedere metadata di destinazione e il protocollo applicativo finale deve fornire la propria cifratura end-to-end, ad esempio HTTPS.

Il valore target è la **reachability adattiva**, non una promessa di "VPN magicamente imbattibile".

Dettagli: [`GATEWAY.md`](GATEWAY.md).

## 3. Signal — benchmark di E2EE production e UX

Signal è un riferimento per esperienza utente, deployment E2EE su larga scala e security engineering operativa.

Signal supporta username per iniziare conversazioni senza condividere il numero al peer, ma richiede ancora un numero per la registrazione. Il servizio facilita delivery asincrono; Sealed Sender riduce metadata visibili al servizio.

Fonti ufficiali:

- https://support.signal.org/hc/en-us/articles/6712070553754-Phone-Number-Privacy-and-Usernames
- https://signal.org/blog/sealed-sender/

Differenza:

```text
Signal
endpoint -> service delivery -> endpoint
           async support

Freedom Communication
endpoint <-> replaceable path <-> endpoint
           authenticated live session
           no mailbox base
```

Freedom non deve dichiarare "più sicuro di Signal" senza evidence production.

## 4. SimpleX — benchmark metadata discipline

SimpleX non assegna identificatori utente globali e usa queue pairwise unidirezionali/anonime. I relay possono conservare temporaneamente ciphertext per delivery.

Fonti ufficiali:

- https://simplex.chat/docs/simplex.html
- https://simplex.chat/messaging/

Differenza:

```text
SimpleX
no global user identifier
pairwise queues
temporary store-and-forward

Freedom
RootIdentity ownership
opaque device record
pairwise aliases
no offline delivery queue
live authenticated session
```

SimpleX resta il benchmark principale per verificare che RootIdentity/control-plane/recovery di Freedom non creino una superficie di correlazione peggiore del necessario.

## 5. Session — benchmark decentralized relay network / onion routing

Session usa Session Nodes, swarm e Onion Requests; il modello supporta storage dei messaggi nello swarm per delivery successiva.

Fonte ufficiale:

- https://docs.getsession.org/session-network/session-protocol/onion-requests-and-message-routing

Freedom differisce perché i relay del communication core sono forward-only e il protocollo base non crea offline delivery.

Session resta un benchmark utile per:

- distributed relay operation;
- onion routing;
- IP protection;
- gestione reale di nodi indipendenti.

## 6. Briar — benchmark di transport resilience

Briar usa Tor quando Internet è disponibile e può comunicare/sincronizzare tramite Bluetooth o Wi-Fi in scenari appropriati.

Fonti ufficiali:

- https://briarproject.org/manual/
- https://briarproject.org/quick-start/

Briar supporta delivery/synchronization successiva quando i peer tornano disponibili. Freedom sceglie semanticamente l'opposto per il core: peer assente -> niente consegna futura automatica.

Briar resta benchmark importante per transport diversity reale fuori dal normale Internet path.

## 7. Tor — benchmark anti-censura e anonimato

Tor usa bridge e pluggable transports per rendere più difficile identificare/bloccare il traffico Tor.

Transport attualmente documentati includono, tra gli altri:

- obfs4;
- WebTunnel;
- Snowflake;
- meek.

Fonti ufficiali:

- https://support.torproject.org/tor-browser/circumvention/unblocking-tor/
- https://bridges.torproject.org/

Freedom non deve ricreare Tor da zero.

Tor è benchmark per:

- bridge distribution;
- anti-enumeration;
- active-probing resistance;
- pluggable transport architecture;
- traffic camouflage;
- multi-hop anonymity research.

Freedom Gateway può integrare o prendere come riferimento transport reviewati compatibili, mantenendo però il proprio identity/control-plane e communication core.

## 8. Psiphon — benchmark diretto per censorship circumvention

Psiphon è progettato specificamente per bypassare blocchi online. La documentazione ufficiale descrive protocol diversity, obfuscation e fallback tra metodi differenti quando un protocollo viene filtrato.

Fonti ufficiali:

- https://psiphon.ca/en/faq.html
- https://www.psiphon.ca/el/user-guide.html
- https://forge.psiphon.ca/

Questa parte è particolarmente importante per Freedom Gateway: una VPN classica può essere facilmente identificabile e bloccabile se usa un numero ridotto di protocolli/fingerprint, mentre un sistema di circumvention deve aggiornare continuamente le proprie strategie.

### Psiphon Conduit

Conduit permette a normali dispositivi volontari di agire come relay verso l'infrastruttura Psiphon. Il device volontario non è l'Internet exit: inoltra traffico cifrato verso Psiphon.

Fonte ufficiale:

- https://conduit.psiphon.ca/en/faq

Questo è molto vicino a una proprietà Freedom già prevista:

```text
DEVICE_RELAY
  volunteer device
  encrypted forwarding
  not Internet egress
```

La differenza Freedom non può quindi essere "nessuno usa device relay".

La differenziazione è la combinazione con:

- Freedom Communication E2EE/live;
- RootIdentity/pairwise identity;
- control-plane verificabile;
- Relay Contributor;
- Shield/Adaptive Defense;
- optional device Gateway.

## 9. Tailscale — benchmark device exit node

Tailscale permette a un device autorizzato della tailnet di diventare un **exit node** e instradare traffico non-Tailscale verso Internet.

Fonte ufficiale:

- https://tailscale.com/docs/features/exit-nodes

Questo dimostra che "usare un device come gateway/exit" non è di per sé unico.

Freedom sceglie però una separazione più rigida:

```text
DEVICE_RELAY
  never automatic Internet egress

FREEDOM_EGRESS
  explicit managed/private/business role
```

La ragione è evitare di trasformare telefoni volontari in open proxy Internet.

## 10. Proton VPN Secure Core — benchmark multi-hop VPN

Proton Secure Core instrada il traffico attraverso più server VPN per mitigare alcuni rischi di server/network compromise.

Fonte ufficiale:

- https://protonvpn.com/support/secure-core-vpn

Freedom Shield/Gateway non può quindi differenziarsi semplicemente dicendo "multi-hop".

Il target distintivo è:

```text
multi-hop
+ relay/device/community fabric
+ transport diversity
+ bridge discovery
+ Adaptive Defense
+ identity/recovery control-plane
+ Freedom Communication
```

## 11. Matrice concettuale

Le celle `target` indicano proprietà progettate ma non necessariamente production-ready.

| Proprietà | Freedom | Signal | SimpleX | Session | Briar | Tor | Psiphon |
|---|---|---|---|---|---|---|---|
| E2EE messenger | target sì | sì | sì | sì | sì | non messenger | non focus |
| Offline delivery base | **no** | sì | sì | sì | sì | n/a | n/a |
| Global device ID nel network layer | **no target** | service/account model | no user ID | Account ID | app identity | no app identity | no app identity |
| Relay forward-only messenger | **target sì** | non modello | relay queues | node storage | peer sync | relay circuits | proxy/circumvention |
| Device/community relay | **target sì** | no | non core | node network | peer model | Snowflake proxies | Conduit |
| Pluggable anti-censorship transport | **target post-V1** | non focus | Tor/config options | onion protocol | Tor/Bluetooth/Wi-Fi | **core** | **core** |
| Adaptive route/transport switching | **target core/post-V1** | non focus | configurable network | network routing | multi-transport | connection assist/PT | protocol fallback |
| Device-wide Internet Gateway | **target post-V1** | no | no | no | no | Tor VPN-style wrappers esterni | VPN/proxy modes |
| Verifiable identity/recovery control-plane | **target sì** | Signal service model | no global registry | network/account model | Briar model | no | no |

## 12. È "molto più potente di una VPN classica"?

Come target anti-censura, **può esserlo** rispetto a una VPN semplice con:

```text
one protocol
known server pool
one-hop tunnel
fixed fingerprint
```

Freedom Gateway mira invece a:

```text
many transports
many paths
many providers
bridges
relay fabric
optional multi-hop
active failure classification
automatic failover
```

Ma non è corretto dire che sarà automaticamente:

- più anonimo di Tor;
- più maturo contro la censura di Psiphon;
- più sicuro di ogni VPN;
- capace di attraversare ogni firewall.

Queste proprietà richiedono implementazione, misure e test reali.

## 13. Posizionamento corretto

Claim Communication:

> **Freedom è un protocollo di comunicazione privata sincrona progettato per non dipendere da una mailbox, da un server centrale di delivery o da un singolo percorso di rete.**

Claim Gateway:

> **Freedom Gateway estende il fabric di routing di Freedom alle applicazioni del dispositivo, cercando automaticamente percorsi e transport alternativi quando la rete filtra o degrada quelli normali.**

Claim complessivo:

> **Freedom separa la sicurezza della comunicazione dalla resilienza del percorso: E2EE live per le conversazioni Freedom, transport adattivo e Gateway opzionale per ambienti di rete ostili.**

Claim da evitare:

- "passa tutti i firewall";
- "impossibile da censurare";
- "più anonimo di Tor/SimpleX";
- "più sicuro di Signal";
- "non tracciabile";
- "rileva la sorveglianza".

## 14. Benchmark di sviluppo

```text
Signal   -> UX / production security engineering
SimpleX  -> metadata minimization
Session  -> decentralized relay network / onion routing
Briar    -> transport resilience
Tor      -> anonymity / bridges / pluggable transports
Psiphon  -> censorship circumvention / adaptive protocol strategy
Tailscale-> overlay / device exit nodes
Proton   -> managed multi-hop VPN
Freedom  -> integrazione coerente senza confondere le security boundary
```

Il successo di Freedom non consiste nell'avere tutte le feature sulla carta, ma nel dimostrare che la combinazione funziona su device reali, reti ostili, DPI reali e implementazioni indipendenti.
