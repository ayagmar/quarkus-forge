#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/_lib.sh"

binary="${1:?usage: native-interactive-smoke-posix.sh <binary>}"
python3 "$SCRIPT_DIR/../interactive_native_smoke.py" --binary "$binary"
