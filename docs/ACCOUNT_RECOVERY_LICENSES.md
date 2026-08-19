# Freedom — Account Recovery & Licenses

## 1. Separazione delle identità

Freedom distingue:

```text
RootIdentity      -> ownership/recovery/licenze
DeviceIdentity    -> identità operativa del singolo device
Session keys      -> materiale effimero delle comunicazioni
```

La `RootIdentity` non viene usata per cifrare le chat. Serve per autorizzare nuovi device, recuperare l'account Freedom e dimostrare la titolarità degli entitlement commerciali o temporanei.

## 2. Prima installazione

La prima installazione genera localmente:

```text
RootKeyPair
RootAccountID = H(root_public_key)
DeviceKeyPair
DeviceID
```

L'installazione locale, da sola, non deve obbligatoriamente produrre una write on-chain. La registrazione viene sponsorizzata/effettuata quando l'identità deve diventare verificabile secondo la policy del protocollo.

## 3. Freedom Recovery Kit

Il client deve permettere di esportare un Recovery Kit composto da:

- QR contenente un bundle cifrato/versionato;
- recovery code umano separato;
- checksum/versione per rilevare errori.

Esempio concettuale:

```text
EncryptedRecoveryBundle {
    version
    root_secret_ciphertext
    account_id
    kdf_params
    checksum
}
```

Il QR non deve contenere la Root private key in chiaro.

Il recovery code deve essere necessario per decifrare il bundle o derivarne la chiave. Salvare QR e recovery code nella stessa immagine riduce fortemente il beneficio della cifratura e deve essere sconsigliato dal client.

## 4. Ripristino dopo reset/nuovo telefono

```text
install Freedom
 -> Restore
 -> scan/import Recovery QR
 -> enter Recovery Code
 -> recover RootIdentity
 -> generate NEW DeviceKeyPair
 -> authorize/activate new device
 -> resolve existing entitlements
```

Un restore non deve clonare automaticamente la vecchia DeviceKey. Il nuovo telefono genera una nuova chiave operativa.

## 5. Entitlement

Una licenza Freedom appartiene alla RootIdentity, non a un APK installato o a un singolo DeviceID.

```text
FreedomEntitlement {
    version
    account_commitment
    tier
    issued_at
    expires_at?
    entitlement_epoch
    max_devices
    base_contact_slots
    policy_version
    status
}
```

Il metodo di pagamento non è necessario per verificare l'entitlement dopo l'emissione.

Benefit temporanei possono modificare capacità specifiche senza cambiare il tier principale.

```text
EntitlementBenefit {
    benefit_type
    value
    issued_at
    expires_at?
    proof_commitment?
    status
}
```

## 6. Controllo multi-device on-chain

Il recovery della RootIdentity non deve permettere l'uso illimitato della stessa licenza su telefoni arbitrari.

La chain deve poter far rispettare un limite di device attivi definito dal tier:

```text
active_devices <= max_devices
```

Policy iniziale di prodotto:

```text
FREE     -> 1 device attivo
PRO      -> max_devices definito dal piano
BUSINESS -> policy dedicata
```

L'attivazione di un nuovo device richiede una prova autorizzata dalla RootIdentity:

```text
ActivateDevice {
    account_commitment
    device_commitment
    entitlement_epoch
    nonce
    root_signature
}
```

Se il limite è raggiunto, l'utente deve poter revocare un device precedente e liberare uno slot usando la RootIdentity recuperata.

## 7. Privacy degli slot device

Non pubblicare una struttura leggibile:

```text
Account -> [DeviceID_A, DeviceID_B, ...]
```

Preferire commitment/slot opachi che consentano enforcement del conteggio senza esporre direttamente il mapping tra account e DeviceID.

Il design definitivo deve considerare anche correlazioni temporali e rotazione degli slot.

## 8. Contatti Free

La policy commerciale Free prevede **10 contatti attivi base**.

Il limite riguarda gli slot attivi, non il numero totale di persone mai conosciute. Eliminare/disattivare un contatto libera uno slot.

La rubrica resta locale e cifrata. Non pubblicare il social graph in chiaro on-chain.

Se serve enforcement resistente a client modificati, usare commitment/slot opachi o una primitive equivalente, non un elenco pubblico dei contatti.

## 9. Relay Contributor benefit

Un account Free che mantiene un `DEVICE_RELAY` qualificato riceve un benefit temporaneo di **+10 contact slots**.

```text
base_contact_slots = 10
relay_contributor_bonus = 10

effective_contact_slots = 20
```

Il benefit appartiene all'account/RootIdentity come capacità temporanea, ma deriva dal contributo del device relay attivo. Non viene ripristinato automaticamente su un nuovo telefono se il nuovo device non contribuisce come relay.

Esempio:

```text
EntitlementBenefit {
    benefit_type = RELAY_CONTRIBUTOR_CONTACTS
    value = 10
    issued_at
    expires_at
    proof_commitment
    status = ACTIVE
}
```

Il semplice toggle `relay_enabled=true` non è prova sufficiente. La qualificazione deve seguire la policy definita in [`RELAYS.md`](RELAYS.md) e deve evitare log pubblici del traffico o dei peer serviti.

Se il benefit scade e l'account ha più contatti del limite base:

- non eliminare automaticamente i contatti;
- non invalidare retroattivamente la RootIdentity;
- non trasformare il beneficio in un lockout di sicurezza;
- impedire soltanto nuove aggiunte finché `active_contacts <= effective_contact_slots` o il benefit torna attivo.

## 10. Invarianti

- RootIdentity e DeviceIdentity sono separate;
- recovery non significa clonazione della DeviceKey;
- la licenza segue la RootIdentity;
- il numero di device attivi è limitabile/verificabile on-chain;
- la rubrica non diventa un social graph pubblico;
- Relay Contributor aumenta la quota contatti senza diventare Pro;
- il bonus relay è temporaneo/revocabile secondo contributo reale, non un premio permanente al toggle;
- la scadenza del bonus non cancella automaticamente contatti;
- il server commerciale non è necessario per verificare ogni avvio dell'app;
- il Recovery Kit deve restare utilizzabile anche se i servizi commerciali ufficiali sono temporaneamente indisponibili.
