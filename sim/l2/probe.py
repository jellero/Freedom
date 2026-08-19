#!/usr/bin/env python3
from __future__ import annotations

import argparse
import socket
import sys


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("host")
    parser.add_argument("port", type=int)
    parser.add_argument("--payload", default="freedom-l2")
    parser.add_argument("--timeout", type=float, default=2.0)
    args = parser.parse_args()
    try:
        with socket.create_connection((args.host, args.port), timeout=args.timeout) as sock:
            sock.sendall(args.payload.encode())
            data = sock.recv(4096).decode()
    except OSError as exc:
        print(f"PROBE_FAIL {exc}", file=sys.stderr)
        return 2
    print(data)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
