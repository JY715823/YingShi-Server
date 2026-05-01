# Frontend Backend Testing Guide

## Scope
- backend repo: `yingshi-server`
- paired Android repo: `YingShi`
- current backend smoke script: `scripts/integration-smoke.ps1`
- current Android diagnostics entry: `Settings -> Backend integration diagnostics`

## Seed Account
- account: `demo.a@yingshi.local`
- password: `demo123456`
- alternate account: `demo.b@yingshi.local`
- alternate password: `demo123456`

## 1. Start the Backend

Requirements:
- Java 17 or newer
- local port `8080` available

Run from the `yingshi-server` root:

```powershell
.\mvnw.cmd spring-boot:run
```

Recommended preflight:

```powershell
.\mvnw.cmd test
```

Current local assumptions:
- Spring profile defaults to `dev`
- database is in-memory H2
- uploaded files are written to `local-storage`
- restarting the server resets H2 data
- restarting the server does not delete `local-storage`

Useful local URLs:
- health: `http://localhost:8080/api/health`
- Swagger UI: `http://localhost:8080/swagger-ui.html`
- OpenAPI: `http://localhost:8080/v3/api-docs`
- H2 console: `http://localhost:8080/h2-console`

## 2. Run the Integration Smoke Script

The smoke script covers:
- health
- login token
- me
- albums
- album posts
- post detail
- post update
- media feed
- post comments
- media comments
- upload token
- local upload
- trash list, detail, and restore

Run from the `yingshi-server` root:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\integration-smoke.ps1
```

Optional custom base URL:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\integration-smoke.ps1 -BaseUrl http://192.168.1.100:8080
```

Success output looks like:

```text
[PASS] health - ...
[PASS] login token - ...
...
Integration smoke completed with 0 failures.
```

Notes:
- the upload step creates one new media record on the running dev server
- if you want a fresh seeded database, stop and restart the backend
- if `local-storage` grows during repeated tests, delete it manually when the backend is stopped

## 3. Manual Health Check

Request:

```http
GET /api/health
```

Expected:
- HTTP `200`
- response header contains `X-Request-Id`
- JSON contains `data.status = "UP"`

## 4. Manual Login and Token Check

Login:

```powershell
curl.exe -X POST http://localhost:8080/api/auth/login `
  -H "Content-Type: application/json" `
  -d "{\"account\":\"demo.a@yingshi.local\",\"password\":\"demo123456\"}"
```

Use the returned access token:

```text
Authorization: Bearer <accessToken>
```

Check current user:

```powershell
curl.exe http://localhost:8080/api/auth/me `
  -H "Authorization: Bearer <accessToken>"
```

## 5. Android Emulator Base URL

Current debug default:

```text
http://10.0.2.2:8080/
```

Why:
- Android Emulator cannot reach your computer backend through `localhost`
- `10.0.2.2` is the emulator bridge back to the host machine

## 6. Android Physical Device Base URL

The diagnostics page includes a preset for:

```text
http://127.0.0.1:8080/
```

Important clarification:
- on a normal phone over Wi-Fi, `127.0.0.1` points to the phone itself, not your PC
- for same-Wi-Fi testing, replace it with your computer LAN IP, for example `http://192.168.1.100:8080/`
- keep the phone and computer on the same Wi-Fi

## 7. Android Debug Cleartext

Current Android behavior:
- debug build allows cleartext HTTP through a debug-only network security config
- release build security policy is unchanged

If Android still reports a cleartext error:
- confirm you installed a debug build, not release
- confirm the base URL starts with `http://`
- rebuild `assembleDebug` after changing local Android files

## 8. Fake Real Switching

Current design:
- app default stays on `FAKE`
- runtime switch is stored in `BackendDebugConfig`
- diagnostics page can flip `FAKE` / `REAL`
- Android now rebuilds the REAL page session when `Repository mode` changes, so fake and real caches do not mix
- Android now clears the current token when `Base URL` changes, so a backend switch always requires relogin

Where it is implemented:
- config state: `app/src/main/java/com/example/yingshi/data/remote/config/BackendDebugConfig.kt`
- repository switch point: `app/src/main/java/com/example/yingshi/data/repository/RepositoryProvider.kt`
- diagnostics page: `app/src/main/java/com/example/yingshi/feature/photos/BackendDiagnosticsScreen.kt`

Recommendation:
- keep normal UI work on `FAKE`
- switch to `REAL` only for integration checks
- after integration checks, switch back to `FAKE`

## 9. Android Diagnostics Entry

Current entry path in the app:
1. Open the app debug build.
2. Go to Photos.
3. Open Notifications.
4. Open Settings.
5. Open `Backend integration diagnostics`.

What the diagnostics page can do:
- show and edit the active `baseUrl`
- switch `FAKE` / `REAL`
- login with the seeded account
- test `health`
- test albums and post detail
- test media and comments
- test trash
- run one combined smoke pass

## 10. Exact Device Acceptance Steps

Use this exact sequence on a phone:

1. Start the backend with `.\mvnw.cmd spring-boot:run`.
2. Confirm `http://<your-pc-ip>:8080/api/health` is reachable from the same Wi-Fi network.
3. Build and install the Android debug app.
4. Open `Photos -> Notifications -> Settings -> Backend integration diagnostics`.
5. In `Base URL`, enter `http://<your-pc-ip>:8080/`.
6. Tap `Save Base URL`.
7. Confirm the `Active base URL` row shows the same value.
8. Keep repository mode on `FAKE` first and tap `Health`.
9. Confirm `Last result` shows `[health] success`.
10. Tap `Login and verify /me`.
11. Confirm `Token state` changes to `Logged in`.
12. If you change `Base URL`, log in again because Android clears the old token on base-url change.
13. Tap `Albums and post detail`.
14. Confirm `Last result` includes `albums=` and `post=`.
15. Tap `Media and comments`.
16. Confirm `Last result` includes `media=`, `postComments=`, and `mediaComments=`.
17. Tap `Trash`.
18. Confirm `Last result` includes `trash=` and no failure text.
19. Tap `Run all smoke actions`.
20. Confirm the diagnostics page lists each smoke item as `success` or `failed`, and the summary contains `health=UP`, `upload=success`, and `trash=`.
21. If you want to verify future real repository wiring, switch mode to `REAL`, then reopen the relevant feature screen so it rebuilds under the new session.
22. After the pass, switch mode back to `FAKE`.

## 11. Windows Firewall and Port 8080 Troubleshooting

If the phone cannot reach the backend:
- confirm the backend is really listening on port `8080`
- confirm Windows Firewall allows inbound traffic on port `8080`
- confirm the network profile is not blocking local LAN traffic
- confirm the phone and computer are on the same Wi-Fi
- confirm you used the PC LAN IP, not `localhost`

Useful local checks:

```powershell
netstat -ano | findstr :8080
ipconfig
```

## 12. Common Problems

`401 AUTH_UNAUTHORIZED`
- token missing
- token expired after server restart
- login was done against a different backend base URL

Android emulator cannot connect
- used `localhost` instead of `10.0.2.2`
- backend is not actually running on `8080`

Android phone cannot connect
- used `127.0.0.1` over Wi-Fi instead of the PC LAN IP
- phone and computer are not on the same Wi-Fi
- Windows Firewall blocked inbound `8080`

Smoke script upload keeps adding media
- this is expected while the dev server keeps running
- restart the backend for a clean H2 state
- delete `local-storage` manually if you want to reclaim disk

Trash restore fails
- the backend data was already changed by an earlier partial test
- restart the backend and rerun the smoke script from a clean dev state
