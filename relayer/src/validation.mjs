const DEVICE_ID = /^[0-9a-f]{64}$/;
const SLOT = /^[0-9a-f]{64}$/;
const UNSIGNED_INTEGER = /^(0|[1-9][0-9]*)$/;

export class RequestError extends Error {
  constructor(status, message) {
    super(message);
    this.name = "RequestError";
    this.status = status;
  }
}

export function validateRegister(body) {
  const value = object(body);
  return {
    device_id: identifier(value.device_id, DEVICE_ID, "device_id"),
    identity_public_key: p256PublicKey(value.identity_public_key),
    protocol_version: exactInteger(value.protocol_version, 1, "protocol_version"),
    signature: base64(value.signature, 64, "signature")
  };
}

export function validateRotate(body) {
  const value = object(body);
  return {
    device_id: identifier(value.device_id, DEVICE_ID, "device_id"),
    new_identity_public_key: p256PublicKey(value.new_identity_public_key),
    new_key_epoch: unsignedString(value.new_key_epoch, "new_key_epoch"),
    auth_nonce: unsignedString(value.auth_nonce, "auth_nonce"),
    signature: base64(value.signature, 64, "signature")
  };
}

export function validateRevoke(body) {
  const value = object(body);
  return {
    device_id: identifier(value.device_id, DEVICE_ID, "device_id"),
    auth_nonce: unsignedString(value.auth_nonce, "auth_nonce"),
    signature: base64(value.signature, 64, "signature")
  };
}

export function validateRendezvous(body, nowNs = BigInt(Date.now()) * 1_000_000n) {
  const value = object(body);
  const expiresAt = unsignedString(value.expires_at_ns, "expires_at_ns");
  const expiry = BigInt(expiresAt);
  if (expiry < nowNs + 30_000_000_000n || expiry > nowNs + 600_000_000_000n) {
    throw new RequestError(400, "expires_at_ns fuori dai limiti consentiti");
  }
  return {
    slot: identifier(value.slot, SLOT, "slot"),
    expires_at_ns: expiresAt,
    ciphertext: base64Range(value.ciphertext, 1, 2_048, "ciphertext")
  };
}

function object(value) {
  if (!value || typeof value !== "object" || Array.isArray(value)) {
    throw new RequestError(400, "Body JSON non valido");
  }
  return value;
}

function identifier(value, pattern, field) {
  if (typeof value !== "string" || !pattern.test(value)) {
    throw new RequestError(400, `${field} non valido`);
  }
  return value;
}

function exactInteger(value, expected, field) {
  if (!Number.isSafeInteger(value) || value !== expected) {
    throw new RequestError(400, `${field} non valido`);
  }
  return value;
}

function unsignedString(value, field) {
  if (typeof value !== "string" || !UNSIGNED_INTEGER.test(value)) {
    throw new RequestError(400, `${field} non valido`);
  }
  return value;
}

function p256PublicKey(value) {
  const canonical = base64(value, 33, "identity_public_key");
  const bytes = Buffer.from(canonical, "base64");
  if (bytes[0] !== 2 && bytes[0] !== 3) {
    throw new RequestError(400, "identity_public_key non è P-256 compressa");
  }
  return canonical;
}

function base64(value, expectedBytes, field) {
  return base64Range(value, expectedBytes, expectedBytes, field);
}

function base64Range(value, minimumBytes, maximumBytes, field) {
  if (typeof value !== "string" || value.length === 0 || value.length > 4_096) {
    throw new RequestError(400, `${field} non valido`);
  }
  const bytes = Buffer.from(value, "base64");
  const canonical = bytes.toString("base64");
  if (canonical !== value || bytes.length < minimumBytes || bytes.length > maximumBytes) {
    throw new RequestError(400, `${field} non valido`);
  }
  return canonical;
}
