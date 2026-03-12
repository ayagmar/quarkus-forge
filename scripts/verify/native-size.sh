#!/usr/bin/env bash
set -euo pipefail
source "$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)/_lib.sh"

mode="${1:?usage: native-size.sh <headless|interactive>}"
mkdir -p target/native-size

run_size_check() {
  if [[ -n "${GITHUB_STEP_SUMMARY:-}" ]]; then
    java scripts/CheckNativeSize.java "$@" | tee -a "$GITHUB_STEP_SUMMARY"
    return
  fi

  java scripts/CheckNativeSize.java "$@"
}

case "$mode" in
  headless)
    ./mvnw clean
    mkdir -p target/native-size
    ./mvnw package -Pheadless,native -DskipTests -Djacoco.skip=true | tee target/native-size/headless-native.log
    run_size_check \
      --label headless-native \
      --binary target/quarkus-forge-headless \
      --report target/quarkus-forge-headless-build-report.html \
      --log target/native-size/headless-native.log \
      --max-bytes 24500000
    ;;
  interactive)
    ./mvnw package -Pnative -DskipTests -Djacoco.skip=true | tee target/native-size/native.log
    run_size_check \
      --label native \
      --binary target/quarkus-forge \
      --report target/quarkus-forge-build-report.html \
      --log target/native-size/native.log \
      --max-bytes 27000000
    ;;
  *)
    echo "Unknown native-size mode: $mode" >&2
    exit 1
    ;;
esac
