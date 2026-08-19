#!/usr/bin/env python3
from __future__ import annotations

import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "tools"))
from build_core import build  # noqa: E402


def main() -> int:
    classes = build(include_tests=True)
    subprocess.run(
        ["java", "-cp", str(classes), "dev.freedom.core.CoreSelfTest"],
        cwd=ROOT,
        check=True,
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
