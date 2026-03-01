# Quarkus Forge

Quarkus Forge is a keyboard-first terminal UI and headless CLI for generating Quarkus projects.

## Requirements

- Java 25
- Maven 3.9+

## Build

```bash
mvn -q -DskipTests package
```

Jar output: `target/quarkus-forge.jar`

## Usage

Interactive TUI:

```bash
java -jar target/quarkus-forge.jar
```

Headless generate:

```bash
java -jar target/quarkus-forge.jar generate \
  --group-id org.acme \
  --artifact-id demo \
  --build-tool maven \
  --java-version 25
```

Headless with presets from code.quarkus.io:

```bash
java -jar target/quarkus-forge.jar generate \
  --preset web \
  --preset data
```

Deterministic replay with recipe + lock:

```bash
java -jar target/quarkus-forge.jar generate --recipe Forgefile --lock forge.lock
```

Refresh lock after intentional changes:

```bash
java -jar target/quarkus-forge.jar generate \
  --recipe Forgefile \
  --lock forge.lock \
  --refresh-lock \
  --dry-run
```

## Verification loop

```bash
mvn -q spotless:check
mvn -q checkstyle:check
mvn -q test
npm ci --prefix site
npm run docs:build --prefix site
npm run docs:linkcheck --prefix site
```

## Docs

- Antora docs source: `docs/`
- Site build scripts: `site/`
- Local docs guide: `site/README.md`
