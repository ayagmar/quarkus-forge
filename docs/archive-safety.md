# Archive Safety and Extraction

## Download Path
- Project archives are downloaded with `QuarkusApiClient#downloadProjectZipToFile(...)` using
  `HttpResponse.BodyHandlers.ofFile(...)`.
- ZIP payloads are streamed directly to a temporary file, not buffered into a full in-memory byte array.

## Extraction Hardening
- `SafeZipExtractor` validates ZIP central directory metadata before extraction.
- The extractor rejects:
  - path traversal / zip-slip entries (`..`, absolute Unix or drive-prefixed paths)
  - duplicate entries
  - symlink entries (detected from Unix mode metadata)
  - suspicious Unix mode metadata (unknown file type or setuid/setgid/sticky bits)
  - ZIP64 archives (currently unsupported)

## Anti Zip-Bomb Limits
Default `ArchiveSafetyPolicy`:
- `maxEntries = 20,000`
- `maxTotalUncompressedBytes = 512 MiB`
- `maxCompressionRatio = 150.0`
- `minBytesForCompressionRatioCheck = 1 MiB`

If an archive exceeds any limit, extraction fails fast before writing to final output.

## Overwrite and Rollback
- `FAIL_IF_EXISTS`: extraction aborts if output already exists.
- `REPLACE_EXISTING`: existing output is moved to a backup path, new output is moved into place, and backup is deleted on success.
- If replacement fails after backup, previous output is restored.

## Cleanup Guarantees
- Extraction always happens in a staging directory.
- On failure, staging content is deleted.
- `ProjectArchiveService` also deletes the temporary downloaded ZIP on success, failure, and cancellation.

## Threading Boundary
- Archive extraction is explicitly offloaded to a dedicated async executor boundary after download
  completion.
- HTTP completion threads are no longer used to run disk-heavy extraction work.
