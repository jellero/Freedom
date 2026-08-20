use anyhow::{Context, Result, bail};
use freedom_near_proof_verifier::{
    NearLightClientVerifier, NearNetworkAnchor, VerificationError, light_client_block_hash,
    light_client_block_lite,
};
use near_jsonrpc_client::{JsonRpcClient, methods};
use near_jsonrpc_primitives::types::query::QueryResponseKind;
use near_primitives::hash::CryptoHash;
use near_primitives::types::validator_stake::ValidatorStake;
use near_primitives::types::{BlockId, BlockReference, StoreKey, TransactionOrReceiptId};
use near_primitives::views::{LightClientBlockView, QueryRequest};
use near_workspaces::{Contract, Worker, network::Sandbox};
use std::collections::HashMap;
use std::sync::Arc;

const BOOTSTRAP_STEP: u64 = 50;
const ADVANCE_STEP: u64 = 20;

async fn next_light_client_block(
    rpc: &JsonRpcClient,
    last_block_hash: CryptoHash,
) -> Result<Option<LightClientBlockView>> {
    rpc.call(methods::next_light_client_block::RpcLightClientNextBlockRequest {
        last_block_hash,
    })
    .await
    .context("next_light_client_block RPC")
}

async fn trusted_bootstrap_anchor(
    worker: &Worker<Sandbox>,
    rpc: &JsonRpcClient,
) -> Result<NearNetworkAnchor> {
    // The local Sandbox process is the trusted out-of-band bootstrap source in this deterministic
    // gate. Start from a recent observed head and walk incrementally so every last_block_hash
    // remains available to the node's light-client endpoint. Production must instead deserialize
    // this equivalent material from Freedom's independently authenticated NetworkAnchor package.
    let seed_block = worker
        .view_block()
        .await
        .context("read current sandbox bootstrap head")?;
    let mut trusted_hash = CryptoHash(seed_block.hash().0);
    let mut producer_sets: HashMap<CryptoHash, Vec<ValidatorStake>> = HashMap::new();

    // Each LightClientBlockView always carries the ordered producer set for its next epoch. Once
    // the walk crosses an epoch boundary, the set learned as "next" on the previous epoch becomes
    // the exact current set for the new anchor; the new block simultaneously supplies its next set.
    for attempt in 0..48 {
        eprintln!("L4 bootstrap: incremental step {attempt}");
        worker
            .fast_forward(BOOTSTRAP_STEP)
            .await
            .context("fast-forward sandbox during trusted bootstrap")?;

        let Some(block) = next_light_client_block(rpc, trusted_hash).await? else {
            continue;
        };
        trusted_hash = light_client_block_hash(&block);

        if let Some(next_bps) = &block.next_bps {
            producer_sets.insert(
                block.inner_lite.next_epoch_id,
                next_bps.iter().cloned().map(Into::into).collect(),
            );
        }

        let Some(current_block_producers) = producer_sets.get(&block.inner_lite.epoch_id) else {
            continue;
        };
        let Some(next_block_producers) = producer_sets.get(&block.inner_lite.next_epoch_id) else {
            continue;
        };

        eprintln!(
            "L4 bootstrap: anchor acquired at height {} with current+next validator sets",
            block.inner_lite.height
        );
        return Ok(NearNetworkAnchor {
            network_id: "freedom-near-sandbox".to_string(),
            chain_id: "sandbox".to_string(),
            trusted_head: light_client_block_lite(&block),
            current_block_producers: current_block_producers.clone(),
            next_block_producers: next_block_producers.clone(),
        });
    }

    bail!("sandbox did not produce enough recent light-client blocks to bootstrap both validator sets")
}

async fn next_acceptable_block(
    worker: &Worker<Sandbox>,
    rpc: &JsonRpcClient,
    anchor: &NearNetworkAnchor,
) -> Result<LightClientBlockView> {
    for attempt in 0..12 {
        eprintln!("L4 verify: candidate step {attempt}");
        worker
            .fast_forward(ADVANCE_STEP)
            .await
            .context("fast-forward sandbox for verifiable light-client block")?;
        if let Some(block) = next_light_client_block(rpc, anchor.trusted_hash()).await? {
            let mut probe = NearLightClientVerifier::new(anchor.clone());
            if probe.verify_and_advance(&block).is_ok() {
                eprintln!("L4 verify: candidate accepted at height {}", block.inner_lite.height);
                return Ok(block);
            }
        }
    }
    bail!("sandbox did not produce a light-client block acceptable from the anchor")
}

async fn advance_after(
    worker: &Worker<Sandbox>,
    rpc: &JsonRpcClient,
    verifier: &mut NearLightClientVerifier,
) -> Result<()> {
    for attempt in 0..12 {
        eprintln!("L4 advance: step {attempt}");
        worker
            .fast_forward(ADVANCE_STEP)
            .await
            .context("fast-forward sandbox after control-plane mutation")?;
        let head_hash = verifier.head().block_hash;
        if let Some(block) = next_light_client_block(rpc, head_hash).await? {
            match verifier.verify_and_advance(&block) {
                Ok(_) => return Ok(()),
                Err(VerificationError::MissingEpochProducers) => continue,
                Err(error) => return Err(error.into()),
            }
        }
    }
    bail!("sandbox did not advance the verified light-client head")
}

async fn deploy_control_plane(worker: &Worker<Sandbox>) -> Result<Contract> {
    eprintln!("L4 contract: compile");
    let wasm = near_workspaces::compile_project("../control-plane-contract")
        .await
        .context("compile control-plane contract for proof gate")?;
    eprintln!("L4 contract: deploy");
    let contract = worker
        .dev_deploy(&wasm)
        .await
        .context("deploy control-plane contract in sandbox")?;
    contract
        .call("new")
        .transact()
        .await
        .context("submit control-plane init transaction")?
        .into_result()
        .context("execute control-plane init transaction")?;
    Ok(contract)
}

#[tokio::test]
async fn malicious_rpc_objects_cannot_be_promoted_to_verified_state() -> Result<()> {
    eprintln!("L4 sandbox: start");
    let worker = near_workspaces::sandbox().await.context("start NEAR Sandbox")?;
    let rpc = JsonRpcClient::connect(worker.rpc_addr());

    // The trusted-bootstrap phase ends here. Every object obtained through `rpc` below this point
    // is attacker-controlled input and becomes trusted only if NearLightClientVerifier accepts it.
    let anchor = trusted_bootstrap_anchor(&worker, &rpc).await?;
    let candidate = next_acceptable_block(&worker, &rpc, &anchor).await?;

    // A modified RPC object cannot move the trusted head. Modifying the signed height changes the
    // approval message/hash and therefore fails cryptographic verification.
    let mut tampered_candidate = candidate.clone();
    tampered_candidate.inner_lite.height += 1;
    let mut attacked = NearLightClientVerifier::new(anchor.clone());
    assert!(attacked.verify_and_advance(&tampered_candidate).is_err());
    assert_eq!(attacked.head().block_hash, anchor.trusted_hash());

    let mut verifier = NearLightClientVerifier::new(anchor);
    let verified = verifier.verify_and_advance(&candidate)?;
    assert_eq!(verified.block_hash, light_client_block_hash(&candidate));

    let contract = deploy_control_plane(&worker).await?;

    // Execute a real state transition, then advance the independently verified head so the
    // execution and resulting state are in its ancestry.
    eprintln!("L4 mutation: submit");
    let mutation = contract
        .call("apply_mutation")
        .args_json(serde_json::json!({"write_version": 7u64, "force_fail": false}))
        .transact()
        .await
        .context("submit control-plane mutation")?;
    mutation
        .clone()
        .into_result()
        .context("execute control-plane mutation")?;
    let tx_hash = CryptoHash(mutation.outcome().transaction_hash.0);

    advance_after(&worker, &rpc, &mut verifier).await?;
    advance_after(&worker, &rpc, &mut verifier).await?;

    // The transaction hash is not success. The execution outcome is accepted only if its outcome
    // root and containing block both prove into the current independently verified head.
    eprintln!("L4 execution: request light-client proof");
    let execution_proof = rpc
        .call(methods::light_client_proof::RpcLightClientExecutionProofRequest {
            id: TransactionOrReceiptId::Transaction {
                transaction_hash: tx_hash,
                sender_id: contract.id().clone(),
            },
            light_client_head: verifier.head().block_hash,
        })
        .await
        .context("light_client_proof RPC for mutation transaction")?;
    verifier.verify_execution_proof(&execution_proof)?;

    let mut tampered_execution: methods::light_client_proof::RpcLightClientExecutionProofResponse =
        serde_json::from_value(serde_json::to_value(&execution_proof)?)?;
    tampered_execution.outcome_proof.block_hash = CryptoHash::hash_bytes(b"malicious-rpc-block");
    assert!(verifier.verify_execution_proof(&tampered_execution).is_err());

    // Query exactly the state block/root authenticated by the verified light-client head. The RPC
    // supplies bytes and trie nodes, but neither is trusted until the local trie-proof verifier
    // authenticates the ContractData path against prev_state_root.
    let head = verifier.head();
    eprintln!("L4 state: request inclusion proof at {}", head.state_block_hash);
    let state_response = rpc
        .call(methods::query::RpcQueryRequest {
            block_reference: BlockReference::BlockId(BlockId::Hash(head.state_block_hash)),
            request: QueryRequest::ViewState {
                account_id: contract.id().clone(),
                prefix: StoreKey::from(b"STATE".to_vec()),
                after_key: None,
                limit: None,
                include_proof: true,
            },
        })
        .await
        .context("view_state inclusion proof RPC")?;
    assert_eq!(state_response.block_hash, head.state_block_hash);
    let state = match state_response.kind {
        QueryResponseKind::ViewState(state) => state,
        other => bail!("unexpected state response: {other:?}"),
    };
    let item = state
        .values
        .iter()
        .find(|item| item.key.as_slice() == b"STATE")
        .context("contract STATE key missing")?;

    verifier.verify_contract_state_value(
        contract.id(),
        item.key.as_slice(),
        Some(item.value.as_slice()),
        &state.proof,
    )?;
    assert!(item.value.len() >= 16, "unexpected contract state encoding");
    let committed_version = u64::from_le_bytes(item.value[8..16].try_into()?);
    assert_eq!(committed_version, 7);

    // A malicious provider cannot swap the state bytes while reusing the proof.
    let mut false_value = item.value.to_vec();
    false_value[8] ^= 0x01;
    assert_eq!(
        verifier.verify_contract_state_value(
            contract.id(),
            item.key.as_slice(),
            Some(&false_value),
            &state.proof,
        ),
        Err(VerificationError::StateValueMismatch)
    );

    // Nor can it mutate one authenticated trie node.
    let mut bad_proof = state.proof.clone();
    let first = bad_proof.first_mut().context("state proof unexpectedly empty")?;
    let mut bytes = first.to_vec();
    bytes[0] ^= 0x01;
    *first = Arc::from(bytes);
    assert!(
        verifier
            .verify_contract_state_value(
                contract.id(),
                item.key.as_slice(),
                Some(item.value.as_slice()),
                &bad_proof,
            )
            .is_err()
    );

    // Exact non-inclusion is also locally verifiable; an RPC cannot invent an absent key as long
    // as the proof is checked against the authenticated root.
    eprintln!("L4 state: request non-inclusion proof");
    let absent_response = rpc
        .call(methods::query::RpcQueryRequest {
            block_reference: BlockReference::BlockId(BlockId::Hash(head.state_block_hash)),
            request: QueryRequest::ViewState {
                account_id: contract.id().clone(),
                prefix: StoreKey::from(b"FREEDOM_ABSENT_KEY".to_vec()),
                after_key: None,
                limit: None,
                include_proof: true,
            },
        })
        .await
        .context("view_state non-inclusion proof RPC")?;
    let absent = match absent_response.kind {
        QueryResponseKind::ViewState(state) => state,
        other => bail!("unexpected absent-state response: {other:?}"),
    };
    assert!(absent.values.is_empty());
    verifier.verify_contract_state_value(
        contract.id(),
        b"FREEDOM_ABSENT_KEY",
        None,
        &absent.proof,
    )?;

    Ok(())
}
