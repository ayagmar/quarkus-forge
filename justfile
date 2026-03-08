# Quarkus Forge task runner
# Install: https://github.com/casey/just

jar      := "target/quarkus-forge.jar"
hjar     := "target/quarkus-forge-headless.jar"
java_bin := "java --enable-native-access=ALL-UNNAMED"
mvn      := "./mvnw"

# Show available recipes
default:
    @just --list

# Build full TUI + headless jar
build:
    {{mvn}} -DskipTests package

# Build headless-only jar (~40% smaller, no TUI deps)
build-headless:
    {{mvn}} clean package -Pheadless

# Build native image (requires GraalVM with native-image on PATH)
build-native:
    {{mvn}} clean package -Pnative -DskipTests

# Run all tests (unit + integration)
test:
    scripts/verify/verify.sh

# Run unit tests only
test-unit:
    scripts/verify/unit.sh

# Run integration tests only (Failsafe; compiles tests without running Surefire)
test-it:
    scripts/verify/integration.sh

# Generate bash completion scripts for both entry points
completion-bash:
    {{mvn}} clean -DskipTests package -Pheadless
    {{mvn}} -DskipTests package
    mkdir -p target/completions
    {{java_bin}} -cp {{jar}} picocli.AutoComplete -f -o target/completions/quarkus-forge.bash -n quarkus-forge dev.ayagmar.quarkusforge.cli.QuarkusForgeCli
    {{java_bin}} -cp {{hjar}} picocli.AutoComplete -f -o target/completions/quarkus-forge-headless.bash -n quarkus-forge-headless dev.ayagmar.quarkusforge.cli.HeadlessCli

# Generate sha256 checksum files for release artifacts
release-checksums:
    {{mvn}} clean -DskipTests package -Pheadless
    {{mvn}} -DskipTests package
    if command -v sha256sum >/dev/null 2>&1; then sha256sum {{jar}} > {{jar}}.sha256; else shasum -a 256 {{jar}} > {{jar}}.sha256; fi
    if command -v sha256sum >/dev/null 2>&1; then sha256sum {{hjar}} > {{hjar}}.sha256; else shasum -a 256 {{hjar}} > {{hjar}}.sha256; fi

# Run a full clean verify and print merged coverage report paths
coverage:
    scripts/verify/coverage.sh
    @echo "HTML coverage: target/site/jacoco/index.html"
    @echo "XML coverage:  target/site/jacoco/jacoco.xml"

# Auto-format all Java sources (Google Java Format)
format:
    {{mvn}} spotless:apply

# Check formatting without modifying files (used in CI)
format-check:
    scripts/verify/format-check.sh

# Verify headless profile compiles cleanly
headless-check:
    scripts/verify/headless-compile.sh

# Build docs locally using the same entrypoint planned for CI
docs-build:
    scripts/verify/docs-build.sh

# Check docs links locally using the same entrypoint planned for CI
docs-linkcheck:
    scripts/verify/docs-linkcheck.sh

# Check native-image size budgets
native-size mode:
    scripts/verify/native-size.sh {{mode}}

# Smoke-test an interactive native binary on POSIX runners
native-smoke-posix binary:
    scripts/verify/native-interactive-smoke-posix.sh {{binary}}

# Smoke-test the interactive native binary using the Windows smoke mode
native-smoke-windows binary:
    scripts/verify/native-interactive-smoke-windows.sh {{binary}}

# Build and launch the interactive TUI
tui: build
    {{java_bin}} -jar {{jar}}

# Full pre-commit check: format + headless compile + all tests
verify: format-check headless-check test

# Remove build artifacts
clean:
    {{mvn}} clean
