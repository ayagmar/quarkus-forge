#!/usr/bin/env bash
set -euo pipefail
source "$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)/_lib.sh"

binary="${1:?usage: native-release-smoke.sh <binary> <headless|interactive-posix|interactive-windows>}"
mode="${2:?usage: native-release-smoke.sh <binary> <headless|interactive-posix|interactive-windows>}"

"$binary" --help > /dev/null
"$binary" --version > /dev/null
"$binary" generate --help > /dev/null

case "$mode" in
  headless)
    ;;
  interactive-posix)
    exec scripts/verify/native-interactive-smoke-posix.sh "$binary"
    ;;
  interactive-windows)
    exec scripts/verify/native-interactive-smoke-windows.sh "$binary"
    ;;
  *)
    echo "Unknown native release smoke mode: $mode" >&2
    exit 1
    ;;
esac
