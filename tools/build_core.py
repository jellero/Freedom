#!/usr/bin/env python3
from __future__ import annotations

import argparse
import shutil
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
BUILD = ROOT / "build" / "core-classes"


def sources(include_tests: bool = False) -> list[Path]:
    paths = list((ROOT / "core" / "src" / "main" / "java").rglob("*.java"))
    paths += list((ROOT / "sim" / "jvm").rglob("*.java"))
    if include_tests:
        paths += list((ROOT / "core" / "src" / "test" / "java").rglob("*.java"))
    return sorted(paths)


def build(include_tests: bool = False) -> Path:
    javac = shutil.which("javac")
    if not javac:
        raise SystemExit("javac not found; Freedom host core requires JDK 17+")
    BUILD.mkdir(parents=True, exist_ok=True)
    src = sources(include_tests)
    if not src:
        raise SystemExit("no Freedom core Java sources found")
    cmd = [javac, "--release", "17", "-encoding", "UTF-8", "-d", str(BUILD)] + [str(p) for p in src]
    subprocess.run(cmd, cwd=ROOT, check=True)
    return BUILD


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--with-tests", action="store_true")
    args = parser.parse_args()
    target = build(args.with_tests)
    print(f"Freedom shared core compiled to {target.relative_to(ROOT)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
