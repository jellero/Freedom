# Freedom — Threat Model

Status: **canonical design draft**

Normative security rules: [`SECURITY_INVARIANTS.md`](SECURITY_INVARIANTS.md).

## 1. Assunzioni

Freedom assume rete, relay, bridge, egress, RPC e source di download non fidati.

Un avversario può:

- osservare traffico e metadata;
- controllare peer, relay, bridge, egress o RPC;
- bloccare/ritardare/duplicare/riordinare pacchetti;
- usare DPI, fingerprinting e active probing;
- applicare allowlist restrittive;
- tentare traffic analysis/correlazione temporale;
- tentare impersonation/replay/downgrade;
- creare molte identity/nodi;
- saturare relay, egress, chain writes o risorse locali;
- tentare reward farming;
- distribuire client/APK modificati;
- compromettere singole signing/attestation keys;
- restituire transaction hash per operazioni fallite o stato stale/incoerente.

## 2. Security boundary separate

### Freedom Communication

```text
Alice endpoint
   <==== authenticated E2EE ====>
Bob endpoint
```

Le session key restano agli endpoint; relay/bridge/path trasportano ciphertext.

### Freedom Gateway

```text
external app
 -> encrypted Freedom tunnel
 -> relay / bridge / Shield
 -> explicit Egress
 -> Internet
```

L'egress ha una trust boundary differente e può osservare metadata di uscita e plaintext se il protocollo finale è plaintext.

> **Freedom Gateway non eredita automaticamente le garanzie E2EE endpoint-to-endpoint di Freedom Communication.**

## 3. Trust anchors

Trust anchor del core:

- RootIdentity/private material locale secondo ruolo;
- DeviceCertificate verificabile;
- DeviceKey locale;
- stato control-plane verificato/cache con freshness;
- primitive standard;
- stato pairwise autenticato;
- BootstrapTrustAnchor/release signer-set root per distribution.

Non sono trust anchor della conversazione:

- IP;
- relay;
- bridge;
- egress;
- RPC;
- fee relayer;
- payment provider;
- source di download;
- transaction hash da solo.

## 4. Identity impersonation

Freedom non usa global `DeviceID` nel network layer.

Per impersonare il peer atteso un attacker deve superare:

1. RootIdentity/contact identity attesa;
2. DeviceCertificate valido e non revocato secondo freshness policy;
3. possesso della DeviceKey;
4. transcript/session proof corrente.

Una chiave che firma se stessa ma non è autorizzata dal contatto atteso viene rifiutata.

## 5. DeviceCertificate / stale revocation

Rischio: un certificato correttamente firmato ma successivamente revocato può essere presentato quando tutti i provider control-plane sono bloccati.

Mitigazioni:

- certificate expiry bounded;
- revocation state cacheato e verificato;
- freshness class per operazione;
- più provider/RPC;
- nuovo handshake high-risk può richiedere refresh se revocation state è troppo stale;
- sessioni già autenticata possono seguire policy separate, senza trasformare una RPC in packet hot path.

Trade-off availability/security deve essere esplicito, versionato e non nascosto.

## 6. MITM / downgrade handshake

Una semplice ECDH non autenticata è insufficiente.

Il transcript lega:

```text
network/protocol version
expected pairwise relationship
DeviceCertificate hash/proof
key epoch
ephemeral keys
nonces
suite
session_id
```

Versione e suite sono autenticate; downgrade non autorizzato causa failure.

## 7. Forward secrecy / key compromise

Minaccia: compromissione futura di RootIdentity o DeviceKey.

Requisito:

- ephemeral key exchange per sessione;
- compromissione futura della DeviceKey non decifra sessioni concluse precedentemente;
- traffic-key lifetime bounded;
- rekey periodico per sessioni lunghe;
- messaging/media keys separate;
- ratchet standard/reviewato come target per post-compromise security.

E2EE non protegge plaintext già compromesso su endpoint/OS.

## 8. Replay

### Rendezvous/recovery

- slot pairwise atteso;
- TTL/expiry;
- nonce;
- payload autenticato;
- materiale effimero;
- state/freshness verificato.

### Session frames

- traffic-key epoch;
- sequence monotona;
- AEAD;
- replay memory bounded.

### Handshake

- nonce casuali;
- ephemeral key nuove;
- transcript completo.

## 9. Rendezvous metadata

Un control-plane pubblico può rendere osservabili timing/pattern di write.

Mitigazioni:

- slot pairwise opachi;
- PairRendezvousSecret;
- payload cifrato;
- no RootIdentity/device commitment/IP in chiaro quando evitabile;
- read-before-write;
- write solo dopo failure/policy;
- TTL/backoff.

Non elimina completamente traffic analysis.

## 10. Account commitment correlation

Rischio: sostituire un global DeviceID con un `root_commitment` stabile riutilizzato ovunque ricreerebbe un correlatore globale del control-plane.

Mitigazioni normative:

```text
DeviceAuthorizationCommitment
EntitlementCommitment
PaymentBindingCommitment
SponsorshipCommitment
```

sono domain-separated.

Pairwise rendezvous non deriva da commitment account-global. Provider payment references non ricevono tali commitment in plaintext salvo necessità esplicita.

## 11. Network identity leakage

Direct path o relay participation possono esporre IP a peer/hop adiacenti.

Mitigazioni:

- direct non obbligatorio;
- relay/Shield quando privacy rete prioritaria;
- niente RootIdentity/DeviceRecordCommitment come routing ID;
- transport token temporanei;
- log minimizzati;
- device relay opt-in con informativa.

## 12. Global traffic analysis

Un osservatore ampio può correlare timing, volume, direzione, durata, rendezvous, ingress/egress relay/Gateway.

Multi-hop, padding, batching e camouflage possono ridurre segnali ma non garantiscono anonimato contro global passive adversary.

## 13. Censura / firewall ostili

Un avversario può bloccare IP, provider, RPC, DNS, UDP/QUIC, TLS fingerprint, bridge, egress o classi intere di transport.

Mitigazioni:

- provider/RPC multipli;
- relay/egress multipli;
- device/community relay come hop non-egress;
- bridge non pubblici;
- bootstrap multipli;
- transport abstraction;
- pluggable/obfuscated transport reviewati;
- active-probing resistance quando supportata;
- path/provider/ASN/geographic diversity;
- Adaptive Defense/failover.

Limite fondamentale:

> **Freedom massimizza la probabilità di trovare un percorso quando esiste almeno un carrier utilizzabile; non promette universal firewall bypass.**

## 14. DPI / active probing

Segnali osservabili: handshake, packet size, timing, ALPN/SNI/TLS features, burst pattern, lifecycle.

Mitigazioni future: transport differenti, web-like carrier, bridge secret distribution, fingerprint rotation dove sicura, padding/timing bounded, primitive anti-censura reviewate.

Non inventare obfuscation proprietaria e dichiararla automaticamente sicura.

## 15. Bridge enumeration

Mitigazioni:

- pool non interamente pubblici;
- descriptor temporanei;
- distribuzione pairwise/out-of-band;
- rate limit;
- anti-enumeration;
- active-probing resistance;
- rotazione;
- bootstrap multipli.

Bridge compromise non compromette identity/E2EE.

## 16. Adaptive interference detection

```text
local connectivity OK
+ control-plane path OK
+ peer beacon recent
+ data path FAIL
 -> INTERFERENCE_OR_ROUTE_FAILURE_SUSPECTED
```

È inferenza, non attribuzione né prova di sorveglianza.

## 17. Malicious RPC

Un RPC può mentire, omettere dati, servire stato stale o censurare richieste.

Difese:

- provider multipli;
- finality awareness;
- proof/light-client verification dove appropriata;
- cache verificata/freshness;
- DeviceCertificate offline verification.

## 18. False-success control-plane transaction

Rischio: il client tratta `tx hash`/submission come operazione riuscita quando execution è `Failure` o lo stato risultante non corrisponde.

Difesa normativa:

```text
submit
 -> acceptable finality
 -> inspect execution outcome
 -> reject Failure/partial failure
 -> read resulting state
 -> verify expected transition
 -> local success
```

Label `ACTIVE/PAID/REVOKED/VERIFIED` non derivano dal solo hash.

## 19. Malicious fee relayer

Può rifiutare/ritardare/censurare/osservare operazioni, ma non può firmare come RootIdentity/DeviceKey, modificare operazioni firmate o essere unico punto obbligatorio.

## 20. Malicious relay

Può droppare, ritardare, correlare timing/volume, mentire su capacità.

Non può decifrare E2EE, impersonare endpoint, derivare session keys o generare ACK applicativi validi.

## 21. Relay open-proxy abuse

Relay base accetta solo circuiti Freedom bounded/capability-protected.

Vietato:

```text
client -> DEVICE_RELAY -> arbitrary Internet IP:port
```

Internet egress appartiene a Freedom Gateway con ruolo esplicito.

## 22. Relay resource exhaustion

Ogni relay impone:

```text
max_frame_size
max_buffer_per_circuit
max_total_buffer
max_concurrent_circuits
max_concurrent_handshakes
rate_limit
idle_timeout
packet_ttl
hop_limit
bandwidth_quota
```

Per device relay anche batteria/rete/CPU/RAM/temperatura/background bounds.

## 23. Gateway egress compromise

Egress può osservare destinazione IP, timing/volume, DNS e plaintext se protocollo finale non cifrato.

Mitigazioni: HTTPS dell'app, egress diversity, health/failover, multi-hop, DNS-over-tunnel, private/business egress, no single egress requirement.

Un egress compromesso non compromette automaticamente Freedom Communication E2EE.

## 24. Gateway leaks / downgrade

Rischi: DNS/IPv6 leak, app scope errato, captive portal, route locale, fallback direct.

Mitigazioni: DNS policy, kill-switch, split tunneling visibile, IPv4/IPv6 self-test, per-app scope, stato Gateway separato.

Se l'utente richiede Shield/strict/kill-switch, fallback direct silenzioso è vietato.

## 25. Relay Contributor farming

Il semplice toggle non prova contributo.

Usare qualification windows, forwarding bounded e receipt/commitment opachi senza pubblicare peer serviti/social graph.

## 26. Chain write/storage exhaustion

Il control-plane non contiene mailbox/message history.

Ogni record temporaneo ha size bound, TTL/epoch, rate limit, authorization e overwrite/reclaim strategy dove possibile.

## 27. Contact spam / QR theft

Contact capability casuale, expiry/rotation/one-shot, local block, approval, rate limit.

Copiare un contact QR non consente impersonation senza RootIdentity/DeviceCertificate/DeviceKey proof valide.

Recovery QR è materiale sensibile separato.

## 28. Device theft

Mitigazioni: secure storage, device lock/biometria opzionale, rotation/revocation, Recovery Kit, nuova DeviceKey su nuovo device.

## 29. Client / supply-chain compromise

E2EE non protegge da un client legittimamente firmato ma malevolo.

Mitigazioni:

- dependency minimization;
- code review;
- reproducible builds dove praticabile;
- canonical FreedomRelease;
- exact artifact verification;
- Android signer lineage;
- threshold release governance;
- signed SecurityPolicy/ReleaseStatus.

## 30. First-install bootstrap attack

Un peer che controlla APK + manifest + QR non deve poter ridefinire quale signer sia “Freedom”.

Il primo sideload usa `BootstrapTrustAnchor` pinned con:

```text
expected_package_id
release_signer_set_root_commitment
android_signing_root_or_lineage_anchor
minimum verifier policy
```

Source e QR non possono modificarli.

## 31. Single super-admin compromise

Una singola production key non deve poter autorizzare arbitrariamente release, revoche e critical security policy.

Production richiede threshold/multi-key e separazione di ruolo:

```text
ReleaseAuthorization   >= 3-of-5
ReleaseRevocation      >= 3-of-5
CriticalSecurityPolicy >= 3-of-5
RootRotation           >= 3-of-5 + recovery
Emergency advisory     scoped + TTL
```

Compromissione di un singolo signer non deve bastare a sovvertire la supply chain.

## 32. Key compromise

Separare RootIdentity, DeviceKey, session keys, media keys, route keys e Gateway tunnel keys.

Forward secrecy tra sessioni è obbligatoria; ratchet standard/reviewato è target per post-compromise security.

## 33. Principio anti-overclaim

Freedom distingue:

```text
SECURITY CLAIM   -> proprietà crittografica verificabile
REACHABILITY     -> strategia/probabilità di trovare path
INFERENCE        -> interpretazione di segnali di rete
```

Claim vietati senza evidenza:

- “passa ogni firewall”;
- “impossibile da bloccare”;
- “non tracciabile”;
- “rileva la sorveglianza”;
- “Gateway ha la stessa E2EE di Communication”.

Claim corretto:

> **Freedom Communication protegge la conversazione endpoint-to-endpoint; Freedom Gateway e Adaptive Defense aumentano la resilienza del percorso quando esistono alternative di rete utilizzabili.**
