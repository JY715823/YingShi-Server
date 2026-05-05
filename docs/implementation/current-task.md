# Current Task: Stage 12.8 - Shared Library, Time Fields, Local Storage Layout

## Background

YingShi is now treated as a private two-person shared app, not a multi-space product. The old public-facing `spaceId` concept is removed from the contract and replaced by `libraryId`, which represents the one shared library used by both seed users.

Media and posts also need two separate time ideas:

- intrinsic time: when the media was captured, or when the post/memory happened
- display time: where the item appears in the app timeline

## Scope

1. Replace backend space domain naming with shared-library naming.
2. Keep request scoping through the authenticated `libraryId`.
3. Add media time metadata: `capturedAtMillis`, `importedAtMillis`, `displayTimeSource`.
4. Add post event time metadata: `eventStartedAtMillis`, `eventEndedAtMillis`, `displayTimeSource`.
5. Reorganize local storage into stable type/year/month buckets.
6. Keep FAKE and REAL architecture intact.

## Local Storage Layout

```text
local-storage/
  originals/yyyy/MM/{mediaId}.{ext}
  previews/yyyy/MM/{mediaId}-720.jpg
  test/photos|long|videos/...
  tmp/uploads/...
  videos/posters/...
```

The old `local-storage/space_demo_shared` and `local-storage/_derived` directories are retired.

## Acceptance

1. Auth responses expose `libraryId` and `libraryDisplayName`.
2. Uploads create media under `originals/yyyy/MM` and generated previews under `previews/yyyy/MM`.
3. Import-only media remains valid with empty `postIds`.
4. Media and post DTOs expose the new time fields without breaking existing clients.
5. `mvnw test` passes.
