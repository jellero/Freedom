# Freedom — Threat Model

## 1. Assunzioni

Freedom assume una rete non fidata.

Un avversario può:

- osservare traffico e metadata;
- controllare peer, relay, bridge, egress o RPC;
- bloccare, ritardare, duplicare o riordinare pacchetti;
- bloccare IP, domini, provider, RPC, relay, egress o classi di transport;
- usare DPI e fingerprinting;
- eseguire active probing contro bridge/relay;
- applicare allowlist molto restrittive;
- tentare traffic analysis e correlazione temporale;
- tentare impersonation/replay;
- creare molte identità o nodi;
- saturare relay, egress, chain writes o risorse locali;
- tentare reward farming del Relay Contributor;
- tentare di usare un relay come open proxy;
- distribuire client modificati;
- compromettere uno o più Internet egress del Gateway.

Freedom non assume che relay, bridge, egress, NAT observer, bootstrap node, fee relayer o RPC siano fidati per autenticare una conversazione Freedom.

## 2. Due security boundary

### Freedom Communication

```text
Alice Freedom endpoint
   <==== authenticated E2EE ====>
Bob Freedom endpoint
```

Le session key restano agli endpoint.

Relay/bridge/path intermedi non devono poter decifrare il contenuto applicativo Freedom.

### Freedom Gateway

```text
external app
 -> encrypted Freedom tunnel
 -> relay / bridge / Shield
 -> Egress
 -> Internet
```

L'egress è una trust boundary differente. Può osservare almeno metadata necessari al forwarding e, se l'applicazione finale usa plaintext, anche quel plaintext.

Quindi:

> **Freedom Gateway non eredita automaticamente le stesse garanzie E2EE endpoint-to-endpoint di Freedom Communication.**

Dettagli: [`GATEWAY.md`](GATEWAY.md).

## 3. Trust anchors

Le radici di fiducia del core sono limitate a:

- RootIdentity/private material locale secondo ruolo;
- DeviceKey locale;
- stato verificato del control-plane;
- primitive crittografiche standard;
- stato locale autenticato derivato da relazioni/sessioni precedenti;
- release/signing root secondo il modello update.

Non sono trust anchor della conversazione:

- IP;
- relay;
- bridge;
- egress;
- RPC;
- fee relayer;
- provider commerciale;
- payment provider;
- risultato di discovery.

## 4. Identity impersonation

Freedom non usa un global `DeviceID` nel network layer.

Un attacker non deve poter impersonare il peer atteso senza:

1. una identity/contact proof coerente con la relazione pairwise;
2. una DeviceKey attualmente autorizzata;
3. prova del possesso della DeviceKey;
4. transcript valido della sessione corrente.

`DeviceRecordCommitment` è un handle tecnico del control-plane e non deve diventare un'identità di trasporto globale.

## 5. Man-in-the-middle

Una semplice ECDH non autenticata è insufficiente.

Il transcript deve legare:

```text
network/protocol version
pairwise identity context
device authorization proof
key epoch
ephemeral keys
nonces
suite
session_id
```

La sostituzione di uno di questi elementi deve causare authentication failure.

## 6. Replay

### Rendezvous/recovery

Freshness tramite:

- slot pairwise atteso;
- stato verificato del control-plane;
- TTL/`expires_at`;
- nonce;
- autenticazione del payload;
- materiale effimero del tentativo corrente.

### Session frames

Sequence monotona/finestra anti-replay autenticata con AEAD.

### Handshake

Nonce casuali + ephemeral key nuove per connessione.

## 7. Rendezvous metadata

Un control-plane pubblico può rendere osservabili timing/pattern di write.

Mitigazioni:

- slot pairwise opachi;
- capability casuali;
- PairRendezvousSecret;
- slot rotanti;
- payload cifrato;
- niente RootIdentity/device commitment/IP in chiaro quando evitabile;
- write solo dopo failure o policy esplicita;
- read-before-write;
- TTL/backoff.

Queste misure non eliminano completamente traffic analysis.

## 8. Network identity leakage

Un direct path o la partecipazione come relay può esporre l'IP a peer/hop adiacenti.

Requisiti:

- direct non obbligatorio;
- relay/Shield quando privacy di rete è prioritaria;
- niente RootIdentity/DeviceRecordCommitment come routing identifier;
- transport token temporanei;
- log minimizzati;
- device relay con informativa sul maggiore livello di esposizione di rete.

## 9. Global traffic analysis

Un osservatore ampio può correlare:

- timing;
- volume;
- direzione;
- durata;
- rendezvous;
- ingressi/uscite relay;
- ingressi/uscite Gateway.

E2EE non elimina automaticamente questi metadata.

Multi-hop, padding, batching e transport camouflage possono ridurre alcuni segnali ma non sono garanzia contro un global passive adversary.

## 10. Censura e firewall ostili

Un avversario può bloccare:

- IP relay/egress noti;
- RPC/bootstrap;
- DNS/domain;
- protocol fingerprints;
- UDP o QUIC;
- TLS pattern selettivi;
- classi intere di transport;
- bridge scoperti tramite scanning/active probing;
- traffico non appartenente a una allowlist.

Mitigazioni architetturali:

- RPC/provider multipli;
- relay multipli;
- egress multipli;
- device/community relay come ingress/intermediate hop;
- bridge non pubblici;
- bootstrap multipli;
- transport abstraction;
- pluggable/obfuscated transports reviewati;
- active-probing resistance quando supportata;
- path diversity;
- provider/ASN/geographic diversity;
- possibilità di disabilitare direct;
- Adaptive Defense e transport failover;
- nessun IP/domain/protocol singolo obbligatorio.

### Limite fondamentale

Freedom non può promettere di attraversare ogni firewall.

Un avversario che:

- spegne ogni connettività;
- consente solo una allowlist stretta senza alcun carrier utilizzabile;
- controlla tutte le route disponibili;

può impedire la comunicazione a qualunque sistema IP.

Claim corretto:

> **Freedom deve massimizzare la probabilità di trovare un percorso quando esiste almeno un carrier utilizzabile; non promettere bypass universale.**

## 11. DPI / protocol fingerprinting

Un censor può classificare traffico cifrato in base a:

- handshake;
- packet size;
- timing;
- ALPN/SNI/TLS features;
- burst pattern;
- connection lifecycle;
- active probing.

Mitigazioni future:

- transport adapter differenti;
- Web/HTTPS-like carrier;
- transport offuscati;
- bridge secret distribution;
- fingerprint rotation dove sicura;
- padding/timing defenses bounded;
- riuso di transport anti-censura già reviewati.

Non inventare obfuscation proprietaria e considerarla automaticamente sicura.

## 12. Bridge enumeration

Attacco:

```text
censor ottiene/scopre elenco bridge
 -> probe
 -> block IPs
```

Mitigazioni:

- pool non interamente pubblici;
- descriptor temporanei;
- distribuzione pairwise/out-of-band;
- rate limit;
- anti-enumeration;
- active-probing-resistant transport;
- rotazione;
- più canali di bootstrap.

La compromissione di un bridge non deve compromettere identity o E2EE.

## 13. Adaptive interference detection

Dopo failure completa del data path:

```text
local connectivity               OK
at least one control-plane path  OK
peer beacon recent               OK
current data path                FAIL
```

può giustificare:

```text
INTERFERENCE_OR_ROUTE_FAILURE_SUSPECTED
```

Azione:

- altro relay;
- altro provider;
- altro transport;
- bridge;
- Shield/multi-hop;
- aggressive bounded retry policy.

Non prova chi causi il blocco o che esista sorveglianza passiva.

## 14. Malicious RPC

Un RPC può mentire, omettere dati, restituire stale state o censurare richieste.

Difese:

- provider multipli;
- finality awareness;
- proof/light-client verification dove disponibile;
- cache verificata con freshness policy.

## 15. Malicious fee relayer

Può rifiutare, ritardare, censurare o osservare operazioni on-chain.

Non deve poter:

- ottenere RootIdentity/DeviceKey;
- firmare come endpoint;
- modificare operazioni firmate;
- diventare unico punto obbligatorio.

## 16. Malicious relay

Un relay, incluso `DEVICE_RELAY`, può:

- droppare;
- ritardare;
- correlare timing/volume;
- rifiutare;
- tentare replay/modifica;
- mentire su capacità.

Non deve poter:

- decifrare Freedom payload E2EE;
- impersonare endpoint;
- derivare session keys;
- generare ACK applicativi validi.

Difese:

- E2EE;
- AEAD;
- sequence;
- circuit capability;
- relay switching;
- diversity.

## 17. Relay come open proxy

Il relay base non deve consentire:

```text
client -> DEVICE_RELAY -> arbitrary Internet IP:port
```

Deve accettare solo circuiti Freedom validi e bounded.

Difese:

- packet format obbligatorio;
- circuit capability;
- hop limit / TTL;
- no generic TCP CONNECT nel relay base;
- rate/circuit limits.

## 18. Gateway Egress compromise

Un egress Gateway può:

- osservare destinazione IP;
- osservare timing/volume;
- osservare DNS se lo risolve;
- vedere plaintext applicativo se il protocollo finale non è cifrato;
- droppare/modificare traffico plaintext;
- censurare destinazioni;
- essere monitorato dal provider/giurisdizione.

Mitigazioni:

- HTTPS/TLS dell'applicazione quando disponibile;
- egress diversity;
- egress health/failover;
- multi-hop per separare client IP da egress;
- DNS-over-tunnel policy;
- no single managed egress requirement;
- explicit status del current egress;
- private/business egress per deployment controllati.

Un egress compromesso **non deve** compromettere una conversazione Freedom Communication E2EE che non necessita di Internet egress.

## 19. Egress correlation / collusion

```text
Client -> Relay A -> Egress B -> Internet
```

Target:

- Relay A non conosce la destinazione Internet finale;
- Egress B non vede direttamente l'IP originale quando il path multi-hop è corretto.

Collusione, osservazione globale o timing correlation possono comunque ridurre questa separazione.

Non promettere anonimato assoluto.

## 20. Gateway DNS / leak

Rischi:

- DNS fuori tunnel;
- IPv6 leak;
- traffico app escluso per errore;
- captive portal;
- route locale non intenzionale;
- fallback direct non autorizzato.

Mitigazioni:

- DNS policy esplicita;
- kill-switch opzionale;
- split tunneling visibile;
- IPv4/IPv6 test;
- connectivity self-test;
- per-app scope verificabile;
- Network Indicator con stato Gateway separato.

## 21. Gateway abuse

Egress Internet possono essere abusati per:

- scanning;
- spam;
- scraping;
- traffico illegale/abusivo;
- resource exhaustion.

Gli egress devono essere esplicitamente amministrati con:

- authenticated capability;
- rate/bandwidth limits;
- circuit/connection quotas;
- abuse controls compatibili;
- eventuali port policy;
- revoca;
- logging minimizzato secondo policy/obblighi applicabili.

Questi controlli non trasformano il relay messenger in un moderation server per le conversazioni E2EE.

## 22. Relay resource exhaustion

Ogni relay impone almeno:

```text
max_frame_size
max_buffer_per_circuit
max_total_buffer
max_concurrent_circuits
rate_limit
idle_timeout
packet_ttl
hop_limit
bandwidth_quota
```

Per `DEVICE_RELAY` anche:

- battery minimum;
- charging-only opzionale;
- Wi-Fi only opzionale;
- metered policy;
- CPU/RAM/temperature limits;
- background execution limits.

## 23. Relay Contributor farming

Il semplice `relay_enabled=true` non è prova di contributo.

Mitigazioni:

- qualification windows;
- availability + useful forwarding bounded;
- receipt/commitment opachi;
- limiti per evitare incentivo al volume artificiale;
- rate limit per RootIdentity/device/epoch;
- benefit expiry.

Nessuna prova deve pubblicare peer serviti, contenuto o social graph.

## 24. Chain write spam

Difese:

- fee/storage economics;
- bounded records;
- slot aggiornabili;
- TTL;
- read-before-write;
- backoff;
- contract bounds.

## 25. Contact spam

Conoscere un contact descriptor non deve concedere capability illimitata.

Usare:

- contact capability casuale;
- expiry/rotation/one-shot;
- local block list;
- request approval;
- rate limits.

## 26. QR theft/copy

Contact QR copiato non consente impersonation senza private key/proof valida.

Recovery QR è invece materiale sensibile cifrato e segue policy separata.

## 27. Device theft

Mitigazioni:

- Android Keystore / platform secure storage;
- biometria/device lock opzionale;
- key rotation/revocation;
- Recovery Kit;
- nuovo device con nuova DeviceKey.

## 28. Client / supply-chain compromise

E2EE non protegge contro un client legittimamente firmato ma malevolo che legge plaintext o private key.

Mitigazioni:

- signing key protection;
- code review;
- reproducible builds dove praticabile;
- release manifest;
- artifact verification;
- signed updates;
- dependency minimization.

## 29. Key compromise

Separare:

- RootIdentity material;
- DeviceKey;
- session keys;
- route control keys;
- media keys;
- Gateway tunnel keys.

Una futura ratchet construction deve usare primitive standard e reviewate.

## 30. Downgrade

Versione e suite sono parte del transcript autenticato.

Un attacker non deve poter forzare una versione/suite inferiore senza failure.

Per Gateway, la policy deve impedire silent fallback da Shield/tunnel a direct Internet quando l'utente ha richiesto kill-switch/strict mode.

## 31. Eclipse / peer isolation

Mitigazioni:

- bootstrap multiple;
- relay/bridge diversity;
- cache indipendenti;
- provider diversity;
- controllo separato identity vs routing;
- confronto di stato.

## 32. Network reachability failures

Freedom degrada attraverso classi di path/transport consentite dalla policy:

```text
known path
 -> alternative endpoint
 -> alternative relay
 -> alternative provider
 -> alternative transport
 -> bridge
 -> shielded/multi-hop
 -> pairwise recovery
```

Se nessun carrier è disponibile, stato finale `UNAVAILABLE`.

## 33. Principio anti-overclaim

Freedom deve distinguere sempre:

```text
SECURITY CLAIM
  proprietà crittografica verificabile

REACHABILITY CLAIM
  probabilità/strategia di trovare un percorso

INFERENCE
  interpretazione di segnali di rete
```

Claim vietati senza evidenza:

- "passa ogni firewall";
- "impossibile da bloccare";
- "non tracciabile";
- "rileva la sorveglianza";
- "Gateway ha la stessa E2EE della chat Freedom".

Claim corretto:

> **Freedom Communication protegge la conversazione endpoint-to-endpoint; Freedom Gateway e Adaptive Defense aumentano la resilienza del percorso quando esistono alternative di rete utilizzabili.**
