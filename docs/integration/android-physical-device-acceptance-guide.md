# Android Physical Device Acceptance Guide

## Scope
- backend repo: `yingshi-server`
- Android repo: `YingShi`
- target: use a real Android phone to connect to the local backend

## Seed Account
- account: `demo.a@yingshi.local`
- password: `demo123456`

## Before You Start
- keep the phone and computer on the same Wi-Fi
- confirm the backend machine IP is reachable from the phone
- if your current local IP is `192.168.137.1`, use `http://192.168.137.1:8080/`

## 1. Start the Backend

From the `yingshi-server` root:

```powershell
.\mvnw.cmd spring-boot:run
```

Optional preflight:

```powershell
.\mvnw.cmd test
```

Optional smoke check:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\integration-smoke.ps1
```

Expected:
- backend starts on port `8080`
- health check returns `UP`

## 2. Check Backend Health

Open on the backend machine:

```text
http://localhost:8080/api/health
```

Expected response:

```json
{
  "data": {
    "status": "UP"
  }
}
```

## 3. Confirm the PC IP

Run:

```powershell
ipconfig
```

Find the IPv4 address used by the current Wi-Fi or hotspot.

For your current test, use:

```text
http://192.168.137.1:8080/
```

## 4. Check Windows Firewall

If the phone cannot connect, first verify port `8080` is listening:

```powershell
netstat -ano | findstr :8080
```

If needed, allow inbound `8080` in Windows Firewall.

Common symptoms of firewall blocking:
- `Health` on the phone always fails
- browser on the phone cannot open `http://192.168.137.1:8080/api/health`

## 5. Install the Android Debug Build

Build from the Android repo:

```powershell
.\gradlew.bat --no-daemon assembleDebug
```

Install the debug APK on the phone.

Important:
- use the debug build
- release build does not include the local cleartext debug allowance

## 6. Open the Diagnostics Page

In the Android app, open:

1. `Photos`
2. `Notifications`
3. `Settings`
4. `Backend integration diagnostics`

## 7. Set the Base URL

On the diagnostics page:

1. Find `Base URL`
2. Enter:

```text
http://192.168.137.1:8080/
```

3. Tap `Save Base URL`

Expected:
- the `Active base URL` row shows `http://192.168.137.1:8080/`

Do not use these on a normal Wi-Fi device run:
- `http://localhost:8080/`
- `http://127.0.0.1:8080/`

Those point back to the phone itself.

## 8. Run the Device Acceptance Flow

Use this exact order:

1. Tap `Health`
2. Confirm `Last result` shows `[health] success`
3. Tap `Login and verify /me`
4. Confirm `Token state` becomes `Logged in`
5. Tap `Albums and post detail`
6. Confirm the result includes `albums=` and `post=`
7. Tap `Media and comments`
8. Confirm the result includes `media=`, `postComments=`, and `mediaComments=`
9. Tap `Trash`
10. Confirm the result includes `trash=` and does not show `failed`
11. Tap `Run all smoke actions`
12. Confirm the result includes:
   - `health=UP`
   - `upload=success`
   - `trash=`

## 9. Fake Real Switching

Recommended mode for this acceptance:
- keep the app default on `FAKE`
- use the diagnostics page to test the backend directly
- only switch to `REAL` if you want to reopen a target screen and verify that screen against the live backend

After testing:
- switch back to `FAKE`

## 10. Fast Troubleshooting

`Health` fails immediately:
- backend not started
- wrong IP
- firewall blocking `8080`
- phone not on the same Wi-Fi

`Login and verify /me` fails:
- health URL is correct but backend was restarted and the old token is invalid
- seed account/password typed wrong

`Albums` or `Media and comments` fails:
- login step was skipped
- backend was restarted between steps

`Run all smoke actions` changes media count:
- expected, because upload smoke creates new media while the dev backend stays running
- restart backend if you want a fresh H2 state

## 11. Quick Acceptance Standard

You can treat the device pass as accepted when all of these are true:
- phone can hit `Health`
- login works with the seeded account
- albums request succeeds
- media/comments request succeeds
- trash request succeeds
- combined smoke action succeeds
- no cleartext or network security error appears in the debug app
