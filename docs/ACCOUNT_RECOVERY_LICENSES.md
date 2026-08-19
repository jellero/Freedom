# Freedom — Account Recovery & Licenses

## 1. Separazione delle identità

Freedom distingue:

```text
RootIdentity      -> ownership/recovery/licenze
DeviceIdentity    -> identità operativa del singolo device
Session keys      -> materiale effimero delle comunicazioni
```

La `RootIdentity` non viene usata per cifrare le chat. Serve per autorizzare nuovi device, recuperare l'account Freedom e dimostrare la titolarità degli entitlement commerciali.

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
    policy_version
    status
}
```

Il metodo di pagamento non è necessario per verificare l'entitlement dopo l'emissione.

## 6. Controllo multi-device on-chain

Il recovery della RootIdentity non deve permettere l'uso illimitato della stessa licenza su telefoni arbitrari.

La chain deve poter far rispettare un limite di device attivi definito dal tier:

```text
active_devices <= max_devices
```

Policy iniziale di prodotto:

```text
FREE -> 1 device attivo
PRO  -> max_devices definito dal piano
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

La policy commerciale Free prevede **10 contatti attivi**.

Il limite riguarda gli slot attivi, non il numero totale di persone mai conosciute. Eliminare/disattivare un contatto libera uno slot.

La rubrica resta locale e cifrata. Non pubblicare il social graph in chiaro on-chain.

Se serve enforcement resistente a client modificati, usare commitment/slot opachi o una primitive equivalente, non un elenco pubblico dei contatti.

## 9. Invarianti

- RootIdentity e DeviceIdentity sono separate;
- recovery non significa clonazione della DeviceKey;
- la licenza segue la RootIdentity;
- il numero di device attivi è limitabile/verificabile on-chain;
- la rubrica non diventa un social graph pubblico;
- il server commerciale non è necessario per verificare ogni avvio dell'app;
- il Recovery Kit deve restare utilizzabile anche se i servizi commerciali ufficiali sono temporaneamente indisponibili.
