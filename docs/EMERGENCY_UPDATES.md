# Freedom — Emergency Bulletins & Secure Updates

## 1. Obiettivo

Freedom deve poter distribuire avvisi di emergenza, policy di sicurezza e release verificabili senza introdurre un server centrale obbligatorio o una singola sorgente di download.

La blockchain/control-plane conserva **manifest piccoli, firmati e verificabili**. File pesanti come APK non vengono memorizzati on-chain.

La distribuzione dell'app deve poter partire anche da un altro client Freedom tramite QR, peer locale o relay, mantenendo separati:

```text
chi fornisce i byte      != chi autorizza la release
artifact transport       != release authenticity
```

Dettagli del bootstrap peer-to-peer: [`APP_DISTRIBUTION.md`](APP_DISTRIBUTION.md).

## 2. Emergency Bulletin

Oggetto concettuale:

```text
EmergencyBulletin {
    version
    bulletin_id
    severity
    issued_at
    expires_at
    geographic_scope?
    topic?
    min_app_version?
    min_protocol_version?
    payload_hash
    issuer_set
    signatures[]
}
```

Possibili categorie:

- security advisory;
- network interference advisory;
- transport/relay migration;
- regional connectivity emergency;
- critical protocol incompatibility;
- recovery instruction.

## 3. Geolocalizzazione privacy-preserving

Una notifica geolocalizzata non richiede di pubblicare la posizione dell'utente.

Il bulletin contiene un'area o insieme di celle geografiche grossolane; il client confronta localmente la propria area con lo scope del bulletin.

```text
bulletin scope -> region/cells
local location -> local only
match          -> show notification
```

La posizione dell'utente non deve essere scritta sulla blockchain o inviata a un server Freedom per il solo matching geografico.

L'accesso alla posizione deve essere opzionale/permissioned; in assenza di permesso si possono mostrare bulletin globali/nazionali o permettere la selezione manuale dell'area.

## 4. Wake/discovery dei bulletin

La blockchain non può svegliare direttamente un'app sospesa. Il client deve supportare fonti ridondanti per scoprire che esiste un nuovo bulletin:

```text
app open / periodic check
peer gossip / Freedom transport
optional platform push hint
```

Un eventuale push di piattaforma è solo un hint non fidato: il client verifica sempre bulletin, firma, epoch e expiry tramite il control-plane verificabile.

## 5. Release manifest

Gli aggiornamenti Freedom usano un manifest piccolo e firmato:

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
    release_notes_hash
    issued_at
    signatures[]
}
```

La chain pubblica/verifica il manifest, non l'APK.

## 6. Distribuzione dell'APK

L'artifact può arrivare da più sorgenti intercambiabili:

```text
official store
HTTPS mirror temporaneo/dinamico
Freedom peer locale
Freedom peer remoto
Freedom relay/update node
future transports
```

La sorgente del file non è un trust anchor. Prima di proporre/installare un artifact devono essere verificati almeno:

- release manifest e firme;
- hash dell'artifact;
- package/application ID atteso;
- version code;
- signing certificate/lineage compatibile;
- policy di rollback/downgrade;
- `SecurityPolicy` applicabile.

Un mirror, relay o peer compromesso può distribuire byte sbagliati ma non deve poter trasformarli in una release Freedom valida.

## 7. Share Freedom / Install QR

Un client già installato può esporre:

```text
Share Freedom
  -> Install QR
```

Il destinatario può non avere Freedom, quindi il QR deve essere utilizzabile dalla fotocamera/browser di sistema e risolvere un `FreedomInstallDescriptor` o un bootstrap URL equivalente.

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

Il QR può condurre a:

- store ufficiale per una build store;
- endpoint locale temporaneo del peer;
- relay/update node;
- mirror compatibile.

Il descriptor indica dove trovare la release; non può ridefinire silenziosamente il release root o il signer set atteso.

## 8. APK external artifact, non embedded duplicate

Freedom non deve incorporare per default una seconda copia completa dell'APK installabile dentro il proprio APK.

Il modello preferito è:

```text
installed client
  + current verified release metadata
  + optional verified standalone APK cache
  + temporary peer transfer service
```

Un client Direct può scaricare/cacheare un artifact standalone già verificato e seedarlo ad altri utenti. Questo evita di duplicare permanentemente il binario dentro ogni build e mantiene trasporto/update separati.

## 9. First-install authenticity

Per un'app già installata, la continuità della firma Android e la policy Freedom possono impedire update con signer incompatibile.

Il primo sideload è più delicato: un'app farlocca può usare nome/icona simili e una propria firma. Il semplice fatto che un APK sia "firmato" non dimostra che sia una release Freedom autorizzata.

Il primo install richiede quindi almeno un trust anchor indipendente:

```text
store verificato
bootstrap web autenticato
release root fingerprint verificato out-of-band
bootstrap verifier con release root pinned
peer già considerato genuino dall'utente
più canali indipendenti che confermano lo stesso release root
```

La sorgente dei byte non deve poter stabilire da sola quale chiave sia la chiave ufficiale Freedom.

Dettagli completi: [`APP_DISTRIBUTION.md`](APP_DISTRIBUTION.md).

## 10. Store build vs direct build

Le build distribuite tramite store devono rispettare il meccanismo di install/update consentito dallo store.

Per la build Google Play, `Share Freedom` può indirizzare alla listing ufficiale; non basare il normale self-update o peer distribution sull'incorporazione di un altro APK o su installazioni silenziose.

Una distribuzione **Freedom Direct** può usare il proprio sistema di discovery/download da peer/relay/mirror, demandando comunque al sistema operativo le conferme e autorizzazioni necessarie per installare un APK.

Il protocollo di update separa:

```text
release authenticity -> Freedom signed manifest / release root
artifact transport    -> replaceable sources
installation policy   -> platform/store specific
```

## 11. Security policy

Freedom può pubblicare una policy firmata che identifica versioni/protocolli vulnerabili:

```text
SecurityPolicy {
    policy_epoch
    latest_version
    min_supported_version
    min_secure_version
    vulnerable_versions[]
    disabled_features[]
    severity
    reason_hash
    remediation_release
    issued_at
    expires_at?
    signatures[]
}
```

## 12. Niente kill-switch arbitrario

La policy di sicurezza non deve diventare un comando centralizzato per spegnere Freedom arbitrariamente.

Preferire enforcement **selettivo e fail-closed solo sulla superficie vulnerabile**:

```text
media vulnerability     -> disable vulnerable media path
transport vulnerability -> disable that transport
messaging vulnerability -> block unsafe messaging mode
identity compromise     -> severe recovery/update mode
```

Anche in modalità critica devono restare, quando tecnicamente sicuri:

- visualizzazione dell'avviso;
- recovery/export;
- verifica e download dell'update;
- eventuali percorsi di emergenza non vulnerabili.

## 13. Threshold governance

Per policy critiche e release manifest production, preferire più chiavi/ruoli e firme threshold invece di una sola chiave online.

Esempio concettuale:

```text
2-of-3 / 3-of-5 security signers
```

Le chiavi devono poter essere ruotate/revocate tramite una procedura documentata.

## 14. UX

Il Freedom Network Indicator può mostrare anche stato update/security:

```text
NETWORK      OK / DEGRADED / SUSPECTED
SECURITY     OK / UPDATE REQUIRED / CRITICAL
VERSION      current -> secure minimum
UPDATE PATH  store / peer / mirror / relay
```

Un security alert critico può aprire automaticamente il pannello, spiegando il motivo e la contromisura senza claim non verificabili.

Il pannello `Share Freedom` deve distinguere chiaramente:

```text
Official release   VERIFIED
Artifact source    PEER / RELAY / MIRROR / STORE
```

`PEER` è una sorgente, non un badge di autenticità.

## 15. Invarianti

- niente APK on-chain;
- niente seconda copia APK embedded obbligatoria nel client;
- un client Direct può cacheare/seedare un artifact standalone verificato;
- un altro utente può ottenere Freedom scansionando l'Install QR di un client esistente;
- nessuna singola URL/IP è necessaria per distribuire aggiornamenti;
- artifact autenticato da hash/firme/signer lineage, non dalla provenienza del mirror/peer;
- first install richiede un trust anchor indipendente;
- posizione utente non necessaria on-chain per notifiche geografiche;
- platform push opzionale e non autoritativo;
- policy critica firmata e preferibilmente threshold;
- niente kill-switch commerciale/arbitrario;
- una versione vulnerabile può essere limitata per proteggere l'utente mantenendo recovery/update disponibili quando sicuro.
