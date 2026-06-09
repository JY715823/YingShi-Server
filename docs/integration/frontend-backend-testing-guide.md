# Frontend Backend Testing Guide

Updated: 2026-05-25

## Scope

- backend repo: `E:\Study\App\YingShi-Server`
- paired Android repo: `E:\Study\App\YingShi`

## Choose The Backend Mode First

Fast local bootstrap:

- profile: `dev`
- database: H2
- storage: `local-storage`
- start: `.\mvnw.cmd spring-boot:run`

Recommended Android integration mode:

- profile: `docker-local`
- database: PostgreSQL in Docker
- storage: MinIO in Docker
- dependency start: `docker compose up -d postgres minio minio-init`
- backend process: IDEA on Windows

## Seed Accounts

- `1085060329@qq.com / 123456`
- `2926315047@qq.com / 123456`
- 登录需要读取 QQ 邮箱验证码；若 smoke 脚本未传 `-LoginCode`，会在控制台提示手动输入

## Backend Preflight

From the backend repo root:

```powershell
.\mvnw.cmd test
```

For cloudlike mode:

```powershell
Copy-Item .env.example .env
docker compose up -d postgres minio minio-init
```

Health URL:

```text
http://localhost:8080/api/health
```

## Backend Smoke Options

Default integration smoke for the current backend API surface:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\integration-smoke.ps1
```

Cloudlike storage smoke for PostgreSQL + MinIO mode:

```powershell
.\scripts\stage16-cloudlike-smoke.ps1 -BaseUrl http://127.0.0.1:8080
```

Daily cloudlike environment check:

```powershell
.\scripts\stage16-cloudlike-check.ps1
```

## Android Base URL Rules

Android emulator:

```text
http://10.0.2.2:8080/
```

Physical phone on the same Wi-Fi:

```text
http://<your-pc-ip>:8080/
```

Do not use `127.0.0.1` on a physical phone. That usually points back to the phone itself.

## Android Diagnostics Page

Current entry:

1. Open the app
2. Go to `My`
3. Open `Settings`
4. Open `Backend Debug Diagnostics`

Current page responsibilities:

- show and edit `Base URL`
- save the address and clear the old session
- check whether the current session can still be restored
- clear local auth cache
- run a minimal health check
- show the latest result

The page is intentionally lightweight. Full backend coverage should rely on backend smoke scripts or real target pages.

## Features Already Aligned With Android

- auth challenge-login / resend / verify / refresh / logout / current-user / profile update
- shared-library and partner display
- albums and posts list/detail flows
- media feed and backend media-file delivery
- post/media comments create/edit/delete
- notifications list/detail/read/mark-all-read
- uploads token + multipart file upload + task status + confirm + cancel
- trash list/detail/restore/remove/purge/undo-remove/pending-cleanup

## Known Intentional Gaps

- Android life page now contains ledger and chat viewer only; the anniversary entry is intentionally removed
- ledger is still intentionally local-only while the user prepares a larger redesign

## Useful Local URLs

In `dev`:

- health: `http://localhost:8080/api/health`
- Swagger UI: `http://localhost:8080/swagger-ui.html`
- OpenAPI: `http://localhost:8080/v3/api-docs`
- H2 console: `http://localhost:8080/h2-console`

In `docker-local`:

- PostgreSQL: `127.0.0.1:15432`
- MinIO S3 API: `http://127.0.0.1:9000`
- MinIO Console: `http://127.0.0.1:9001`

## Common Problems

`401 AUTH_SESSION_INVALID`

- old refresh token was reused after rotation
- the current session was logged out
- the app is using a stale token from another backend base URL

登录挑战或验证码失败

- account/password is wrong
- the code expired
- the code was entered too many times
- resend was requested before cooldown ended
- QQ SMTP configuration is missing or invalid

`Port 8080 already in use`

- another backend or local tool is already using `8080`
- stop that process or change the backend port

Android emulator cannot connect:

- used `localhost` instead of `10.0.2.2`
- backend is not actually running on `8080`

Android phone cannot connect:

- used `127.0.0.1` instead of the PC LAN IP
- phone and PC are not on the same Wi-Fi
- Windows Firewall is blocking inbound `8080`
