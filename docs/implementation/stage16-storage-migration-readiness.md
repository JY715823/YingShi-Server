# Stage 16 Storage Migration Readiness

## Scope

This document records the current backend storage shape and the migration rules for the next Stage 16 steps.

Stage 16 step 2 adds the first Server storage abstraction, still backed by local files. It does not connect MinIO, does not switch to PostgreSQL, does not add Docker Compose, and does not rewrite upload or media streaming.

## Current Runtime

- Spring Boot backend with Spring Web MVC, Spring Data JPA, validation, JWT auth, and springdoc in dev.
- Default profile is `dev`.
- Dev database is H2 file mode: `jdbc:h2:file:./local-storage/dev-db/yingshi;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE`.
- PostgreSQL driver is already present, but the current dev profile is still H2.
- Server storage provider defaults to `app.storage.provider=local`.
- Server storage bucket defaults to `app.storage.bucket=yingshi-media`.
- Server storage root is configured by `app.storage.local-root`, currently `local-storage`.
- Health check is `GET /api/health` and returns `status`, `application`, `activeProfiles`, and `serverTime`.

## Main Entities

- `UserEntity`: account, password hash, display name, avatar URL, default shared library.
- `SharedLibraryEntity` and `SharedLibraryMemberEntity`: private shared library and membership.
- `AlbumEntity`: album title/subtitle and `coverMediaId`.
- `PostEntity`: title, summary, display/event time fields, `coverMediaId`, soft delete timestamp.
- `PostAlbumEntity`: post-album relation.
- `PostMediaEntity`: post-media relation and sort order.
- `CommentEntity`: separate post/media comment target fields.
- `TrashItemEntity`: trash type/state, source ids, related ids, snapshot JSON, delete/remove/restore timestamps.
- `UploadTaskEntity`: temporary upload metadata and completion state.
- `MediaEntity`: current media metadata and storage pointers.

Current `MediaEntity` metadata fields:

- `id`
- `libraryId`
- `mediaType`
- `url`
- `previewUrl`
- `originalUrl`
- `videoUrl`
- `coverUrl`
- `mimeType`
- `sizeBytes`
- `width`
- `height`
- `aspectRatio`
- `durationMillis`
- `displayTimeMillis`
- `capturedAtMillis`
- `importedAtMillis`
- `displayTimeSource`
- `storagePath`
- `storageProvider`
- `bucket`
- `originalObjectKey`
- `previewObjectKey`
- `coverObjectKey`
- `checksum`
- `sourceFingerprint`
- `deletedAt`

Current migration concern: `url`, `previewUrl`, `originalUrl`, `videoUrl`, `coverUrl`, and `storagePath` still exist for compatibility and mix API URL shape with local-storage implementation details. Stage 16 step 2 adds nullable object-storage transition fields so old H2/dev rows continue to start and new media can write object keys without changing DTOs.

## Stage 16 Step 2 Implementation Status

Added Server classes:

- `ObjectStorageService`: provider-neutral storage interface with `put`, `get`, `exists`, `delete`, and `getMetadata`.
- `LocalObjectStorageService`: local provider implementation backed by `app.storage.local-root`.
- `ObjectMetadata`: object key, content type, size, checksum, and last modified metadata.
- `StoredObject`: returned object resource plus metadata.

Current wrapped paths:

- Original upload writes now go through `ObjectStorageService.put(...)`.
- Original file reads for `/api/media/files/{mediaId}` now go through `ObjectStorageService.get(...)` when the stored path is a relative local object key.
- Preview and cover reads go through `ObjectStorageService.get(...)` after the existing local generator ensures the file exists.
- Trash purge deletes original and derived preview/cover files through `ObjectStorageService.delete(...)` when paths are under the local root.
- New uploaded media rows write `storageProvider`, `bucket`, `originalObjectKey`, and `checksum`.
- New image preview warmup writes `previewObjectKey` when generation succeeds.
- New video media writes the expected deterministic `coverObjectKey`; actual video cover file generation remains lazy through the current media file endpoint.
- Dev originals recovery and dev test import populate provider/bucket/object key/checksum where possible.

Still intentionally local/path based in this pass:

- Image preview generation still uses `Path`, `ImageIO`, and EXIF orientation handling.
- Video cover generation still uses a local `ffmpeg` process and local file paths.
- Dev scans still walk `local-storage/originals` and `local-storage/test`.
- Legacy preview cleanup still scans the local preview directory.
- `storagePath` remains required and is still the fallback for old data.

These are the next migration points when object storage becomes remote. For MinIO/OSS, generation code will need either a temporary local work file or a provider-neutral working-file adapter before writing the generated preview/cover back through `ObjectStorageService.put(...)`.

## Current Local Storage Layout

The local root is resolved from `app.storage.local-root`.

Original uploads:

- Path: `local-storage/originals/yyyy/MM/{mediaId}.{ext}`
- Month bucket comes from `displayTimeMillis`.
- Extension comes from sanitized original file name, with `.jpg` for images and `.mp4` for videos as fallback.

Generated image preview:

- Path: `local-storage/previews/yyyy/MM/{mediaId}-preview-v2-1280.jpg`
- Generated on upload warmup or on first `variant=preview` request.
- JPEG quality is currently 90%.
- EXIF orientation is applied during preview generation.

Generated video cover:

- Path: `local-storage/previews/yyyy/MM/{mediaId}-cover-v1-1280.jpg`
- Generated by local `ffmpeg` if available.
- The server tries a frame at 1 second, then 0 seconds.
- Generation is best effort; clients must tolerate missing covers.

Seed and dev import media:

- Seed/demo storage paths currently include `local-storage/test/photos`, `local-storage/test/long`, and `local-storage/test/videos`.
- Dev test import scans `local-storage/test/...` when `yingshi.dev.test-import.enabled=true`.
- Seed media rows also store backend API URL fields and relative `storagePath`.

Current cleanup:

- Dev startup may delete legacy preview files matching `media_xxx-720.jpg`.
- Trash purge for `mediaSystemDeleted` deletes the original `storagePath` and derived `preview-v2` / `cover-v1` files owned by that media cache key.
- Stage 16 step 2 routes those local deletes through `ObjectStorageService.delete(...)` for keys under the configured local root.

## Current Server API Surface

Auth:

- `POST /api/auth/login`
- `GET /api/auth/me`
- `POST /api/auth/logout`

Health:

- `GET /api/health`

Albums:

- `GET /api/albums`
- `GET /api/albums/{albumId}/posts`

Media:

- `GET /api/media/feed`
- `GET /api/media/feed?cursor={cursor}&pageSize={pageSize}`
- `GET /api/media/files/{mediaId}`
- `GET /api/media/files/{mediaId}?variant=original`
- `GET /api/media/files/{mediaId}?variant=preview`
- `GET /api/media/files/{mediaId}?variant=cover`
- `DELETE /api/media/{mediaId}`

Uploads:

- `POST /api/uploads/token`
- `POST /api/uploads/{uploadId}/file` with multipart field name `file`

Posts:

- `GET /api/posts/{postId}`
- `POST /api/posts`
- `PATCH /api/posts/{postId}`
- `PATCH /api/posts/{postId}/cover`
- `PATCH /api/posts/{postId}/media-order`
- `POST /api/posts/{postId}/media`
- `DELETE /api/posts/{postId}`
- `DELETE /api/posts/{postId}/media/{mediaId}?deleteMode=directory|system`

Comments:

- `GET /api/posts/{postId}/comments`
- `POST /api/posts/{postId}/comments`
- `GET /api/media/{mediaId}/comments`
- `POST /api/media/{mediaId}/comments`
- `PATCH /api/comments/{commentId}`
- `DELETE /api/comments/{commentId}`

Trash:

- `GET /api/trash/items`
- `GET /api/trash/items?itemType={itemType}`
- `GET /api/trash/items/{trashItemId}`
- `POST /api/trash/items/{trashItemId}/restore`
- `POST /api/trash/items/{trashItemId}/remove`
- `POST /api/trash/items/{trashItemId}/purge`
- `POST /api/trash/items/{trashItemId}/undo-remove`
- `GET /api/trash/pending-cleanup`

## Current Risk Findings

- Database currently stores backend API URL fields in `media.url`, `media.previewUrl`, `media.originalUrl`, `media.videoUrl`, and `media.coverUrl`.
- Database currently stores `media.storagePath`, a local-storage relative path, as the only real file locator.
- `LocalMediaStorageService.resolveStoragePath` accepts absolute paths, then delete/load safety checks only partially constrain later operations. Future object-key migration should remove absolute-path support from stored media references.
- DTOs currently expose backend API paths, not disk paths. This is good for Android compatibility.
- Android REAL resolves relative media URLs against its configured backend `baseUrl`. It does not need local-storage paths.
- Android REAL trash preview reconstructs `/api/media/files/{mediaId}?variant=...` from media ids for deleted media preview. This still targets backend API and is acceptable, but it is a compatibility dependency to preserve during storage migration.
- No MinIO, OSS, bucket name, object storage endpoint, or object storage access key is currently present in Android code.

## Future Media Object Field Standard

Future media persistence should use object metadata fields rather than stable public URLs or local file paths:

- `provider`: storage provider name, for example `local`, `minio`, or `oss`.
- `bucket`: logical object bucket/container name.
- `objectKey`: canonical object key for the main/original media object when one key is enough.
- `originalObjectKey`: original media object key.
- `previewObjectKey`: generated preview object key.
- `coverObjectKey`: generated video cover/poster object key.
- `checksum`: content checksum, preferably SHA-256 or provider-neutral checksum metadata.
- `sizeBytes`: object size in bytes.
- `mimeType`: media MIME type.
- `width`: pixel width when known.
- `height`: pixel height when known.
- `durationMs`: media duration in milliseconds when known.

Naming note: current Java code uses `durationMillis`; the future storage field standard names this `durationMs`. API compatibility can keep `durationMillis` until a separate DTO versioning decision is made.

Database rule:

- Store object keys and metadata, not `localhost`, LAN IP, MinIO endpoint URLs, OSS URLs, signed URLs, or Cloudflare Tunnel URLs.
- Public delivery URLs are derived at request time by backend controllers/services.
- Android must receive backend API URLs or relative backend API paths, not storage-provider URLs.
- During the transition, keep legacy URL columns and `storagePath` only for compatibility. New provider fields should use relative object keys such as `originals/2026/04/media_xxx.jpg`, never absolute paths or object-store HTTP URLs.

Android rule:

- Android only calls backend APIs.
- Android must not connect directly to MinIO or OSS.
- Android must not persist object storage access keys or secret keys.
- Android may store the backend `baseUrl` for diagnostics and local/dev switching.
- Android may cache backend media responses locally through HTTP/image/video cache, but cache keys should be based on backend API URLs.

## ObjectStorageService Direction

Stage 16 step 2 introduces an `ObjectStorageService` abstraction before connecting MinIO or OSS.

Current first interface:

- `put(objectKey, contentType, sizeBytes, inputStream)`
- `get(objectKey)` returning a backend `Resource` plus lightweight metadata
- `delete(objectKey)`
- `exists(objectKey)`
- `getMetadata(objectKey)` returning metadata when available
- `multipart` initiation/upload/complete hooks are reserved for a later large-file pass

Provider implementations should be hidden behind the interface:

- `local`: stores objects under the current local root using object keys.
- `minio`: S3-compatible local/prod-like test provider.
- `oss`: future Aliyun OSS implementation.

Only use common object storage capabilities:

- put
- get
- delete
- multipart upload

Do not depend on MinIO-only admin APIs, bucket notifications, lifecycle shortcuts, browser URLs, or OSS-only media processing features in the business layer. If provider-specific optimizations are added later, keep them optional and outside the core media contract.

## Next S3-Compatible Provider Shape

When Docker Compose + PostgreSQL + MinIO is introduced, add a separate `S3ObjectStorageService` implementation rather than changing controllers or Android contracts.

Recommended shape:

- Bind config from `app.storage.provider=minio`, `app.storage.bucket`, `app.storage.endpoint`, `app.storage.region`, `app.storage.access-key`, and `app.storage.secret-key`.
- Implement the same `ObjectStorageService` methods with S3-compatible SDK calls.
- Keep object keys identical to the current relative local keys where practical.
- Continue returning backend delivery URLs from DTO mappers.
- Keep `/api/media/files/{mediaId}?variant=original|preview|cover` as the Android-facing binary endpoint.
- Use multipart only behind the storage interface when upload size requires it.
- Do not expose MinIO browser URLs, presigned provider URLs, bucket names, or object keys to Android in this stage.

For Aliyun OSS later, add an OSS implementation behind the same interface and keep business services working with bucket/key/provider metadata only.

## API Compatibility Principles

Storage migration must preserve these API contracts:

- Android continues to fetch media through `/api/media/files/{mediaId}`.
- Variant access remains backend-owned: `/api/media/files/{mediaId}?variant=original|preview|cover`.
- Android must not build `http://minio...`, `https://oss...`, or signed object URLs.
- `MediaDto` keeps compatible URL fields while migration is in progress:
  - image preview: `previewUrl` or `thumbnailUrl`
  - canonical media: `url` or `mediaUrl`
  - image original: `originalUrl`
  - video playback: `videoUrl`
  - video poster: `coverUrl`
- Relative backend API paths remain valid. Android joins them with its configured backend `baseUrl`.
- Absolute backend URLs may be returned later, but they must still point to backend delivery endpoints, not object-storage endpoints.
- `POST /api/uploads/token` and `POST /api/uploads/{uploadId}/file` remain the Stage 16 step-1 upload contract.
- Post create/add-media continues to use returned `mediaId`, not object keys.

## Configuration Layering Plan

Profiles:

- `local`: current local development, H2 or lightweight local DB, local object store implementation.
- `docker`: PostgreSQL + MinIO cloud-like local environment.
- `prod`: future ECS + OSS deployment.

Future environment variables:

- `STORAGE_PROVIDER`
- `STORAGE_BUCKET`
- `STORAGE_ENDPOINT`
- `STORAGE_REGION`
- `STORAGE_ACCESS_KEY`
- `STORAGE_SECRET_KEY`
- `STORAGE_LOCAL_ROOT`
- `SPRING_DATASOURCE_URL`
- `SPRING_DATASOURCE_USERNAME`
- `SPRING_DATASOURCE_PASSWORD`
- `SPRING_JPA_HIBERNATE_DDL_AUTO`

Mapping suggestion:

- `app.storage.provider=${STORAGE_PROVIDER:local}`
- `app.storage.bucket=${STORAGE_BUCKET:yingshi-media-local}`
- `app.storage.endpoint=${STORAGE_ENDPOINT:}`
- `app.storage.region=${STORAGE_REGION:}`
- `app.storage.access-key=${STORAGE_ACCESS_KEY:}`
- `app.storage.secret-key=${STORAGE_SECRET_KEY:}`
- `app.storage.local-root=${STORAGE_LOCAL_ROOT:local-storage}`

Current Stage 16 step 2 config:

- `app.storage.provider=${STORAGE_PROVIDER:local}`
- `app.storage.bucket=${STORAGE_BUCKET:yingshi-media}`
- `app.storage.local-root=${STORAGE_LOCAL_ROOT:local-storage}`

Remote endpoint, region, and secret config names remain reserved for the MinIO/OSS pass and must not contain committed real credentials.

Secrets rule:

- Do not commit `.env` files.
- Do not commit object storage data directories.
- Do not commit PostgreSQL data directories.
- Do not commit generated local media.

## Suggested Stage 16 Step 2

1. Done: add `ObjectStorageService`, `StoredObject`, and `ObjectMetadata` without changing controllers.
2. Done: implement a `local` provider backed by the current local root.
3. Done: add nullable object-key fields to `MediaEntity` while keeping current fields temporarily.
4. Partially done: new upload/recovery/import rows write object fields; old rows continue using `storagePath` fallback.
5. Partially done: original put/get/delete and generated file read/delete are wrapped; preview/cover generation itself remains local path based.
6. Done: keep `/api/media/files/{mediaId}` and upload DTOs unchanged during the first abstraction pass.
7. Next: add docker profile, PostgreSQL, and MinIO in a separate Stage 16 step.

## PostgreSQL Schema Management Note

The current dev profile can still rely on H2 plus Hibernate `ddl-auto=update` for quick local bootstrap. When PostgreSQL is introduced, schema changes should be managed by committed migration scripts, such as Flyway, Liquibase, or a documented SQL migration directory.

Navicat or other GUI-created tables can be useful for inspection, but should not become the project source of truth. The repository should contain the migration history for fields such as `storage_provider`, `bucket`, `original_object_key`, `preview_object_key`, `cover_object_key`, and `checksum`.
