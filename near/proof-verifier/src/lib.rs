use borsh::BorshDeserialize;
use near_jsonrpc_client::methods::light_client_proof::RpcLightClientExecutionProofResponse;
use near_primitives::block_header::{
    Approval, ApprovalInner, compute_bp_hash_from_validator_stakes,
};
use near_primitives::hash::CryptoHash;
use near_primitives::merkle::{
    combine_hash, compute_root_from_path_and_item, merklize, verify_hash, verify_path,
};
use near_primitives::state::ValueRef;
use near_primitives::trie_key::TrieKey;
use near_primitives::types::AccountId;
use near_primitives::types::validator_stake::ValidatorStake;
use near_primitives::views::{BlockView, LightClientBlockLiteView, LightClientBlockView};
use std::collections::HashMap;
use std::io::Read;
use std::sync::Arc;
use thiserror::Error;

/// Release/bootstrap-pinned trust material for the NEAR verifier profile.
///
/// The anchor is deliberately constructed outside RPC transport. Production code must obtain
/// this material from Freedom's independently authenticated bootstrap/update path, never by
/// asking the same untrusted RPC that will later be verified.
#[derive(Clone, Debug)]
pub struct NearNetworkAnchor {
    pub network_id: String,
    pub chain_id: String,
    pub trusted_head: LightClientBlockLiteView,
    pub current_block_producers: Vec<ValidatorStake>,
    pub next_block_producers: Vec<ValidatorStake>,
}

impl NearNetworkAnchor {
    pub fn trusted_hash(&self) -> CryptoHash {
        self.trusted_head.hash()
    }
}

#[derive(Clone, Debug, PartialEq, Eq)]
pub struct VerifiedNearHead {
    pub height: u64,
    pub block_hash: CryptoHash,
    /// Aggregate previous-state root authenticated by this head. In the pre-Spice NEAR profile
    /// this is the Merkle root over the ordered `chunk.prev_state_root` values of this block, not
    /// a contract trie root by itself.
    pub state_block_hash: CryptoHash,
    pub state_root: CryptoHash,
    pub timestamp_nanosec: u64,
}

#[derive(Debug, Error, PartialEq, Eq)]
pub enum VerificationError {
    #[error("checkpoint height did not advance")]
    HeightNotMonotonic,
    #[error("candidate belongs to an epoch not authorized by the trusted head")]
    EpochNotAdjacent,
    #[error("missing block producers for candidate epoch")]
    MissingEpochProducers,
    #[error("approval vector does not match block-producer set")]
    ApprovalSetLengthMismatch,
    #[error("approval signature is invalid")]
    InvalidApprovalSignature,
    #[error("approved stake does not exceed two thirds")]
    InsufficientApprovedStake,
    #[error("next block producers are required at an epoch transition")]
    MissingNextBlockProducers,
    #[error("next block-producer commitment does not match the light-client header")]
    NextBlockProducersHashMismatch,
    #[error("execution outcome proof does not reconstruct the block outcome root")]
    OutcomeRootProofInvalid,
    #[error("execution proof block header does not hash to the outcome block")]
    OutcomeBlockHashMismatch,
    #[error("execution proof block is not in the verified head block-merkle tree")]
    BlockMerkleProofInvalid,
    #[error("state block does not match the independently verified light-client head")]
    StateBlockHashMismatch,
    #[error("verified-head block contains no shard state roots")]
    StateShardRootsEmpty,
    #[error("ordered shard state roots do not reconstruct the light-client aggregate state root")]
    StateShardRootSetMismatch,
    #[error("requested shard index is outside the authenticated shard-root set")]
    StateShardIndexOutOfBounds,
    #[error("state proof is missing an authenticated trie node")]
    StateProofMissingNode,
    #[error("state proof contains a malformed trie node")]
    StateProofMalformedNode,
    #[error("state proof path is malformed")]
    StateProofMalformedPath,
    #[error("state proof value differs from the expected value")]
    StateValueMismatch,
    #[error("state proof proves inclusion when non-inclusion was expected")]
    StateUnexpectedlyPresent,
    #[error("state proof proves non-inclusion when inclusion was expected")]
    StateUnexpectedlyAbsent,
}

/// Stateful NEP-25 verifier. RPC responses are inputs, never trust roots.
///
/// This implementation follows nearcore's external-light-client validation algorithm: candidate
/// light-client blocks are accepted only after epoch continuity, validator signatures, >2/3 stake
/// and next-validator-set commitment checks. The highest verified head is then the only root used
/// for execution/state proof verification.
pub struct NearLightClientVerifier {
    network_id: String,
    chain_id: String,
    head: LightClientBlockLiteView,
    block_producers: HashMap<CryptoHash, Vec<ValidatorStake>>,
}

impl NearLightClientVerifier {
    pub fn new(anchor: NearNetworkAnchor) -> Self {
        let mut block_producers = HashMap::new();
        if !anchor.current_block_producers.is_empty() {
            block_producers.insert(
                anchor.trusted_head.inner_lite.epoch_id,
                anchor.current_block_producers,
            );
        }
        if !anchor.next_block_producers.is_empty() {
            block_producers.insert(
                anchor.trusted_head.inner_lite.next_epoch_id,
                anchor.next_block_producers,
            );
        }
        Self {
            network_id: anchor.network_id,
            chain_id: anchor.chain_id,
            head: anchor.trusted_head,
            block_producers,
        }
    }

    pub fn network_id(&self) -> &str {
        &self.network_id
    }

    pub fn chain_id(&self) -> &str {
        &self.chain_id
    }

    pub fn head(&self) -> VerifiedNearHead {
        VerifiedNearHead {
            height: self.head.inner_lite.height,
            block_hash: self.head.hash(),
            state_block_hash: self.head.prev_block_hash,
            state_root: self.head.inner_lite.prev_state_root,
            timestamp_nanosec: self.head.inner_lite.timestamp_nanosec,
        }
    }

    /// Verify and atomically advance the trusted light-client head.
    ///
    /// On any error the previous head and validator map remain unchanged.
    pub fn verify_and_advance(
        &mut self,
        candidate: &LightClientBlockView,
    ) -> Result<VerifiedNearHead, VerificationError> {
        if candidate.inner_lite.height <= self.head.inner_lite.height {
            return Err(VerificationError::HeightNotMonotonic);
        }
        if candidate.inner_lite.epoch_id != self.head.inner_lite.epoch_id
            && candidate.inner_lite.epoch_id != self.head.inner_lite.next_epoch_id
        {
            return Err(VerificationError::EpochNotAdjacent);
        }

        let producers = self
            .block_producers
            .get(&candidate.inner_lite.epoch_id)
            .ok_or(VerificationError::MissingEpochProducers)?;
        if candidate.approvals_after_next.len() != producers.len() {
            return Err(VerificationError::ApprovalSetLengthMismatch);
        }

        let candidate_hash = light_client_block_hash(candidate);
        let next_block_hash = combine_hash(&candidate.next_block_inner_hash, &candidate_hash);
        let approval_message = Approval::get_data_for_sig(
            &ApprovalInner::Endorsement(next_block_hash),
            candidate.inner_lite.height + 2,
        );

        let mut total_stake = 0u128;
        let mut approved_stake = 0u128;
        for (approval, producer) in candidate.approvals_after_next.iter().zip(producers) {
            let stake = producer.stake().as_yoctonear();
            total_stake += stake;
            let Some(signature) = approval else {
                continue;
            };
            if !signature.verify(&approval_message, producer.public_key()) {
                return Err(VerificationError::InvalidApprovalSignature);
            }
            approved_stake += stake;
        }
        if approved_stake * 3 <= total_stake * 2 {
            return Err(VerificationError::InsufficientApprovedStake);
        }

        // Compute next-epoch changes before mutating trusted state. A rejected proof cannot alter
        // the validator map used for later verification.
        let next_epoch_update = if candidate.inner_lite.epoch_id == self.head.inner_lite.next_epoch_id
        {
            let next_bps = candidate
                .next_bps
                .as_ref()
                .ok_or(VerificationError::MissingNextBlockProducers)?;
            let next_stakes: Vec<ValidatorStake> =
                next_bps.iter().cloned().map(Into::into).collect();
            if compute_bp_hash_from_validator_stakes(&next_stakes, true)
                != candidate.inner_lite.next_bp_hash
            {
                return Err(VerificationError::NextBlockProducersHashMismatch);
            }
            Some((candidate.inner_lite.next_epoch_id, next_stakes))
        } else {
            None
        };

        if let Some((epoch_id, next_stakes)) = next_epoch_update {
            self.block_producers.insert(epoch_id, next_stakes);
        }
        self.head = light_client_block_lite(candidate);
        Ok(self.head())
    }

    /// Verify a transaction/receipt execution proof against the independently verified head.
    pub fn verify_execution_proof(
        &self,
        proof: &RpcLightClientExecutionProofResponse,
    ) -> Result<(), VerificationError> {
        let chunk_outcome_root = compute_root_from_path_and_item(
            &proof.outcome_proof.proof,
            &proof.outcome_proof.to_hashes(),
        );
        if !verify_path(
            proof.block_header_lite.inner_lite.outcome_root,
            &proof.outcome_root_proof,
            &chunk_outcome_root,
        ) {
            return Err(VerificationError::OutcomeRootProofInvalid);
        }
        if proof.block_header_lite.hash() != proof.outcome_proof.block_hash {
            return Err(VerificationError::OutcomeBlockHashMismatch);
        }
        if !verify_hash(
            self.head.inner_lite.block_merkle_root,
            &proof.block_proof,
            proof.outcome_proof.block_hash,
        ) {
            return Err(VerificationError::BlockMerkleProofInvalid);
        }
        Ok(())
    }

    /// Verify one NEAR contract-storage key through the pre-Spice shard-state commitment of the
    /// independently verified light-client head.
    ///
    /// `head_block` is an untrusted full block response for the exact verified head. Its hash must
    /// match the light-client head, and the ordered `chunk.prev_state_root` values must reconstruct
    /// the aggregate `prev_state_root` committed by that head. Only then is `shard_index` used to
    /// choose the trie root for `proof_nodes`.
    ///
    /// `shard_index` MUST come from an independently authenticated shard-layout/routing rule. This
    /// matters especially for non-inclusion: a proof from the wrong shard could honestly prove that
    /// a key is absent there. The Sandbox L4 gate is explicitly one-shard, where index 0 is unique.
    ///
    /// This profile intentionally fails closed if NEAR changes the state-root commitment model
    /// (including Spice deployments whose header commitment no longer follows this aggregate rule).
    pub fn verify_contract_state_value(
        &self,
        head_block: &BlockView,
        shard_index: usize,
        account_id: &AccountId,
        storage_key: &[u8],
        expected_value: Option<&[u8]>,
        proof_nodes: &[Arc<[u8]>],
    ) -> Result<(), VerificationError> {
        if head_block.header.hash != self.head.hash() {
            return Err(VerificationError::StateBlockHashMismatch);
        }

        let shard_state_roots: Vec<CryptoHash> =
            head_block.chunks.iter().map(|chunk| chunk.prev_state_root).collect();
        if shard_state_roots.is_empty() {
            return Err(VerificationError::StateShardRootsEmpty);
        }
        if merklize(&shard_state_roots).0 != self.head.inner_lite.prev_state_root {
            return Err(VerificationError::StateShardRootSetMismatch);
        }
        let shard_root = *shard_state_roots
            .get(shard_index)
            .ok_or(VerificationError::StateShardIndexOutOfBounds)?;

        verify_contract_state_value_at_root(
            shard_root,
            account_id,
            storage_key,
            expected_value,
            proof_nodes,
        )
    }
}

pub fn light_client_block_lite(block: &LightClientBlockView) -> LightClientBlockLiteView {
    LightClientBlockLiteView {
        prev_block_hash: block.prev_block_hash,
        inner_rest_hash: block.inner_rest_hash,
        inner_lite: block.inner_lite.clone(),
    }
}

pub fn light_client_block_hash(block: &LightClientBlockView) -> CryptoHash {
    light_client_block_lite(block).hash()
}

/// Low-level trie verifier for an already authenticated shard state root.
///
/// Callers handling RPC responses should normally use `NearLightClientVerifier::verify_contract_state_value`
/// so the shard root itself is first bound to the independently verified light-client head.
pub fn verify_contract_state_value_at_root(
    state_root: CryptoHash,
    account_id: &AccountId,
    storage_key: &[u8],
    expected_value: Option<&[u8]>,
    proof_nodes: &[Arc<[u8]>],
) -> Result<(), VerificationError> {
    let raw_key = TrieKey::ContractData {
        account_id: account_id.clone(),
        key: storage_key.to_vec(),
    }
    .to_vec();
    let value_ref = lookup_proof_value_ref(state_root, &raw_key, proof_nodes)?;

    match (value_ref, expected_value) {
        (None, None) => Ok(()),
        (None, Some(_)) => Err(VerificationError::StateUnexpectedlyAbsent),
        (Some(_), None) => Err(VerificationError::StateUnexpectedlyPresent),
        (Some(reference), Some(value)) => {
            if reference.length as usize != value.len()
                || reference.hash != CryptoHash::hash_bytes(value)
            {
                return Err(VerificationError::StateValueMismatch);
            }
            Ok(())
        }
    }
}

fn lookup_proof_value_ref(
    state_root: CryptoHash,
    raw_key: &[u8],
    proof_nodes: &[Arc<[u8]>],
) -> Result<Option<ValueRef>, VerificationError> {
    let mut authenticated = HashMap::<CryptoHash, &[u8]>::new();
    for blob in proof_nodes {
        authenticated.insert(CryptoHash::hash_bytes(blob), blob.as_ref());
    }

    let key = bytes_to_nibbles(raw_key);
    let mut key_offset = 0usize;
    let mut current = state_root;

    loop {
        let encoded_node = authenticated
            .get(&current)
            .ok_or(VerificationError::StateProofMissingNode)?;
        let parsed = RawTrieNodeWithSizeCompat::try_from_slice(encoded_node)
            .map_err(|_| VerificationError::StateProofMalformedNode)?;
        match parsed.node {
            RawTrieNodeCompat::Leaf(encoded_path, value) => {
                let (path, is_leaf) = decode_hpe_path(&encoded_path)?;
                if !is_leaf {
                    return Err(VerificationError::StateProofMalformedPath);
                }
                return if key[key_offset..] == path {
                    Ok(Some(value))
                } else {
                    Ok(None)
                };
            }
            RawTrieNodeCompat::BranchNoValue(children) => {
                if key_offset == key.len() {
                    return Ok(None);
                }
                let nibble = key[key_offset] as usize;
                key_offset += 1;
                let Some(child) = children.0[nibble] else {
                    return Ok(None);
                };
                current = child;
            }
            RawTrieNodeCompat::BranchWithValue(value, children) => {
                if key_offset == key.len() {
                    return Ok(Some(value));
                }
                let nibble = key[key_offset] as usize;
                key_offset += 1;
                let Some(child) = children.0[nibble] else {
                    return Ok(None);
                };
                current = child;
            }
            RawTrieNodeCompat::Extension(encoded_path, child) => {
                let (path, is_leaf) = decode_hpe_path(&encoded_path)?;
                if is_leaf {
                    return Err(VerificationError::StateProofMalformedPath);
                }
                if key.len() - key_offset < path.len()
                    || key[key_offset..key_offset + path.len()] != path
                {
                    return Ok(None);
                }
                key_offset += path.len();
                current = child;
            }
        }
    }
}

fn bytes_to_nibbles(bytes: &[u8]) -> Vec<u8> {
    let mut out = Vec::with_capacity(bytes.len() * 2);
    for byte in bytes {
        out.push(byte >> 4);
        out.push(byte & 0x0f);
    }
    out
}

fn decode_hpe_path(encoded: &[u8]) -> Result<(Vec<u8>, bool), VerificationError> {
    if encoded.is_empty() {
        return Err(VerificationError::StateProofMalformedPath);
    }
    let first = encoded[0];
    if first & 0xc0 != 0 {
        return Err(VerificationError::StateProofMalformedPath);
    }
    let odd = first & 0x10 != 0;
    if !odd && first & 0x0f != 0 {
        return Err(VerificationError::StateProofMalformedPath);
    }
    let is_leaf = first & 0x20 != 0;
    let offset = if odd { 1usize } else { 2usize };
    let total = encoded.len() * 2;
    if offset > total {
        return Err(VerificationError::StateProofMalformedPath);
    }
    let mut path = Vec::with_capacity(total - offset);
    for pos in offset..total {
        let byte = encoded[pos / 2];
        path.push(if pos % 2 == 0 { byte >> 4 } else { byte & 0x0f });
    }
    Ok((path, is_leaf))
}

#[derive(BorshDeserialize)]
struct RawTrieNodeWithSizeCompat {
    node: RawTrieNodeCompat,
    #[allow(dead_code)]
    memory_usage: u64,
}

#[derive(BorshDeserialize)]
#[borsh(use_discriminant = true)]
#[repr(u8)]
enum RawTrieNodeCompat {
    Leaf(Vec<u8>, ValueRef) = 0,
    BranchNoValue(ChildrenCompat) = 1,
    BranchWithValue(ValueRef, ChildrenCompat) = 2,
    Extension(Vec<u8>, CryptoHash) = 3,
}

struct ChildrenCompat([Option<CryptoHash>; 16]);

impl BorshDeserialize for ChildrenCompat {
    fn deserialize_reader<R: Read>(reader: &mut R) -> std::io::Result<Self> {
        let mut bitmap = u16::deserialize_reader(reader)?;
        let mut children: [Option<CryptoHash>; 16] = Default::default();
        while bitmap != 0 {
            let index = bitmap.trailing_zeros() as usize;
            bitmap &= bitmap - 1;
            children[index] = Some(CryptoHash::deserialize_reader(reader)?);
        }
        Ok(Self(children))
    }
}
