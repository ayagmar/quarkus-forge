# Catalog Cache

## Scope
- Persist only metadata + extension catalog payloads.
- Single snapshot file: `~/.quarkus-forge/catalog-snapshot.json`.
- Generated ZIP payloads and Maven artifacts are never cached.

## Policy
- Schema version: `1`.
- Freshness TTL: `6h` (`fresh` when age <= 6h, `stale` otherwise).
- Size cap: `2 MiB` (oversized writes are rejected).
- Invalid schema/version/payload cache files are ignored.

## Runtime Fallback Order
1. `live`: load metadata + extension catalog from Quarkus API.
Metadata source details: `/api/streams` + `/q/openapi`.
2. `cache`: if live load fails and cache snapshot is valid.
3. `snapshot`: if live load fails and no valid cache exists.

## UI Behavior
- Catalog source is shown in the selection panel as `live`, `cache`, or `snapshot`.
- Stale cache is explicitly marked as `[stale]`.
- `Ctrl+R` retries catalog load without restarting the app.
