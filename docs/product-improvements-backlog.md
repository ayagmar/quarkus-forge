# Product Improvements Backlog

## UX

- Add a pre-generate "Safe Generate" preflight summary (output conflict, invalid fields, missing tools).
- Add first-run onboarding flow (short guided path, dismissable and remembered).
- Add progressive UI density modes (`compact`, `normal`, `verbose`).
- Add a short-lived "what changed" contextual feedback line after key actions.
- Add undo for recent extension/filter actions (`u`).

## UI / TUI

- Improve extension relevance ranking with deterministic tie-breakers.
- Keep a curated golden-render suite and move most behavior tests to semantic state assertions.
- Expand post-generation menu prechecks (show unavailable actions with actionable reason).
- Add better affordances for long text in narrow terminals (consistent clipping/ellipsis policy).
- Add keyboard shortcut discoverability panel by context.

## CLI

- Add `--print-plan` for resolved generation plan without generating.
- Add `--log-format text|json` for diagnostics output.
- Add shell completions (`bash`, `zsh`, `fish`, `powershell`).
- Add `--non-interactive` parity mode for full binary to force headless behavior.

## Reliability / Delivery

- Expand native release smoke tests to include one minimal dry-run path where applicable.
- Publish checksum files (`sha256`) and verification snippets with releases.
- Add architecture-fitness CI summaries (ArchUnit + contract test counts).
- Add latency/performance sanity checks for TUI event loop/render cycle.

## Docs

- Add "Which binary should I use?" decision table in README.
- Add symptom-driven troubleshooting index ("If you see X, do Y").
- Keep ADR-style short decision records for major architecture changes.
- Add maintainer checklists for architecture-sensitive areas (routing, reducer, effects, native release).

## Landing Page

- Add interactive terminal demo (asciinema/recorded walkthrough).
- Add install selector by OS + usage mode (TUI/headless/CI).
- Surface trust signals prominently (release freshness, checksums, CI/native smoke).
