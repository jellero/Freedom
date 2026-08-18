import assert from "node:assert/strict";
import test from "node:test";
import { RateLimiter } from "../src/rate-limit.mjs";
import {
  RequestError,
  validateRegister,
  validateRendezvous
} from "../src/validation.mjs";

test("accepts a bounded P-256 registration request", () => {
  const key = Buffer.concat([Buffer.from([2]), Buffer.alloc(32, 7)]).toString("base64");
  const signature = Buffer.alloc(64, 9).toString("base64");
  const value = validateRegister({
    device_id: "ab".repeat(32),
    identity_public_key: key,
    protocol_version: 1,
    signature
  });
  assert.equal(value.identity_public_key, key);
  assert.equal(value.signature, signature);
});

test("rejects malformed signatures and keys", () => {
  assert.throws(
    () => validateRegister({
      device_id: "ab".repeat(32),
      identity_public_key: Buffer.alloc(33).toString("base64"),
      protocol_version: 1,
      signature: Buffer.alloc(63).toString("base64")
    }),
    RequestError
  );
});

test("enforces rendezvous size and TTL", () => {
  const now = 1_000_000_000_000n;
  const valid = validateRendezvous({
    slot: "cd".repeat(32),
    expires_at_ns: (now + 60_000_000_000n).toString(),
    ciphertext: Buffer.alloc(2_048, 3).toString("base64")
  }, now);
  assert.equal(Buffer.from(valid.ciphertext, "base64").length, 2_048);
  assert.throws(
    () => validateRendezvous({
      slot: "cd".repeat(32),
      expires_at_ns: (now + 60_000_000_000n).toString(),
      ciphertext: Buffer.alloc(2_049, 3).toString("base64")
    }, now),
    RequestError
  );
});

test("rate limiter refills but rejects bursts", () => {
  const limiter = new RateLimiter({ capacity: 2, refillPerMinute: 1 });
  limiter.consume("client", 1, 0);
  limiter.consume("client", 1, 0);
  assert.throws(() => limiter.consume("client", 1, 0), RequestError);
  limiter.consume("client", 1, 60_000);
});
