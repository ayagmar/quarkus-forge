#!/usr/bin/env bash
set -euo pipefail

mode="${1:?usage: native-size.sh <headless|interactive>}"
mkdir -p target/native-size

case "$mode" in
  headless)
    ./mvnw clean
    set -o pipefail
    ./mvnw package -Pheadless-native -DskipTests -Djacoco.skip=true | tee target/native-size/headless-native.log
    java scripts/CheckNativeSize.java \
      --label headless-native \
      --binary target/quarkus-forge-headless \
      --report target/quarkus-forge-headless-build-report.html \
      --log target/native-size/headless-native.log \
      --max-bytes 24500000
    ;;
  interactive)
    set -o pipefail
    ./mvnw package -Pnative -DskipTests -Djacoco.skip=true | tee target/native-size/native.log
    java scripts/CheckNativeSize.java \
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
