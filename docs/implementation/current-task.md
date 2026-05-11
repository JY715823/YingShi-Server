# Current Task: Legacy Preview Cleanup

## Background

Local image previews now use `preview-v2` file names and video covers use `cover-v1`. Older generated previews used names such as `media_xxx-720.jpg`. They can be removed safely as long as originals, preview-v2 files, video covers, and video sources are never touched.

## Goals

1. Clean up only legacy local preview files under `local-storage/previews`.
2. Keep original media files, preview-v2 files, cover-v1 files, and video sources untouched.
3. Make cleanup best-effort so failures do not affect server startup, upload, or media access.
4. Preserve current preview/cover regeneration behavior for old media.

## Scope

- Local storage legacy preview file matching.
- Dev startup cleanup runner.
- Contract documentation for preview/cover file lifecycle.

## Non Goals

- No full cache-management page.
- No OSS, HLS, transcoding, or object-storage migration.
- No upload center, trash, post detail, paging, or Viewer playback-control changes.

## Acceptance

1. Legacy files matching `media_xxx-<size>.jpg` under `previews` can be deleted.
2. Files containing `preview-v2` or `cover-v1` are never deleted by this cleanup.
3. Cleanup errors are swallowed and do not block the server.
4. Server `mvnw test` passes.
