import {
  generateKeyPairSync,
  randomBytes,
  sign
} from "node:crypto";

const contractId = process.argv[2] || "freedom-registry-jellero.testnet";
const { privateKey, publicKey } = generateKeyPairSync("ec", {
  namedCurve: "prime256v1"
});
const jwk = publicKey.export({ format: "jwk" });
const x = Buffer.from(jwk.x, "base64url");
const y = Buffer.from(jwk.y, "base64url");
const compressedPublicKey = Buffer.concat([
  Buffer.from([(y[y.length - 1] & 1) === 0 ? 2 : 3]),
  x
]);
const deviceId = randomBytes(32);
const message = authorizationMessage(contractId, deviceId, compressedPublicKey);
const signature = canonicalLowS(sign("sha256", message, {
  key: privateKey,
  dsaEncoding: "ieee-p1363"
}));

const args = {
  device_id: deviceId.toString("hex"),
  identity_public_key: compressedPublicKey.toString("base64"),
  protocol_version: 1,
  signature: signature.toString("base64")
};

process.stdout.write(JSON.stringify(args));

function authorizationMessage(accountId, id, key) {
  const domain = Buffer.from("FREEDOM_REGISTRY_V1\0", "utf8");
  const contract = Buffer.from(accountId, "utf8");
  return Buffer.concat([
    domain,
    unsigned(2, contract.length),
    contract,
    Buffer.from([1]),
    id,
    unsigned(8, 0),
    unsigned(8, 1),
    unsigned(2, 1),
    unsigned(2, key.length),
    key
  ]);
}

function unsigned(bytes, value) {
  const buffer = Buffer.alloc(bytes);
  if (bytes === 8) buffer.writeBigUInt64BE(BigInt(value));
  else if (bytes === 2) buffer.writeUInt16BE(value);
  else throw new Error("Dimensione intero non supportata");
  return buffer;
}

function canonicalLowS(value) {
  if (value.length !== 64) throw new Error("Firma P-256 non valida");
  const order = BigInt("0xffffffff00000000ffffffffffffffffbce6faada7179e84f3b9cac2fc632551");
  const r = BigInt(`0x${value.subarray(0, 32).toString("hex")}`);
  let s = BigInt(`0x${value.subarray(32).toString("hex")}`);
  if (s > order / 2n) s = order - s;
  return Buffer.concat([number32(r), number32(s)]);
}

function number32(value) {
  return Buffer.from(value.toString(16).padStart(64, "0"), "hex");
}
