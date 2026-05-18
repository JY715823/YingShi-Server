# Stage 16 Docker Cloudlike Environment

## Purpose

This environment runs the Server with PostgreSQL and MinIO on the local machine. It is a cloudlike rehearsal for later ECS + OSS work, not a public deployment plan.

Only the backend API should be exposed to Android or future public ingress. PostgreSQL and MinIO are local development services and should not be exposed to the public internet.

## Files

- `docker-compose.yml`: PostgreSQL, MinIO, MinIO bucket init, and Server.
- `Dockerfile`: builds the Spring Boot app image.
- `.env.example`: sample local environment variables.
- `src/main/resources/application-docker.yml`: Spring `docker` profile.
- `src/main/resources/application-docker-local.yml`: Spring profile for running Server from Windows IDEA while PostgreSQL and MinIO run in Docker.
- `docker-compose.cloudflare.yml`: optional Cloudflare Tunnel connector for the full Docker stack.
- `docs/implementation/stage16-cloudflare-tunnel-access.md`: quasi-online HTTPS access guide.
- `docs/implementation/stage16-cloudlike-data-safety.md`: backup, restore, audit, and migration safety guide.
- `scripts/stage16-object-audit.ps1`: read-only DB/object-key/object-existence audit.
- `scripts/stage16-cloudlike-smoke.ps1`: Stage 16 backend API smoke test for local or Tunnel base URLs.
- `scripts/stage16-cloudlike-check.ps1`: daily cloudlike environment check for Docker, health, Flyway, object audit, optional smoke, and optional Maven tests.
- `scripts/stage16-retire-local-storage.ps1`: dry-run-first helper for deleting retired old H2/local-storage test data.

## First Run

From the Server repository root:

```powershell
Copy-Item .env.example .env
docker compose up --build
```

The `.env` file is ignored by git. Change sample passwords in `.env` if you keep the environment running for more than local testing.
If local port `8080` is already in use, change `SERVER_HOST_PORT` in `.env` and use that host port for the Server API.

## Windows IDEA Run Mode

This is the recommended daily development mode on Windows:

```text
PostgreSQL and MinIO: Docker Desktop
Server backend: Windows IDEA
Android REAL: backend API only
```

Start only the dependency containers:

```powershell
Copy-Item .env.example .env
docker compose up -d postgres minio minio-init
```

Then run the Spring Boot application in IDEA with active profile:

```text
docker-local
```

The `docker-local` profile uses these defaults:

- PostgreSQL JDBC URL: `jdbc:postgresql://127.0.0.1:15432/yingshi`
- PostgreSQL user: `yingshi`
- PostgreSQL password: `yingshi_dev_password`
- Storage provider: `s3`
- Storage bucket: `yingshi-media`
- MinIO endpoint: `http://127.0.0.1:9000`
- MinIO access key: `yingshi_minio_access`
- MinIO secret key: `yingshi_minio_secret`

Use `docker-local` only when the Server process runs on Windows or another host process outside Docker Compose. If the Server also runs inside Docker Compose, use the `docker` profile because containers must reach PostgreSQL and MinIO by service names such as `postgres` and `minio`.

If your Windows machine already has another PostgreSQL service on `5432`, keep using the Docker host port `15432`. Do not point Navicat or IDEA at `5432` unless you intentionally want the local Windows PostgreSQL instance instead of the Docker one.

No code changes are needed between Windows and Linux. The difference is only configuration:

- Windows IDEA process connects to Docker-published ports through `localhost`.
- Server container connects to Compose services through `postgres` and `minio`.
- Future ECS/OSS deployment should keep the same Server code and replace only datasource/storage environment variables.

## Services

- Server API: `http://localhost:8080`
- Existing health API: `http://localhost:8080/api/health`
- PostgreSQL: `127.0.0.1:15432`
- MinIO S3 API: `http://127.0.0.1:9000`
- MinIO Console: `http://127.0.0.1:9001`

PostgreSQL and MinIO host ports are bound to `127.0.0.1` for local inspection only. Do not publish them through Cloudflare Tunnel or expose them as public services.

Default sample credentials from `.env.example`:

- PostgreSQL database: `yingshi`
- PostgreSQL user: `yingshi`
- MinIO user/access key: `yingshi_minio_access`
- MinIO bucket: `yingshi-media`
- Demo login account: `demo.a@yingshi.local`
- Demo login password: `demo123456`

Do not commit real credentials.

## Verify Server

```powershell
Invoke-RestMethod http://localhost:8080/api/health
```

Expected result: response data contains `status = UP` and active profile includes `docker`.

The project does not currently add Spring Actuator. Use the existing `/api/health` endpoint rather than `/actuator/health`.

## Verify MinIO Bucket

The `minio-init` service creates the bucket named by `MINIO_BUCKET`, default `yingshi-media`.

Check with Docker:

```powershell
docker compose ps
docker compose logs minio-init
```

Or open MinIO Console:

1. Open `http://localhost:9001`.
2. Log in with `MINIO_ROOT_USER` and `MINIO_ROOT_PASSWORD` from `.env`.
3. Confirm bucket `yingshi-media` exists.
4. After uploading media through the Server API, objects should appear under keys such as `originals/2026/04/media_xxx.jpg`.

Android must not use the MinIO Console or S3 API URL.

## Backup And Migration Safety

For the next 1-2 months of cloudlike local development, treat PostgreSQL and MinIO together as the data set:

- PostgreSQL stores media and business metadata.
- MinIO bucket `yingshi-media` stores the media binaries.
- Android stays behind Server APIs and does not know object keys or storage credentials.

The detailed backup, restore, disaster recovery, and future OSS migration flow lives in:

```text
docs/implementation/stage16-cloudlike-data-safety.md
```

The two core backup commands are:

```powershell
New-Item -ItemType Directory -Force backups | Out-Null
docker compose exec -T postgres pg_dump -U yingshi -d yingshi --format=custom --file=/tmp/yingshi.dump
docker compose cp postgres:/tmp/yingshi.dump .\backups\yingshi-postgres.dump
```

```powershell
New-Item -ItemType Directory -Force backups\minio-yingshi-media | Out-Null
docker run --rm --network yingshi-server_default `
  -v "${PWD}\backups\minio-yingshi-media:/backup" `
  --entrypoint /bin/sh `
  minio/mc:RELEASE.2025-04-16T18-13-26Z `
  -c "mc alias set local http://minio:9000 yingshi_minio_access yingshi_minio_secret && mc mirror --overwrite local/yingshi-media /backup"
```

Do not commit `backups/`; it is ignored by git and may contain private media or metadata.

Old H2/local-storage test media is not part of the cloudlike source of truth. To inspect or delete it:

```powershell
.\scripts\stage16-retire-local-storage.ps1
.\scripts\stage16-retire-local-storage.ps1 -Apply
```

Run the `-Apply` form only after stopping any Server process that might still use the `dev` profile. This cleanup does not touch Docker PostgreSQL or MinIO.

## Navicat PostgreSQL Connection

Create a PostgreSQL connection in Navicat:

- Host: `127.0.0.1`
- Port: `15432`
- Initial database: `yingshi`
- User: value of `POSTGRES_USER`, default `yingshi`
- Password: value of `POSTGRES_PASSWORD`, default `yingshi_dev_password`

This is for inspection only. Do not create or alter project tables manually as the source of truth.

Current docker and docker-local profiles use Flyway migrations plus Hibernate schema validation by default:

- Flyway location: `src/main/resources/db/migration/postgresql`
- Initial migration: `V1__initial_schema.sql`
- Default Hibernate mode: `spring.jpa.hibernate.ddl-auto=validate`
- Existing local PostgreSQL volumes are adopted with `spring.flyway.baseline-on-migrate=true`, so Flyway records a baseline instead of dropping or recreating already existing tables.

For a brand-new PostgreSQL volume, Flyway creates the tables from migration scripts. For later schema changes, add a new migration such as `V2__add_xxx.sql`; do not hand-edit the schema in Navicat as the project fact source.

Use Navicat to inspect rows such as `storage_provider`, `bucket`, `original_object_key`, `preview_object_key`, `cover_object_key`, `checksum`, `size_bytes`, `mime_type`, `width`, `height`, and `duration_millis`. Do not paste MinIO Console URLs, OSS URLs, or Cloudflare Tunnel URLs into object-key columns.

The docker profile seeds only the demo users and shared library. It does not seed local sample media paths, run local test import, or run local originals recovery.

## Android REAL Base URL

For Android emulator on the same host:

```text
http://10.0.2.2:8080/
```

If `SERVER_HOST_PORT` is not `8080`, replace `8080` with that host port in the emulator base URL.

For a physical phone on the same Wi-Fi:

```text
http://<your-pc-lan-ip>:8080/
```

For Cloudflare Tunnel quasi-online testing:

```text
https://api.your-domain.com/
```

See `docs/implementation/stage16-cloudflare-tunnel-access.md` for the Windows cloudflared and Docker cloudflared options. Only the backend API hostname should be public; PostgreSQL, MinIO API, and MinIO Console must stay local/private.

Android still calls only backend APIs such as `/api/media/files/{mediaId}?variant=preview`. It must not connect to `localhost:9000`, `localhost:9001`, MinIO, or OSS, and it must not store object storage keys.

## Upload Verification

Use Android REAL or the existing backend smoke flow to upload media. In docker profile:

- `POST /api/uploads/token` still returns a backend upload URL.
- `POST /api/uploads/{uploadId}/file` writes original media to MinIO through `ObjectStorageService`.
- Media rows continue to store `storageProvider`, `bucket`, `originalObjectKey`, and `checksum`.
- In this profile `STORAGE_PROVIDER` may be configured as `s3` or `minio`, but persisted media rows should use normalized `storageProvider = s3`. MinIO is only the local S3-compatible service.
- `objectKey` values are relative keys, not endpoint URLs.
- `/api/media/files/{mediaId}?variant=original|preview|cover` remains the Android-facing binary route.
- Lazy preview or video cover generation writes generated objects back through the storage abstraction and records `previewObjectKey` or `coverObjectKey` when generation succeeds.

When using an empty Docker database, log in with `demo.a@yingshi.local` / `demo123456` first. Android and manual HTTP clients should use the returned bearer token for upload and media file requests.

## Object Field Checks

After an upload in `docker-local`, a media row should look conceptually like:

```text
storage_provider = s3
bucket = yingshi-media
original_object_key = originals/2026/04/media_xxx.jpg
preview_object_key = previews/2026/04/media_xxx-preview-v2-1280.jpg   # images after preview generation
cover_object_key = previews/2026/04/media_xxx-cover-v1-1280.jpg       # videos after cover generation
```

The object key columns must not contain:

- `http://127.0.0.1:9000/...`
- `http://localhost:9000/...`
- MinIO Console URLs
- OSS endpoint URLs
- Cloudflare Tunnel URLs

Use this read-only PostgreSQL check in Navicat when needed:

```sql
select id, storage_provider, bucket, original_object_key, preview_object_key, cover_object_key
from media
where original_object_key is null
   or original_object_key like 'http://%'
   or original_object_key like 'https://%'
   or original_object_key like 's3://%'
   or original_object_key like 'oss://%';
```

Or run the read-only audit script:

```powershell
.\scripts\stage16-object-audit.ps1
```

The audit reports rows with missing `original_object_key`, URL-shaped object-key values, and MinIO objects referenced by PostgreSQL but missing from the bucket. It does not change the database or object storage.

For the normal daily environment check:

```powershell
.\scripts\stage16-cloudlike-check.ps1
```

This checks Docker PostgreSQL/MinIO, Server health, Flyway schema history, object audit, and retired local-storage dry-run. Add `-RunSmoke` when you want it to upload one tiny test image through the backend API; add `-RunTests` when you also want the full Server Maven tests.

For an end-to-end smoke upload through the Server API:

```powershell
.\scripts\stage16-cloudlike-smoke.ps1 -BaseUrl http://127.0.0.1:8080
```

To verify the same flow through a Cloudflare Quick Tunnel URL:

```powershell
.\scripts\stage16-cloudlike-smoke.ps1 -BaseUrl https://your-temporary.trycloudflare.com
```

The smoke script logs in, checks the feed, uploads one tiny image, reads original and preview through `/api/media/files/{mediaId}?variant=...`, verifies PostgreSQL object fields, and checks MinIO object existence when the provider is S3-compatible.

Android REAL should continue to use only the Server URL, for example `http://10.0.2.2:8080/` or `http://<your-pc-lan-ip>:8080/`. It should never use MinIO's `9000` or `9001` ports.

## Current Media Limitations

Supported in docker profile:

- New original uploads write to MinIO through `S3ObjectStorageService`.
- Original reads stream through the backend API.
- Image preview generation downloads the original to a temporary local work file, writes the preview to MinIO, then serves it through the backend API.
- Video cover generation follows the same temporary-work-file pattern when `ffmpeg` is available in the runtime image.

Known follow-ups:

- The current Docker image does not install `ffmpeg`; video cover generation may fail until a later image pass adds it. Video original playback remains available.
- HTTP Range requests still go through the backend API path, but S3/MinIO-backed originals now use native object-storage ranged reads. Android and media players keep using `/api/media/files/{mediaId}?variant=...`.
- Dev local scans, originals recovery, and legacy preview cleanup are local-provider features and are disabled/no-op in docker profile.

## Stop And Cleanup

Stop containers:

```powershell
docker compose down
```

Remove local Docker volumes only when you want to discard PostgreSQL and MinIO data:

```powershell
docker compose down -v
```
