# Stage 16 Storage Migration Readiness

## Scope

This document records the current backend storage shape and the migration rules for the next Stage 16 steps.

Stage 16 step 2 added the first Server storage abstraction, still backed by local files.

Stage 16 step 3 adds a local Docker cloudlike environment and an S3-compatible storage provider for MinIO smoke testing. Default local development still uses H2 plus `local-storage`; Android REAL contracts remain unchanged.

Stage 16 step 4 tightens media object field rules and compatibility: new local and S3-backed media records use relative object keys, lazy preview/cover reads backfill generated object keys where possible, and lightweight diagnostics/tests check for URL-shaped object keys.

Stage 16 step 5 adds the Cloudflare Tunnel quasi-online access plan. It exposes only the backend API hostname, keeps Android behind Server APIs, and keeps PostgreSQL, MinIO API, and MinIO Console private/local.

Stage 16 step 6 adds the cloudlike data-safety layer for the next 1-2 months of local development: PostgreSQL/MinIO backup commands, disaster recovery guidance, a read-only object audit script, and a narrow backend smoke script. It does not change Android contracts or the upload main path.

Stage 16 step 7 retires old H2/local-storage test data as non-authoritative. Old local media is not migrated by default because it was only test data; the project now treats PostgreSQL + MinIO as the development source of truth.

Stage 16 step 8 adds storage-level ranged reads for large media. `/api/media/files/{mediaId}?variant=...` keeps the same Android contract, while S3/MinIO range requests now ask object storage for the requested byte slice instead of opening the full object and skipping bytes in the backend.

Stage 16 step 9 introduces Flyway-backed PostgreSQL schema ownership for docker and docker-local profiles. H2/dev remains `ddl-auto=update` for quick local bootstrap, while PostgreSQL now has a committed initial schema migration and Hibernate validates the schema by default.

Stage 16 step 11 adds a daily cloudlike check entry point. `scripts/stage16-cloudlike-check.ps1` verifies Docker PostgreSQL/MinIO, Server health, Flyway history, object audit, and local-storage retirement dry-run, with optional smoke upload and Maven tests. Keep status updates in conversation summaries rather than in a separate progress-board document.

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

Current field responsibility:

- Legacy API URL fields: `url`, `previewUrl`, `originalUrl`, `videoUrl`, and `coverUrl`. These stay temporarily for DTO compatibility and must point to backend API routes, not MinIO/OSS.
- Legacy storage locator: `storagePath`. This stays temporarily for old H2/dev rows and local recovery flows.
- Object storage fields: `storageProvider`, `bucket`, `originalObjectKey`, `previewObjectKey`, `coverObjectKey`, and `checksum`. These are the migration-safe fields.
- Media metadata fields: `sizeBytes`, `mimeType`, `width`, `height`, and `durationMillis`. The future standard name `durationMs` maps to the current Java field `durationMillis`.

Current migration concern: legacy URL fields and `storagePath` still exist for compatibility. New logic should prefer object keys, infer them from legacy fields only when they are safe relative keys, and never store MinIO, OSS, Cloudflare Tunnel, LAN, or localhost URLs in object-key columns.

## Stage 16 Step 2 Implementation Status

Added Server classes:

- `ObjectStorageService`: provider-neutral storage interface with `put`, `get`, `getRange`, `exists`, `delete`, and `getMetadata`.
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

## Stage 16 Step 3 Implementation Status

Added Server files:

- `docker-compose.yml`: starts PostgreSQL, MinIO, a bucket init job, and the Server container.
- `Dockerfile`: builds the Spring Boot app image.
- `.dockerignore`: keeps local media, data volumes, build output, and secrets out of the image context.
- `.env.example`: example-only local variables for PostgreSQL, MinIO, Spring, and storage config.
- `src/main/resources/application-docker.yml`: Spring `docker` profile using PostgreSQL and S3-compatible storage.
- `S3ObjectStorageService`: S3-compatible `ObjectStorageService` implementation for `app.storage.provider=s3` or `minio`.
- `docs/implementation/stage16-docker-cloudlike-env.md`: local startup and verification guide.

Docker profile behavior:

- PostgreSQL datasource is configured from `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, and `SPRING_DATASOURCE_PASSWORD`.
- Storage config is read from `STORAGE_PROVIDER`, `STORAGE_BUCKET`, `STORAGE_ENDPOINT`, `STORAGE_REGION`, `STORAGE_ACCESS_KEY`, and `STORAGE_SECRET_KEY`.
- Default docker storage provider is `s3`, endpoint `http://minio:9000`, bucket `yingshi-media`.
- Empty docker databases are bootstrapped with the existing demo auth/library seed only. Dev media content seed, local test import, originals recovery, and legacy preview cleanup do not run in docker profile.
- Current PostgreSQL schema strategy is Flyway plus Hibernate validation. `src/main/resources/db/migration/postgresql/V1__initial_schema.sql` owns the initial table structure, and docker/docker-local default `spring.jpa.hibernate.ddl-auto` is `validate`.

Current S3-compatible media support:

- New original uploads are written through `ObjectStorageService.put(...)` to MinIO/S3 when `app.storage.provider=s3` or `minio`.
- Media rows continue to store `storageProvider`, `bucket`, `originalObjectKey`, `previewObjectKey`, `coverObjectKey`, and `checksum` where available.
- Object keys stay relative, such as `originals/2026/04/media_xxx.jpg`; they must not include MinIO endpoints, localhost URLs, OSS URLs, or Cloudflare Tunnel URLs.
- Original reads for `/api/media/files/{mediaId}?variant=original` stream through the backend API from object storage.
- Image preview generation in remote mode downloads the original to a temporary local file, generates the JPEG preview, writes it back through `ObjectStorageService.put(...)`, and serves it through the same backend API path.
- Video cover generation follows the same temporary-file pattern when `ffmpeg` is available to the Server runtime.
- Trash purge can delete original, preview, and cover objects through the storage abstraction for remote provider rows.

Known Step 3 limits:

- The Docker image currently does not install `ffmpeg`; remote video cover generation may fail in docker until a later image pass adds it. Original video playback still uses the backend file endpoint.
- HTTP Range for S3-backed originals is now backed by native S3 ranged reads through `ObjectStorageService.getRange(...)`.
- Direct-to-object-storage upload, multipart upload, presigned URLs, ACL rules, MinIO admin APIs, and OSS media processing are intentionally not part of this pass.
- Existing legacy fields (`url`, `previewUrl`, `originalUrl`, `videoUrl`, `coverUrl`, `storagePath`) remain for compatibility.

## Stage 16 Step 4 Implementation Status

Added Server classes/tests:

- `ObjectKeyPolicy`: provider-neutral relative object-key validation and URL-shaped value detection.
- `MediaStorageFieldService`: small helper for filling missing storage fields, normalizing provider names, deriving safe object keys from legacy fields, marking generated preview/cover keys, and diagnosing object existence.
- `ObjectKeyPolicyTests` and `MediaStorageFieldServiceTests`: focused checks for relative key rules, URL rejection, provider normalization, and old-field inference.

Updated behavior:

- New local uploads still write originals through `ObjectStorageService` and persist `storageProvider=local`, `bucket`, `originalObjectKey`, `checksum`, `sizeBytes`, and `mimeType`.
- New S3/MinIO uploads persist `storageProvider=s3`, `bucket`, `originalObjectKey`, `checksum` when available, `sizeBytes`, and `mimeType`. The `minio` config value is accepted as an S3-compatible provider alias, but rows should use `s3`.
- `S3ObjectStorageService.provider()` now returns `s3`, keeping provider values stable across MinIO and future S3-compatible test environments.
- Object key validation rejects URL-shaped values such as `http://...`, `https://...`, `s3://...`, `oss://...`, `file://...`, and Windows absolute paths.
- `/api/media/files/{mediaId}?variant=...` now fills missing provider/bucket/original object fields on read when the values can be safely inferred from existing relative fields.
- Lazy image preview reads mark `previewObjectKey` after successful preview generation.
- Lazy video cover reads mark `coverObjectKey` after successful cover generation.
- `ContentMapper` can still produce backend media URLs when `storagePath` is absent but `originalObjectKey` is available.

Compatibility rules:

- Old rows with only `storagePath` still read through the backend file endpoint.
- Old rows with missing object keys can be gradually filled by upload/recovery/import/read paths.
- Full URLs in legacy API fields are not copied into object-key fields.
- If a row has only URL-shaped legacy values and no safe relative key, it remains unreadable by storage until a manual backfill can map it to a real object.

Lightweight diagnostics:

- `MediaStorageFieldService.diagnose(media)` returns whether the original object key is missing, whether any object key looks like a URL, and whether the current provider can find the object.
- For PostgreSQL inspection, useful read-only checks are:

```sql
select id, storage_provider, bucket, original_object_key, preview_object_key, cover_object_key
from media
where original_object_key is null
   or original_object_key like 'http://%'
   or original_object_key like 'https://%'
   or original_object_key like 's3://%'
   or original_object_key like 'oss://%';
```

Backfill guidance:

- Prefer a committed migration or an explicit admin/script flow for bulk updates.
- Safe automatic fill is limited to cases where `storagePath` or another legacy field is already a relative object key such as `originals/2026/04/media_xxx.jpg`.
- Do not infer object keys from public URLs unless a controlled mapping has been verified.

## Stage 16 Step 5 Cloudflare Tunnel Status

Added Server files/config:

- `docs/implementation/stage16-cloudflare-tunnel-access.md`: Cloudflare Dashboard, Windows `cloudflared`, Docker `cloudflared`, Android REAL baseUrl, smoke test, and troubleshooting guide.
- `docker-compose.cloudflare.yml`: optional Cloudflare connector for the full Docker Compose stack.
- `.env.example`: example-only `CLOUDFLARE_TUNNEL_TOKEN` placeholder.
- `application-docker.yml` and `application-docker-local.yml`: `server.forward-headers-strategy=framework` for proxy header awareness.
- `docker-compose.yml`: PostgreSQL and MinIO host ports are bound to `127.0.0.1` for local inspection.

Cloudflare access rules:

- Public hostname should be only the backend API, for example `https://api.your-domain.com/`.
- Windows IDEA mode maps the hostname to `http://127.0.0.1:8080`.
- Docker cloudflared mode maps the hostname to `http://server:8080`.
- Do not publish PostgreSQL, MinIO API, MinIO Console, Navicat, object storage URLs, or object storage credentials.
- Android REAL baseUrl may be set to the Cloudflare HTTPS API URL, but Android still calls only backend API paths.

## Stage 16 Step 6 Data Safety Status

Added Server files:

- `docs/implementation/stage16-cloudlike-data-safety.md`: PostgreSQL + MinIO source-of-truth, backup, restore, migration, and disaster recovery guide.
- `scripts/stage16-object-audit.ps1`: read-only PostgreSQL and MinIO object consistency audit.
- `scripts/stage16-cloudlike-smoke.ps1`: focused login/feed/upload/file-read/object-field smoke script for local or Cloudflare Tunnel base URLs.
- `scripts/stage16-cloudlike-check.ps1`: daily cloudlike environment check that composes Docker, health, Flyway, object audit, local-storage dry-run, optional smoke, and optional Maven tests.
- `.gitignore` and `.dockerignore`: exclude `backups/` so local dumps and mirrored media are not committed or copied into the Server image.

Current data ownership:

- In `docker-local` and `docker` profiles, PostgreSQL is the media metadata source of truth.
- MinIO bucket `yingshi-media` is the media binary source of truth.
- The database must store relative `original_object_key`, `preview_object_key`, and `cover_object_key` values, not localhost, LAN, MinIO, OSS, or Cloudflare URLs.
- Android continues to use only Server APIs and does not store object keys or object storage credentials.

Backup commands are documented in `stage16-cloudlike-data-safety.md`:

- PostgreSQL: `pg_dump --format=custom` copied to `backups/yingshi-postgres.dump`.
- MinIO: `minio/mc mirror` copied to `backups/minio-yingshi-media`.

Read-only migration check:

```powershell
.\scripts\stage16-object-audit.ps1
```

The audit checks missing original keys, URL-shaped object-key columns, and missing MinIO objects referenced by media rows. It makes no data changes.

Cloudlike API smoke:

```powershell
.\scripts\stage16-cloudlike-smoke.ps1 -BaseUrl http://127.0.0.1:8080
.\scripts\stage16-cloudlike-smoke.ps1 -BaseUrl https://your-temporary.trycloudflare.com
```

The smoke script creates one tiny test image upload. It verifies health, auth, feed, upload token, multipart upload, original read, preview read, PostgreSQL object fields, and MinIO object existence when the row uses the S3-compatible provider.

Daily cloudlike check:

```powershell
.\scripts\stage16-cloudlike-check.ps1
.\scripts\stage16-cloudlike-check.ps1 -RunSmoke
.\scripts\stage16-cloudlike-check.ps1 -RunSmoke -RunTests
```

The default check is non-destructive. `-RunSmoke` uploads one tiny test image, and `-RunTests` runs the full Server Maven test suite.

Legacy local-storage strategy:

- Old H2 + `local-storage` data remains legacy local development data and is currently considered disposable.
- Normal startup must not auto-migrate old H2/local-storage rows.
- If old media must be retained, add a separate dry-run-first migration tool later. It should scan old `storagePath` values, locate local originals, compute relative target keys, put objects into MinIO or OSS, update PostgreSQL object fields only with an explicit apply flag, and never delete old local files in the first migration.

Deferred hardening:

- Docker `ffmpeg` strategy should be added when video cover generation becomes a required Docker smoke target.

## Stage 16 Step 8 Ranged Read Status

Updated Server classes/tests:

- `ObjectStorageService`: adds `getRange(objectKey, start, endInclusive)`.
- `S3ObjectStorageService`: sends native S3 range requests such as `bytes=0-1048575`, so MinIO/S3 returns only the requested slice.
- `LocalObjectStorageService`: supports the same range abstraction with a bounded local stream.
- `MediaFilePayload` and `MediaController`: keep the existing `/api/media/files/{mediaId}?variant=...` API while using range-capable resources for valid single HTTP `Range` requests.
- `LocalObjectStorageServiceTests`: verifies the local provider returns exactly the requested bytes.

Compatibility and limits:

- Android continues to call only backend API URLs and does not need to know whether storage is local, MinIO, or future OSS.
- Full responses still return `200 OK`; valid range responses still return `206 Partial Content`, `Accept-Ranges: bytes`, `Content-Range`, and the requested `Content-Length`.
- S3/MinIO original reads no longer require the backend to stream from byte zero and discard skipped bytes.
- Preview and cover files are small generated JPEGs; they use the same endpoint and can still be served by the backend, but the main hardening target is large original media, especially videos.
- Multi-range HTTP requests are still intentionally not implemented; the first requested range is served, matching the current endpoint behavior.
- Docker image `ffmpeg` support for video cover generation remains deferred until video-cover testing becomes important.

## Stage 16 Step 9 PostgreSQL Schema Status

Added Server files/config:

- `pom.xml`: adds Flyway core and PostgreSQL database support.
- `src/main/resources/db/migration/postgresql/V1__initial_schema.sql`: initial PostgreSQL schema for the current core tables.
- `application-docker.yml` and `application-docker-local.yml`: enable Flyway for PostgreSQL profiles and default Hibernate to `ddl-auto=validate`.
- `.env.example` and `docker-compose.yml`: expose `SPRING_FLYWAY_ENABLED`, `SPRING_FLYWAY_BASELINE_ON_MIGRATE`, and `SPRING_FLYWAY_BASELINE_VERSION` without real secrets.

Current strategy:

- New empty PostgreSQL databases are created by Flyway migration `V1__initial_schema.sql`.
- Existing docker-local databases that already have Hibernate-created tables are adopted with `baseline-on-migrate=true` and baseline version `1`. This records Flyway ownership without dropping or recreating existing data.
- Hibernate validates the mapped schema after Flyway runs. It should not silently mutate PostgreSQL schema by default.
- The `dev` profile still disables Flyway and keeps H2 with `ddl-auto=update`, because that path is only for lightweight local bootstrap and tests.

Rules for future schema changes:

- Add a new migration file such as `V2__add_xxx.sql`.
- Do not edit `V1__initial_schema.sql` after it has been used by shared or important databases.
- Use Navicat only to inspect data or run temporary diagnostics. Do not use Navicat manual table edits as the project source of truth.
- Keep media object columns as relative keys and metadata columns; do not add migrations that store MinIO, OSS, Cloudflare, localhost, or LAN URLs in object-key fields.

## Stage 16 Step 7 local-storage Retirement Status

Added Server file:

- `scripts/stage16-retire-local-storage.ps1`: safe dry-run-first cleanup helper for retired local H2/local-storage test data.

Decision:

- Old H2 + `local-storage` media is disposable test data and is not part of the migration target.
- New development media should be created in `docker-local` or `docker` profile and stored in PostgreSQL + MinIO.
- No automatic H2/local-storage migration runs during Server startup.
- The cleanup script is manual and defaults to dry-run. It requires `-Apply` to delete files.

Cleanup command:

```powershell
.\scripts\stage16-retire-local-storage.ps1
.\scripts\stage16-retire-local-storage.ps1 -Apply
```

The script deletes only retired directories under the Server repository's `local-storage` root. It does not touch PostgreSQL, MinIO, Docker volumes, `.env`, `backups/`, or the small generated `local-storage/seed` dev fixtures.

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

- `provider`: storage provider name. Current normalized values are `local` and `s3`; `minio` is only a local S3-compatible config alias.
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
- `getRange(objectKey, start, endInclusive)` for large media byte-range delivery
- `delete(objectKey)`
- `exists(objectKey)`
- `getMetadata(objectKey)` returning metadata when available
- `multipart` initiation/upload/complete hooks are reserved for a later large-file pass

Provider implementations should be hidden behind the interface:

- `local`: stores objects under the current local root using object keys.
- `s3`: S3-compatible provider. In local Docker this points to MinIO; later it can point to another S3-compatible service.
- `oss`: future Aliyun OSS implementation.

Only use common object storage capabilities:

- put
- get
- getRange
- delete
- multipart upload

Do not depend on MinIO-only admin APIs, bucket notifications, lifecycle shortcuts, browser URLs, or OSS-only media processing features in the business layer. If provider-specific optimizations are added later, keep them optional and outside the core media contract.

## Next S3-Compatible Provider Shape

Docker Compose + PostgreSQL + MinIO now uses a separate `S3ObjectStorageService` implementation rather than changing controllers or Android contracts.

Recommended shape:

- Bind config from `app.storage.provider=s3|minio`, `app.storage.bucket`, `app.storage.endpoint`, `app.storage.region`, `app.storage.access-key`, and `app.storage.secret-key`.
- Implemented current `ObjectStorageService` methods with S3-compatible SDK calls.
- Use native S3 ranged reads for `GET /api/media/files/{mediaId}` when Android or the platform media player sends a `Range` header.
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
7. Done in step 3: add docker profile, PostgreSQL, MinIO, and the S3-compatible provider without changing Android contracts.

## Suggested Stage 16 Next Steps

1. Add a production-like Server image dependency strategy for `ffmpeg` or choose a separate media-processing worker.
2. Add OSS provider/config once ECS/OSS migration is actually scheduled.
3. Keep Android behind backend APIs; public ingress should expose only the Server API.

## PostgreSQL Schema Management Note

The current dev profile can still rely on H2 plus Hibernate `ddl-auto=update` for quick local bootstrap. PostgreSQL profiles now use Flyway migrations plus Hibernate validation by default.

Navicat or other GUI-created tables can be useful for inspection, but should not become the project source of truth. The repository migration history owns fields such as `storage_provider`, `bucket`, `original_object_key`, `preview_object_key`, `cover_object_key`, and `checksum`.
