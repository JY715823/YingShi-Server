# Android Physical Device Acceptance Guide

Updated: 2026-05-25

## Scope

- backend repo: `E:\Study\App\YingShi-Server`
- Android repo: `E:\Study\App\YingShi`
- target: use a real Android phone to connect to the local backend

## Seed Account

- `demo.a@yingshi.local / demo123456`

## Before You Start

- keep the phone and computer on the same Wi-Fi
- confirm the backend machine IP is reachable from the phone
- use the PC LAN IP rather than `localhost` or `127.0.0.1`
- prefer the `docker-local` backend shape when doing serious Android acceptance

## 1. Start The Backend

Recommended cloudlike mode:

```powershell
Copy-Item .env.example .env
docker compose up -d postgres minio minio-init
```

Then run the Spring app from IDEA with profile:

```text
docker-local
```

Optional preflight:

```powershell
.\mvnw.cmd test
```

Optional smoke:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\integration-smoke.ps1
```

## 2. Check Backend Health

Open:

```text
http://localhost:8080/api/health
```

Expected:

- HTTP `200`
- JSON `data.status = "UP"`

## 3. Find The PC IP

Run:

```powershell
ipconfig
```

Use the Wi-Fi or hotspot IPv4 address, for example:

```text
http://192.168.137.1:8080/
```

## 4. Check Windows Firewall

Verify port `8080` is listening:

```powershell
netstat -ano | findstr :8080
```

If needed, allow inbound `8080` in Windows Firewall.

## 5. Install Android Debug Build

From the Android repo:

```powershell
.\gradlew.bat --no-daemon assembleDebug
```

Important:

- use a debug build
- debug build is the one that allows local cleartext HTTP

## 6. Open The Diagnostics Page

In the Android app, open:

1. `我的`
2. `设置`
3. `后端联调诊断`

## 7. Set The Base URL

On the diagnostics page:

1. Fill `Base URL` with:

```text
http://<your-pc-ip>:8080/
```

2. Tap the save/relogin action

Expected:

- `当前生效地址` shows the same LAN URL
- login succeeds with the seeded demo account

Do not use these for normal Wi-Fi phone testing:

- `http://localhost:8080/`
- `http://127.0.0.1:8080/`

## 8. Run The Acceptance Flow

1. Tap the health-check action.
2. Confirm the latest result contains `health=UP`.
3. Switch repository mode to `REAL`.
4. Reopen `我的` and confirm current user, shared library, and partner info are visible.
5. Open `照片` and confirm the real feed loads.
6. Open `相册` and confirm real post cards load.
7. Open one post detail and confirm media and comments load.
8. Open the bell entry and confirm notification list/detail/read work.
9. Open `回收站` and confirm list/detail/restore/remove/purge work.
10. Test upload/import from system media and confirm the returned media can flow back into the feed.

## 9. Fast Troubleshooting

Health check fails:

- backend not started
- wrong LAN IP
- firewall blocking `8080`
- phone not on the same Wi-Fi

Login fails:

- backend was restarted and the old token is invalid
- base URL points to the wrong machine

REAL pages still look fake or say to log in first:

- you changed to `REAL` but did not reopen the page
- login was not completed successfully

Upload/import fails:

- inspect Android logcat tag `SystemMediaUpload`
- confirm backend multipart limits are still `1024MB` / `1100MB`

Notifications do not appear:

- verify the backend has generated events for comments, trash, post updates, or uploads
- use Swagger or smoke scripts to confirm `/api/notifications` returns data
