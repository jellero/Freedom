#!/usr/bin/env python3
from __future__ import annotations

import argparse
import socket


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--name", required=True)
    parser.add_argument("--port", type=int, default=9100)
    args = parser.parse_args()
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as server:
        server.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        server.bind(("0.0.0.0", args.port))
        server.listen(32)
        while True:
            conn, peer = server.accept()
            with conn:
                payload = conn.recv(4096)
                response = f"{args.name}|peer={peer[0]}|".encode() + payload
                conn.sendall(response)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
