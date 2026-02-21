# Quarkus API Client

## Endpoints
- `GET /api/extensions`: fetch extension catalog.
- `GET /api/metadata`: fetch Java/build-tool metadata.
- `GET /api/download`: generate project ZIP with encoded query parameters.

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
