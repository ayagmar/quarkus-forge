# Changelog

All notable changes to Quarkus Forge are documented here.
This project uses [Conventional Commits](https://www.conventionalcommits.org/). GitHub Releases contain auto-generated notes from [JReleaser](https://jreleaser.org/).

## [Unreleased](https://github.com/ayagmar/quarkus-forge/compare/v0.6.3...HEAD)

## [0.6.3](https://github.com/ayagmar/quarkus-forge/compare/v0.6.2...v0.6.3) (2026-03-08)

### Changed

- fix(ui): tighten follow-up validation paths (f87c94d)
- test(runtime): cover bootstrap and focus helpers (54a54c5)
- fix(repo): address follow-up review findings (5d8a920)
- fix(ui): guide multi-field validation recovery (c02ba57)
- fix(ui): surface blocked submit errors (92be7e4)
- fix(ci): harden workflow checks on windows (ba38879)
- refactor(ui): dedupe extension update intents (0596574)
- fix(headless): propagate generation timeout cancellation (41a592d)
- chore: update docs (28bc74b)
- docs(review): close p1 review trail (65f4e97)
- docs(release): clarify artifact verification (57b53fa)
- docs(review): resolve native ci findings (bd9efd1)
- docs(release): clarify artifact verification (ad91de3)
- test(ci): pin final status invariants (ee81d08)
- refactor(runtime): make session own headless opening (bf11373)
- refactor(headless): narrow generation assembly seam (c7a2d80)
- test(ci): harden native size workflow coverage (22df807)
- test(ui): tighten overlay routing coverage (7098abb)
- refactor(ui): group extension selection state (99c410d)
- refactor(ui): extract input effects (3db8640)
- refactor(ui): move extension flow into reducer (330d242)
- fix(tui): use jline backend on windows (70100f9)
- z (34a1b9c)
- fix(ui): remove duplicate post-generation visibility (329a2fd)
- docs(review): capture phase lock-in findings (9044d8b)
- docs(backlog): consolidate active workstreams (21b2d77)
- fix(architecture): remove runtime postgen cycle (f16505c)
- docs(architecture): align final package boundaries (ec14145)
- docs(backlog): prune stale planning references (107d0a7)
- test(architecture): lock package ownership (e698ae6)
- refactor(headless): slim generation orchestration (72a928c)
- refactor(headless): isolate extension resolution flow (4bf432a)
- refactor(headless): extract forgefile persistence (af63678)
- test(headless): tighten extension resolution checks (0167429)
- refactor(headless): extract extension resolution (d3fd18c)
- docs(review): finalize runtime slice review (125916d)
- refactor(runtime): remove runtime wiring adapter (f7ecf01)
- refactor(runtime): route tui bootstrap through runtime bundle (273b922)
- refactor(runtime): introduce runtime services bundle (04575fc)
- refactor(startup): route cli startup through service (80973b0)
- refactor(startup): extract startup policy service (86f1c68)
- refactor(startup): define application startup contract (eee02b6)
- refactor(ui): split reducer and render state (264623e)
- refactor(ui): introduce render model (0671a16)
- refactor(ui): define ui state ownership (2f4130b)
- test(ui): cover generation state transitions (3ec79d9)
- refactor(ui): extract generation state machine (b23b18c)
- refactor(ui): promote generation state type (81f6529)
- refactor(ui): extract extension effects (b571b9c)
- refactor(ui): extract generation effects (accf0ea)
- refactor(ui): extract catalog effects (e49e8ed)
- refactor(ui): extract render state assembler (e4eee99)
- test(release): pin interactive smoke matrix rows (c5f6853)
- docs(verify): cover native smoke entrypoints (85b6dc1)
- docs(verify): align maintainer verification docs (ed29419)
- refactor(verify): wire workflows to shared entrypoints (7c0d90a)
- fix(release): preserve windows smoke console (ab9397f)
- refactor(verify): add shared verification scripts (e889877)
- fix(native): smoke windows interactive startup (2620e0e)
- fix(release): smoke macos interactive startup (ea32a7e)
- fix(release): smoke interactive native startup (db20260)
- chore(release): prepare next iteration 0.6.3-SNAPSHOT (3876f37)


## [0.6.2](https://github.com/ayagmar/quarkus-forge/compare/v0.6.1...v0.6.2) (2026-03-08)

### Changed

- fix(native): pin tamboui snapshot dependencies (#37) (5157e58)
- chore(release): prepare next iteration 0.6.2-SNAPSHOT (adce15b)


## [0.6.1](https://github.com/ayagmar/quarkus-forge/compare/v0.6.0...v0.6.1) (2026-03-07)

### Changed

- refactor(native): align metadata with package boundaries (2f0b825)
- fix(native): enable shared arena support (6176e9e)
- chore(release): prepare next iteration 0.6.1-SNAPSHOT (f074a92)


## [0.6.0](https://github.com/ayagmar/quarkus-forge/compare/v0.5.0...v0.6.0) (2026-03-07)

### Changed

- style(ui): apply spotless formatting (d7b61da)
- refactor(ui): remove toggle error details payload (2acd9e9)
- refactor: clean up method parameters and improve test assertions (a03e5a2)
- fix(core): harden reducer and path edge cases (d44b626)
- refactor: update file reading to use UTF-8 encoding and enhance test coverage (d76a55a)
- test(headless): harden output path assertions on windows (52046e0)
- refactor(forge): remove deprecated compatibility shims (20c8800)
- docs(architecture): align docs with runtime split (5e970ae)
- refactor(core): centralize runtime and persistence wiring (8b1cfee)
- refactor(ui): finish reducer-owned extension state (d24a5c6)
- refactor(ui:  effect handling and extend functionality (742a426)
- refactor(ui): unify shared shortcut and palette actions (fe021b8)
- refactor(ui): route command palette actions through effects (9705b4f)
- refactor(ui): enhance command palette and help overlay handling in reducer and controller (39b85ca)
- refactor(ui): implement extension panel focus intent and associated reducer logic (33a1c19)
- refactor(ui): update submit request handling and status messaging (64aa292)
- refactor(ui): narrow submit and effect authority (22aa17c)
- docs(ui): align architecture with state machine refactor (f59338c)
- refactor(ui): split catalog state and render adapters (ec25a7b)
- refactor(ui): harden reducer-owned controller flows (5501dce)
- fix(headless): fallback to cache and resolve home paths (caa6722)
- fix(api): harden catalog cache fallback handling (9328390)
- fix(core): address verified review findings (ecdf0a8)
- test(core): harden startup and transport coverage (2ab4fd7)
- refactor(core): centralize input and ui state flows (39b9869)
- fix(core): tighten forgefile compatibility and startup defaults (d23ea91)
- fix(ci): address review feedback and native gating (ea23dce)
- refactor(architecture): split cli headless forge and postgen boundaries (ea92d8d)
- chore(native): port size budget check to Java 25 (cb3526e)
- fix(core): tighten review cleanup (5930238)
- fix(runtime): unwrap preset load failure diagnostics (79016fe)
- refactor(runtime): separate bootstrap and startup packages (31bc864)
- refactor(ui): split extension catalog collaborators (7c44e52)
- refactor(ui): route post-generation reset through reducer (6b526f7)
- chore(tests): ignore generated python bytecode (cacea31)
- test(native): cover native size script regressions (6cc2a3c)
- test(headless): harden client and cli boundaries (d7b5955)
- test(api): add transport coverage and stabilize timeouts (12e455a)
- refactor(tests): simplify GenerationRequest creation in ProjectArchiveServiceTest (966d3e3)
- fix(native): remove timeout race and stabilize timeout tests (99b2eb9)
- refactor(native): type api transport and enforce size budgets (6f59fd4)
- docs: sync runtime docs and remove stale headless spec (c09cf87)
- chore(release): prepare next iteration 0.5.1-SNAPSHOT (f34b081)


## [0.5.0](https://github.com/ayagmar/quarkus-forge/compare/v0.4.0...v0.5.0) (2026-03-04)

### Changed

- refactor(ui): unify reducer dispatch and simplify snapshot mapping (7dceaa0)
- fix(ui): resolve valid coderabbit findings (9b5c00b)
- test(ui): make generation success path assertion cross-platform (ccababd)
- test(ui): expand reducer intent coverage and trim controller indirection (ae9a3ca)
- chore(ui): add core javadocs and tighten reducer guard (a837bf2)
- refactor(ui): finalize state-machine cleanup and docs (71420df)
- refactor(ui): extract state-driven renderer orchestration (7564016)
- refactor(ui): route focus and input decisions via reducer (a25aeda)
- refactor(ui): migrate generation callbacks to reducer effects (9a96359)
- refactor(ui): migrate post-generation flow to reducer (eefb237)
- refactor(ui): introduce immutable ui state snapshots (4f53a29)
- test(ui): lock state-machine migration contracts (ec10f3a)
- test(ui): favor semantic assertions for generation flow (0edd0dc)
- ci(release): smoke test native binaries before upload (50f4118)
- test(architecture): enforce headless dependency guardrails (37f9f39)
- test(ui): harden post-generation label navigation (5515e9a)
- feat(ui): reorder export action and bound footer lines (7c3d910)
- feat(ui): surface resolved target and focused field diagnostics (9e1a54b)
- test(ui): assert absolute output path with stable prefix (db3b3c1)
- style(test): apply spotless formatting (779a508)
- test(ui): make plan path assertion OS-agnostic (6c55690)
- test(util): cover remaining OutputPathResolver branches (f951045)
- fix(ui): resolve output directory to absolute path (e41bc0e)
- chore(release): prepare next iteration 0.4.1-SNAPSHOT (ddef97c)


## [0.4.0](https://github.com/ayagmar/quarkus-forge/compare/v0.3.2...v0.4.0) (2026-03-04)

### Changed

- fix(ci): ignore test sources in Codecov patch coverage (5e3b74d)
- test(ui): add lowercase invalid-nav coverage and fixture helper (7c173f6)
- test(ui): improve coverage for overlay and filter flows (dc4e096)
- fix(ui): address review findings and remove duplicated logic (07d7cd2)
- docs(ui): refresh tui docs for tamboui-native widgets (7cc0280)
- feat(ui): improve tui input feedback and validation navigation (ecbcaee)
- chore(release): prepare next iteration 0.3.3-SNAPSHOT (3e14413)


## [0.3.2](https://github.com/ayagmar/quarkus-forge/compare/v0.3.1...v0.3.2) (2026-03-03)

### Changed

- fix(api): preserve extension descriptions in catalog cache (#31) (9c09233)
- chore(release): prepare next iteration 0.3.2-SNAPSHOT (c092c39)


## [0.3.1](https://github.com/ayagmar/quarkus-forge/compare/v0.3.0...v0.3.1) (2026-03-03)

### Changed

- fix(ui): prevent duplicate category sections in search results (#30) (b06b463)
- chore(release): prepare next iteration 0.3.1-SNAPSHOT (933a4cb)


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
