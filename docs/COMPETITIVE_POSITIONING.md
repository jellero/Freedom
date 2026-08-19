# Freedom — Competitive Positioning

Status: **product/architecture positioning draft**

Ultima verifica delle fonti concorrenti: 2026-08-19.

Freedom combina due superfici differenti:

```text
Freedom Communication
  authenticated E2EE live communication
  no offline mailbox

Freedom Gateway
  optional app/device tunnel
  adaptive relay / bridge / transport fabric
  censorship-oriented reachability
```

Non esiste un vincitore assoluto: prodotti diversi ottimizzano problemi diversi.

## 1. Claim di unicità: cosa possiamo e non possiamo dire

Non è corretto dichiarare:

> "Nessun altro messenger integra VPN/circumvention."

Esistono almeno prodotti che combinano messaggistica con funzioni VPN o anti-censura e messenger che includono propri proxy/circumvention.

La differenziazione Freedom deve quindi essere la **combinazione architetturale specifica**:

```text
synchronous no-mailbox communication
+ RootIdentity / opaque device authorization
+ pairwise identity
+ forward-only replaceable relay fabric
+ device/community relay
+ Adaptive Defense
+ distributed recovery control-plane
+ optional censorship-resilient device Gateway
```

## 2. Freedom Communication

Target:

- comunicazione sincrona/live;
- autenticazione E2EE endpoint-to-endpoint;
- RootIdentity separata da DeviceKey/routing;
- nessun global DeviceID necessario nel network layer;
- alias pairwise e commitment opachi;
- relay forward-only;
- no offline delivery queue nel protocollo base;
- recovery pairwise / Adaptive Defense.

> **Synchronous. Ephemeral. Endpoint-to-endpoint.**

## 3. Freedom Gateway

Post-V1:

```text
browser / app / device
 -> encrypted tunnel
 -> Freedom path selector
 -> relay / bridge / Shield / pluggable transport
 -> explicit egress
 -> Internet
```

Il Gateway protegge il percorso fino all'egress; non trasforma automaticamente il protocollo finale in Freedom E2EE.

Dettagli: [`GATEWAY.md`](GATEWAY.md).

## 4. Signal — E2EE production / UX

Signal è benchmark per UX, deployment E2EE e security engineering operativa.

Signal supporta username per iniziare conversazioni senza condividere il numero col peer, ma richiede ancora un numero per registrarsi. Il servizio supporta delivery asincrono; Sealed Sender riduce metadata visibili al servizio.

Fonti ufficiali:

- https://support.signal.org/hc/en-us/articles/6712070553754-Phone-Number-Privacy-and-Usernames
- https://signal.org/blog/sealed-sender/

Differenza Freedom: live-only/no-mailbox base + route/relay sostituibili + identity/routing model differente.

## 5. SimpleX — metadata discipline

SimpleX non assegna identificatori utente globali e usa queue pairwise anonime/unidirezionali, con relay che possono conservare temporaneamente ciphertext per delivery.

Fonti:

- https://simplex.chat/docs/simplex.html
- https://simplex.chat/messaging/

SimpleX resta benchmark principale per verificare che RootIdentity/control-plane di Freedom non introducano correlabilità non necessaria.

## 6. Session — decentralized relay / onion routing

Session usa Session Nodes, swarm e Onion Requests; supporta storage dei messaggi per delivery successiva.

Fonte:

- https://docs.getsession.org/session-network/session-protocol/onion-requests-and-message-routing

Freedom differisce principalmente per semanticamente live-only e relay forward-only nel communication core.

## 7. Briar — transport resilience

Briar usa Tor e può comunicare/sincronizzare tramite Bluetooth/Wi-Fi in scenari appropriati.

Fonti:

- https://briarproject.org/manual/
- https://briarproject.org/quick-start/

Briar accetta sincronizzazione successiva; Freedom non consegna automaticamente ciò che è stato perso mentre il peer era offline.

## 8. Tor — bridges / pluggable transports / anonymity

Tor è benchmark per:

- bridge distribution;
- anti-enumeration;
- pluggable transports;
- active-probing resistance;
- traffic camouflage;
- multi-hop anonymity research.

Transport documentati includono obfs4, WebTunnel, Snowflake e meek.

Fonti:

- https://support.torproject.org/tor-browser/circumvention/unblocking-tor/
- https://bridges.torproject.org/

Freedom non deve ricreare Tor da zero e dovrebbe riusare tecniche/transport reviewati quando compatibili.

## 9. Psiphon — censorship circumvention

Psiphon è benchmark diretto per Gateway/Maximum Reachability: usa protocol diversity, obfuscation e fallback tra metodi differenti in reti filtrate.

Fonti:

- https://psiphon.ca/en/faq.html
- https://www.psiphon.ca/el/user-guide.html
- https://forge.psiphon.ca/

### Psiphon Conduit

Conduit permette a normali dispositivi volontari di diventare relay verso l'infrastruttura Psiphon senza diventare Internet exit.

Fonte:

- https://conduit.psiphon.ca/en/faq

Quindi `DEVICE_RELAY` volontario non è di per sé una proprietà unica di Freedom.

## 10. Tailscale — device exit nodes

Tailscale permette a un device autorizzato di instradare traffico Internet della tailnet come exit node.

Fonte:

- https://tailscale.com/docs/features/exit-nodes

Freedom sceglie una separazione esplicita:

```text
DEVICE_RELAY
  no arbitrary Internet egress

FREEDOM_EGRESS
  explicit managed/private/business role
```

## 11. Proton VPN Secure Core — managed multi-hop VPN

Secure Core instrada traffico attraverso più server VPN per mitigare alcuni rischi di server/network compromise.

Fonte:

- https://protonvpn.com/support/secure-core-vpn

Quindi "multi-hop" da solo non differenzia Freedom.

## 12. Telegram MTProxy — messenger-specific censorship bypass

Telegram documenta MTProxy come meccanismo per aggirare blocchi di Telegram mascherando/rinstradando il traffico verso i server Telegram.

Fonte:

- https://core.telegram.org/proxy

Freedom differisce perché il target Gateway è generalizzabile ad app/device traffic e il communication core non dipende da un central delivery server equivalente.

## 13. RCQ — messenger con circumvention integrata

RCQ si presenta come messenger E2EE con censorship circumvention integrata e transport VLESS/Reality e Hysteria2/Salamander.

Fonte ufficiale di prodotto:

- https://rcq.app/

Questo impedisce il claim "Freedom è il primo messenger con circumvention built-in" senza una ricerca storica molto più ampia.

La differenziazione Freedom resta la semantica sincrona/no-mailbox, identity/control-plane, relay fabric e Gateway adattivo più generale.

## 14. ARX — messaging + built-in VPN

ARX si presenta come app all-in-one con secure messaging/calls e built-in VPN.

Fonte ufficiale di prodotto:

- https://www.arx.pro/

Anche questo rende scorretto vendere "messenger + VPN" come categoria unica di Freedom.

## 15. È più potente di una VPN classica?

Come target di **censorship reachability**, Freedom Gateway può diventare più resiliente di una VPN semplice che dipende da:

```text
one protocol
known server pool
fixed fingerprint
one-hop path
```

Freedom mira invece a:

```text
many transport families
many providers
bridges
replaceable relay fabric
optional multi-hop
automatic failure classification
automatic failover
```

Questo non implica automaticamente:

- privacy superiore a Tor;
- maturità anti-censura superiore a Psiphon;
- sicurezza superiore a ogni VPN;
- universal firewall bypass.

## 16. Matrice concettuale

| Proprietà | Freedom target | Signal | SimpleX | Session | Briar | Tor | Psiphon |
|---|---|---|---|---|---|---|---|
| E2EE messenger | sì | sì | sì | sì | sì | n/a | non focus |
| Offline delivery base | **no** | sì | sì | sì | sì | n/a | n/a |
| Global DeviceID nel network layer | **no** | service model | no user ID | Account ID | app model | n/a | n/a |
| Forward-only messenger relay | **sì** | non modello | queue relay | node storage | peer sync | circuits | proxy network |
| Device/community relay | **sì** | no | non core | node network | peer model | Snowflake | Conduit |
| Pluggable anti-censorship transport | **post-V1** | limited bypass | optional Tor paths | non focus | Tor/transports | **core** | **core** |
| Adaptive route/transport switching | **core/post-V1** | non focus | configurable | routing | multi-transport | PT selection | protocol fallback |
| Device-wide Internet Gateway | **post-V1** | no | no | no | no | via separate wrappers | VPN/proxy modes |
| Verifiable identity/recovery control-plane | **sì** | Signal service | no global registry | network model | Briar model | no | no |

## 17. Posizionamento consigliato

Communication:

> **Freedom è un protocollo di comunicazione privata sincrona progettato per non dipendere da una mailbox, da un server centrale di delivery o da un singolo percorso di rete.**

Gateway:

> **Freedom Gateway estende il fabric di routing alle applicazioni del dispositivo, cercando percorsi e transport alternativi quando la rete filtra o degrada quelli normali.**

Complessivo:

> **Freedom separa la sicurezza della comunicazione dalla resilienza del percorso: E2EE live per le conversazioni Freedom, transport adattivo e Gateway opzionale per ambienti di rete ostili.**

Claim da evitare:

- "nessun altro lo fa";
- "primo messenger con VPN";
- "passa tutti i firewall";
- "impossibile da censurare";
- "più anonimo di Tor/SimpleX";
- "più sicuro di Signal";
- "non tracciabile";
- "rileva la sorveglianza".

## 18. Benchmark di sviluppo

```text
Signal    -> UX / production security engineering
SimpleX   -> metadata minimization
Session   -> decentralized relay / onion routing
Briar     -> transport resilience
Tor       -> anonymity / bridges / pluggable transports
Psiphon   -> adaptive censorship circumvention
Tailscale -> overlay / device exit nodes
Proton    -> managed multi-hop VPN
RCQ/ARX   -> adjacent messenger + circumvention/VPN product category
Freedom   -> coherent integration without confusing security boundaries
```

Il successo di Freedom deve essere dimostrato su device reali, reti ostili, DPI/firewall reali e review indipendente.
