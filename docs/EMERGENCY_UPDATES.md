# Freedom — Emergency Bulletins & Secure Updates

## 1. Obiettivo

Freedom deve poter distribuire avvisi di emergenza e policy di sicurezza verificabili senza introdurre un server centrale obbligatorio o una singola sorgente di download.

La blockchain/control-plane conserva **manifest piccoli, firmati e verificabili**. File pesanti come APK non vengono memorizzati on-chain.

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
Freedom peer
Freedom relay/update node
future transports
```

La sorgente del file non è un trust anchor. Prima di proporre/installare un artifact, il client verifica almeno:

- hash del manifest;
- package/application ID atteso;
- version code;
- signing certificate compatibile;
- firme del release manifest;
- policy di rollback/downgrade.

Un mirror compromesso può distribuire byte sbagliati ma non deve poter trasformarli in una release Freedom valida.

## 7. Store build vs direct build

Le build distribuite tramite store devono rispettare il meccanismo di update imposto/consentito dallo store.

Una distribuzione Freedom Direct può usare il proprio sistema di discovery/download, demandando comunque al sistema operativo le conferme/controlli necessari per installare un APK.

Il protocollo di update deve quindi separare:

```text
release authenticity -> Freedom signed manifest
artifact transport    -> replaceable sources
installation policy  -> platform/store specific
```

## 8. Security policy

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

## 9. Niente kill-switch arbitrario

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

## 10. Threshold governance

Per policy critiche e release manifest production, preferire più chiavi/ruoli e firme threshold invece di una sola chiave online.

Esempio concettuale:

```text
2-of-3 / 3-of-5 security signers
```

Le chiavi devono poter essere ruotate/revocate tramite una procedura documentata.

## 11. UX

Il Freedom Network Indicator può mostrare anche stato update/security:

```text
NETWORK      OK / DEGRADED / SUSPECTED
SECURITY     OK / UPDATE REQUIRED / CRITICAL
VERSION      current -> secure minimum
UPDATE PATH  store / mirror / peer / relay
```

Un security alert critico può aprire automaticamente il pannello, spiegando il motivo e la contromisura senza claim non verificabili.

## 12. Invarianti

- niente APK on-chain;
- nessuna singola URL/IP è necessaria per distribuire aggiornamenti;
- artifact autenticato da hash/firma, non dalla provenienza del mirror;
- posizione utente non necessaria on-chain per notifiche geografiche;
- platform push opzionale e non autoritativo;
- policy critica firmata e preferibilmente threshold;
- niente kill-switch commerciale/arbitrario;
- una versione vulnerabile può essere limitata per proteggere l'utente mantenendo recovery/update disponibili quando sicuro.
