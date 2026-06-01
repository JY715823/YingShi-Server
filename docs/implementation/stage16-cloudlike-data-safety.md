# Stage 16 Cloudlike Data Safety And Migration

Updated: 2026-05-25

## Purpose

This document makes the local PostgreSQL + MinIO environment safe enough for the current 1-2 month development window before any real cloud migration.

Recommended shape today:

```text
PostgreSQL + MinIO: Docker Desktop
Server: Windows IDEA with docker-local profile
Android REAL: LAN or tunnel base URL to the backend API only
```

The goal is not to move to cloud today. The goal is to make uploaded media inspectable, backupable, and restorable.

## Source Of Truth

In `docker-local` and `docker` profile:

- PostgreSQL is the source of truth for business metadata and media metadata.
- MinIO bucket `yingshi-media` is the source of truth for media binary objects.
- media rows must store relative object keys in `original_object_key`, `preview_object_key`, and `cover_object_key`
- Android must store only backend API URLs, not object keys, bucket names, or storage credentials

Old H2 + `local-storage` data is now legacy local development data, not the authoritative dataset.

## Recommended Backup Flow

Run from the Server repo root:

```powershell
.\scripts\stage16-cloudlike-backup.ps1
```

Optional inspectable SQL:

```powershell
.\scripts\stage16-cloudlike-backup.ps1 -IncludePlainSql
```

The script creates a timestamped backup set under `backups/` with:

- PostgreSQL custom dump
- optional PostgreSQL plain SQL dump
- MinIO bucket mirror
- `backup-manifest.json`

Use the custom dump as the primary restore artifact. Use the plain SQL dump mainly for inspection.

If you need only PostgreSQL:

```powershell
.\scripts\stage16-cloudlike-backup.ps1 -SkipMinio
```

Do not commit `backups/`.

## Manual PostgreSQL Backup Fallback

If you need the raw commands:

```powershell
New-Item -ItemType Directory -Force backups | Out-Null
docker compose exec -T postgres pg_dump -U yingshi -d yingshi --format=custom --file=/tmp/yingshi.dump
docker compose cp postgres:/tmp/yingshi.dump .\backups\yingshi-postgres.dump
```

Optional plain SQL:

```powershell
docker compose exec -T postgres pg_dump -U yingshi -d yingshi --file=/tmp/yingshi.sql
docker compose cp postgres:/tmp/yingshi.sql .\backups\yingshi-postgres.sql
```

## Manual MinIO Backup Fallback

```powershell
New-Item -ItemType Directory -Force backups\minio-yingshi-media | Out-Null
docker run --rm --network yingshi-server_default `
  -v "${PWD}\backups\minio-yingshi-media:/backup" `
  --entrypoint /bin/sh `
  minio/mc:RELEASE.2025-04-16T18-13-26Z `
  -c "mc alias set local http://minio:9000 yingshi_minio_access yingshi_minio_secret && mc mirror --overwrite local/yingshi-media /backup"
```

## Restore PostgreSQL

Restore only when intentionally rebuilding a local environment:

```powershell
docker compose cp .\backups\yingshi-postgres.dump postgres:/tmp/yingshi.dump
docker compose exec -T postgres dropdb -U yingshi --if-exists yingshi
docker compose exec -T postgres createdb -U yingshi yingshi
docker compose exec -T postgres pg_restore -U yingshi -d yingshi --clean --if-exists /tmp/yingshi.dump
```

After restore, start the backend with `docker-local` or `docker` and verify:

```powershell
Invoke-RestMethod http://127.0.0.1:8080/api/health
```

## Restore MinIO Bucket

Run this when rebuilding the bucket:

```powershell
docker run --rm --network yingshi-server_default `
  -v "${PWD}\backups\minio-yingshi-media:/backup" `
  --entrypoint /bin/sh `
  minio/mc:RELEASE.2025-04-16T18-13-26Z `
  -c "mc alias set local http://minio:9000 yingshi_minio_access yingshi_minio_secret && mc mb --ignore-existing local/yingshi-media && mc mirror --overwrite /backup local/yingshi-media"
```

## Read-Only Audit And Smoke

Object audit:

```powershell
.\scripts\stage16-object-audit.ps1
```

Cloudlike smoke:

```powershell
.\scripts\stage16-cloudlike-smoke.ps1 -BaseUrl http://127.0.0.1:8080
```

Daily check entry point:

```powershell
.\scripts\stage16-cloudlike-check.ps1
```

These tools do not mutate PostgreSQL or MinIO, except for the smoke upload itself.

## Legacy local-storage Retirement

Old H2 + `local-storage` media is not the migration target.

Inspect what would be removed:

```powershell
.\scripts\stage16-retire-local-storage.ps1
```

Delete retired local test data only after the backend is stopped:

```powershell
.\scripts\stage16-retire-local-storage.ps1 -Apply
```

This does not touch PostgreSQL, MinIO, Docker volumes, `.env`, or `backups/`.

## Future OSS Migration Shape

For media uploaded in `docker-local`, migration to OSS should stay a bucket/key copy plus configuration change:

1. Export PostgreSQL dump.
2. Mirror the MinIO bucket.
3. Copy objects to OSS with the same relative keys.
4. Reconfigure the backend to use the OSS provider.
5. Keep Android pointed at the backend API, not object storage.
6. Run object audit after restore.

Database object-key columns must remain relative. Do not rewrite them to MinIO or OSS URLs.

## Disaster Recovery Drill

Use this before trusting the environment with important data:

1. Upload one image from Android REAL or the smoke script.
2. Run `stage16-cloudlike-backup.ps1`.
3. Stop the backend.
4. Restore PostgreSQL into a clean DB.
5. Restore the MinIO bucket.
6. Start the backend with `docker-local`.
7. Run:

```powershell
.\scripts\stage16-object-audit.ps1
.\scripts\stage16-cloudlike-smoke.ps1 -BaseUrl http://127.0.0.1:8080
```

8. Verify the same media still reads through `/api/media/files/{mediaId}?variant=...`.
