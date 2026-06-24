# Stage 16 Storage Migration Readiness

Updated: 2026-05-25

## Scope

This document records the current backend storage shape and the rules that keep local development compatible with a later OSS migration.

## Current Runtime Shape

- `dev`: H2 plus `local-storage`
- `docker-local`: PostgreSQL plus MinIO, backend process on Windows
- `docker`: PostgreSQL plus MinIO, backend process in Docker

Current backend stack:

- Spring Boot
- Spring Web MVC
- Spring Data JPA
- JWT auth
- Flyway for PostgreSQL schema ownership in `docker` and `docker-local`
- local and S3-compatible `ObjectStorageService` providers

## Current Storage Rules

- Android still talks only to backend APIs.
- Public media delivery still goes through `/api/media/files/{mediaId}?variant=original|preview|cover`.
- Database object-key columns must contain relative keys, not URLs.
- New S3-compatible rows normalize provider values to `s3`.
- Legacy URL fields and `storagePath` remain for compatibility, but new logic should prefer object metadata fields.

Media rows currently carry both compatibility fields and migration-safe fields:

- compatibility: `url`, `previewUrl`, `originalUrl`, `videoUrl`, `coverUrl`, `storagePath`
- migration-safe: `storageProvider`, `bucket`, `originalObjectKey`, `previewObjectKey`, `coverObjectKey`, `checksum`

## Current Cloudlike Readiness Status

Implemented:

- local object storage abstraction
- S3-compatible provider for MinIO
- native ranged reads through storage abstraction
- PostgreSQL schema ownership with Flyway
- read-only object audit script
- focused cloudlike smoke script
- daily cloudlike check script
- dry-run-first local-storage retirement helper
- combined PostgreSQL + MinIO backup script
- Docker runtime image with `ffmpeg`

Still intentional limitations:

- Android does not consume MinIO or OSS directly
- presigned uploads and direct-to-object-storage flows are not part of this stage
- OSS provider is still deferred until real cloud migration is scheduled
- when using `docker-local`, video-cover generation still depends on Windows host `ffmpeg`

## Current Auth And Notification Side Effects On Storage Work

Recent backend hardening changed the surrounding runtime assumptions:

- auth sessions now live in PostgreSQL table `auth_sessions`
- refresh-token rotation and logout revocation are enforced server-side
- comment audit fields enable comment edit/delete notification variants
- expired trash items can now be purged from scheduled cleanup

These changes do not alter Android media contracts, but they do matter for PostgreSQL backups and restore drills because they add new persisted state beyond media metadata alone.

## Diagnostics

Use the backend API as the stable contract surface:

- `GET /api/health`
- `GET /api/media/files/{mediaId}?variant=...`

Use these repo scripts for inspection:

```powershell
.\scripts\stage16-object-audit.ps1
.\scripts\stage16-cloudlike-smoke.ps1 -BaseUrl http://127.0.0.1:8080
.\scripts\stage16-cloudlike-check.ps1
.\scripts\stage16-cloudlike-backup.ps1
```

Useful read-only PostgreSQL check:

```sql
select id, storage_provider, bucket, original_object_key, preview_object_key, cover_object_key
from media
where original_object_key is null
   or original_object_key like 'http://%'
   or original_object_key like 'https://%'
   or original_object_key like 's3://%'
   or original_object_key like 'oss://%';
```

## Migration Guardrails

- never store MinIO, OSS, localhost, LAN, or public gateway URLs in object-key columns
- do not expose MinIO or OSS URLs to Android DTOs
- do not hand-edit PostgreSQL schema as the project source of truth
- do not auto-migrate legacy H2/local-storage rows on normal startup
- keep future storage-provider differences in configuration and provider implementations, not controllers

## Next Real Gaps

1. Add the future OSS provider behind the same storage abstraction when cloud work is actually scheduled.
2. Run an end-to-end restore drill from `stage16-cloudlike-backup.ps1` artifacts into a clean local environment.
3. Connect Android UI to the backend notification and avatar APIs without changing media delivery contracts.
