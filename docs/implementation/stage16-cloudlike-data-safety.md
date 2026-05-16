# Stage 16 Cloudlike Data Safety And Migration

## Purpose

This document makes the local cloudlike environment safe to use for the next 1-2 months before a real ECS + OSS migration.

Current recommended development shape:

```text
PostgreSQL + MinIO: Docker Desktop
Server: Windows IDEA with docker-local profile
Android REAL: LAN baseUrl or Cloudflare Quick Tunnel HTTPS baseUrl
```

The goal is not to move to cloud now. The goal is to ensure media uploaded today can be backed up, checked, and migrated later.

## Source Of Truth

In `docker-local` and `docker` profile:

- PostgreSQL is the source of truth for business metadata and media metadata.
- MinIO bucket `yingshi-media` is the source of truth for media binary objects.
- Media rows should store relative object keys in `original_object_key`, `preview_object_key`, and `cover_object_key`.
- Android only stores and calls backend API URLs. Android must not store object keys, MinIO URLs, OSS URLs, bucket names, access keys, or secret keys.

Local Docker volumes are implementation details. Do not rely on manually copying Docker Desktop volume internals as the only backup path.

## Backup PostgreSQL

Run this from the Server repository root while the `postgres` container is running:

```powershell
New-Item -ItemType Directory -Force backups | Out-Null
docker compose exec -T postgres pg_dump -U yingshi -d yingshi --format=custom --file=/tmp/yingshi.dump
docker compose cp postgres:/tmp/yingshi.dump .\backups\yingshi-postgres.dump
```

Optional plain SQL dump for inspection:

```powershell
docker compose exec -T postgres pg_dump -U yingshi -d yingshi --file=/tmp/yingshi.sql
docker compose cp postgres:/tmp/yingshi.sql .\backups\yingshi-postgres.sql
```

The custom dump is the better restore artifact. The SQL dump is easier to read.

Do not commit `backups/`. It may contain private metadata.

## Restore PostgreSQL

Use restore only when intentionally rebuilding a local environment. It will overwrite data in the target database.

```powershell
docker compose cp .\backups\yingshi-postgres.dump postgres:/tmp/yingshi.dump
docker compose exec -T postgres dropdb -U yingshi --if-exists yingshi
docker compose exec -T postgres createdb -U yingshi yingshi
docker compose exec -T postgres pg_restore -U yingshi -d yingshi --clean --if-exists /tmp/yingshi.dump
```

After restore, start Server with `docker-local` or `docker` profile and verify:

```powershell
Invoke-RestMethod http://127.0.0.1:8080/api/health
```

## Backup MinIO Bucket

Use the MinIO client container to mirror the bucket to a local backup folder:

```powershell
New-Item -ItemType Directory -Force backups\minio-yingshi-media | Out-Null
docker run --rm --network yingshi-server_default `
  -v "${PWD}\backups\minio-yingshi-media:/backup" `
  --entrypoint /bin/sh `
  minio/mc:RELEASE.2025-04-16T18-13-26Z `
  -c "mc alias set local http://minio:9000 yingshi_minio_access yingshi_minio_secret && mc mirror --overwrite local/yingshi-media /backup"
```

If your `.env` uses different MinIO credentials or bucket name, replace the values in the command.

The backup folder preserves object keys such as:

```text
backups/minio-yingshi-media/originals/2026/05/media_xxx.jpg
backups/minio-yingshi-media/previews/2026/05/media_xxx-preview-v2-1280.jpg
```

## Restore MinIO Bucket

Run this after PostgreSQL restore or when rebuilding the bucket:

```powershell
docker run --rm --network yingshi-server_default `
  -v "${PWD}\backups\minio-yingshi-media:/backup" `
  --entrypoint /bin/sh `
  minio/mc:RELEASE.2025-04-16T18-13-26Z `
  -c "mc alias set local http://minio:9000 yingshi_minio_access yingshi_minio_secret && mc mb --ignore-existing local/yingshi-media && mc mirror --overwrite /backup local/yingshi-media"
```

Then use MinIO Console at `http://127.0.0.1:9001` to inspect bucket `yingshi-media`.

## Read-Only Migration Check

Run:

```powershell
.\scripts\stage16-object-audit.ps1
```

The script checks:

- media rows missing `original_object_key`
- object key columns that look like full URLs
- referenced original/preview/cover objects missing from MinIO

For a Cloudflare or local API smoke test, run:

```powershell
.\scripts\stage16-cloudlike-smoke.ps1 -BaseUrl http://127.0.0.1:8080
.\scripts\stage16-cloudlike-smoke.ps1 -BaseUrl https://your-temporary.trycloudflare.com
```

These scripts are read-only except for the smoke upload itself. They do not delete local-storage files, do not mutate old H2 data, and do not direct Android to MinIO.

## Legacy local-storage Retirement

Old H2 + `local-storage` data is legacy local development data. The current cloudlike source of truth is PostgreSQL + MinIO.

For the current project state, old H2/local-storage media does not need to be preserved. Do not auto-migrate old H2/local-storage on normal startup.

To inspect what would be removed:

```powershell
.\scripts\stage16-retire-local-storage.ps1
```

To delete the retired local test data after Server is stopped:

```powershell
.\scripts\stage16-retire-local-storage.ps1 -Apply
```

The cleanup script only removes retired directories under the Server repository's `local-storage` root, such as `dev-db`, `originals`, `previews`, `test`, `tmp`, and `videos`. It does not touch PostgreSQL, MinIO, Docker volumes, `.env`, `backups/`, or the small generated `local-storage/seed` dev fixtures.

If old media ever becomes worth preserving after all, do not use the cleanup script first. Build a separate explicit migration tool with this shape:

- default `dry-run`
- scan old rows that still depend on `storagePath`
- locate originals under `local-storage`
- compute relative target object keys
- upload objects to MinIO or OSS through `ObjectStorageService`
- update PostgreSQL object fields only when explicitly requested
- never delete old local files as part of the first migration

## Future OSS Migration Shape

For media uploaded in `docker-local`, migration to OSS should be a bucket/key copy plus configuration change:

1. Export PostgreSQL dump.
2. Mirror MinIO bucket `yingshi-media`.
3. Copy objects to OSS using the same relative keys.
4. Configure Server with OSS provider, bucket, endpoint/region, and credentials.
5. Keep Android baseUrl pointed at the backend API, not OSS.
6. Run object audit against the restored database and OSS-backed provider.

Database object key columns must remain relative. Do not rewrite them to OSS URLs.

## Disaster Recovery Drill

Use this flow before you trust the local cloudlike environment for important media:

1. Upload one image from Android REAL.
2. Run PostgreSQL backup.
3. Run MinIO bucket backup.
4. Stop Server.
5. Restore PostgreSQL into a fresh or cleared local database.
6. Restore MinIO bucket into a fresh or cleared bucket.
7. Start Server with `docker-local`.
8. Run:

```powershell
.\scripts\stage16-object-audit.ps1
.\scripts\stage16-cloudlike-smoke.ps1 -BaseUrl http://127.0.0.1:8080
```

9. Open Android REAL and verify the uploaded media still reads through `/api/media/files/{mediaId}?variant=...`.

## Current Deferred Items

- Native S3 ranged reads for large original media are now implemented behind the backend file endpoint. Keep validating video playback through `/api/media/files/{mediaId}` as larger files are introduced.
- Docker image `ffmpeg` support should be added only when video cover generation becomes a test target.
- PostgreSQL schema ownership now starts with Flyway in docker/docker-local profiles. Future table or column changes should be committed as new migration files under `src/main/resources/db/migration/postgresql`.
