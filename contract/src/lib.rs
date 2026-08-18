use near_sdk::json_types::{Base64VecU8, U128, U64};
use near_sdk::store::LookupMap;
use near_sdk::{assert_one_yocto, env, near, require, AccountId, NearToken, Promise};

const CONTRACT_VERSION: &str = "0.2.0";
const PROTOCOL_VERSION: u16 = 1;
const DEVICE_ID_BYTES: usize = 32;
const P256_PUBLIC_KEY_BYTES: usize = 33;
const P256_SIGNATURE_BYTES: usize = 64;
const SLOT_BYTES: usize = 32;
const MAX_RENDEZVOUS_BYTES: usize = 2_048;
const MIN_RENDEZVOUS_TTL_NS: u64 = 30 * 1_000_000_000;
const MAX_RENDEZVOUS_TTL_NS: u64 = 10 * 60 * 1_000_000_000;
const AUTH_DOMAIN: &[u8] = b"FREEDOM_REGISTRY_V1\0";
const REGISTER_OPERATION: u8 = 1;
const ROTATE_OPERATION: u8 = 2;
const REVOKE_OPERATION: u8 = 3;

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
pub struct RendezvousRecord {
    pub version: u8,
    pub expires_at_ns: U64,
    pub ciphertext: Base64VecU8,
}

#[near(serializers = [json])]
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct RegistryConfig {
    pub contract_version: String,
    pub protocol_version: u16,
    pub identity_curve: String,
    pub max_rendezvous_bytes: u32,
    pub min_rendezvous_ttl_seconds: u32,
    pub max_rendezvous_ttl_seconds: u32,
}

#[near(serializers = [json])]
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct StorageBalance {
    pub available: U128,
}

#[near(serializers = [borsh])]
struct FreedomRegistryV1 {
    devices: LookupMap<String, StoredDeviceRecord>,
    rendezvous: LookupMap<String, StoredRendezvousRecord>,
}

#[near(contract_state)]
pub struct FreedomRegistry {
    devices: LookupMap<String, StoredDeviceRecord>,
    rendezvous: LookupMap<String, StoredRendezvousRecord>,
    storage_balances: LookupMap<AccountId, u128>,
}

impl Default for FreedomRegistry {
    fn default() -> Self {
        Self {
            devices: LookupMap::new(b"d"),
            rendezvous: LookupMap::new(b"r"),
            storage_balances: LookupMap::new(b"s"),
        }
    }
}

#[near]
impl FreedomRegistry {
    #[init(ignore_state)]
    pub fn migrate() -> Self {
        let old: FreedomRegistryV1 =
            env::state_read().unwrap_or_else(|| env::panic_str("Legacy state is unavailable"));
        Self {
            devices: old.devices,
            rendezvous: old.rendezvous,
            storage_balances: LookupMap::new(b"s"),
        }
    }

    pub fn get_config(&self) -> RegistryConfig {
        RegistryConfig {
            contract_version: CONTRACT_VERSION.to_string(),
            protocol_version: PROTOCOL_VERSION,
            identity_curve: "P-256".to_string(),
            max_rendezvous_bytes: MAX_RENDEZVOUS_BYTES as u32,
            min_rendezvous_ttl_seconds: (MIN_RENDEZVOUS_TTL_NS / 1_000_000_000) as u32,
            max_rendezvous_ttl_seconds: (MAX_RENDEZVOUS_TTL_NS / 1_000_000_000) as u32,
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
        require!(requested <= available, "Insufficient available storage balance");

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
        require!(record.status == DeviceStatus::Active, "Device is already revoked");
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

    pub fn get_rendezvous(&self, slot: String) -> Option<RendezvousRecord> {
        validate_hex_identifier(&slot, SLOT_BYTES, "Invalid rendezvous slot");
        self.rendezvous.get(&slot).and_then(|record| {
            if record.expires_at_ns <= env::block_timestamp() {
                None
            } else {
                Some(rendezvous_view(record))
            }
        })
    }

    #[payable]
    pub fn put_rendezvous(
        &mut self,
        slot: String,
        expires_at_ns: U64,
        ciphertext: Base64VecU8,
    ) -> RendezvousRecord {
        validate_hex_identifier(&slot, SLOT_BYTES, "Invalid rendezvous slot");
        require!(
            !ciphertext.0.is_empty() && ciphertext.0.len() <= MAX_RENDEZVOUS_BYTES,
            "Invalid rendezvous ciphertext size"
        );
        let now = env::block_timestamp();
        let ttl = expires_at_ns
            .0
            .checked_sub(now)
            .unwrap_or_else(|| env::panic_str("Rendezvous expiry must be in the future"));
        require!(ttl >= MIN_RENDEZVOUS_TTL_NS, "Rendezvous TTL is too short");
        require!(ttl <= MAX_RENDEZVOUS_TTL_NS, "Rendezvous TTL is too long");
        require!(
            self.rendezvous.get(&slot).is_none(),
            "Rendezvous slot is already occupied; clean it after expiry or rotate the slot"
        );

        let storage_before = env::storage_usage();
        let record = StoredRendezvousRecord {
            version: 1,
            expires_at_ns: expires_at_ns.0,
            ciphertext: ciphertext.0,
            storage_payer: env::predecessor_account_id(),
        };
        self.rendezvous.insert(slot, record.clone());
        self.rendezvous.flush();
        self.settle_storage(storage_before, env::predecessor_account_id());
        rendezvous_view(&record)
    }

    pub fn remove_expired_rendezvous(&mut self, slot: String) -> bool {
        validate_hex_identifier(&slot, SLOT_BYTES, "Invalid rendezvous slot");
        let Some(record) = self.rendezvous.get(&slot).cloned() else {
            return false;
        };
        require!(
            record.expires_at_ns <= env::block_timestamp(),
            "Rendezvous is still active"
        );

        let storage_before = env::storage_usage();
        self.rendezvous.remove(&slot);
        self.rendezvous.flush();
        let storage_after = env::storage_usage();
        let released_bytes = storage_before.saturating_sub(storage_after) as u128;
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
        require!(available >= required, "Insufficient prepaid storage balance");
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

fn verify_authorization(
    public_key: &[u8],
    signature: &[u8; P256_SIGNATURE_BYTES],
    message: &[u8],
) {
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
    let device_bytes = hex::decode(device_id)
        .unwrap_or_else(|_| env::panic_str("Invalid device ID encoding"));
    require!(contract_bytes.len() <= u16::MAX as usize, "Contract ID is too long");
    require!(key_material.len() <= u16::MAX as usize, "Key material is too long");

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

fn rendezvous_view(record: &StoredRendezvousRecord) -> RendezvousRecord {
    RendezvousRecord {
        version: record.version,
        expires_at_ns: U64(record.expires_at_ns),
        ciphertext: Base64VecU8(record.ciphertext.clone()),
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use near_sdk::test_utils::VMContextBuilder;
    use near_sdk::{testing_env, NearToken};
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

        let revoke_message = authorization_message(
            REVOKE_OPERATION,
            &device_id,
            2,
            2,
            PROTOCOL_VERSION,
            &[],
        );
        let revoked = contract.revoke_device(
            device_id,
            U64(2),
            sign(&second, &revoke_message),
        );
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
    fn rendezvous_is_bounded_hidden_after_expiry_and_removable() {
        let now = 1_000_000_000;
        context(now);
        let mut contract = FreedomRegistry::default();
        let slot = "33".repeat(SLOT_BYTES);
        let expiry = now + MIN_RENDEZVOUS_TTL_NS;

        contract.put_rendezvous(
            slot.clone(),
            U64(expiry),
            Base64VecU8(vec![5; 128]),
        );
        assert_eq!(
            contract.get_rendezvous(slot.clone()).unwrap().ciphertext.0,
            vec![5; 128]
        );

        context(expiry);
        assert!(contract.get_rendezvous(slot.clone()).is_none());
        assert!(contract.remove_expired_rendezvous(slot.clone()));
        assert!(!contract.remove_expired_rendezvous(slot));
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

        let slot = "55".repeat(SLOT_BYTES);
        contract.put_rendezvous(
            slot.clone(),
            U64(now + MIN_RENDEZVOUS_TTL_NS),
            Base64VecU8(vec![8; 64]),
        );
        assert!(contract.get_rendezvous(slot).is_some());
        let after_rendezvous = contract
            .storage_balance_of(RELAYER.parse().unwrap())
            .available
            .0;
        assert!(after_rendezvous < after_registration);
    }

    #[test]
    fn migrates_v1_state_without_changing_registry_prefixes() {
        context(3_000_000_000);
        let old = FreedomRegistryV1 {
            devices: LookupMap::new(b"d"),
            rendezvous: LookupMap::new(b"r"),
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
    }
}
