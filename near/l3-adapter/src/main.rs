use anyhow::{bail, Context, Result};
use near_workspaces::{network::Sandbox, Contract, Worker};
use serde_json::{json, Value};
use std::io::{self, BufRead, Write};

const PREFIX: &str = "FREEDOM_L3\t";

struct NearChainAdapter {
    worker: Worker<Sandbox>,
    contract: Contract,
    bootstrap_floor: u64,
    verified_height: Option<u64>,
    /// Last mutation version accepted by the Freedom client after all verification.
    /// This is deliberately distinct from state merely observed on NEAR.
    client_committed_version: u64,
}

impl NearChainAdapter {
    async fn start() -> Result<Self> {
        eprintln!("Freedom L3: compiling NEAR control-plane contract...");
        let wasm = near_workspaces::compile_project("./near/control-plane-contract")
            .await
            .context("compile NEAR control-plane contract")?;

        eprintln!("Freedom L3: starting NEAR Sandbox...");
        let worker = near_workspaces::sandbox().await.context("start NEAR Sandbox")?;
        let contract = worker.dev_deploy(&wasm).await.context("deploy control-plane contract")?;
        contract
            .call("new")
            .transact()
            .await
            .context("initialize control-plane contract")?
            .into_result()
            .map_err(|failure| anyhow::anyhow!("contract init failed: {failure:?}"))?;

        Ok(Self {
            worker,
            contract,
            bootstrap_floor: 0,
            verified_height: None,
            client_committed_version: 0,
        })
    }

    async fn handle(&mut self, request: &Value) -> Result<Value> {
        let op = request
            .get("op")
            .and_then(Value::as_str)
            .context("request.op must be a string")?;

        match op {
            "set_bootstrap_floor" => self.set_bootstrap_floor(request).await,
            "verify_checkpoint" => self.verify_checkpoint(request).await,
            "verify_mutation" => self.verify_mutation(request).await,
            other => bail!("unsupported L3 operation: {other}"),
        }
    }

    async fn set_bootstrap_floor(&mut self, request: &Value) -> Result<Value> {
        let minimum_height = u64_field(request, "minimum_height")?;
        let outcome = self
            .contract
            .call("set_bootstrap_floor")
            .args_json(json!({"minimum_height": minimum_height}))
            .transact()
            .await
            .context("submit set_bootstrap_floor")?;

        if outcome.is_failure() {
            return Ok(json!({
                "accepted": false,
                "failure": "CONTROL_PLANE_ROLLBACK"
            }));
        }

        let chain_floor: u64 = self
            .contract
            .view("get_bootstrap_floor")
            .await
            .context("read bootstrap floor")?
            .json()
            .context("decode bootstrap floor")?;
        if chain_floor != minimum_height {
            bail!("resulting bootstrap floor mismatch: requested={minimum_height} chain={chain_floor}");
        }
        self.bootstrap_floor = chain_floor;

        Ok(json!({
            "accepted": true,
            "minimum_height": chain_floor
        }))
    }

    async fn verify_checkpoint(&mut self, request: &Value) -> Result<Value> {
        let logical_height = u64_field(request, "height")?;
        let proof_valid = bool_field(request, "proof_valid")?;

        if !proof_valid {
            return Ok(json!({
                "accepted": false,
                "failure": "CONTROL_PLANE_PROOF_INVALID",
                "verified_height": self.verified_height
            }));
        }

        // Real node/RPC integration gate: every proof-valid path is backed by a
        // successful read from the running NEAR Sandbox. The vector's logical height
        // remains the canonical checkpoint height under test; sandbox block data is
        // evidence only and is NOT treated as an independently verified light-client proof.
        let block = self.worker.view_block().await.context("read latest sandbox block")?;
        let near_block_height = block.height();

        let chain_floor: u64 = self
            .contract
            .view("get_bootstrap_floor")
            .await
            .context("read bootstrap floor during checkpoint verification")?
            .json()
            .context("decode bootstrap floor during checkpoint verification")?;
        if chain_floor != self.bootstrap_floor {
            bail!("adapter/contract bootstrap floor drift: local={} chain={chain_floor}", self.bootstrap_floor);
        }

        if logical_height < self.bootstrap_floor {
            return Ok(json!({
                "accepted": false,
                "failure": "BOOTSTRAP_STATE_TOO_OLD",
                "verified_height": self.verified_height,
                "near_block_height": near_block_height
            }));
        }
        if self.verified_height.is_some_and(|seen| logical_height < seen) {
            return Ok(json!({
                "accepted": false,
                "failure": "CONTROL_PLANE_ROLLBACK",
                "verified_height": self.verified_height,
                "near_block_height": near_block_height
            }));
        }

        self.verified_height = Some(logical_height);
        Ok(json!({
            "accepted": true,
            "failure": null,
            "verified_height": logical_height,
            "near_block_height": near_block_height,
            "near_block_hash": block.hash().to_string()
        }))
    }

    async fn verify_mutation(&mut self, request: &Value) -> Result<Value> {
        let finality_proof_valid = bool_field(request, "finality_proof_valid")?;
        let execution_succeeded = bool_field(request, "execution_succeeded")?;
        let resulting_state_proof_valid = bool_field(request, "resulting_state_proof_valid")?;
        let exact_transition_matched = bool_field(request, "exact_transition_matched")?;
        let resulting_version = u64_field(request, "resulting_version")?;

        if !finality_proof_valid {
            return Ok(json!({
                "accepted": false,
                "failure": "CONTROL_PLANE_PROOF_INVALID",
                "committed_version": self.client_committed_version
            }));
        }

        let chain_before = self.read_chain_version().await?;

        // Submit a lower version to the real contract so rollback rejection is tested
        // by NEAR execution, not only by adapter-side logic.
        if resulting_version < chain_before {
            let outcome = self
                .call_mutation(resulting_version, false)
                .await
                .context("submit rollback mutation")?;
            if !outcome.is_failure() {
                bail!("contract accepted rollback mutation {resulting_version} < {chain_before}");
            }
            let chain_after = self.read_chain_version().await?;
            if chain_after != chain_before {
                bail!("failed rollback transaction changed state: before={chain_before} after={chain_after}");
            }
            return Ok(json!({
                "accepted": false,
                "failure": "CONTROL_PLANE_ROLLBACK",
                "committed_version": self.client_committed_version,
                "near_observed_version": chain_after
            }));
        }

        if !execution_succeeded {
            let outcome = self
                .call_mutation(resulting_version, true)
                .await
                .context("submit forced-failure mutation")?;
            if !outcome.is_failure() {
                bail!("forced-failure mutation unexpectedly succeeded");
            }
            let chain_after = self.read_chain_version().await?;
            if chain_after != chain_before {
                bail!("failed transaction changed state: before={chain_before} after={chain_after}");
            }
            return Ok(json!({
                "accepted": false,
                "failure": "CONTROL_PLANE_EXECUTION_FAILED",
                "committed_version": self.client_committed_version,
                "near_observed_version": chain_after
            }));
        }

        // When the vector asks for an exact-state mismatch, deliberately write a
        // different monotonic value. The real post-transaction view must detect it.
        let write_version = if exact_transition_matched {
            resulting_version
        } else {
            resulting_version.checked_add(1).context("resulting version overflow")?
        };
        let outcome = self
            .call_mutation(write_version, false)
            .await
            .context("submit mutation")?;
        if outcome.is_failure() {
            return Ok(json!({
                "accepted": false,
                "failure": "CONTROL_PLANE_EXECUTION_FAILED",
                "committed_version": self.client_committed_version,
                "near_observed_version": self.read_chain_version().await?
            }));
        }

        let block = self.worker.view_block().await.context("read sandbox block after mutation")?;
        let chain_after = self.read_chain_version().await?;

        // A chain state may advance even when the client cannot verify it. Do not
        // silently promote observed state to trusted local committed state.
        if !resulting_state_proof_valid {
            return Ok(json!({
                "accepted": false,
                "failure": "CONTROL_PLANE_PROOF_INVALID",
                "committed_version": self.client_committed_version,
                "near_observed_version": chain_after,
                "near_block_height": block.height()
            }));
        }

        if chain_after != resulting_version {
            return Ok(json!({
                "accepted": false,
                "failure": "CONTROL_PLANE_STATE_MISMATCH",
                "committed_version": self.client_committed_version,
                "near_observed_version": chain_after,
                "near_block_height": block.height()
            }));
        }

        self.client_committed_version = resulting_version;
        Ok(json!({
            "accepted": true,
            "failure": null,
            "committed_version": self.client_committed_version,
            "near_observed_version": chain_after,
            "near_block_height": block.height(),
            "near_block_hash": block.hash().to_string()
        }))
    }

    async fn call_mutation(
        &self,
        write_version: u64,
        force_fail: bool,
    ) -> Result<near_workspaces::result::ExecutionFinalResult> {
        self.contract
            .call("apply_mutation")
            .args_json(json!({
                "write_version": write_version,
                "force_fail": force_fail
            }))
            .transact()
            .await
            .context("contract apply_mutation")
    }

    async fn read_chain_version(&self) -> Result<u64> {
        self.contract
            .view("get_committed_version")
            .await
            .context("read committed version from NEAR")?
            .json()
            .context("decode committed version from NEAR")
    }
}

fn u64_field(request: &Value, name: &str) -> Result<u64> {
    request
        .get(name)
        .and_then(Value::as_u64)
        .with_context(|| format!("request.{name} must be an unsigned integer"))
}

fn bool_field(request: &Value, name: &str) -> Result<bool> {
    request
        .get(name)
        .and_then(Value::as_bool)
        .with_context(|| format!("request.{name} must be a boolean"))
}

#[tokio::main]
async fn main() -> Result<()> {
    let mut adapter = NearChainAdapter::start().await?;
    eprintln!("Freedom L3: NEAR Sandbox adapter ready.");

    let stdin = io::stdin();
    let mut stdout = io::stdout().lock();
    for line in stdin.lock().lines() {
        let line = line.context("read adapter request")?;
        if line.trim().is_empty() {
            continue;
        }
        let request: Value = serde_json::from_str(&line).context("decode adapter request JSON")?;
        let response = match adapter.handle(&request).await {
            Ok(value) => value,
            Err(error) => json!({
                "accepted": false,
                "failure": "L3_ADAPTER_ERROR",
                "detail": format!("{error:#}")
            }),
        };
        writeln!(stdout, "{PREFIX}{}", serde_json::to_string(&response)?)?;
        stdout.flush()?;
    }
    Ok(())
}
