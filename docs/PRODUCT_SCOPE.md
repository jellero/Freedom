# Freedom — Product Scope

## 1. Obiettivo

Freedom Messenger non deve competere al lancio sulla quantità di feature. Deve dimostrare in modo affidabile la proposta centrale di Freedom Protocol: **comunicazione privata live, autenticata E2EE, sincrona, senza mailbox centrale e senza dipendenza permanente da un singolo percorso o provider.**

Principio UX:

> **Semplice quando tutto funziona. Trasparente quando qualcosa cerca di impedirti di comunicare.**

Freedom è anche un prodotto per utenti che hanno bisogno di capire lo stato reale della propria capacità di comunicare.

## 2. Launch scope — V1

La prima release pubblica è focalizzata sul **1:1**.

Funzioni essenziali:

- RootIdentity + DeviceIdentity inizializzate localmente;
- Recovery Kit esportabile: QR/bundle cifrato + recovery code;
- registrazione sponsorizzata quando serve, senza wallet NEAR obbligatorio;
- aggiunta contatto tramite QR/link;
- **10 contatti attivi nel piano Free**;
- **1 device attivo nel piano Free**;
- sessione autenticata E2EE;
- text 1:1;
- foto/video/file;
- messaggi vocali;
- chiamata audio 1:1;
- videochiamata 1:1;
- Live/ephemeral mode;
- stato peer/sessione comprensibile;
- route selection/privacy policy;
- Adaptive Defense base;
- Freedom Network Indicator;
- Emergency Shield Free con quota da dimensionare con dati reali;
- blocco contatto e requisiti store minimi.

L'utente non deve essere obbligato a comprendere account NEAR, gas, RPC, NAT, relay o primitive crittografiche nell'uso normale.

## 3. Recovery e multi-device

Il reset del telefono non deve distruggere ownership/licenza se l'utente possiede il Recovery Kit.

```text
Recovery Kit
 -> RootIdentity
 -> NEW DeviceKey
 -> device activation
 -> entitlement restore
```

La chain fa rispettare `max_devices`; il restore non deve permettere di usare una licenza su telefoni illimitati.

Dettagli: [`ACCOUNT_RECOVERY_LICENSES.md`](ACCOUNT_RECOVERY_LICENSES.md).

## 4. Contatti Free

Il limite Free è **10 contatti attivi**.

- non è un limite lifetime;
- eliminare/disattivare un contatto libera uno slot;
- la lista contatti resta locale e cifrata;
- non pubblicare social graph in chiaro;
- eventuale enforcement anti-tampering deve usare commitment/slot opachi.

## 5. Pagamenti e Pro

Freedom supporta payment adapter multipli:

```text
PayPal
crypto/stablecoin
future providers
```

PayPal può essere aperto dall'app senza API pubblica Freedom; worker privati outbound-only verificano il pagamento e pubblicano l'attestazione. Crypto verificabile on-chain può attivare direttamente l'entitlement.

Il callback client non è prova autoritativa di pagamento e nessun merchant secret deve stare nell'APK.

Dettagli: [`PAYMENTS.md`](PAYMENTS.md).

## 6. Adaptive Defense e Network Indicator

Il client deve mostrare uno stato rete sempre accessibile:

```text
NORMAL
SHIELDED
DEGRADED
SUSPECTED
UNAVAILABLE
```

In caso di incidente significativo il pannello può aprirsi automaticamente e distinguere fatti osservati da inferenze.

Core Free:

- route health;
- RPC/provider fallback;
- relay/path fallback;
- recovery rendezvous;
- `peer recently active + data path unavailable`;
- route switch automatico;
- stessa diagnosi tecnica di Pro;
- quota Emergency Shield quando disponibile.

Pro/Shield può aggiungere Always-Shielded, multi-hop, relay gestiti multipli, candidate pre-warmed, parallel failover, transport rotation aggressiva e Maximum Resilience.

## 7. Emergency bulletin e secure updates

Freedom deve poter ricevere bulletin firmati globali o geolocalizzati. Il matching geografico avviene localmente senza pubblicare la posizione dell'utente on-chain.

Gli aggiornamenti usano `FreedomRelease` firmati con hash/versione/signing fingerprint. L'APK resta off-chain e può essere scaricato da store, mirror temporanei, peer/relay Freedom o altri transport compatibili.

Una `SecurityPolicy` critica può disabilitare selettivamente funzioni/versioni vulnerabili, mantenendo recovery/update quando sicuro. Non deve esistere un kill-switch commerciale arbitrario.

Dettagli: [`EMERGENCY_UPDATES.md`](EMERGENCY_UPDATES.md).

## 8. Cosa NON blocca il primo lancio

Non sono prerequisiti della prima release pubblica:

- gruppi testuali;
- group voice/video;
- community pubbliche;
- bot/feed/social graph;
- mailbox offline;
- cloud history sync;
- Maximum Resilience completa;
- bridge/non-public pool avanzato;
- padding avanzato;
- update swarm completo se esiste già un canale sicuro di distribuzione V1.

## 9. Live Groups — V1.5

I gruppi devono preservare la semantica sincrona:

```text
Alice online  -> delivered
Bob online    -> delivered
David offline -> not delivered
```

Nessuna mailbox condivisa automatica per gli assenti.

Scope iniziale:

- piccoli gruppi testuali;
- media;
- membership autenticata;
- invito QR/link/capability;
- presenza minima;
- modalità effimera.

## 10. Freedom Live Rooms

Una **Live Room** è una sessione privata multi-party che serve le persone presenti adesso, non una mailbox permanente.

```text
explicit invite
authenticated membership
E2EE
history optional/off
Live mode
no server-side mailbox
no automatic offline delivery
```

## 11. Multi-party voice/video — V2

Non usare mesh P2P illimitata per gruppi grandi. Per media multi-party usare forwarding scalabile/SFU compatibile con il trust model:

- nodo media non è identity trust anchor;
- più operatori/nodi;
- sostituibile;
- nessun singolo SFU requisito permanente;
- design E2EE multi-party reviewato separatamente.

## 12. Registrazione ed economia Free

L'installazione non produce automaticamente una write on-chain.

Sponsored registration:

```text
RootIdentity
 -> anti-abuse proof / adaptive PoW
 -> sponsorship unused
 -> relayer rate limit
 -> bounded global budget
 -> register
```

Messaggi/chiamate non devono essere limitati per pagare il gas blockchain. Il costo chain deve dipendere da eventi rari del control-plane.

Dettagli: [`REGISTRATION_ECONOMICS.md`](REGISTRATION_ECONOMICS.md).

## 13. Roadmap prodotto

```text
V1 — Launch
  RootIdentity + Recovery Kit
  sponsored registration
  1 active device Free
  10 active contacts Free
  1:1 text/media/file
  voice messages
  audio/video call
  Live mode
  QR/link contacts
  basic Adaptive Defense
  Network Indicator
  payment/entitlement foundation

Security plane
  emergency bulletin
  signed release manifest
  secure update sources
  selective security policy

V1.5 — Live Groups
  small group text/media
  ephemeral Live Rooms
  authenticated membership

V2 — Multi-party realtime
  group voice/video
  scalable media forwarding
  replaceable/distributed SFU or equivalent

Pro evolution — Freedom Shield
  Always-Shielded
  multi-hop managed paths
  aggressive path/transport diversity
  Maximum Resilience
```

## 14. Launch quality gate

Blocker prima del Creator Pilot:

- latenza sistematica anomala dei messaggi;
- onboarding con configurazione tecnica manuale;
- crash riproducibili;
- session establishment inaffidabile;
- perdita/corruzione RootIdentity/DeviceIdentity;
- Recovery Kit non verificato end-to-end;
- chiamate 1:1 instabili;
- Network Indicator con falsi allarmi sistematici;
- privacy claim non implementati;
- dipendenza hardcoded da credenziali personali/singola infrastruttura;
- merchant secret nell'APK;
- meccanismo update non autenticato.

Gruppi e Shield avanzato non devono ritardare il lancio se il V1 soddisfa questi criteri.
