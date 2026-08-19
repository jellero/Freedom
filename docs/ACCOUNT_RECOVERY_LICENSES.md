# Freedom — Account Recovery & Licenses

## 1. Separazione delle identità

Freedom distingue:

```text
RootIdentity             -> ownership / recovery / licenze
DeviceKey                -> chiave operativa del singolo device
DeviceRecordCommitment   -> handle opaco del control-plane
PairwiseContactAlias     -> identità specifica della relazione
Session keys             -> materiale effimero delle comunicazioni
```

La `RootIdentity` non viene usata per cifrare le chat. Serve per autorizzare/revocare device, recuperare ownership e dimostrare la titolarità degli entitlement commerciali o temporanei.

Freedom non richiede un `DeviceID` globale user-facing o transport-facing. Dettagli: [`IDENTITY_MODEL.md`](IDENTITY_MODEL.md).

## 2. Prima installazione

La prima installazione genera localmente:

```text
RootKeyPair
root_commitment = H(root_public_key || domain)
DeviceKeyPair
device_random_secret
DeviceRecordCommitment
```

`DeviceRecordCommitment` è un handle tecnico opaco per lookup/rotation/revocation del control-plane. Non è username, contatto o indirizzo di rete.

L'installazione locale, da sola, non deve obbligatoriamente produrre una write on-chain. La registrazione viene sponsorizzata/effettuata quando l'identità deve diventare verificabile secondo la policy del protocollo.

## 3. Freedom Recovery Kit

Il client deve permettere di esportare un Recovery Kit composto da:

- QR contenente un bundle cifrato/versionato;
- recovery code umano separato;
- checksum/versione per rilevare errori.

```text
EncryptedRecoveryBundle {
    version
    root_secret_ciphertext
    root_commitment
    kdf_params
    checksum
}
```

Il QR non deve contenere la Root private key in chiaro. Il recovery code deve essere necessario per decifrare il bundle o derivarne la chiave. Salvare QR e recovery code nello stesso posto deve essere sconsigliato dal client.

## 4. Ripristino dopo reset/nuovo telefono

```text
install Freedom
 -> Restore
 -> scan/import Recovery QR
 -> enter Recovery Code
 -> recover RootIdentity
 -> generate NEW DeviceKeyPair
 -> generate NEW DeviceRecordCommitment
 -> authorize/activate new device
 -> resolve existing entitlements
```

Un restore non clona la vecchia DeviceKey né il vecchio handle tecnico. Il nuovo telefono genera materiale operativo nuovo.

## 5. Entitlement

Una licenza Freedom appartiene alla RootIdentity, non a un APK o a un singolo device.

```text
FreedomEntitlement {
    version
    root_commitment
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

Benefit temporanei possono modificare capacità specifiche senza cambiare il tier principale:

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

```text
active_devices <= max_devices
```

Policy iniziale:

```text
FREE     -> 1 device attivo
PRO      -> max_devices definito dal piano
BUSINESS -> policy dedicata
```

L'attivazione di un nuovo device richiede una prova autorizzata dalla RootIdentity:

```text
ActivateDevice {
    root_commitment
    device_record_commitment
    device_public_key
    entitlement_epoch
    nonce
    root_signature
}
```

Se il limite è raggiunto, l'utente può revocare un device precedente e liberare uno slot usando la RootIdentity recuperata.

## 7. Privacy degli slot device

Non pubblicare strutture leggibili che colleghino direttamente una persona ai suoi device.

Preferire slot/commitment opachi che consentano enforcement del conteggio. Un `DeviceRecordCommitment` non deve essere riutilizzato come alias di contatto, routing identifier o token relay.

Il design definitivo deve considerare correlazioni temporali tra activation, revocation, rendezvous e entitlement.

## 8. Contatti Free

La policy commerciale Free prevede **10 contatti attivi base**.

Il contatto logico rappresenta una persona/RootIdentity, non ogni suo singolo device. Un contatto può quindi possedere più device autorizzati senza occupare più slot della rubrica.

Il limite riguarda gli slot attivi, non il numero totale di persone mai conosciute. Eliminare/disattivare un contatto libera uno slot.

La rubrica resta locale e cifrata. Non pubblicare il social graph in chiaro on-chain. Se serve enforcement resistente a client modificati, usare commitment/slot opachi o primitive equivalenti.

## 9. Relay Contributor benefit

Un account Free che mantiene un `DEVICE_RELAY` qualificato riceve un benefit temporaneo di **+10 contact slots**.

```text
base_contact_slots = 10
relay_contributor_bonus = 10
effective_contact_slots = 20
```

Il benefit appartiene alla RootIdentity come capacità temporanea, ma deriva dal contributo del device relay attivo. Non viene ripristinato automaticamente su un nuovo telefono se il nuovo device non contribuisce come relay.

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

Il semplice toggle `relay_enabled=true` non è prova sufficiente. La qualificazione segue [`RELAYS.md`](RELAYS.md) e deve evitare log pubblici del traffico o dei peer serviti.

Se il benefit scade e l'account ha più contatti del limite base:

- non eliminare automaticamente i contatti;
- non invalidare la RootIdentity;
- non trasformare il beneficio in un lockout di sicurezza;
- impedire soltanto nuove aggiunte finché la quota torna valida o il benefit torna attivo.

## 10. Invarianti

- RootIdentity, DeviceKey, device commitment, alias pairwise e session keys hanno ruoli separati;
- non esiste un `DeviceID` globale necessario al network layer;
- recovery non significa clonazione della DeviceKey;
- la licenza segue la RootIdentity;
- il numero di device attivi è limitabile/verificabile con slot opachi;
- il contatto è una persona/RootIdentity, non un singolo device;
- la rubrica non diventa un social graph pubblico;
- Relay Contributor aumenta la quota contatti senza diventare Pro;
- il bonus relay è temporaneo e legato a contributo reale;
- la scadenza del bonus non cancella automaticamente contatti;
- il server commerciale non è necessario per verificare ogni avvio dell'app;
- il Recovery Kit deve restare utilizzabile anche se i servizi commerciali ufficiali sono indisponibili.
