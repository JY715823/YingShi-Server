# Current Task: Photo Feed Paging Step 1

## Background

The Android photo feed now needs to grow beyond full-list loading. REAL mode should be able to load the first page quickly, continue loading older media when the user reaches the bottom, and keep the existing full-feed API behavior for callers that have not opted in to pagination.

## Goals

1. Add compatible pagination support to `GET /api/media/feed`.
2. Return a next-page marker when the client requests paged data.
3. Keep the original no-query feed response behavior unchanged.
4. Cover the first page and next-page behavior with backend tests.

## Scope

- `GET /api/media/feed?cursor=...&pageSize=...`
- Cursor metadata in the standard API envelope.
- Feed ordering by display time and media id.
- Conservative in-memory paging over the existing sorted feed implementation.

## Non Goals

- No database-level cursor query in this step.
- No changes to media upload, recovery scan, file streaming, comments, posts, or trash APIs.
- No cross-page target-media positioning logic.
- No object storage, transcoding, HLS, or storage layout changes.

## Acceptance

1. `GET /api/media/feed` without pagination parameters still returns the original full list.
2. `GET /api/media/feed?pageSize=...` returns only the requested page size.
3. Paged responses include `hasMore` and `nextCursor`.
4. A request with `cursor` returns the following page.
5. `mvnw test` passes.
