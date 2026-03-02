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

# Run all tests (unit + integration) with JaCoCo coverage
test:
    {{mvn}} verify

# Run unit tests only
test-unit:
    {{mvn}} test

# Run integration tests only
test-it:
    {{mvn}} verify -DskipTests=true

# Run a full clean verify and print coverage report paths
coverage:
    {{mvn}} clean verify
    @echo "Unit coverage:  target/site/jacoco/index.html"
    @echo "IT coverage:    target/site/jacoco-it/index.html"

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
