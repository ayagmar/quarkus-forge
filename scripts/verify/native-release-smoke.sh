#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/_lib.sh"

binary="${1:?usage: native-release-smoke.sh <binary> <headless|interactive-posix|interactive-windows>}"
mode="${2:?usage: native-release-smoke.sh <binary> <headless|interactive-posix|interactive-windows>}"

"$binary" --help > /dev/null
"$binary" --version > /dev/null
"$binary" generate --help > /dev/null
"$binary" generate --dry-run --group-id org.acme --artifact-id native-smoke-app > /dev/null

case "$mode" in
  headless)
    exit 0
  interactive-posix)
    exec "$SCRIPT_DIR/native-interactive-smoke-posix.sh" "$binary"
    ;;
  interactive-windows)
    exec "$SCRIPT_DIR/native-interactive-smoke-windows.sh" "$binary"
    ;;
  *)
    echo "Unknown native release smoke mode: $mode" >&2
    exit 1
    ;;
esac
