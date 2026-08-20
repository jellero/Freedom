#!/usr/bin/env node
import readline from 'node:readline';
import path from 'node:path';
import process from 'node:process';
import { Worker } from 'near-workspaces';

const PREFIX = 'FREEDOM_L3\t';
const ROOT = process.cwd();
const WASM = process.env.FREEDOM_NEAR_PROBE_WASM ||
  path.join(ROOT, 'control-plane/near-probe/target/wasm32-unknown-unknown/release/freedom_near_probe.wasm');

let worker;
let root;
let minimumHeight = null;
let verifiedHeight = null;
let committedVersion = 0;

function response(value) {
  process.stdout.write(PREFIX + JSON.stringify(value) + '\n');
}

async function deployProbe(initialVersion) {
  const contract = await root.devDeploy(WASM);
  await root.call(contract, 'new', { initial_version: String(initialVersion) });
  return contract;
}

async function finalizedSandboxHeight() {
  const block = await worker.provider.block({ finality: 'final' });
  return Number(block.header.height);
}

async function ensureLogicalHeight(height) {
  const current = await finalizedSandboxHeight();
  if (current < height) {
    await worker.provider.fastForward(height - current + 1);
  }
  return finalizedSandboxHeight();
}

async function verifyCheckpoint(req) {
  const height = Number(req.height);
  const actualFinalHeight = await ensureLogicalHeight(height);

  if (!req.proof_valid) {
    return {
      accepted: false,
      failure: 'CONTROL_PLANE_PROOF_INVALID',
      verified_height: verifiedHeight,
      sandbox_final_height: actualFinalHeight,
    };
  }
  if (minimumHeight !== null && height < minimumHeight) {
    return {
      accepted: false,
      failure: 'BOOTSTRAP_STATE_TOO_OLD',
      verified_height: verifiedHeight,
      sandbox_final_height: actualFinalHeight,
    };
  }
  if (verifiedHeight !== null && height < verifiedHeight) {
    return {
      accepted: false,
      failure: 'CONTROL_PLANE_ROLLBACK',
      verified_height: verifiedHeight,
      sandbox_final_height: actualFinalHeight,
    };
  }

  // A real finalized Sandbox block exists at/after the logical checkpoint height.
  // The logical test height remains the canonical vector value so the same vector
  // can run against Sandbox and a future Testnet-backed adapter.
  verifiedHeight = height;
  return {
    accepted: true,
    failure: null,
    verified_height: verifiedHeight,
    sandbox_final_height: actualFinalHeight,
  };
}

async function verifyMutation(req) {
  const requestedVersion = Number(req.resulting_version);

  if (!req.finality_proof_valid) {
    return { accepted: false, failure: 'CONTROL_PLANE_PROOF_INVALID', committed_version: committedVersion };
  }

  if (requestedVersion < committedVersion) {
    return { accepted: false, failure: 'CONTROL_PLANE_ROLLBACK', committed_version: committedVersion };
  }

  // Each vector uses a fresh contract initialized to the last locally committed
  // version. Failed or adversarial transactions therefore cannot contaminate the
  // next vector while the adapter's monotonic client state remains persistent.
  const contract = await deployProbe(committedVersion);

  if (!req.execution_succeeded) {
    let failed = false;
    try {
      await root.call(contract, 'fail_write', { version: String(requestedVersion) });
    } catch (_) {
      failed = true;
    }
    if (!failed) {
      throw new Error('expected NEAR transaction failure did not occur');
    }
    const after = Number(await contract.view('get_version', {}));
    if (after !== committedVersion) {
      throw new Error(`failed transaction changed state: ${after} != ${committedVersion}`);
    }
    return { accepted: false, failure: 'CONTROL_PLANE_EXECUTION_FAILED', committed_version: committedVersion };
  }

  const method = req.exact_transition_matched ? 'set_version' : 'set_version_mismatch';
  const args = req.exact_transition_matched
    ? { version: String(requestedVersion) }
    : { requested_version: String(requestedVersion) };

  await root.call(contract, method, args);

  // The state is read back from a finalized Sandbox-backed view. The differential
  // vector can then inject a proof-verification failure independently of execution.
  const observedVersion = Number(await contract.view('get_version', {}));

  if (!req.resulting_state_proof_valid) {
    return { accepted: false, failure: 'CONTROL_PLANE_PROOF_INVALID', committed_version: committedVersion };
  }

  if (observedVersion !== requestedVersion) {
    return { accepted: false, failure: 'CONTROL_PLANE_STATE_MISMATCH', committed_version: committedVersion };
  }

  committedVersion = observedVersion;
  return { accepted: true, failure: null, committed_version: committedVersion };
}

async function handle(req) {
  switch (req.op) {
    case 'set_bootstrap_floor': {
      const next = Number(req.minimum_height);
      if (minimumHeight !== null && next < minimumHeight) {
        return { accepted: false, failure: 'CONTROL_PLANE_ROLLBACK', minimum_height: minimumHeight };
      }
      minimumHeight = next;
      return { accepted: true, minimum_height: minimumHeight };
    }
    case 'verify_checkpoint':
      return verifyCheckpoint(req);
    case 'verify_mutation':
      return verifyMutation(req);
    default:
      throw new Error(`unsupported operation: ${req.op}`);
  }
}

async function main() {
  worker = await Worker.init();
  root = worker.rootAccount;

  const rl = readline.createInterface({ input: process.stdin, crlfDelay: Infinity });
  try {
    for await (const line of rl) {
      if (!line.trim()) continue;
      try {
        response(await handle(JSON.parse(line)));
      } catch (err) {
        console.error('[near-l3]', err?.stack || String(err));
        response({ accepted: false, failure: 'NEAR_ADAPTER_INTERNAL_ERROR', detail: String(err) });
      }
    }
  } finally {
    await worker.tearDown().catch((err) => console.error('[near-l3 teardown]', err));
  }
}

main().catch((err) => {
  console.error(err?.stack || String(err));
  process.exit(1);
});
