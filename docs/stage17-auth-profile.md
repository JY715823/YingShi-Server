# Stage 17 Auth And Profile

## Scope

This pass closes the first login loop for Android REAL mode:

- account/password login with the demo account
- bearer access token on protected requests
- current-user lookup for the profile page
- logout endpoint for the client logout action

Out of scope: registration, password reset, refresh-token exchange, third-party login, avatar upload, profile editing, relationship binding, direct MinIO access from Android, and any media read/upload contract changes.

## Demo Account

The local development profiles seed and maintain these accounts:

- `demo.a@yingshi.local / demo123456`
- `demo.b@yingshi.local / demo123456`

Profiles covered by the seed: `dev`, `docker`, and `docker-local`.

Passwords are stored as BCrypt hashes through the shared `PasswordEncoder`. The seed is idempotent: if demo accounts or the shared library are missing in an existing local database, they are created; if a demo password hash no longer matches `demo123456`, it is replaced with a fresh hash.

## Endpoints

### Public

`POST /api/auth/login`

Request:

```json
{
  "account": "demo.a@yingshi.local",
  "password": "demo123456"
}
```

Response data includes:

- `userId`
- `account`
- `displayName`
- `libraryId`
- `libraryDisplayName`
- `accessToken`
- `refreshToken`
- token expiry timestamps

`GET /api/health`

Health remains public and does not require an `Authorization` header.

### Authenticated

`GET /api/auth/me`

Requires:

```http
Authorization: Bearer <accessToken>
```

Returns current user basics: id, account/email, display name, optional avatar URL, shared library id, and shared library display name.

`POST /api/auth/logout`

Requires bearer auth. The request body is optional. This stage returns `{ "success": true }`; server-side token revocation is not introduced in Stage 17.

## Auth Boundary

Only methods annotated with `@AuthRequired` require bearer auth. In this stage:

- `/api/auth/login` is public.
- `/api/auth/me` and `/api/auth/logout` are protected.
- `/api/health` is public.
- albums, posts, comments, trash, upload, media feed, and media file endpoints keep their existing protected boundary.

This pass does not loosen private media, post, comment, upload, or media file endpoints. Android still loads `original`, `preview`, and `cover` files through backend media file URLs with bearer auth; it does not connect to MinIO directly and does not store object-storage credentials.

## Android Login Flow

1. App starts and loads any persisted token.
2. If a token exists, Android calls `GET /api/auth/me`.
3. If `/me` succeeds, the main shell opens and the profile page can render current user data.
4. If no token exists, or `/me` fails with unauthorized/expired session, Android clears the token and shows the login page.
5. The login page calls `POST /api/auth/login`.
6. On success, Android persists the access/refresh token bundle and calls `/me`.
7. OkHttp attaches `Authorization: Bearer <accessToken>` to protected requests.
8. Any protected request returning `401` clears the token so the app returns to login.
9. Logout calls `POST /api/auth/logout`, clears local tokens, and returns to login.

FAKE mode keeps using local fake repositories and fake auth data. REAL mode requires `baseUrl` to point at the current backend, for example `http://10.0.2.2:8080/` on an Android emulator.

## Acceptance

Server:

```powershell
.\mvnw.cmd test
```

Manual checks:

```powershell
curl.exe http://localhost:8080/api/health

curl.exe http://localhost:8080/api/auth/me

curl.exe -X POST http://localhost:8080/api/auth/login `
  -H "Content-Type: application/json" `
  -d "{\"account\":\"demo.a@yingshi.local\",\"password\":\"demo123456\"}"

curl.exe http://localhost:8080/api/auth/me `
  -H "Authorization: Bearer <accessToken>"
```

Android:

```powershell
.\gradlew.bat assembleDebug
```

Expected behavior:

- App opens to the login page when there is no valid session.
- Demo quick fill uses `demo.a@yingshi.local / demo123456`.
- REAL mode login succeeds when `baseUrl` points to the running backend.
- Login enters the main shell.
- `我的` shows display name, email, avatar placeholder, intro/library text, current mode, and baseUrl.
- Logout clears local auth state and returns to login.
- Restart keeps a valid saved session, or returns to login if the token is missing/invalid.
