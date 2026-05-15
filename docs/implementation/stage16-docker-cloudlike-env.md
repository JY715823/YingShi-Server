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

## Navicat PostgreSQL Connection

Create a PostgreSQL connection in Navicat:

- Host: `127.0.0.1`
- Port: `15432`
- Initial database: `yingshi`
- User: value of `POSTGRES_USER`, default `yingshi`
- Password: value of `POSTGRES_PASSWORD`, default `yingshi_dev_password`

This is for inspection only. Do not create or alter project tables manually as the source of truth.

Current docker profile strategy is `spring.jpa.hibernate.ddl-auto=update` by default for bootstrapping. Future PostgreSQL stages should move schema changes to committed Flyway, Liquibase, or SQL migration scripts.

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

Android still calls only backend APIs such as `/api/media/files/{mediaId}?variant=preview`. It must not connect to `localhost:9000`, `localhost:9001`, MinIO, or OSS, and it must not store object storage keys.

## Upload Verification

Use Android REAL or the existing backend smoke flow to upload media. In docker profile:

- `POST /api/uploads/token` still returns a backend upload URL.
- `POST /api/uploads/{uploadId}/file` writes original media to MinIO through `ObjectStorageService`.
- Media rows continue to store `storageProvider`, `bucket`, `originalObjectKey`, and `checksum`.
- `objectKey` values are relative keys, not endpoint URLs.
- `/api/media/files/{mediaId}?variant=original|preview|cover` remains the Android-facing binary route.

When using an empty Docker database, log in with `demo.a@yingshi.local` / `demo123456` first. Android and manual HTTP clients should use the returned bearer token for upload and media file requests.

## Current Media Limitations

Supported in docker profile:

- New original uploads write to MinIO through `S3ObjectStorageService`.
- Original reads stream through the backend API.
- Image preview generation downloads the original to a temporary local work file, writes the preview to MinIO, then serves it through the backend API.
- Video cover generation follows the same temporary-work-file pattern when `ffmpeg` is available in the runtime image.

Known follow-ups:

- The current Docker image does not install `ffmpeg`; video cover generation may fail until a later image pass adds it. Video original playback remains available.
- HTTP Range requests still go through the backend `Resource` path. The current implementation can serve ranges by skipping through the S3 stream, which is acceptable for smoke testing but should be replaced with native S3 ranged reads for large videos.
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
