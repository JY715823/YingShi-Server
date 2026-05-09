# Current Task: Media File Range Streaming

## Background

App remote videos are served through `GET /api/media/files/{mediaId}`. Smooth network playback and seek need standard HTTP Range support so the Android player can request byte ranges instead of waiting on full-file reads after scrubbing.

## Scope

1. Keep existing media metadata and storage layout unchanged.
2. Add `Accept-Ranges: bytes` to media file responses.
3. Support single-range requests on `/api/media/files/{mediaId}` with `206 Partial Content`.
4. Preserve auth, cache control, content type, last-modified, and preview/original variant behavior.
5. Add backend test coverage for partial media file responses.

## Non Goals

- No transcoding.
- No HLS / DASH packaging.
- No object storage migration.
- No upload flow or post/trash/comment API changes.

## Acceptance

1. Normal file requests still return `200 OK`.
2. Range requests return `206 Partial Content` with `Content-Range`.
3. Existing media API tests pass.
4. `mvnw test` passes.
