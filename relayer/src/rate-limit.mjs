import { RequestError } from "./validation.mjs";

export class RateLimiter {
  constructor({ capacity = 12, refillPerMinute = 2, maximumEntries = 10_000 } = {}) {
    this.capacity = capacity;
    this.refillPerMillisecond = refillPerMinute / 60_000;
    this.maximumEntries = maximumEntries;
    this.buckets = new Map();
  }

  consume(key, cost = 1, now = Date.now()) {
    if (typeof key !== "string" || key.length === 0 || key.length > 200) {
      throw new RequestError(400, "Identificatore client non valido");
    }
    const previous = this.buckets.get(key) ?? { tokens: this.capacity, updatedAt: now };
    const elapsed = Math.max(0, now - previous.updatedAt);
    const tokens = Math.min(
      this.capacity,
      previous.tokens + elapsed * this.refillPerMillisecond
    );
    if (tokens < cost) {
      this.buckets.set(key, { tokens, updatedAt: now });
      throw new RequestError(429, "Quota relayer temporaneamente esaurita");
    }
    this.buckets.set(key, { tokens: tokens - cost, updatedAt: now });
    this.prune(now);
  }

  prune(now) {
    if (this.buckets.size <= this.maximumEntries) return;
    for (const [key, bucket] of this.buckets) {
      if (now - bucket.updatedAt > 60 * 60_000) this.buckets.delete(key);
      if (this.buckets.size <= this.maximumEntries) return;
    }
    const oldest = [...this.buckets.entries()]
      .sort((left, right) => left[1].updatedAt - right[1].updatedAt)
      .slice(0, this.buckets.size - this.maximumEntries);
    oldest.forEach(([key]) => this.buckets.delete(key));
  }
}
