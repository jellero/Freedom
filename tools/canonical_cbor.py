#!/usr/bin/env python3
"""Freedom deterministic-CBOR profile primitives.

This module intentionally implements only the data model admitted by Freedom-DCBOR-1:
integers within CBOR major-type 0/1 range, byte/text strings, arrays, maps with
text-string keys, booleans and null. Tags, floats, undefined and indefinite
lengths are rejected.

It is a small reference implementation for vectors/tests, not a general CBOR library.
"""

from __future__ import annotations

from dataclasses import dataclass
from typing import Any


class CBORError(ValueError):
    pass


def _head(major: int, value: int) -> bytes:
    if value < 0:
        raise CBORError("negative length/value")
    if value < 24:
        return bytes([(major << 5) | value])
    if value <= 0xFF:
        return bytes([(major << 5) | 24, value])
    if value <= 0xFFFF:
        return bytes([(major << 5) | 25]) + value.to_bytes(2, "big")
    if value <= 0xFFFFFFFF:
        return bytes([(major << 5) | 26]) + value.to_bytes(4, "big")
    if value <= 0xFFFFFFFFFFFFFFFF:
        return bytes([(major << 5) | 27]) + value.to_bytes(8, "big")
    raise CBORError("integer outside Freedom-DCBOR-1 range")


def encode(value: Any) -> bytes:
    """Encode a Freedom-DCBOR-1 value."""
    if value is None:
        return b"\xf6"
    if value is False:
        return b"\xf4"
    if value is True:
        return b"\xf5"
    if isinstance(value, int) and not isinstance(value, bool):
        if value >= 0:
            return _head(0, value)
        n = -1 - value
        return _head(1, n)
    if isinstance(value, bytes):
        return _head(2, len(value)) + value
    if isinstance(value, str):
        raw = value.encode("utf-8", errors="strict")
        return _head(3, len(raw)) + raw
    if isinstance(value, (list, tuple)):
        return _head(4, len(value)) + b"".join(encode(item) for item in value)
    if isinstance(value, dict):
        encoded_items: list[tuple[bytes, bytes]] = []
        for key, item in value.items():
            if not isinstance(key, str):
                raise CBORError("Freedom maps require text-string keys")
            encoded_key = encode(key)
            encoded_items.append((encoded_key, encode(item)))
        encoded_items.sort(key=lambda pair: pair[0])
        return _head(5, len(encoded_items)) + b"".join(
            key + item for key, item in encoded_items
        )
    if isinstance(value, float):
        raise CBORError("floating point is forbidden in Freedom-DCBOR-1")
    raise CBORError(f"unsupported Freedom-DCBOR-1 type: {type(value).__name__}")


@dataclass
class _Decoder:
    raw: bytes
    pos: int = 0

    def take(self, count: int) -> bytes:
        end = self.pos + count
        if end > len(self.raw):
            raise CBORError("truncated CBOR")
        part = self.raw[self.pos:end]
        self.pos = end
        return part

    def one(self) -> int:
        return self.take(1)[0]

    def argument(self, ai: int) -> int:
        if ai < 24:
            return ai
        if ai == 24:
            value = self.one()
            if value < 24:
                raise CBORError("non-preferred integer/length encoding")
            return value
        if ai == 25:
            value = int.from_bytes(self.take(2), "big")
            if value <= 0xFF:
                raise CBORError("non-preferred integer/length encoding")
            return value
        if ai == 26:
            value = int.from_bytes(self.take(4), "big")
            if value <= 0xFFFF:
                raise CBORError("non-preferred integer/length encoding")
            return value
        if ai == 27:
            value = int.from_bytes(self.take(8), "big")
            if value <= 0xFFFFFFFF:
                raise CBORError("non-preferred integer/length encoding")
            return value
        if ai == 31:
            raise CBORError("indefinite-length items forbidden")
        raise CBORError("reserved additional-information value")

    def item(self) -> Any:
        initial_pos = self.pos
        initial = self.one()
        major, ai = initial >> 5, initial & 0x1F

        if major in (0, 1):
            n = self.argument(ai)
            return n if major == 0 else -1 - n

        if major == 2:
            length = self.argument(ai)
            return self.take(length)

        if major == 3:
            length = self.argument(ai)
            try:
                return self.take(length).decode("utf-8", errors="strict")
            except UnicodeDecodeError as exc:
                raise CBORError("invalid UTF-8 text string") from exc

        if major == 4:
            length = self.argument(ai)
            return [self.item() for _ in range(length)]

        if major == 5:
            length = self.argument(ai)
            result: dict[str, Any] = {}
            previous_encoded_key: bytes | None = None
            for _ in range(length):
                key_start = self.pos
                key = self.item()
                key_end = self.pos
                encoded_key = self.raw[key_start:key_end]
                if not isinstance(key, str):
                    raise CBORError("Freedom maps require text-string keys")
                if previous_encoded_key is not None and encoded_key <= previous_encoded_key:
                    if encoded_key == previous_encoded_key:
                        raise CBORError("duplicate map key")
                    raise CBORError("map keys not in deterministic bytewise order")
                previous_encoded_key = encoded_key
                if key in result:
                    raise CBORError("duplicate map key")
                result[key] = self.item()
            return result

        if major == 6:
            raise CBORError("CBOR tags forbidden in Freedom-DCBOR-1")

        if major == 7:
            if ai == 20:
                return False
            if ai == 21:
                return True
            if ai == 22:
                return None
            if ai in (25, 26, 27):
                raise CBORError("floating point forbidden in Freedom-DCBOR-1")
            if ai == 31:
                raise CBORError("break/indefinite form forbidden")
            raise CBORError("simple value forbidden in Freedom-DCBOR-1")

        raise CBORError(f"unsupported CBOR major type at {initial_pos}")


def decode_strict(raw: bytes) -> Any:
    """Decode and reject any non-Freedom/non-deterministic representation."""
    decoder = _Decoder(raw)
    value = decoder.item()
    if decoder.pos != len(raw):
        raise CBORError("trailing bytes after top-level item")
    if encode(value) != raw:
        raise CBORError("encoding is not canonical Freedom-DCBOR-1")
    return value


def signing_preimage(
    *,
    network_id: str,
    domain: str,
    schema_version: int,
    body: dict[str, Any],
) -> bytes:
    """Build the exact V1 standalone-signature preimage.

    Signature/authentication fields must already be removed from ``body``.
    """
    payload = encode(body)
    return encode([
        "FreedomSigningInput",
        1,
        network_id,
        domain,
        schema_version,
        payload,
    ])
