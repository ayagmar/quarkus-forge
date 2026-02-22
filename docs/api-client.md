# Quarkus API Client

## Endpoints
- `GET /api/extensions`: fetch extension catalog.
- `GET /api/metadata`: fetch Java/build-tool metadata.
- `GET /api/download`: generate project ZIP with encoded query parameters.
- ZIP download supports both byte-array payload usage and streaming-to-file usage.

## Catalog Cache Fallback
- Runtime catalog loading uses `live -> cache -> snapshot` fallback order.
- Cache persists metadata + extension catalog only.
- Cache snapshot schema/version is validated before use.
- TTL is `6h`, and stale cache is explicitly surfaced in UI state.
- Cache file size is capped at `2 MiB`; oversized snapshots are rejected.

## Retry Policy
- Async HTTP client based on `java.net.http.HttpClient`.
- Bounded retries with exponential backoff + jitter.
- Retries on:
  - network/timeouts
  - `429` throttling
  - `5xx` responses
- `Retry-After` is honored when present.

## Contract Safety
- JSON payloads are parsed with strict required fields.
- Extension parsing requires `id` and `name`; `shortName` is tolerant and falls back to `name`
  when missing/blank.
- Metadata contract includes compatibility matrix entries per build tool.
- Compatibility lookup is normalized once during parsing (case-insensitive key normalization), then
  resolved by direct map lookup.
- Case-colliding compatibility keys (for example `maven` and `MAVEN`) are rejected as contract
  errors to avoid ambiguous behavior.
- Missing/renamed required fields raise `ApiContractException`.
- Snapshot contract drift test is pinned at `src/test/resources/contracts/quarkus-api-snapshot.json`.

## Generation Query Mapping
- Generation query parameters are encoded using the contract keys:
  - `g` = groupId
  - `a` = artifactId
  - `v` = version
  - `b` = build tool
  - `j` = Java version
  - `e` = comma-separated extension IDs

## Streaming ZIP Download
- `QuarkusApiClient#downloadProjectZipToFile(...)` writes the ZIP response directly to a destination
  path via `BodyHandlers.ofFile(...)`.
- Use this path for generation flows that need bounded memory behavior for large archives.
