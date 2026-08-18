import http from "node:http";
import { Account, JsonRpcProvider, nearToYocto, teraToGas } from "near-api-js";
import { RateLimiter } from "./rate-limit.mjs";
import {
  RequestError,
  validateRegister,
  validateRendezvous,
  validateRevoke,
  validateRotate
} from "./validation.mjs";

const config = loadConfig(process.env);
const provider = new JsonRpcProvider({ url: config.rpcUrl });
const account = new Account(config.relayerAccountId, provider, config.privateKey);
const limiter = new RateLimiter();

const routes = new Map([
  ["POST /v1/devices/register", action("register_device", validateRegister, "0.01", 4)],
  ["POST /v1/devices/rotate", action("rotate_device_key", validateRotate, "0.002", 2)],
  ["POST /v1/devices/revoke", action("revoke_device", validateRevoke, "0.002", 2)],
  ["POST /v1/rendezvous", action("put_rendezvous", validateRendezvous, "0.03", 1)]
]);

const server = http.createServer(async (request, response) => {
  try {
    applySecurityHeaders(response);
    if (request.method === "GET" && request.url === "/health") {
      const registry = await provider.callFunction({
        contractId: config.contractId,
        method: "get_config",
        args: {}
      });
      return json(response, 200, {
        ok: true,
        network: config.networkId,
        contract: config.contractId,
        contractVersion: registry.contract_version,
        protocolVersion: registry.protocol_version
      });
    }

    const handler = routes.get(`${request.method} ${request.url}`);
    if (!handler) throw new RequestError(404, "Endpoint non trovato");
    const client = request.socket.remoteAddress ?? "unknown";
    await handler(request, response, client);
  } catch (failure) {
    const status = failure instanceof RequestError ? failure.status : 502;
    const message = failure instanceof RequestError
      ? failure.message
      : "Transazione NEAR non completata";
    if (status >= 500) console.error(failure);
    json(response, status, { ok: false, error: message });
  }
});

server.listen(config.port, "0.0.0.0", () => {
  console.log(`Freedom relayer listening on :${config.port}`);
});

function action(methodName, validate, depositNear, rateCost) {
  return async (request, response, client) => {
    limiter.consume(client, rateCost);
    const body = await readJson(request);
    const args = validate(body);
    const outcome = await account.callFunction({
      contractId: config.contractId,
      methodName,
      args,
      gas: teraToGas("50"),
      deposit: nearToYocto(depositNear)
    });
    json(response, 200, {
      ok: true,
      transactionHash: outcome.transaction.hash,
      method: methodName
    });
  };
}

async function readJson(request) {
  const contentType = request.headers["content-type"]?.split(";", 1)[0];
  if (contentType !== "application/json") {
    throw new RequestError(415, "Content-Type deve essere application/json");
  }
  const chunks = [];
  let size = 0;
  for await (const chunk of request) {
    size += chunk.length;
    if (size > 16_384) throw new RequestError(413, "Richiesta troppo grande");
    chunks.push(chunk);
  }
  try {
    return JSON.parse(Buffer.concat(chunks).toString("utf8"));
  } catch {
    throw new RequestError(400, "Body JSON non valido");
  }
}

function loadConfig(environment) {
  const privateKey = required(environment.NEAR_RELAYER_PRIVATE_KEY, "NEAR_RELAYER_PRIVATE_KEY");
  if (!/^ed25519:[1-9A-HJ-NP-Za-km-z]+$/.test(privateKey)) {
    throw new Error("NEAR_RELAYER_PRIVATE_KEY non valida");
  }
  return {
    networkId: environment.NEAR_NETWORK_ID || "testnet",
    rpcUrl: environment.NEAR_RPC_URL || "https://rpc.testnet.near.org",
    contractId: environment.NEAR_CONTRACT_ID || "freedom-registry-jellero.testnet",
    relayerAccountId: required(environment.NEAR_RELAYER_ACCOUNT_ID, "NEAR_RELAYER_ACCOUNT_ID"),
    privateKey,
    port: port(environment.PORT || "8787")
  };
}

function required(value, name) {
  if (!value) throw new Error(`${name} obbligatoria`);
  return value;
}

function port(value) {
  const parsed = Number(value);
  if (!Number.isSafeInteger(parsed) || parsed < 1 || parsed > 65_535) {
    throw new Error("PORT non valida");
  }
  return parsed;
}

function applySecurityHeaders(response) {
  response.setHeader("Content-Type", "application/json; charset=utf-8");
  response.setHeader("Cache-Control", "no-store");
  response.setHeader("X-Content-Type-Options", "nosniff");
  response.setHeader("Referrer-Policy", "no-referrer");
}

function json(response, status, value) {
  if (response.headersSent) return;
  response.writeHead(status);
  response.end(JSON.stringify(value));
}
