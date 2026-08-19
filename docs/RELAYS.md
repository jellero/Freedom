# Freedom — Relay Architecture

## 1. Definizione fisica

Un relay Freedom è **una macchina o un dispositivo che esegue software Freedom Relay e inoltra ciphertext tra endpoint o tra hop successivi**.

Può essere:

- VPS/VM Linux;
- server dedicato;
- mini PC / Raspberry Pi;
- nodo community;
- infrastruttura managed Freedom;
- infrastruttura privata di un'organizzazione;
- **un normale dispositivo Freedom** che abilita volontariamente la modalità relay quando risorse e connettività lo consentono.

Il relay non è un account server, non è una mailbox e non è un trust anchor.

Principio:

> **forward, not store.**

---

## 2. Tipi di relay

```text
DEDICATED_RELAY
  VPS/server/VM sempre o quasi sempre raggiungibile

COMMUNITY_RELAY
  nodo gestito volontariamente da community/utente

DEVICE_RELAY
  telefono/tablet/desktop Freedom che presta capacità di forwarding

PRIVATE_RELAY
  nodo di un'organizzazione o dell'utente

MANAGED_RELAY
  infrastruttura commerciale Freedom/partner
```

Il wire protocol non deve dipendere dall'operatore del nodo.

---

## 3. Device Relay

Un dispositivo Freedom può svolgere contemporaneamente due ruoli:

```text
ENDPOINT
  comunica per il proprio utente

RELAY
  inoltra ciphertext per circuiti di altri peer
```

I due ruoli devono essere separati logicamente. Essere relay non concede accesso alle chiavi E2EE delle sessioni inoltrate e non autorizza il device a impersonare altri endpoint.

### 3.1 Opt-in e policy

La modalità `DEVICE_RELAY` deve essere **opt-in** nei client ufficiali salvo deployment esplicitamente amministrati.

Policy configurabili:

```text
relay_enabled
wifi_only
charging_only
battery_minimum
metered_network_allowed
max_bandwidth
max_concurrent_circuits
max_memory
max_cpu
background_policy
```

Un telefono non deve consumare in modo imprevedibile batteria o traffico dati per sostenere la rete.

### 3.2 Reachability

Un device relay non necessita sempre di una porta pubblica in ingresso.

Può essere utilizzabile quando:

- possiede un endpoint pubblico/NAT mapping valido;
- è raggiungibile tramite un altro trasporto supportato;
- mantiene connessioni outbound verso il fabric Freedom e inoltra tra circuiti già stabiliti;
- è disponibile localmente tramite LAN/Wi-Fi Direct/transport futuri.

Quindi `DEVICE_RELAY` descrive una **capacità di forwarding**, non implica necessariamente `public_ip:port` permanente.

La raggiungibilità concreta viene annunciata come `RelayCandidate` temporaneo.

---

## 4. RelayCandidate

```text
RelayCandidate {
    relay_id
    relay_class
    endpoint
    transport
    capability_token?
    capacity_hint?
    expires_at
}
```

`relay_class` può distinguere `DEDICATED`, `COMMUNITY`, `DEVICE`, `PRIVATE`, `MANAGED` senza rendere la classe un trust signal crittografico.

Un relay candidate deve avere TTL ed essere sostituibile.

---

## 5. Forwarding

Flusso minimo:

```text
Alice
  |
  | RelayPacket ciphertext
  v
Relay
  |
  | forward
  v
Bob
```

Il relay usa token/capability di circuito invece di richiedere un mapping pubblico leggibile `DeviceID -> peer`.

```text
RelayPacket {
    version
    packet_id
    next_hop_token
    hop_limit
    expires_at
    ciphertext
}
```

Il relay può conoscere quanto necessario al forwarding, ma non deve ricevere le session keys endpoint-to-endpoint.

---

## 6. Nessuna mailbox

Se il next hop non è raggiungibile, il relay non conserva il messaggio per una consegna futura.

Permessi:

- buffer RAM bounded per congestione e forwarding immediato;
- timeout brevi;
- retry locale limitato per il pacchetto/circuito corrente.

Vietato nel protocollo base:

- inbox persistente;
- database della conversazione;
- store-and-forward offline;
- replica del ciphertext per consegna futura.

La caduta del relay può perdere pacchetti in-flight. Gli endpoint possono cambiare route o ristabilire la sessione, ma non trasformano il messaggio in consegna asincrona.

---

## 7. Privacy

Un singolo relay può osservare metadata di rete come timing, volume e connessioni adiacenti.

Un percorso semplice:

```text
Alice -> Relay A -> Bob
```

può nascondere gli IP degli endpoint l'uno all'altro, ma Relay A può potenzialmente osservare entrambi i lati.

Per privacy superiore Freedom Shield può usare circuiti multi-hop:

```text
Alice -> Relay A -> Relay B -> Bob
```

Obiettivo del multi-hop: nessun singolo relay dovrebbe conoscere contemporaneamente origine finale, destinazione finale e contenuto.

Il multi-hop deve usare un design crittografico/circuit protocol dedicato; concatenare semplici proxy non è sufficiente.

---

## 8. Relay discovery e diversity

Relay candidate possono provenire da:

- cache locale verificata;
- route update E2EE;
- rendezvous/recovery pairwise;
- bootstrap multipli;
- directory/pool compatibili non autoritativi;
- peer Freedom che annunciano temporaneamente capacità relay.

Nessuna singola directory deve autenticare il relay come identità del destinatario.

Il path selector deve preferire diversity tra:

- operatori;
- ASN/provider;
- geografia;
- classi di transport;
- relay dedicated e device/community quando appropriato.

---

## 9. Abuse e resource bounds

Ogni relay, incluso un device relay, deve imporre almeno:

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

Può richiedere capability/token per evitare uso arbitrario.

Un device relay deve poter interrompere immediatamente nuovi circuiti quando cambiano batteria, rete, temperatura o policy; i circuiti esistenti possono essere chiusi secondo una breve graceful policy bounded.

---

## 10. Security invariants

Un relay non deve poter:

- decifrare payload E2EE;
- impersonare un endpoint;
- firmare come DeviceID del peer;
- creare ACK applicativi validi per conto del destinatario;
- trasformarsi in mailbox persistente;
- diventare percorso obbligatorio permanente.

Un relay può:

- rifiutare;
- droppare;
- ritardare;
- osservare metadata adiacenti;
- limitare capacità;
- uscire dalla rete.

Il protocollo deve quindi trattarlo come **non fidato e sacrificabile**.

---

## 11. Relay Contributor reward

Freedom incentiva gli utenti Free che mettono a disposizione il proprio dispositivo come relay utile alla rete.

Policy iniziale:

```text
FREE
  10 active contact slots

FREE + RELAY CONTRIBUTOR
  20 active contact slots
  = 10 base + 10 bonus
```

Il bonus è un **entitlement di partecipazione**, non una licenza Pro e non modifica la sicurezza crittografica.

### 11.1 Requisito di contributo

Non deve essere sufficiente accendere `relay_enabled` per pochi secondi e ottenere il bonus permanentemente.

Il beneficio resta attivo quando il device soddisfa una policy minima di contributo verificabile, definita e calibrata con dati reali. I segnali possono includere, senza richiedere tutti contemporaneamente:

- disponibilità relay durante finestre temporali bounded;
- circuiti accettati;
- traffico effettivamente inoltrato con soglie minime/massime;
- uptime utile;
- attestazioni/receipt opache di forwarding;
- rispetto dei limiti e assenza di comportamento abusivo.

Non premiare volume illimitato: il sistema non deve incentivare traffico artificiale, relay farming o spreco di banda.

### 11.2 Privacy del reward

La prova di contributo non deve pubblicare:

- contatti dell'utente;
- peer serviti;
- contenuto inoltrato;
- mapping leggibile `DeviceID -> circuiti`;
- cronologia dettagliata del traffico.

Se serve enforcement resistente a client modificati, usare receipt/commitment opachi e finestre aggregate invece di un log pubblico dei circuiti.

### 11.3 Perdita del bonus

Se l'utente disabilita il relay o non soddisfa più la policy minima, il bonus può scadere dopo una grace period bounded.

Se in quel momento l'utente ha più di 10 contatti attivi:

- nessun contatto viene cancellato automaticamente;
- le sessioni esistenti non vengono distrutte come punizione commerciale;
- non può aggiungere nuovi contatti finché non torna entro il limite base o riattiva/riqualifica il contributo relay;
- il client mostra chiaramente lo stato degli slot.

Questo evita perdita dati/social state improvvisa e rende il reward reversibile senza dark pattern.

---

## 12. Economia relay

```text
DIRECT
  nessun costo relay

DEVICE / COMMUNITY RELAY
  capacità volontaria / best effort
  Relay Contributor: +10 contact slots per Free qualificato

EMERGENCY SHIELD FREE
  quota managed bounded

MANAGED RELAY
  capacità commerciale

MULTI-HOP / MAXIMUM RESILIENCE
  più banda e più nodi, tipicamente premium
```

Ulteriori incentivi economici ai relay community/device sono possibili in futuro, ma richiedono un design separato contro Sybil, farming e abuso.

---

## 13. Principio architetturale

> **Qualsiasi macchina compatibile può inoltrare Freedom; nessuna macchina deve diventare Freedom.**

Un VPS, un server dedicato o un telefono possono essere relay. La sicurezza della conversazione resta negli endpoint e il percorso deve poter cambiare senza cambiare identità o protocollo applicativo.
