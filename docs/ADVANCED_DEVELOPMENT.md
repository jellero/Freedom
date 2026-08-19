# Freedom — Advanced Development Method

Status: **development methodology / internal engineering documentation**

Questo documento definisce il loop di sviluppo avanzato di Freedom. Non descrive una garanzia del protocollo e non appartiene al README prodotto.

## 1. Principio

Il loop principale non deve dipendere dalla compilazione/installazione continua dell'APK Android.

Separare:

```text
PROTOCOL / CONTROL-PLANE / ROUTING LOGIC
 -> host-side executable/testable components
 -> deterministic simulators
 -> Docker/network namespaces
 -> scenario tests

ANDROID PLATFORM INTEGRATION
 -> APK/emulator/device gates successivi
 -> Keystore / lifecycle / background / VpnService / permissions
```

Per eseguire i sorgenti il core deve comunque essere compilato o interpretato dal relativo runtime, ma **non serve produrre/installare l'intera app Android a ogni iterazione**.

## 2. Obiettivo del laboratorio

Riprodurre automaticamente più macchine e reti ostili:

```text
Alice endpoint
Bob endpoint
Relay A
Relay B
Bridge
Gateway Egress
Honest RPC
Stale RPC
Malicious RPC
Control-plane simulator
Censor / firewall
NAT A
NAT B
Clock-skew node
Release mirror
```

Ogni nodo deve essere isolabile e sostituibile.

## 3. Codex come orchestratore di engineering

Codex viene usato per:

- leggere specifiche normative;
- creare/modificare implementazioni;
- generare test vector e negative tests;
- eseguire suite e scenario matrix;
- analizzare failure log;
- proporre patch;
- rieseguire regressioni;
- confrontare implementazione e threat model;
- mantenere documentazione tecnica allineata.

Codex **non è un trust authority** del protocollo. Un test che passa perché Codex lo ha scritto non sostituisce oracle indipendenti, review crittografica o test vector verificati.

## 4. Lavoro parallelo

Usare worktree/branch isolate per domini indipendenti:

```text
agent/protocol
agent/control-plane
agent/identity
agent/relay
agent/chaos-lab
agent/release-security
agent/test-oracle
agent/reviewer
```

Ogni task deve avere scope e acceptance criteria specifici.

Un reviewer agent non deve modificare automaticamente il codice che sta revisionando nello stesso passaggio: prima produce failure/criticità riproducibili, poi un agent separato applica la patch.

## 5. AGENTS.md

La root del repository contiene `AGENTS.md` con:

- documenti normativi da leggere prima di modificare security-sensitive code;
- invariant MUST/MUST NOT;
- comandi di test ufficiali;
- policy di scenario simulation;
- divieto di indebolire test per farli passare;
- obbligo di aggiungere regressioni per ogni bug corretto.

## 6. Architettura simulator-first

Target modulare:

```text
core/
  identity/
  protocol/
  session/
  routing/
  controlplane/
  release/

sim/
  node/
  chain/
  relay/
  nat/
  censor/
  clock/
  scenario/
  oracle/

platform-android/
  keystore/
  app-lifecycle/
  vpn/
```

Il simulatore usa le stesse state machine e serialization del core production dove possibile. Non mantenere una seconda implementazione semplificata che possa divergere silenziosamente.

## 7. Docker topology

Il laboratorio locale target usa Docker Compose o equivalente:

```text
freedom-alice
freedom-bob
relay-a
relay-b
bridge-a
egress-a
rpc-honest
rpc-stale
rpc-malicious
control-plane-mock
chaos-proxy
release-mirror
metrics-test-only
```

Il mock control-plane non sostituisce i test della chain reale; serve a rendere deterministiche failure, fork, stale state, storage e governance.

## 8. Network chaos

Usare strumenti Linux/container per modificare dinamicamente la rete:

- network namespace;
- `tc netem` per latency/loss/reorder/duplication;
- `nftables`/`iptables` per allow/deny/drop;
- connection tracking/NAT rules per cambiare mapping;
- proxy fault-injection tipo Toxiproxy o equivalente;
- DNS test resolver per poisoning/failure;
- reverse proxy/TLS endpoints per fingerprint/block simulation.

Nessun singolo tool è parte del protocollo.

## 9. NAT scenarios

Simulare almeno:

```text
OPEN / public
full-cone-like
restricted
port-restricted
symmetric-like mapping
mapping change mid-session
mobile-network address change
Wi-Fi -> mobile handover
NAT rebinding
hairpin unavailable
```

Il test non deve assumere che Docker NAT sia una replica perfetta di CGNAT reale. Dopo il laboratorio deterministico restano necessari test su reti/device reali.

## 10. Censorship / ban simulation

Scenario automatici:

```text
block relay IP
block complete provider subnet
block one ASN-equivalent test network
block UDP
block QUIC-like transport
block known TCP port
block DNS name
serve DNS poison
block TLS SNI/domain
reset connections after protocol signature
throttle bandwidth
inject high jitter/loss
active-probe bridge endpoint
allowlist-only environment
```

Il censor simulator deve poter cambiare policy durante una sessione.

## 11. RPC/control-plane adversarial simulation

Simulare:

```text
honest finalized state
stale state
valid old signer set
valid old SecurityPolicy
transaction submitted but failed
partial execution/failure
state mismatch after tx
RPC omission
conflicting RPC responses
invalid inclusion proof
rollback checkpoint
forked/non-final checkpoint
all RPC temporarily unavailable
```

Oracle: un client non deve accettare come `VERIFIED` stato che non supera `CONTROL_PLANE_SECURITY.md`.

## 12. Storage simulation

Per ogni record temporaneo:

```text
create
renew
expire
prune/overwrite
repeat thousands/millions logically
```

Assert:

```text
active storage <= configured theoretical bound
expired active keys converge to zero/bounded ring
refund/bounty accounting bounded
no message/media state introduced
```

## 13. Identity and recovery simulation

Scenari:

```text
new RootIdentity
new DeviceKey
second authorized device
revoke first device
stale revocation cache
RootRecoveryKey offline
DeviceAuthorizationKey compromise
RootIdentity compromise
UserRootRotation
lost all devices + valid PairwiseRecoveryBundle
lost all devices + no pairwise backup
```

Il risultato atteso deve essere definito prima dell'implementazione.

## 14. Contact bootstrap attacks

Automatizzare:

```text
copied QR
expired capability
replayed capability
substituted QR before first contact
wrong RootIdentity proof
valid attacker contact substituted for Bob
colluding contacts compare identity material
```

Il sistema deve distinguere autenticazione crittografica da assurance umana del primo contatto.

## 15. Handshake matrix

Generare combinazioni di:

```text
versions
suite offers
selected suite
DeviceCertificate epochs
revocation freshness
rekey epochs
transport changes
```

Negative cases:

- offer stripping;
- downgrade below policy;
- self-signed unknown peer;
- certificate for wrong relationship;
- replayed ephemeral material;
- stale certificate;
- mismatched transcript;
- wrong traffic-key epoch.

## 16. Transport semantic tests

Il core distingue due classi:

```text
RELIABLE_ORDERED_STREAM
UNRELIABLE_DATAGRAM
```

Text/control/session messages richiedono semantica affidabile/ordinata o un reliability layer esplicito.

Media può usare datagram/stream separati senza bloccare control/text quando un frame media viene perso.

Testare reorder/loss route-switch separatamente per ogni classe.

## 17. Shield simulation

Prima di claim production su Shield il laboratorio deve simulare:

```text
Alice -> Hop A -> Hop B -> Bob
```

con:

- circuit setup autenticato;
- chiavi per-hop separate;
- layered forwarding;
- next-hop token non globali;
- rotation/teardown;
- hop compromise singolo;
- hop collusion;
- path rebuild durante failure.

Concatenare due proxy TCP non conta come test di Freedom Shield.

## 18. Relay Sybil / eclipse simulation

Generare molti relay controllati dallo stesso adversary con:

- ID differenti;
- classi dichiarate differenti;
- endpoint differenti sulla stessa provenance;
- capacity hint falsi;
- geografia/provider dichiarati falsamente.

Il selector deve distinguere self-declared metadata da provenance osservata/verificata e non considerare automaticamente `N relay IDs` come `N operatori indipendenti`.

## 19. Release/governance simulation

Scenari:

```text
valid 3-of-5 release
2-of-5 insufficient
compromised one signer
signer-set N -> N+1
rollback to old signer set
lost quorum + governance recovery set
old SecurityPolicy replay
contract upgrade timelock
unauthorized contract code hash
first-install malicious peer
malicious mirror serves renamed APK
```

## 20. Clock simulation

Ogni nodo ha un clock fault-injectable:

```text
-24h
+24h
rollback during session
jump forward during certificate validation
monotonic clock preserved / reset after process restart
```

Assert contro `VerifiedTimeAnchor` e highest-seen checkpoint.

## 21. Scenario DSL

Target file:

```yaml
name: relay-block-and-nat-rebind
nodes:
  - alice
  - bob
  - relay_a
  - relay_b
steps:
  - at: 0s
    action: connect
    from: alice
    to: bob
  - at: 10s
    action: block
    target: relay_a
  - at: 12s
    action: nat_rebind
    target: alice
  - at: 20s
    assert: session_recovered
  - at: 20s
    assert: peer_identity_unchanged
  - at: 20s
    assert: no_mailbox_write
```

La sintassi definitiva può cambiare; il requisito è avere scenari versionati e riproducibili.

## 22. Automatic development loop

```text
spec change
 -> Codex reads normative docs
 -> generate/update failing tests first
 -> implement isolated change
 -> run unit + property + scenario matrix
 -> chaos/adversarial regression
 -> reviewer agent
 -> compare invariants/threat model
 -> commit only if gates pass
```

Per security-sensitive changes evitare il pattern “modifica test finché diventa verde” senza giustificazione della modifica dell'oracle.

## 23. Test levels

### L0 — pure/unit

- canonical encoding;
- hash/signatures/proofs;
- state machines;
- replay/rekey;
- parser bounds.

### L1 — process simulation

Più nodi come processi/containers sulla stessa macchina.

### L2 — network chaos

NAT/firewall/loss/DNS/RPC adversarial.

### L3 — chain integration

NEAR Testnet o local environment compatibile con prove/finality.

### L4 — Android emulator

Keystore, lifecycle, permissions, connectivity transitions.

### L5 — physical devices / real networks

Wi-Fi differenti, mobile/CGNAT, vendor Android, sleep/background, hotspot, roaming.

### L6 — external security/interoperability

Independent review, fuzzing esterno, multi-implementation test.

## 24. Quando compilare l'APK

Non come loop primario per:

- protocol state machine;
- routing logic;
- control-plane verifier;
- release verifier;
- relay/circuit logic;
- chaos simulations.

APK/emulator/device è obbligatorio per validare:

- Android Keystore;
- process death/restart;
- background restrictions;
- Doze/battery behavior;
- sockets reali Android;
- camera/QR;
- package signing/update;
- `VpnService` Gateway;
- notifications/permissions;
- platform network handover.

## 25. Evidence artifacts

Ogni scenario produce artefatti machine-readable:

```text
scenario manifest
seed
node versions/code hashes
network events
security state transitions
assertions
logs redacted of secrets
result
```

I failure devono essere riproducibili con lo stesso seed/config.

## 26. CI strategy

CI minima futura:

```text
unit
property/fuzz smoke
protocol vectors
control-plane proof vectors
scenario fast matrix
storage-bound tests
release/governance tests
```

Nightly/extended:

```text
large chaos matrix
randomized NAT/firewall scenarios
long-session rekey
relay Sybil/eclipsing
clock skew
storage stress
multi-agent review tasks
Android emulator gates
```

## 27. Codex safety boundaries

Per task automatizzati:

- repo/worktree isolati;
- secret production non disponibili;
- test chain/keys separate;
- rete esterna limitata al necessario;
- azioni destructive production vietate;
- log delle azioni agentiche conservati per audit del processo;
- human review obbligatoria prima di cambiare root, governance, release signer o mainnet config.

## 28. Definition of done

Una feature security/network non è “done” perché funziona nel happy path.

È done quando:

```text
normative spec exists
positive tests pass
negative tests pass
adversarial scenario exists
resource bound asserted
rollback/replay behavior tested
logs do not leak secrets
threat model updated if boundary changed
Android gate added if platform-dependent
```
