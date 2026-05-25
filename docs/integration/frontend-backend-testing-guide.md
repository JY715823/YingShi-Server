# Frontend Backend Testing Guide

更新时间：2026-05-25

## Scope

- backend repo: `E:\Study\App\YingShi-Server`
- paired Android repo: `E:\Study\App\YingShi`
- current backend smoke script: `scripts/integration-smoke.ps1`
- current Android diagnostics entry: `我的 -> 设置 -> 后端联调诊断`

## Seed Account

- account: `demo.a@yingshi.local`
- password: `demo123456`
- alternate account: `demo.b@yingshi.local`
- alternate password: `demo123456`

## 1. Start the Backend

Requirements:

- Java 17 or newer
- local port `8080` available

Run from the repo root:

```powershell
.\mvnw.cmd spring-boot:run
```

Recommended preflight:

```powershell
.\mvnw.cmd test
```

Current local assumptions:

- Spring profile defaults to `dev`
- `dev` uses file-based H2 + `local-storage`
- uploaded files are written under `local-storage`
- multipart limits are currently:
  - `1024MB` per file
  - `1100MB` per request

Useful local URLs in `dev`:

- health: `http://localhost:8080/api/health`
- Swagger UI: `http://localhost:8080/swagger-ui.html`
- OpenAPI: `http://localhost:8080/v3/api-docs`
- H2 console: `http://localhost:8080/h2-console`

## 2. Run the Integration Smoke Script

Run from the repo root:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\integration-smoke.ps1
```

Optional custom base URL:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\integration-smoke.ps1 -BaseUrl http://192.168.1.100:8080
```

Current smoke coverage:

- health
- login token
- refresh token
- me
- albums
- album posts
- posts list
- post detail
- post update
- post cover
- post media order
- media feed
- post comments
- media comments
- upload token
- upload task status
- local upload
- upload confirm
- upload cancel
- avatar upload / avatar fetch
- notifications list / detail / read / read-all
- trash list / detail / restore

Success output:

```text
Integration smoke completed with 0 failures.
```

## 3. Android Base URL Facts

Current Android defaults:

- app build default: `http://10.106.3.193:8080/`
- diagnostics preset for emulator: `http://10.0.2.2:8080/`
- diagnostics preset for loopback: `http://127.0.0.1:8080/`

Guidance:

- emulator should use `10.0.2.2`
- physical phone should use the PC LAN IP
- `127.0.0.1` on a phone usually points back to the phone itself

## 4. Android Diagnostics Page

Current entry path:

1. Open the Android app
2. Go to `我的`
3. Open `设置`
4. Open `后端联调诊断`

Current page capabilities:

- show and edit active `Base URL`
- apply emulator / loopback presets
- `保存并重登` seeded demo account
- clear local auth cache
- switch `FAKE / REAL`
- run a minimal `health` check
- show the latest result

Important:

- this page no longer contains the old combined buttons for albums/media/trash/upload smoke
- Android notification center is still fake data, so do not use it to verify backend notifications
- avatar upload is currently backend-ready but not yet exposed in Android UI

## 5. Physical Device Acceptance Flow

1. Start the backend with `.\mvnw.cmd spring-boot:run`
2. Keep phone and PC on the same Wi-Fi
3. Find the PC LAN IP with `ipconfig`
4. Confirm Windows Firewall allows inbound `8080`
5. Build Android debug
6. Open `我的 -> 设置 -> 后端联调诊断`
7. Replace `Base URL` with `http://<your-pc-ip>:8080/`
8. Tap `保存并重登`
9. Confirm login succeeds
10. Tap `检查健康`
11. Confirm latest result contains `health=UP`
12. Switch Android to `REAL`
13. Reopen target pages and verify:
    - 我的 / 个人资料
    - 照片流
    - 相册与帖子详情
    - 评论
    - 回收站
    - 系统媒体上传 / 导入

## 6. Common Problems

`401 AUTH_UNAUTHORIZED`

- token missing
- token expired after server restart
- Android logged in against a different base URL

Android emulator cannot connect:

- used `localhost` instead of `10.0.2.2`
- backend not actually running on `8080`

Android phone cannot connect:

- used `127.0.0.1` instead of the PC LAN IP
- phone and PC are not on the same Wi-Fi
- Windows Firewall blocked inbound `8080`

Smoke upload keeps adding media:

- expected while the backend keeps running
- restart the backend for a clean test state

REAL pages still say “请先到后端联调诊断页登录”:

- Android page was opened before REAL login completed
- repository mode changed but the page was not reopened
- backend restarted and old token became invalid
