#!/usr/bin/env bash
set -euo pipefail

binary="${1:?usage: native-interactive-smoke-windows.sh <binary>}"
mkdir -p target/native-smoke
"$binary" --interactive-smoke-test --verbose 2>&1 | tee target/native-smoke/windows-interactive.log
grep -F '"event":"tui.render.ready"' target/native-smoke/windows-interactive.log > /dev/null
