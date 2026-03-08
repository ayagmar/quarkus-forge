#!/usr/bin/env bash
set -euo pipefail

./mvnw test-compile failsafe:integration-test failsafe:verify
