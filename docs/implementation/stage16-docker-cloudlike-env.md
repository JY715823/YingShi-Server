# Stage 16 Docker Cloudlike Environment

Updated: 2026-05-25

## Purpose

This environment runs the backend against PostgreSQL and MinIO on the local machine. It is a cloudlike rehearsal for later ECS + OSS work, not a public deployment plan.

Only the backend API should be exposed to Android or any future public ingress. PostgreSQL and MinIO are local development services and must stay private.

## Files

- `docker-compose.yml`: PostgreSQL, MinIO, MinIO bucket init, and Server
- `Dockerfile`: Spring Boot runtime image, now with `ffmpeg`
- `.env.example`: sample local environment variables
- `src/main/resources/application-docker.yml`: Spring `docker` profile
- `src/main/resources/application-docker-local.yml`: Spring profile for running the backend from IDEA while PostgreSQL and MinIO stay in Docker
- `scripts/stage16-cloudlike-smoke.ps1`: focused upload/file/object smoke for PostgreSQL + MinIO mode
- `scripts/stage16-cloudlike-check.ps1`: health, Flyway, object-audit, local-storage dry-run, optional smoke/tests
- `scripts/stage16-cloudlike-backup.ps1`: combined PostgreSQL dump plus MinIO mirror backup
- `scripts/stage16-object-audit.ps1`: read-only DB/object consistency audit
- `scripts/stage16-retire-local-storage.ps1`: dry-run-first cleanup helper for retired H2/local-storage test data

## Recommended Daily Mode On Windows

```text
PostgreSQL and MinIO: Docker Desktop
Server backend: Windows IDEA
Android REAL: backend API only
```

Start dependencies:

```powershell
Copy-Item .env.example .env
docker compose up -d postgres minio minio-init
```

Then run the Spring Boot app from IDEA with profile:

```text
docker-local
```

Default `docker-local` values:

- PostgreSQL JDBC URL: `jdbc:postgresql://127.0.0.1:15432/yingshi`
- PostgreSQL user: `yingshi`
- PostgreSQL password: `yingshi_dev_password`
- storage provider: `s3`
- storage bucket: `yingshi-media`
- MinIO endpoint: `http://127.0.0.1:9000`
- MinIO access key: `yingshi_minio_access`
- MinIO secret key: `yingshi_minio_secret`

Use `docker-local` only when the backend process runs on Windows or another host process outside Docker Compose. If the backend itself runs inside Docker Compose, use `docker` instead.

## Full Docker Compose Mode

Run the full stack:

```powershell
Copy-Item .env.example .env
docker compose up --build
```

If host port `8080` is already used, change `SERVER_HOST_PORT` in `.env`.

## Service Endpoints

- backend API: `http://localhost:8080`
- backend health: `http://localhost:8080/api/health`
- PostgreSQL: `127.0.0.1:15432`
- MinIO S3 API: `http://127.0.0.1:9000`
- MinIO Console: `http://127.0.0.1:9001`

Default sample credentials:

- PostgreSQL database: `yingshi`
- PostgreSQL user: `yingshi`
- PostgreSQL password: `yingshi_dev_password`
- MinIO root user/access key: `yingshi_minio_access`
- MinIO root password/secret key: `yingshi_minio_secret`
- MinIO bucket: `yingshi-media`

Do not commit real credentials.

## Verify The Environment

Check backend health:

```powershell
Invoke-RestMethod http://localhost:8080/api/health
```

Run the cloudlike smoke:

```powershell
.\scripts\stage16-cloudlike-smoke.ps1 -BaseUrl http://127.0.0.1:8080
```

Run the broader daily check:

```powershell
.\scripts\stage16-cloudlike-check.ps1
.\scripts\stage16-cloudlike-check.ps1 -RunSmoke
.\scripts\stage16-cloudlike-check.ps1 -RunSmoke -RunTests
```

Inspect the MinIO bucket:

1. Open `http://127.0.0.1:9001`
2. Log in with `.env` values `MINIO_ROOT_USER` and `MINIO_ROOT_PASSWORD`
3. Confirm bucket `yingshi-media` exists

Inspect PostgreSQL with Navicat:

- host: `127.0.0.1`
- port: `15432`
- database: `yingshi`
- user: value of `POSTGRES_USER`
- password: value of `POSTGRES_PASSWORD`

Flyway owns the PostgreSQL schema in `docker` and `docker-local` profiles. Use Navicat for inspection only, not as the source of truth for schema changes.

## Backup

Recommended backup commands:

```powershell
.\scripts\stage16-cloudlike-backup.ps1
.\scripts\stage16-cloudlike-backup.ps1 -IncludePlainSql
```

This creates a timestamped backup set under `backups/` containing:

- a PostgreSQL custom dump
- an optional plain SQL dump
- a MinIO bucket mirror
- a JSON manifest with paths and settings

`backups/` is ignored by git.

## Android Base URL Rules

For Android emulator:

```text
http://10.0.2.2:8080/
```

For physical phone on the same Wi-Fi:

```text
http://<your-pc-lan-ip>:8080/
```

Android must keep using only backend API URLs. It must not call MinIO directly and must not store object keys or storage credentials.

## Important `ffmpeg` Note

- In full Docker mode, the backend container now includes `ffmpeg`.
- In `docker-local` mode, the backend process runs on Windows, so video-cover generation still depends on `ffmpeg` being available on the Windows host `PATH`.
- Missing `ffmpeg` affects generated video covers, not normal image previews or original media upload/download.

## Stop And Cleanup

Stop containers:

```powershell
docker compose down
```

Remove Docker volumes only when you intentionally want to discard PostgreSQL and MinIO data:

```powershell
docker compose down -v
```
