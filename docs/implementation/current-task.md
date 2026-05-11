# Current Task: Trash Restore And Permanent Delete Closure

## Background

The trash API already supports soft delete and restore. This pass adds explicit permanent deletion for in-trash items and safe local-storage cleanup for globally deleted media.

## Goals

1. Restore endpoints continue to restore posts, removed post-media relations, and globally deleted media.
2. Add a permanent delete endpoint for in-trash items.
3. Permanently deleting `mediaSystemDeleted` removes the media record and that media's owned original / `preview-v2` / `cover-v1` local-storage files.
4. Permanent delete failures must not fake success; the trash item remains if cleanup cannot be completed.
5. Keep system gallery semantics out of Server local-storage deletion.

## Scope

- `TrashController` / `TrashService` purge flow.
- `LocalMediaStorageService` safe file deletion helpers.
- Trash API contract and backend tests.

## Non Goals

- No OSS/HLS/transcoding.
- No Android cache cleanup changes.
- No scheduled purge worker.

## Acceptance

1. `POST /api/trash/items/{trashItemId}/purge` works for in-trash items.
2. `mediaSystemDeleted` purge removes the media DB row and owned local files.
3. Non-media purge does not delete unrelated media files.
4. Server `mvnw test` passes.
