# Changelog

All notable changes to Quarkus Forge are documented here.
This project uses [Conventional Commits](https://www.conventionalcommits.org/). GitHub Releases contain auto-generated notes from [JReleaser](https://jreleaser.org/).

## [Unreleased](https://github.com/ayagmar/quarkus-forge/compare/v0.3.0...HEAD)

## [0.3.0](https://github.com/ayagmar/quarkus-forge/compare/v0.2.3...v0.3.0) (2026-03-03)

### Changed

- fix(ci): resolve shellcheck output redirection warning (a9b3cfc)
- fix(ci): lint workflows and repair release-cut changelog step (002e5e6)
- feat(release): update changelog during release-cut (29a9e76)
- chore(release): restore release-cut workflow and bump 0.3.0 (d47f213)


### Added

- JaCoCo test coverage with Codecov integration
- Maven Wrapper (`./mvnw`) pinned to Maven 3.9.9
- `justfile` task runner with common development recipes
- `.sdkmanrc` for SDKMAN! Java version management
- `.editorconfig`, `CONTRIBUTING.md`, `SECURITY.md`
- GitHub issue templates (bug report, feature request) and PR template
- Dependabot for Maven and GitHub Actions dependency updates
- `ci-status` gate job for branch protection
- TUI screenshot in README
- Landing page mobile responsiveness fix

### Fixed

- Windows native build failure (PowerShell comma parsing in `-Pheadless,headless-native`)
- CI Maven cache using wrong path (`.m2/repository` instead of `~/.m2/repository`)
- Duplicate `artifactId` in spotless-maven-plugin POM block
- Documentation drift: headless-mode TIP jar name, missing `--enable-native-access`

### Changed

- CI and release workflows switch from `mvn` to `./mvnw`
- Expanded troubleshooting guide with 6 new sections
- Restructured README with badges, comparison tables, and direct doc links

## [0.2.0](https://github.com/ayagmar/quarkus-forge/compare/v0.1.2...v0.2.0) (2026-03-02)

### Added

- Headless CLI entry point (`HeadlessCli`) with `generate` subcommand
- Headless and headless-native Maven profiles (smaller jar, no TUI deps)
- HeadlessCli smoke tests (no-subcommand, help, version)

### Changed

- Decouple `GenerateCommand` from `QuarkusForgeCli` via `HeadlessRunner`
- Move TUI config helpers to `TuiBootstrapService`
- Extract `ExitCodes` class and Maven build properties
- Move favorites store from `ui/` to `api/` package
- Use headless jar in all headless-mode documentation

### Fixed

- CLI version provider uses own class for package version lookup
- Reorder release steps to prevent `clean` from deleting JVM jar

## [0.1.2](https://github.com/ayagmar/quarkus-forge/compare/v0.1.1...v0.1.2) (2026-03-02)

### Added

- Default recipe library and TUI export for saved recipes
- Polish landing page with Mermaid diagrams and IDE auto-detection docs

### Changed

- Decompose god classes, apply SOLID principles, improve code quality

## [0.1.1](https://github.com/ayagmar/quarkus-forge/compare/v0.1.0...v0.1.1) (2026-03-01)

### Added

- Dynamic version resolution and updated CLI version command
- GitHub visibility options integrated into post-generation flow

## [0.1.0](https://github.com/ayagmar/quarkus-forge/compare/v0.0.6...v0.1.0) (2026-03-01)

### Changed

- Replace Jackson Databind with jackson-core streaming parser (smaller footprint)
- Simplify backend preference logic and update tests

## [0.0.6](https://github.com/ayagmar/quarkus-forge/compare/v0.0.5...v0.0.6) (2026-03-01)

### Fixed

- Scope CI triggers and enable shared arena for native builds
- Remove duplicate quality gates from release workflow

## [0.0.5](https://github.com/ayagmar/quarkus-forge/compare/v0.0.4...v0.0.5) (2026-03-01)

### Fixed

- Pin JReleaser GitHub branch for release publishing

## [0.0.4](https://github.com/ayagmar/quarkus-forge/compare/v0.0.3...v0.0.4) (2026-03-01)

### Fixed

- Scope native artifacts by platform in release pipeline

## [0.0.3](https://github.com/ayagmar/quarkus-forge/compare/v0.0.2...v0.0.3) (2026-03-01)

### Fixed

- Replace release-please with manual release cut
- Correct JReleaser project metadata and asset configuration

## [0.0.2](https://github.com/ayagmar/quarkus-forge/compare/v0.0.1...v0.0.2) (2026-03-01)

### Fixed

- Harden Windows native build and catalog publish
- Publish tag and release in one manual run

## [0.0.1](https://github.com/ayagmar/quarkus-forge/releases/tag/v0.0.1) (2026-03-01)

Initial release.

### Added

- Keyboard-first TUI with Vim-style navigation, fuzzy search, and category filtering
- Headless `generate` command with dry-run and verbose diagnostics
- Async Quarkus API client with retry policies and catalog caching
- Deterministic Forgefile with optional locked section for reproducible builds
- ZIP extraction with zip-slip and zip-bomb protection
- Post-generation actions: open IDE, shell, publish to GitHub
- Command palette, help overlay, theming via `.tcss` files
- Extension favorites, recents, and session persistence
- Stream-aware selectors and platform compatibility rules
- Native image support via GraalVM
- JBang catalog for direct execution
- Antora documentation site
