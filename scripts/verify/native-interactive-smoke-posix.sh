#!/usr/bin/env bash
set -euo pipefail
source "$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)/_lib.sh"

binary="${1:?usage: native-interactive-smoke-posix.sh <binary>}"
python3 scripts/interactive_native_smoke.py --binary "$binary"
