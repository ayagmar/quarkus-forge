# Metadata Freshness Strategy

## Context
- `MetadataCompatibilityContext.loadDefault()` currently uses only the bundled metadata snapshot.
- CLI/TUI compatibility validation is deterministic and offline-friendly, but newer valid platform
  combinations can be rejected until a new application release updates the snapshot.

## Options

### 1. Snapshot-only (current behavior)
- Pros:
  - deterministic startup and validation behavior
  - no network dependency during startup
  - simplest failure model
- Cons:
  - stale compatibility data between releases
  - valid new combinations can be blocked unnecessarily

### 2. Live metadata only
- Pros:
  - freshest compatibility matrix at runtime
  - no stale snapshot rejection window
- Cons:
  - startup depends on network/API health
  - offline runs fail or need separate bypass logic
  - higher complexity in failure handling and tests

### 3. Hybrid startup refresh with snapshot fallback (recommended)
- Behavior:
  - try one bounded live metadata fetch at startup
  - if fetch/parsing fails, fall back to bundled snapshot
  - keep validation deterministic after startup by using the selected context for the full run
- Pros:
  - improves freshness without breaking offline behavior
  - preserves deterministic validation semantics per run
  - keeps change scope limited to metadata-loading boundary
- Cons:
  - startup may incur one network call
  - requires explicit timeout and fallback telemetry messaging

## Recommendation
- Adopt option 3 (hybrid startup refresh with snapshot fallback).
- Do not add background periodic refresh yet; it adds state churn and concurrency complexity before
  there is a confirmed product requirement.

## Minimal Next Implementation Slice
1. Add a metadata loader boundary in CLI startup that attempts `QuarkusApiClient.fetchMetadata()`
   with a short timeout.
2. Build `MetadataCompatibilityContext` from live metadata when successful, otherwise fall back to
   `MetadataCompatibilityContext.loadDefault()`.
3. Surface one status line in startup logs/footer indicating `live metadata` vs `snapshot fallback`
   source.
4. Add tests for:
   - live metadata success path
   - live metadata failure fallback path
   - deterministic validation behavior after context selection
