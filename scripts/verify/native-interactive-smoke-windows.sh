#!/usr/bin/env bash
set -euo pipefail

binary="${1:?usage: native-interactive-smoke-windows.sh <binary>}"
mkdir -p target/native-smoke
log_path="target/native-smoke/windows-interactive.log"
if ! "$binary" --interactive-smoke-test --verbose 2> "$log_path"; then
  cat "$log_path"
  exit 1
fi
if ! grep -F '"event":"tui.render.ready"' "$log_path" > /dev/null; then
  cat "$log_path"
  exit 1
fi
