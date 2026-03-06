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
    {{mvn}} verify

# Run unit tests only
test-unit:
    {{mvn}} test

# Run integration tests only (Failsafe)
test-it:
    {{mvn}} test-compile failsafe:integration-test failsafe:verify

# Generate bash completion scripts for both entry points
completion-bash:
    {{mvn}} -DskipTests package -Pheadless
    {{mvn}} -DskipTests package
    mkdir -p target/completions
    {{java_bin}} -cp {{jar}} picocli.AutoComplete -f -o target/completions/quarkus-forge.bash -n quarkus-forge dev.ayagmar.quarkusforge.cli.QuarkusForgeCli
    java -cp {{hjar}} picocli.AutoComplete -f -o target/completions/quarkus-forge-headless.bash -n quarkus-forge-headless dev.ayagmar.quarkusforge.cli.HeadlessCli

# Generate sha256 checksum files for release artifacts
release-checksums:
    {{mvn}} -DskipTests package -Pheadless
    {{mvn}} -DskipTests package
    if command -v sha256sum >/dev/null 2>&1; then sha256sum {{jar}} > {{jar}}.sha256; else shasum -a 256 {{jar}} > {{jar}}.sha256; fi
    if command -v sha256sum >/dev/null 2>&1; then sha256sum {{hjar}} > {{hjar}}.sha256; else shasum -a 256 {{hjar}} > {{hjar}}.sha256; fi

# Run a full clean verify and print merged coverage report paths
coverage:
    {{mvn}} clean verify
    @echo "HTML coverage: target/site/jacoco/index.html"
    @echo "XML coverage:  target/site/jacoco/jacoco.xml"

# Auto-format all Java sources (Google Java Format)
format:
    {{mvn}} spotless:apply

# Check formatting without modifying files (used in CI)
format-check:
    {{mvn}} spotless:check

# Verify headless profile compiles cleanly
headless-check:
    {{mvn}} clean compile -Pheadless

# Build and launch the interactive TUI
tui: build
    {{java_bin}} -jar {{jar}}

# Full pre-commit check: format + headless compile + all tests
verify: format-check headless-check test

# Remove build artifacts
clean:
    {{mvn}} clean
