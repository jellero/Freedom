# Freedom — Peer Bootstrap & App Distribution

## 1. Obiettivo

Freedom deve poter essere installato anche **da persona a persona** senza rendere obbligatori Google Play, un singolo sito web, un singolo mirror o un singolo relay.

Caso d'uso principale:

```text
Alice ha già Freedom
Bob non ha Freedom

Alice -> Share Freedom -> mostra QR
Bob   -> fotocamera di sistema -> scansiona QR
      -> ottiene una sorgente valida dell'artifact
      -> scarica
      -> verifica autenticità
      -> installazione esplicitamente confermata dall'utente
```

Il telefono di Alice può essere una sorgente diretta dell'artifact quando tecnicamente possibile, oppure può indicare un relay/mirror/store sostituibile.

La proprietà fondamentale è:

> **la sorgente distribuisce byte; non decide cosa è una release Freedom valida.**

---

## 2. Decisione: APK esterno, non duplicato dentro l'app

La build Freedom non deve contenere per default una seconda copia dell'APK installabile come asset interno.

Motivi:

- raddoppia inutilmente parte della dimensione del pacchetto;
- complica update e cache invalidation;
- una build installata da App Bundle/store può essere composta da split e non coincide necessariamente con un APK standalone redistribuibile;
- lega troppo strettamente runtime e artifact di distribuzione;
- per la build Google Play, bundling/self-update tramite `REQUEST_INSTALL_PACKAGES` introduce vincoli di policy non coerenti con il client principale;
- non migliora l'autenticità: anche un APK embedded deve comunque essere verificato.

Il modello preferito è:

```text
installed Freedom app
       |
       +-- current verified release metadata
       +-- optional verified APK cache / seed artifact
       +-- peer transfer service
       +-- relay/mirror/store source hints
```

Un client Direct può mantenere opzionalmente in cache un **artifact standalone già verificato** e servirlo ad altri dispositivi. Non è necessario che tale artifact sia incorporato nel proprio APK.

---

## 3. Install QR

Il client espone un'azione esplicita:

```text
Share Freedom
  -> Install QR
```

Poiché il destinatario può non avere Freedom installato, il QR deve essere utilizzabile dalla **fotocamera/browser di sistema** e non dipendere esclusivamente da un custom URI gestito dall'app.

Concettualmente il QR risolve un descriptor:

```text
FreedomInstallDescriptor {
    version
    channel
    package_id
    release_manifest_hash
    release_id
    source_hints[]
    peer_transfer_capability?
    expires_at
}
```

Il descriptor può essere trasportato tramite URL HTTPS/bootstrap gateway oppure, in modalità nearby, tramite URL temporaneo raggiungibile sul link locale/hotspot del peer.

Il descriptor non è di per sé il trust anchor. Non deve poter sostituire liberamente le chiavi di release attese.

---

## 4. Sorgenti dell'artifact

Una stessa release può essere ottenuta da sorgenti diverse:

```text
STORE
  Google Play / altro store compatibile

PEER_LOCAL
  telefono/tablet/desktop Freedom vicino
  LAN / hotspot / transport locale supportato

RELAY
  relay/update node Freedom

MIRROR
  HTTPS mirror temporaneo/dinamico

PEER_NETWORK
  peer Freedom raggiungibile attraverso un percorso compatibile
```

La selezione della sorgente è separata dall'autenticità dell'artifact.

Se un mirror o relay viene compromesso può distribuire byte errati, ma questi devono essere rifiutati prima dell'installazione.

---

## 5. Peer-local transfer

Per il vero trasferimento client-to-client:

```text
Alice Freedom
  -> apre endpoint temporaneo di download
  -> genera capability casuale a TTL breve
  -> mostra QR

Bob camera/browser
  -> apre URL temporaneo
  -> scarica verified Freedom APK
```

Il trasferimento può avvenire su:

- stessa LAN;
- hotspot temporaneo;
- Wi-Fi Direct/transport equivalente quando supportato;
- altro transport locale futuro.

L'endpoint deve essere:

- temporaneo;
- capability-protected;
- rate-limited;
- read-only;
- limitato al solo artifact previsto;
- chiuso automaticamente a expiry/completamento;
- non trasformato in file server generico.

Anche su LAN non fidata l'integrità non dipende dal trasporto: hash e firme devono essere verificati.

---

## 6. Relay/network transfer

Se il trasferimento locale non è disponibile:

```text
Alice client
  -> sceglie/ottiene source descriptor di un relay/update node
  -> mostra QR

Bob
  -> scarica dal relay
```

Il relay non deve essere fidato per autenticare la release.

Può essere:

- managed;
- community;
- temporary;
- private;
- device relay con sufficiente capacità, se il transport lo consente.

L'APK può essere cacheato/content-addressed tramite `artifact_sha256` per evitare copie semanticamente differenti della stessa release.

---

## 7. Release authenticity

Il controllo canonico deriva da `FreedomRelease` / `SecurityPolicy`, non dalla URL e non dal peer che mostra il QR.

```text
FreedomRelease {
    version_code
    version_name
    package_id
    artifact_sha256
    artifact_size
    signing_cert_fingerprint
    min_supported_version
    min_secure_version
    criticality
    source_descriptors[]
    issued_at
    signatures[]
}
```

Prima dell'installazione devono essere verificati almeno:

1. release manifest valido e non scaduto/revocato;
2. firme release valide secondo il release root / signer set atteso;
3. `artifact_sha256` esatto;
4. `artifact_size` coerente;
5. `package_id` ufficiale atteso;
6. `version_code` e policy anti-downgrade;
7. certificato di firma APK atteso o appartenente alla lineage autorizzata;
8. eventuale `SecurityPolicy` che renda la versione vulnerabile/non installabile.

Fail closed su mismatch.

---

## 8. Android signing e continuità

Android richiede APK firmati digitalmente.

Per Freedom Direct va mantenuta una signing lineage controllata e documentata. La verifica applicativa deve supportare rotazione autorizzata delle chiavi senza accettare certificati arbitrari.

Per un'app **già installata**, Android impedisce normalmente che un APK con package name uguale ma certificato incompatibile venga accettato come update.

Il rischio più delicato è il **primo sideload**, perché un attacker può creare un'app diversa con nome/icona simili e firmarla con la propria chiave.

Per questo il primo install non deve fidarsi del semplice fatto che:

```text
"l'APK è firmato"
```

ma deve verificare:

```text
"l'APK è firmato da una chiave autorizzata per Freedom"
```

---

## 9. First-install trust anchor

Non esiste una soluzione crittografica che permetta a un dispositivo completamente nuovo di distinguere una chiave Freedom autentica da una chiave inventata da un attacker **senza almeno un trust anchor indipendente**.

Trust anchor possibili:

```text
store verificato
bootstrap web ufficiale autenticato
release root fingerprint pubblicato/verificato out-of-band
manifest threshold-signed verificato da un bootstrap verifier con root pinned
peer già noto e considerato genuino dall'utente
più canali indipendenti che confermano lo stesso release root
```

Quindi il peer-to-peer install propaga fiducia da un'installazione già considerata valida, ma non può magicamente certificare una sorgente fake se l'utente parte da zero e non dispone di alcun riferimento esterno.

Il client deve mostrare chiaramente il release/signing identity quando condivide Freedom e non deve permettere a un QR ricevuto di ridefinire silenziosamente il release root.

---

## 10. Difesa contro app farlocche

Threats:

```text
fake Freedom APK
fake QR
malicious mirror
malicious relay
old vulnerable APK
same icon/name, different package
same package_id, attacker signing key
modified APK after signing
compromised release signer
```

Difese:

- package ID canonico;
- APK signature verification;
- signing certificate fingerprint/lineage;
- signed `FreedomRelease`;
- hash content-addressed;
- min secure version / anti-downgrade;
- threshold/multi-key release governance;
- source diversity;
- source never authoritative;
- warning/fail closed su release non verificabile;
- optional display di fingerprint/short release identity per verifica umana;
- revocation/rotation delle release keys tramite governance documentata.

Un QR deve contenere capability e locator, non un'autorità che dice unilateralmente "questa chiave è Freedom".

---

## 11. Play build vs Direct build

### Google Play build

La build Play deve usare il percorso conforme allo store per install/update.

`Share Freedom` può mostrare un QR che porta alla listing ufficiale/store bootstrap.

Non basare la build Play su:

- APK embedded per self-update;
- installazione silenziosa;
- `REQUEST_INSTALL_PACKAGES` usato come meccanismo ordinario di self-update/P2P app distribution se non conforme alle policy applicabili.

### Freedom Direct build

La Direct build può supportare:

```text
peer-local APK transfer
relay/mirror download
verified release manifest
PackageInstaller / installer di sistema
```

L'installazione deve comunque essere **user initiated** e rispettare le autorizzazioni Android per le sorgenti esterne. Non assumere silent install su normali device consumer.

---

## 12. UX proposta

Sul client esistente:

```text
Share Freedom

[QR]

Freedom 1.4.2
Official release: VERIFIED
Transfer: Nearby / Relay / Store
Expires: 10 min
```

Sul nuovo dispositivo, prima dell'installazione quando il bootstrap verifier è disponibile:

```text
Freedom Messenger
Version 1.4.2
Package: verified
Release signature: verified
APK hash: verified
Security policy: secure
Source: peer / relay / mirror

Install
```

Non usare la provenienza `peer` come badge di sicurezza; è solo la sorgente del file.

---

## 13. Invarianti

- un client Freedom può aiutare un altro utente a ottenere l'app mostrando un QR;
- APK non duplicato obbligatoriamente dentro l'app;
- client Direct può cacheare e seedare un artifact standalone già verificato;
- store, peer, relay e mirror sono sorgenti intercambiabili;
- byte source != release authority;
- first install richiede un trust anchor indipendente;
- Android/package signature + FreedomRelease + hash + signer lineage devono concordare;
- nessuna installazione silenziosa su normali device consumer;
- un relay compromesso non deve poter produrre una release Freedom valida;
- downgrade/release vulnerabili devono essere bloccabili tramite `SecurityPolicy`;
- il sistema di distribuzione non deve diventare un nuovo server centrale obbligatorio.
