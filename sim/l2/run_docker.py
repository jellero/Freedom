#!/usr/bin/env python3
"""Real-container L2 smoke for path failure and address rebinding.

This is intentionally small and deterministic enough for CI. It validates real
container namespaces/TCP sockets; it does not claim to reproduce carrier CGNAT.
"""
from __future__ import annotations

import os
import re
import shutil
import subprocess
import sys
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
IMAGE = os.environ.get("FREEDOM_L2_IMAGE", "python:3.12-alpine")
PREFIX = f"freedom-l2-{os.getpid()}"
NET_A = PREFIX + "-a"
NET_B = PREFIX + "-b"
RELAY_A = PREFIX + "-relay-a"
RELAY_B = PREFIX + "-relay-b"


def run(*args: str, check: bool = True, capture: bool = False) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        ["docker", *args], cwd=ROOT, text=True,
        stdout=subprocess.PIPE if capture else None,
        stderr=subprocess.PIPE if capture else None,
        check=check,
    )


def cleanup() -> None:
    for container in (RELAY_A, RELAY_B):
        run("rm", "-f", container, check=False, capture=True)
    for network in (NET_A, NET_B):
        run("network", "rm", network, check=False, capture=True)


def start_relay(container: str, network: str, alias: str) -> None:
    run(
        "run", "-d", "--rm", "--name", container,
        "--network", network, "--network-alias", alias,
        "-v", f"{ROOT}:/work:ro", IMAGE,
        "python", "/work/sim/l2/relay_server.py", "--name", alias,
        capture=True,
    )


def probe(network: str, host: str, expect_success: bool = True) -> str:
    cp = run(
        "run", "--rm", "--network", network,
        "-v", f"{ROOT}:/work:ro", IMAGE,
        "python", "/work/sim/l2/probe.py", host, "9100",
        check=False, capture=True,
    )
    if expect_success and cp.returncode != 0:
        raise RuntimeError(f"probe {host} on {network} failed: {cp.stderr.strip()}")
    if not expect_success and cp.returncode == 0:
        raise RuntimeError(f"probe {host} unexpectedly succeeded after block")
    return cp.stdout.strip()


def peer_ip(response: str) -> str:
    match = re.search(r"\|peer=([^|]+)\|", response)
    if not match:
        raise RuntimeError(f"relay response missing peer address: {response!r}")
    return match.group(1)


def main() -> int:
    if not shutil.which("docker"):
        print("docker not found; L2 requires a disposable Docker-capable runner", file=sys.stderr)
        return 2
    cleanup()
    try:
        run("network", "create", NET_A, capture=True)
        run("network", "create", NET_B, capture=True)
        start_relay(RELAY_A, NET_A, "relay-a")
        start_relay(RELAY_B, NET_A, "relay-b")
        run("network", "connect", "--alias", "relay-a", NET_B, RELAY_A, capture=True)
        run("network", "connect", "--alias", "relay-b", NET_B, RELAY_B, capture=True)
        time.sleep(0.5)

        first = probe(NET_A, "relay-a")
        second = probe(NET_B, "relay-a")
        first_ip, second_ip = peer_ip(first), peer_ip(second)
        if first_ip == second_ip:
            raise RuntimeError("address-rebind smoke did not change client-visible source address")

        # Simulated provider/relay ban: the primary path disappears from the network.
        run("stop", RELAY_A, capture=True)
        probe(NET_B, "relay-a", expect_success=False)
        fallback = probe(NET_B, "relay-b")
        if not fallback.startswith("relay-b|"):
            raise RuntimeError(f"alternate relay response invalid: {fallback!r}")

        print(
            "Freedom L2 Docker smoke passed: "
            f"address {first_ip} -> {second_ip}, primary blocked, alternate relay reachable."
        )
        return 0
    finally:
        cleanup()


if __name__ == "__main__":
    raise SystemExit(main())
