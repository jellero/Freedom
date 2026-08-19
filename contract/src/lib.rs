use near_sdk::json_types::{Base64VecU8, U64, U128};
use near_sdk::store::LookupMap;
use near_sdk::{AccountId, NearToken, Promise, assert_one_yocto, env, near, require};
use std::collections::BTreeMap;

const CONTRACT_VERSION: &str = "0.4.0";
const PROTOCOL_VERSION: u16 = 1;
const DEVICE_ID_BYTES: usize = 32;
const P256_PUBLIC_KEY_BYTES: usize = 33;
const P256_SIGNATURE_BYTES: usize = 64;
const AUTH_DOMAIN: &[u8] = b"FREEDOM_REGISTRY_V1\0";
const REGISTER_OPERATION: u8 = 1;
const ROTATE_OPERATION: u8 = 2;
const REVOKE_OPERATION: u8 = 3;
const PUBLISH_CONTACT_OPERATION: u8 = 4;
const SEND_MESSAGE_OPERATION: u8 = 5;
const FREEDOM_NUMBER_PAYLOAD_DIGITS: usize = 19;
const FREEDOM_NUMBER_DIGITS: usize = FREEDOM_NUMBER_PAYLOAD_DIGITS + 1;
const MESSAGE_ID_BYTES: usize = 32;
const MESSAGE_NONCE_BYTES: usize = 12;
const MAX_MESSAGE_CIPHERTEXT_BYTES: usize = 4_096;
const MIN_MESSAGE_TTL_NS: u64 = 60 * 1_000_000_000;
const MAX_MESSAGE_TTL_NS: u64 = 7 * 24 * 60 * 60 * 1_000_000_000;
const MAX_MAILBOX_MESSAGES: usize = 100;

#[near(serializers = [borsh, json])]
#[derive(Clone, Debug, PartialEq, Eq)]
#[serde(rename_all = "snake_case")]
pub enum DeviceStatus {
    Active,
    Revoked,
}

#[near(serializers = [borsh])]
#[derive(Clone)]
struct StoredDeviceRecord {
    version: u8,
    identity_public_key: Vec<u8>,
    key_epoch: u64,
    auth_nonce: u64,
    status: DeviceStatus,
    protocol_version: u16,
    updated_at_ns: u64,
}

#[near(serializers = [json])]
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct DeviceRecord {
    pub version: u8,
    pub device_id: String,
    pub identity_public_key: Base64VecU8,
    pub key_epoch: U64,
    pub auth_nonce: U64,
    pub status: DeviceStatus,
    pub protocol_version: u16,
    pub updated_at_ns: U64,
}

#[near(serializers = [borsh])]
#[derive(Clone)]
struct StoredRendezvousRecord {
    version: u8,
    expires_at_ns: u64,
    ciphertext: Vec<u8>,
    storage_payer: AccountId,
}

#[near(serializers = [json])]
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct RegistryConfig {
    pub contract_version: String,
    pub protocol_version: u16,
    pub identity_curve: String,
}

#[near(serializers = [json])]
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct StorageBalance {
    pub available: U128,
}

#[near(serializers = [borsh])]
struct FreedomRegistryV2 {
    devices: LookupMap<String, StoredDeviceRecord>,
    rendezvous: LookupMap<String, StoredRendezvousRecord>,
    storage_balances: LookupMap<AccountId, u128>,
}

#[near(serializers = [borsh])]
#[derive(Clone)]
struct StoredContactRecord {
    device_id: String,
    rendezvous_capability: Vec<u8>,
    mailbox_public_key: Vec<u8>,
    updated_at_ns: u64,
}

#[near(serializers = [json])]
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct ContactRecord {
    pub freedom_number: String,
    pub device_id: String,
    pub identity_public_key: Base64VecU8,
    pub mailbox_public_key: Base64VecU8,
    pub key_epoch: U64,
    pub updated_at_ns: U64,
}

#[near(serializers = [borsh])]
#[derive(Clone)]
struct StoredMessageRecord {
    version: u8,
    sender_device_id: String,
    recipient_device_id: String,
    sent_at_ns: u64,
    expires_at_ns: u64,
    ephemeral_public_key: Vec<u8>,
    nonce: Vec<u8>,
    ciphertext: Vec<u8>,
    storage_payer: AccountId,
}

#[near(serializers = [json])]
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct MessageRecord {
    pub version: u8,
    pub message_id: String,
    pub sender_device_id: String,
    pub recipient_device_id: String,
    pub sent_at_ns: U64,
    pub expires_at_ns: U64,
    pub ephemeral_public_key: Base64VecU8,
    pub nonce: Base64VecU8,
    pub ciphertext: Base64VecU8,
}

#[near(contract_state)]
pub struct FreedomRegistry {
    devices: LookupMap<String, StoredDeviceRecord>,
    rendezvous: LookupMap<String, StoredRendezvousRecord>,
    storage_balances: LookupMap<AccountId, u128>,
    contacts: LookupMap<String, StoredContactRecord>,
    device_numbers: LookupMap<String, String>,
    messages: LookupMap<String, StoredMessageRecord>,
    mailboxes: LookupMap<String, Vec<String>>,
}

impl Default for FreedomRegistry {
    fn default() -> Self {
        Self {
            devices: LookupMap::new(b"d"),
            rendezvous: LookupMap::new(b"r"),
            storage_balances: LookupMap::new(b"s"),
            contacts: LookupMap::new(b"c"),
            device_numbers: LookupMap::new(b"n"),
            messages: LookupMap::new(b"m"),
            mailboxes: LookupMap::new(b"b"),
        }
    }
}

#[near]
impl FreedomRegistry {
    #[init(ignore_state)]
    pub fn migrate() -> Self {
        let old: FreedomRegistryV2 =
            env::state_read().unwrap_or_else(|| env::panic_str("Legacy state is unavailable"));
        Self {
            devices: old.devices,
            rendezvous: old.rendezvous,
            storage_balances: old.storage_balances,
            contacts: LookupMap::new(b"c"),
            device_numbers: LookupMap::new(b"n"),
            messages: LookupMap::new(b"m"),
            mailboxes: LookupMap::new(b"b"),
        }
    }

    pub fn get_config(&self) -> RegistryConfig {
        RegistryConfig {
            contract_version: CONTRACT_VERSION.to_string(),
            protocol_version: PROTOCOL_VERSION,
            identity_curve: "P-256".to_string(),
        }
    }

    pub fn get_device(&self, device_id: String) -> Option<DeviceRecord> {
        validate_hex_identifier(&device_id, DEVICE_ID_BYTES, "Invalid device ID");
        self.devices
            .get(&device_id)
            .map(|record| device_view(device_id, record))
    }

    pub fn storage_balance_of(&self, account_id: AccountId) -> StorageBalance {
        StorageBalance {
            available: U128(self.storage_balances.get(&account_id).copied().unwrap_or(0)),
        }
    }

    pub fn get_contact_by_number(&self, freedom_number: String) -> Option<ContactRecord> {
        validate_freedom_number(&freedom_number);
        let contact = self.contacts.get(&freedom_number)?;
        let device = self.devices.get(&contact.device_id)?;
        if device.status != DeviceStatus::Active {
            return None;
        }
        // Version 0.3 accepted any checksummed number. Hiding records that are not
        // derived from the identity prevents legacy first-claim impersonation.
        if freedom_number_from_public_key(&device.identity_public_key) != freedom_number {
            return None;
        }
        Some(ContactRecord {
            freedom_number,
            device_id: contact.device_id.clone(),
            identity_public_key: Base64VecU8(device.identity_public_key.clone()),
            mailbox_public_key: Base64VecU8(contact.mailbox_public_key.clone()),
            key_epoch: U64(device.key_epoch),
            updated_at_ns: U64(contact.updated_at_ns),
        })
    }

    pub fn get_messages(&self, device_id: String) -> Vec<MessageRecord> {
        validate_hex_identifier(&device_id, DEVICE_ID_BYTES, "Invalid device ID");
        let now = env::block_timestamp();
        self.mailboxes
            .get(&device_id)
            .cloned()
            .unwrap_or_default()
            .into_iter()
            .filter_map(|message_id| {
                let record = self.messages.get(&message_id)?;
                (record.expires_at_ns > now).then(|| message_view(message_id, record))
            })
            .collect()
    }

    #[payable]
    pub fn storage_deposit(&mut self) -> StorageBalance {
        let payer = env::predecessor_account_id();
        let deposit = env::attached_deposit().as_yoctonear();
        require!(deposit > 0, "Storage deposit must be positive");

        let storage_before = env::storage_usage();
        let previous = self.storage_balances.get(&payer).copied().unwrap_or(0);
        let provisional = previous
            .checked_add(deposit)
            .unwrap_or_else(|| env::panic_str("Storage balance overflow"));
        self.storage_balances.insert(payer.clone(), provisional);
        self.storage_balances.flush();

        let added_bytes = env::storage_usage().saturating_sub(storage_before) as u128;
        let entry_cost = env::storage_byte_cost()
            .saturating_mul(added_bytes)
            .as_yoctonear();
        require!(deposit > entry_cost, "Storage deposit is too small");
        let available = provisional.saturating_sub(entry_cost);
        self.storage_balances.insert(payer, available);
        self.storage_balances.flush();
        StorageBalance {
            available: U128(available),
        }
    }

    #[payable]
    pub fn storage_withdraw(&mut self, amount: Option<U128>) -> StorageBalance {
        assert_one_yocto();
        let payer = env::predecessor_account_id();
        let available = self.storage_balances.get(&payer).copied().unwrap_or(0);
        let requested = amount.map(|value| value.0).unwrap_or(available);
        require!(
            requested <= available,
            "Insufficient available storage balance"
        );

        let remaining = available - requested;
        self.storage_balances.insert(payer.clone(), remaining);
        self.storage_balances.flush();
        if requested > 0 {
            Promise::new(payer)
                .transfer(NearToken::from_yoctonear(requested))
                .detach();
        }
        StorageBalance {
            available: U128(remaining),
        }
    }

    #[payable]
    pub fn register_device(
        &mut self,
        device_id: String,
        identity_public_key: Base64VecU8,
        protocol_version: u16,
        signature: Base64VecU8,
    ) -> DeviceRecord {
        validate_hex_identifier(&device_id, DEVICE_ID_BYTES, "Invalid device ID");
        require!(
            self.devices.get(&device_id).is_none(),
            "Device ID is already registered"
        );
        require!(
            protocol_version == PROTOCOL_VERSION,
            "Unsupported protocol version"
        );

        let public_key = validate_public_key(identity_public_key.0);
        let signature = validate_signature(signature.0);
        let message = authorization_message(
            REGISTER_OPERATION,
            &device_id,
            0,
            1,
            protocol_version,
            &public_key,
        );
        verify_authorization(&public_key, &signature, &message);

        let storage_before = env::storage_usage();
        let record = StoredDeviceRecord {
            version: 1,
            identity_public_key: public_key,
            key_epoch: 1,
            auth_nonce: 0,
            status: DeviceStatus::Active,
            protocol_version,
            updated_at_ns: env::block_timestamp(),
        };
        self.devices.insert(device_id.clone(), record.clone());
        self.devices.flush();
        self.settle_storage(storage_before, env::predecessor_account_id());
        device_view(device_id, &record)
    }

    #[payable]
    pub fn rotate_device_key(
        &mut self,
        device_id: String,
        new_identity_public_key: Base64VecU8,
        new_key_epoch: U64,
        auth_nonce: U64,
        signature: Base64VecU8,
    ) -> DeviceRecord {
        validate_hex_identifier(&device_id, DEVICE_ID_BYTES, "Invalid device ID");
        let mut record = self
            .devices
            .get(&device_id)
            .cloned()
            .unwrap_or_else(|| env::panic_str("Device ID is not registered"));
        require!(record.status == DeviceStatus::Active, "Device is revoked");
        require!(
            new_key_epoch.0 == record.key_epoch.saturating_add(1),
            "Key epoch must increase by one"
        );
        require!(
            auth_nonce.0 == record.auth_nonce.saturating_add(1),
            "Invalid authorization nonce"
        );

        let new_public_key = validate_public_key(new_identity_public_key.0);
        let signature = validate_signature(signature.0);
        let message = authorization_message(
            ROTATE_OPERATION,
            &device_id,
            auth_nonce.0,
            new_key_epoch.0,
            record.protocol_version,
            &new_public_key,
        );
        verify_authorization(&record.identity_public_key, &signature, &message);

        let storage_before = env::storage_usage();
        record.identity_public_key = new_public_key;
        record.key_epoch = new_key_epoch.0;
        record.auth_nonce = auth_nonce.0;
        record.updated_at_ns = env::block_timestamp();
        self.devices.insert(device_id.clone(), record.clone());
        self.devices.flush();
        self.settle_storage(storage_before, env::predecessor_account_id());
        device_view(device_id, &record)
    }

    #[payable]
    pub fn revoke_device(
        &mut self,
        device_id: String,
        auth_nonce: U64,
        signature: Base64VecU8,
    ) -> DeviceRecord {
        validate_hex_identifier(&device_id, DEVICE_ID_BYTES, "Invalid device ID");
        let mut record = self
            .devices
            .get(&device_id)
            .cloned()
            .unwrap_or_else(|| env::panic_str("Device ID is not registered"));
        require!(
            record.status == DeviceStatus::Active,
            "Device is already revoked"
        );
        require!(
            auth_nonce.0 == record.auth_nonce.saturating_add(1),
            "Invalid authorization nonce"
        );

        let signature = validate_signature(signature.0);
        let message = authorization_message(
            REVOKE_OPERATION,
            &device_id,
            auth_nonce.0,
            record.key_epoch,
            record.protocol_version,
            &[],
        );
        verify_authorization(&record.identity_public_key, &signature, &message);

        let storage_before = env::storage_usage();
        record.auth_nonce = auth_nonce.0;
        record.status = DeviceStatus::Revoked;
        record.updated_at_ns = env::block_timestamp();
        self.devices.insert(device_id.clone(), record.clone());
        self.devices.flush();
        self.settle_storage(storage_before, env::predecessor_account_id());
        device_view(device_id, &record)
    }

    #[payable]
    pub fn publish_contact(
        &mut self,
        device_id: String,
        freedom_number: String,
        mailbox_public_key: Base64VecU8,
        auth_nonce: U64,
        signature: Base64VecU8,
    ) -> ContactRecord {
        validate_hex_identifier(&device_id, DEVICE_ID_BYTES, "Invalid device ID");
        validate_freedom_number(&freedom_number);
        let mailbox_public_key = validate_public_key(mailbox_public_key.0);
        let mut device = self
            .devices
            .get(&device_id)
            .cloned()
            .unwrap_or_else(|| env::panic_str("Device ID is not registered"));
        require!(device.status == DeviceStatus::Active, "Device is revoked");
        require!(
            freedom_number_from_public_key(&device.identity_public_key) == freedom_number,
            "Freedom number does not match the registered identity key"
        );
        require!(
            auth_nonce.0 == device.auth_nonce.saturating_add(1),
            "Invalid authorization nonce"
        );
        if let Some(existing) = self.contacts.get(&freedom_number) {
            require!(
                existing.device_id == device_id,
                "Freedom number is already registered"
            );
        }

        let mut key_material = freedom_number.as_bytes().to_vec();
        key_material.extend_from_slice(&mailbox_public_key);
        let message = authorization_message(
            PUBLISH_CONTACT_OPERATION,
            &device_id,
            auth_nonce.0,
            device.key_epoch,
            device.protocol_version,
            &key_material,
        );
        let signature = validate_signature(signature.0);
        verify_authorization(&device.identity_public_key, &signature, &message);

        let storage_before = env::storage_usage();
        if let Some(previous_number) = self.device_numbers.get(&device_id).cloned() {
            if previous_number != freedom_number {
                self.contacts.remove(&previous_number);
            }
        }
        let contact = StoredContactRecord {
            device_id: device_id.clone(),
            // Retained only for Borsh compatibility with the deployed v0.3 state.
            rendezvous_capability: Vec::new(),
            mailbox_public_key,
            updated_at_ns: env::block_timestamp(),
        };
        self.contacts
            .insert(freedom_number.clone(), contact.clone());
        self.device_numbers
            .insert(device_id.clone(), freedom_number.clone());
        device.auth_nonce = auth_nonce.0;
        device.updated_at_ns = env::block_timestamp();
        self.devices.insert(device_id.clone(), device.clone());
        self.contacts.flush();
        self.device_numbers.flush();
        self.devices.flush();
        self.settle_storage(storage_before, env::predecessor_account_id());

        ContactRecord {
            freedom_number,
            device_id,
            identity_public_key: Base64VecU8(device.identity_public_key),
            mailbox_public_key: Base64VecU8(contact.mailbox_public_key),
            key_epoch: U64(device.key_epoch),
            updated_at_ns: U64(contact.updated_at_ns),
        }
    }

    #[payable]
    pub fn send_message(
        &mut self,
        sender_device_id: String,
        recipient_device_id: String,
        message_id: String,
        expires_at_ns: U64,
        ephemeral_public_key: Base64VecU8,
        nonce: Base64VecU8,
        ciphertext: Base64VecU8,
        auth_nonce: U64,
        signature: Base64VecU8,
    ) -> MessageRecord {
        validate_hex_identifier(
            &sender_device_id,
            DEVICE_ID_BYTES,
            "Invalid sender device ID",
        );
        validate_hex_identifier(
            &recipient_device_id,
            DEVICE_ID_BYTES,
            "Invalid recipient device ID",
        );
        validate_hex_identifier(&message_id, MESSAGE_ID_BYTES, "Invalid message ID");
        let ephemeral_public_key = validate_public_key(ephemeral_public_key.0);
        require!(
            nonce.0.len() == MESSAGE_NONCE_BYTES,
            "Invalid message nonce"
        );
        require!(
            !ciphertext.0.is_empty() && ciphertext.0.len() <= MAX_MESSAGE_CIPHERTEXT_BYTES,
            "Invalid message ciphertext size"
        );
        require!(
            self.messages.get(&message_id).is_none(),
            "Message ID already exists"
        );

        let now = env::block_timestamp();
        let ttl = expires_at_ns
            .0
            .checked_sub(now)
            .unwrap_or_else(|| env::panic_str("Message expiry must be in the future"));
        require!(ttl >= MIN_MESSAGE_TTL_NS, "Message TTL is too short");
        require!(ttl <= MAX_MESSAGE_TTL_NS, "Message TTL is too long");

        let mut sender = self
            .devices
            .get(&sender_device_id)
            .cloned()
            .unwrap_or_else(|| env::panic_str("Sender device is not registered"));
        require!(
            sender.status == DeviceStatus::Active,
            "Sender device is revoked"
        );
        let recipient = self
            .devices
            .get(&recipient_device_id)
            .unwrap_or_else(|| env::panic_str("Recipient device is not registered"));
        require!(
            recipient.status == DeviceStatus::Active,
            "Recipient device is revoked"
        );
        require!(
            auth_nonce.0 == sender.auth_nonce.saturating_add(1),
            "Invalid authorization nonce"
        );

        let mut key_material = hex::decode(&message_id)
            .unwrap_or_else(|_| env::panic_str("Invalid message ID encoding"));
        key_material.extend_from_slice(
            &hex::decode(&recipient_device_id)
                .unwrap_or_else(|_| env::panic_str("Invalid recipient device ID encoding")),
        );
        key_material.extend_from_slice(&expires_at_ns.0.to_be_bytes());
        key_material.extend_from_slice(&ephemeral_public_key);
        key_material.extend_from_slice(&nonce.0);
        key_material.extend_from_slice(&env::sha256_array(&ciphertext.0));
        let message = authorization_message(
            SEND_MESSAGE_OPERATION,
            &sender_device_id,
            auth_nonce.0,
            sender.key_epoch,
            sender.protocol_version,
            &key_material,
        );
        let signature = validate_signature(signature.0);
        verify_authorization(&sender.identity_public_key, &signature, &message);

        let mut mailbox = self
            .mailboxes
            .get(&recipient_device_id)
            .cloned()
            .unwrap_or_default();
        self.cleanup_expired_mailbox(&recipient_device_id, &mut mailbox, now);
        require!(
            mailbox.len() < MAX_MAILBOX_MESSAGES,
            "Recipient mailbox is full"
        );

        let storage_before = env::storage_usage();
        let record = StoredMessageRecord {
            version: 1,
            sender_device_id: sender_device_id.clone(),
            recipient_device_id: recipient_device_id.clone(),
            sent_at_ns: now,
            expires_at_ns: expires_at_ns.0,
            ephemeral_public_key,
            nonce: nonce.0,
            ciphertext: ciphertext.0,
            storage_payer: env::predecessor_account_id(),
        };
        mailbox.push(message_id.clone());
        self.messages.insert(message_id.clone(), record.clone());
        self.mailboxes.insert(recipient_device_id, mailbox);
        sender.auth_nonce = auth_nonce.0;
        sender.updated_at_ns = now;
        self.devices.insert(sender_device_id, sender);
        self.messages.flush();
        self.mailboxes.flush();
        self.devices.flush();
        self.settle_storage(storage_before, env::predecessor_account_id());
        message_view(message_id, &record)
    }

    pub fn remove_expired_message(&mut self, message_id: String) -> bool {
        validate_hex_identifier(&message_id, MESSAGE_ID_BYTES, "Invalid message ID");
        let Some(record) = self.messages.get(&message_id).cloned() else {
            return false;
        };
        require!(
            record.expires_at_ns <= env::block_timestamp(),
            "Message is still active"
        );

        let storage_before = env::storage_usage();
        self.messages.remove(&message_id);
        if let Some(mut mailbox) = self.mailboxes.get(&record.recipient_device_id).cloned() {
            mailbox.retain(|id| id != &message_id);
            self.mailboxes
                .insert(record.recipient_device_id.clone(), mailbox);
        }
        self.messages.flush();
        self.mailboxes.flush();
        let released_bytes = storage_before.saturating_sub(env::storage_usage()) as u128;
        let refund = env::storage_byte_cost().saturating_mul(released_bytes);
        if !refund.is_zero() {
            self.credit_storage(record.storage_payer, refund.as_yoctonear());
        }
        true
    }

    fn settle_storage(&mut self, storage_before: u64, payer: AccountId) {
        let attached = env::attached_deposit().as_yoctonear();
        if attached > 0 {
            self.credit_storage(payer.clone(), attached);
        }

        let storage_after = env::storage_usage();
        let added_bytes = storage_after.saturating_sub(storage_before) as u128;
        let required = env::storage_byte_cost()
            .saturating_mul(added_bytes)
            .as_yoctonear();
        let available = self.storage_balances.get(&payer).copied().unwrap_or(0);
        require!(
            available >= required,
            "Insufficient prepaid storage balance"
        );
        self.storage_balances.insert(payer, available - required);
        self.storage_balances.flush();
    }

    fn credit_storage(&mut self, payer: AccountId, amount: u128) {
        if amount == 0 {
            return;
        }
        let available = self.storage_balances.get(&payer).copied().unwrap_or(0);
        let updated = available
            .checked_add(amount)
            .unwrap_or_else(|| env::panic_str("Storage balance overflow"));
        self.storage_balances.insert(payer, updated);
        self.storage_balances.flush();
    }

    fn cleanup_expired_mailbox(
        &mut self,
        recipient_device_id: &str,
        mailbox: &mut Vec<String>,
        now: u64,
    ) {
        let mut expired = Vec::new();
        let mut payer_counts: BTreeMap<AccountId, u128> = BTreeMap::new();
        mailbox.retain(|message_id| {
            let Some(record) = self.messages.get(message_id) else {
                return false;
            };
            if record.expires_at_ns > now {
                return true;
            }
            expired.push(message_id.clone());
            *payer_counts
                .entry(record.storage_payer.clone())
                .or_default() += 1;
            false
        });
        if expired.is_empty() {
            return;
        }

        let storage_before = env::storage_usage();
        for message_id in expired {
            self.messages.remove(&message_id);
        }
        if mailbox.is_empty() {
            self.mailboxes.remove(recipient_device_id);
        } else {
            self.mailboxes
                .insert(recipient_device_id.to_string(), mailbox.clone());
        }
        self.messages.flush();
        self.mailboxes.flush();

        let released_bytes = storage_before.saturating_sub(env::storage_usage()) as u128;
        let total_refund = env::storage_byte_cost()
            .saturating_mul(released_bytes)
            .as_yoctonear();
        let total_messages: u128 = payer_counts.values().sum();
        let payer_count = payer_counts.len();
        let mut distributed = 0_u128;
        for (index, (payer, count)) in payer_counts.into_iter().enumerate() {
            let refund = if index + 1 == payer_count {
                total_refund.saturating_sub(distributed)
            } else {
                total_refund.saturating_mul(count) / total_messages
            };
            distributed = distributed.saturating_add(refund);
            self.credit_storage(payer, refund);
        }
    }
}

fn validate_hex_identifier(value: &str, byte_length: usize, message: &str) {
    require!(value.len() == byte_length * 2, message);
    require!(
        value
            .as_bytes()
            .iter()
            .all(|byte| matches!(byte, b'0'..=b'9' | b'a'..=b'f')),
        message
    );
}

fn validate_freedom_number(value: &str) {
    require!(
        value.len() == FREEDOM_NUMBER_DIGITS
            && value.as_bytes().iter().all(u8::is_ascii_digit)
            && value.as_bytes().iter().any(|digit| *digit != b'0'),
        "Invalid Freedom number"
    );
    let mut sum = 0_u32;
    for (index, byte) in value.as_bytes().iter().rev().enumerate() {
        let mut digit = (byte - b'0') as u32;
        if index % 2 == 1 {
            digit *= 2;
            if digit > 9 {
                digit -= 9;
            }
        }
        sum += digit;
    }
    require!(sum % 10 == 0, "Invalid Freedom number checksum");
}

fn freedom_number_from_public_key(public_key: &[u8]) -> String {
    require!(
        public_key.len() == P256_PUBLIC_KEY_BYTES && matches!(public_key.first(), Some(2 | 3)),
        "Invalid stored identity public key"
    );
    let modulus = 10_u128.pow(FREEDOM_NUMBER_PAYLOAD_DIGITS as u32);
    let value = env::sha256(public_key)
        .into_iter()
        .fold(0_u128, |accumulator, byte| {
            (accumulator * 256 + byte as u128) % modulus
        });
    let payload = format!("{:019}", value);
    let mut sum = 0_u32;
    for (index, byte) in payload.as_bytes().iter().rev().enumerate() {
        let mut digit = (byte - b'0') as u32;
        if index % 2 == 0 {
            digit *= 2;
            if digit > 9 {
                digit -= 9;
            }
        }
        sum += digit;
    }
    let check_digit = (10 - (sum % 10)) % 10;
    format!("{payload}{check_digit}")
}

fn validate_public_key(value: Vec<u8>) -> Vec<u8> {
    require!(
        value.len() == P256_PUBLIC_KEY_BYTES && matches!(value.first(), Some(2 | 3)),
        "Identity public key must be a compressed P-256 key"
    );
    value
}

fn validate_signature(value: Vec<u8>) -> [u8; P256_SIGNATURE_BYTES] {
    let signature: [u8; P256_SIGNATURE_BYTES] = value
        .try_into()
        .unwrap_or_else(|_| env::panic_str("Signature must be raw P-256 r||s"));
    require!(
        env::p256_signature_is_low_s(&signature),
        "P-256 signature must use canonical low-S form"
    );
    signature
}

fn verify_authorization(public_key: &[u8], signature: &[u8; P256_SIGNATURE_BYTES], message: &[u8]) {
    let public_key: [u8; P256_PUBLIC_KEY_BYTES] = public_key
        .try_into()
        .unwrap_or_else(|_| env::panic_str("Invalid stored identity public key"));
    let digest = env::sha256_array(message);
    require!(
        env::p256_verify(signature, &digest, &public_key),
        "Invalid device authorization signature"
    );
}

fn authorization_message(
    operation: u8,
    device_id: &str,
    auth_nonce: u64,
    key_epoch: u64,
    protocol_version: u16,
    key_material: &[u8],
) -> Vec<u8> {
    let contract_id = env::current_account_id();
    let contract_bytes = contract_id.as_bytes();
    let device_bytes =
        hex::decode(device_id).unwrap_or_else(|_| env::panic_str("Invalid device ID encoding"));
    require!(
        contract_bytes.len() <= u16::MAX as usize,
        "Contract ID is too long"
    );
    require!(
        key_material.len() <= u16::MAX as usize,
        "Key material is too long"
    );

    let mut message = Vec::with_capacity(
        AUTH_DOMAIN.len() + contract_bytes.len() + device_bytes.len() + key_material.len() + 32,
    );
    message.extend_from_slice(AUTH_DOMAIN);
    message.extend_from_slice(&(contract_bytes.len() as u16).to_be_bytes());
    message.extend_from_slice(contract_bytes);
    message.push(operation);
    message.extend_from_slice(&device_bytes);
    message.extend_from_slice(&auth_nonce.to_be_bytes());
    message.extend_from_slice(&key_epoch.to_be_bytes());
    message.extend_from_slice(&protocol_version.to_be_bytes());
    message.extend_from_slice(&(key_material.len() as u16).to_be_bytes());
    message.extend_from_slice(key_material);
    message
}

fn device_view(device_id: String, record: &StoredDeviceRecord) -> DeviceRecord {
    DeviceRecord {
        version: record.version,
        device_id,
        identity_public_key: Base64VecU8(record.identity_public_key.clone()),
        key_epoch: U64(record.key_epoch),
        auth_nonce: U64(record.auth_nonce),
        status: record.status.clone(),
        protocol_version: record.protocol_version,
        updated_at_ns: U64(record.updated_at_ns),
    }
}

fn message_view(message_id: String, record: &StoredMessageRecord) -> MessageRecord {
    MessageRecord {
        version: record.version,
        message_id,
        sender_device_id: record.sender_device_id.clone(),
        recipient_device_id: record.recipient_device_id.clone(),
        sent_at_ns: U64(record.sent_at_ns),
        expires_at_ns: U64(record.expires_at_ns),
        ephemeral_public_key: Base64VecU8(record.ephemeral_public_key.clone()),
        nonce: Base64VecU8(record.nonce.clone()),
        ciphertext: Base64VecU8(record.ciphertext.clone()),
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use near_sdk::test_utils::VMContextBuilder;
    use near_sdk::{NearToken, testing_env};
    use p256::ecdsa::signature::hazmat::PrehashSigner;
    use p256::ecdsa::{Signature, SigningKey};
    use sha2::{Digest, Sha256};

    const CONTRACT: &str = "freedom-registry.testnet";
    const RELAYER: &str = "relayer.testnet";

    fn context_with_deposit(timestamp_ns: u64, deposit: NearToken) {
        let mut builder = VMContextBuilder::new();
        builder
            .current_account_id(CONTRACT.parse().unwrap())
            .signer_account_id(RELAYER.parse().unwrap())
            .predecessor_account_id(RELAYER.parse().unwrap())
            .block_timestamp(timestamp_ns)
            .attached_deposit(deposit);
        testing_env!(builder.build());
    }

    fn context(timestamp_ns: u64) {
        context_with_deposit(timestamp_ns, NearToken::from_near(2));
    }

    fn key(seed: u8) -> SigningKey {
        SigningKey::from_bytes((&[seed; 32]).into()).unwrap()
    }

    fn public_key(key: &SigningKey) -> Vec<u8> {
        key.verifying_key()
            .to_encoded_point(true)
            .as_bytes()
            .to_vec()
    }

    #[test]
    fn freedom_number_matches_android_vector() {
        let compressed_generator =
            hex::decode("036b17d1f2e12c4247f8bce6e563a440f277037d812deb33a0f4a13945d898c296")
                .unwrap();
        assert_eq!(
            freedom_number_from_public_key(&compressed_generator),
            "71110821717511868363"
        );
    }

    fn sign(key: &SigningKey, message: &[u8]) -> Base64VecU8 {
        let digest = Sha256::digest(message);
        let signature: Signature = key.sign_prehash(&digest).unwrap();
        let signature = signature.normalize_s().unwrap_or(signature);
        Base64VecU8(signature.to_bytes().to_vec())
    }

    fn register(contract: &mut FreedomRegistry, key: &SigningKey, device_id: &str) {
        let public_key = public_key(key);
        let message = authorization_message(
            REGISTER_OPERATION,
            device_id,
            0,
            1,
            PROTOCOL_VERSION,
            &public_key,
        );
        contract.register_device(
            device_id.to_string(),
            Base64VecU8(public_key),
            PROTOCOL_VERSION,
            sign(key, &message),
        );
    }

    #[test]
    fn registers_rotates_and_revokes_with_device_proofs() {
        context(1_000_000_000);
        let mut contract = FreedomRegistry::default();
        let first = key(7);
        let second = key(9);
        let device_id = "11".repeat(DEVICE_ID_BYTES);
        register(&mut contract, &first, &device_id);

        let registered = contract.get_device(device_id.clone()).unwrap();
        assert_eq!(registered.key_epoch.0, 1);
        assert_eq!(registered.auth_nonce.0, 0);
        assert_eq!(registered.status, DeviceStatus::Active);

        let second_public_key = public_key(&second);
        let rotate_message = authorization_message(
            ROTATE_OPERATION,
            &device_id,
            1,
            2,
            PROTOCOL_VERSION,
            &second_public_key,
        );
        let rotated = contract.rotate_device_key(
            device_id.clone(),
            Base64VecU8(second_public_key),
            U64(2),
            U64(1),
            sign(&first, &rotate_message),
        );
        assert_eq!(rotated.key_epoch.0, 2);
        assert_eq!(rotated.auth_nonce.0, 1);

        let revoke_message =
            authorization_message(REVOKE_OPERATION, &device_id, 2, 2, PROTOCOL_VERSION, &[]);
        let revoked = contract.revoke_device(device_id, U64(2), sign(&second, &revoke_message));
        assert_eq!(revoked.status, DeviceStatus::Revoked);
    }

    #[test]
    #[should_panic(expected = "Invalid device authorization signature")]
    fn rejects_registration_signed_by_another_key() {
        context(1_000_000_000);
        let mut contract = FreedomRegistry::default();
        let identity = key(7);
        let attacker = key(8);
        let device_id = "22".repeat(DEVICE_ID_BYTES);
        let identity_public_key = public_key(&identity);
        let message = authorization_message(
            REGISTER_OPERATION,
            &device_id,
            0,
            1,
            PROTOCOL_VERSION,
            &identity_public_key,
        );
        contract.register_device(
            device_id,
            Base64VecU8(identity_public_key),
            PROTOCOL_VERSION,
            sign(&attacker, &message),
        );
    }

    #[test]
    fn prepaid_storage_allows_zero_deposit_function_calls() {
        let now = 2_000_000_000;
        context_with_deposit(now, NearToken::from_near(1));
        let mut contract = FreedomRegistry::default();
        let funded = contract.storage_deposit().available.0;
        assert!(funded > 0);

        context_with_deposit(now, NearToken::from_yoctonear(0));
        let identity = key(12);
        let device_id = "44".repeat(DEVICE_ID_BYTES);
        register(&mut contract, &identity, &device_id);
        let after_registration = contract
            .storage_balance_of(RELAYER.parse().unwrap())
            .available
            .0;
        assert!(after_registration < funded);
    }

    #[test]
    fn publishes_and_resolves_contact_by_number() {
        let now = 2_500_000_000;
        context_with_deposit(now, NearToken::from_near(1));
        let mut contract = FreedomRegistry::default();
        contract.storage_deposit();

        context_with_deposit(now, NearToken::from_yoctonear(0));
        let identity = key(15);
        let device_id = "66".repeat(DEVICE_ID_BYTES);
        register(&mut contract, &identity, &device_id);
        let freedom_number = freedom_number_from_public_key(&public_key(&identity));
        let mailbox_public_key = public_key(&key(16));
        let mut key_material = freedom_number.as_bytes().to_vec();
        key_material.extend_from_slice(&mailbox_public_key);
        let message = authorization_message(
            PUBLISH_CONTACT_OPERATION,
            &device_id,
            1,
            1,
            PROTOCOL_VERSION,
            &key_material,
        );
        let published = contract.publish_contact(
            device_id.clone(),
            freedom_number.clone(),
            Base64VecU8(mailbox_public_key.clone()),
            U64(1),
            sign(&identity, &message),
        );
        assert_eq!(published.device_id, device_id);
        assert_eq!(published.mailbox_public_key.0, mailbox_public_key);
        assert_eq!(
            contract
                .get_contact_by_number(freedom_number)
                .unwrap()
                .identity_public_key
                .0,
            public_key(&identity)
        );
    }

    #[test]
    #[should_panic(expected = "Freedom number does not match the registered identity key")]
    fn rejects_a_valid_number_not_derived_from_the_identity() {
        let now = 2_600_000_000;
        context_with_deposit(now, NearToken::from_near(1));
        let mut contract = FreedomRegistry::default();
        contract.storage_deposit();

        context_with_deposit(now, NearToken::from_yoctonear(0));
        let identity = key(17);
        let other_identity = key(18);
        let device_id = "67".repeat(DEVICE_ID_BYTES);
        register(&mut contract, &identity, &device_id);
        let freedom_number = freedom_number_from_public_key(&public_key(&other_identity));
        let mailbox_public_key = public_key(&key(19));
        let mut key_material = freedom_number.as_bytes().to_vec();
        key_material.extend_from_slice(&mailbox_public_key);
        let authorization = authorization_message(
            PUBLISH_CONTACT_OPERATION,
            &device_id,
            1,
            1,
            PROTOCOL_VERSION,
            &key_material,
        );
        contract.publish_contact(
            device_id,
            freedom_number,
            Base64VecU8(mailbox_public_key),
            U64(1),
            sign(&identity, &authorization),
        );
    }

    #[test]
    fn sends_reads_and_cleans_up_encrypted_self_test_message() {
        let now = 2_700_000_000;
        context_with_deposit(now, NearToken::from_near(1));
        let mut contract = FreedomRegistry::default();
        contract.storage_deposit();

        context_with_deposit(now, NearToken::from_yoctonear(0));
        let identity = key(18);
        let device_id = "88".repeat(DEVICE_ID_BYTES);
        register(&mut contract, &identity, &device_id);
        let message_id = "99".repeat(MESSAGE_ID_BYTES);
        let expiry = now + MIN_MESSAGE_TTL_NS;
        let ephemeral_public_key = public_key(&key(19));
        let nonce = vec![7; MESSAGE_NONCE_BYTES];
        let ciphertext = vec![8; 64];
        let mut key_material = hex::decode(&message_id).unwrap();
        key_material.extend_from_slice(&hex::decode(&device_id).unwrap());
        key_material.extend_from_slice(&expiry.to_be_bytes());
        key_material.extend_from_slice(&ephemeral_public_key);
        key_material.extend_from_slice(&nonce);
        key_material.extend_from_slice(&env::sha256_array(&ciphertext));
        let authorization = authorization_message(
            SEND_MESSAGE_OPERATION,
            &device_id,
            1,
            1,
            PROTOCOL_VERSION,
            &key_material,
        );

        let sent = contract.send_message(
            device_id.clone(),
            device_id.clone(),
            message_id.clone(),
            U64(expiry),
            Base64VecU8(ephemeral_public_key),
            Base64VecU8(nonce),
            Base64VecU8(ciphertext.clone()),
            U64(1),
            sign(&identity, &authorization),
        );
        assert_eq!(sent.ciphertext.0, ciphertext);
        assert_eq!(contract.get_messages(device_id.clone()).len(), 1);

        context_with_deposit(expiry, NearToken::from_yoctonear(0));
        assert!(contract.get_messages(device_id.clone()).is_empty());

        let second_message_id = "aa".repeat(MESSAGE_ID_BYTES);
        let second_expiry = expiry + MIN_MESSAGE_TTL_NS;
        let second_ephemeral_public_key = public_key(&key(20));
        let second_nonce = vec![9; MESSAGE_NONCE_BYTES];
        let second_ciphertext = vec![10; 64];
        let mut second_key_material = hex::decode(&second_message_id).unwrap();
        second_key_material.extend_from_slice(&hex::decode(&device_id).unwrap());
        second_key_material.extend_from_slice(&second_expiry.to_be_bytes());
        second_key_material.extend_from_slice(&second_ephemeral_public_key);
        second_key_material.extend_from_slice(&second_nonce);
        second_key_material.extend_from_slice(&env::sha256_array(&second_ciphertext));
        let second_authorization = authorization_message(
            SEND_MESSAGE_OPERATION,
            &device_id,
            2,
            1,
            PROTOCOL_VERSION,
            &second_key_material,
        );
        contract.send_message(
            device_id.clone(),
            device_id.clone(),
            second_message_id,
            U64(second_expiry),
            Base64VecU8(second_ephemeral_public_key),
            Base64VecU8(second_nonce),
            Base64VecU8(second_ciphertext),
            U64(2),
            sign(&identity, &second_authorization),
        );
        assert!(contract.messages.get(&message_id).is_none());
        assert!(!contract.remove_expired_message(message_id));
    }

    #[test]
    fn migrates_v2_state_without_changing_registry_prefixes() {
        context(3_000_000_000);
        let old = FreedomRegistryV2 {
            devices: LookupMap::new(b"d"),
            rendezvous: LookupMap::new(b"r"),
            storage_balances: LookupMap::new(b"s"),
        };
        env::state_write(&old);

        let migrated = FreedomRegistry::migrate();
        assert_eq!(
            migrated
                .storage_balance_of(RELAYER.parse().unwrap())
                .available
                .0,
            0
        );
        assert!(
            migrated
                .get_messages("77".repeat(DEVICE_ID_BYTES))
                .is_empty()
        );
    }
}
