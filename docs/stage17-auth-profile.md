# Stage 17 Auth And Profile

## Scope

This pass closes the REAL-mode login loop and adds the personal profile loop:

- account/password login with the demo account
- bearer access token on protected requests
- current-user lookup for the profile page
- current-user profile update for nickname and intro
- logout endpoint for the client logout action

Out of scope: registration, password reset, refresh-token exchange, third-party login, avatar upload, relationship binding, direct MinIO access from Android, and any media read/upload contract changes.

## Demo Account

The local development profiles seed and maintain these accounts:

- `demo.a@yingshi.local / demo123456`
- `demo.b@yingshi.local / demo123456`

Profiles covered by the seed: `dev`, `docker`, and `docker-local`.

Passwords are stored as BCrypt hashes through the shared `PasswordEncoder`. The seed is idempotent: if demo accounts or the shared library are missing in an existing local database, they are created; if a demo password hash no longer matches `demo123456`, it is replaced with a fresh hash.

Demo users also carry lightweight profile data so the personal page can show a nickname and intro in `dev`, `docker`, and `docker-local`.

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
- `avatarUrl`
- `bio`
- `libraryId`
- `libraryDisplayName`
- `createdAtMillis`
- `updatedAtMillis`
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

Returns current user basics plus the lightweight profile fields used by the personal page: `bio`, `createdAtMillis`, and `updatedAtMillis`.

`PATCH /api/auth/me/profile`

Requires bearer auth.

Request:

```json
{
  "displayName": "映世小屋",
  "bio": "把两个人的日常安静收进这里。"
}
```

The server only updates the authenticated user row. There is no path-based user selection here, so a client cannot edit another user by mistake or by tampering with the body. The response returns the updated current-user profile payload.

`POST /api/auth/logout`

Requires bearer auth. The request body is optional. This stage returns `{ "success": true }`; server-side token revocation is not introduced in Stage 17.

## Auth Boundary

Only methods annotated with `@AuthRequired` require bearer auth. In this stage:

- `/api/auth/login` is public.
- `/api/auth/me`, `/api/auth/me/profile`, and `/api/auth/logout` are protected.
- `/api/health` is public.
- albums, posts, comments, trash, upload, media feed, and media file endpoints keep their existing protected boundary.

This pass does not loosen private media, post, comment, upload, or media file endpoints. Android still loads `original`, `preview`, and `cover` files through backend media file URLs with bearer auth; it does not connect to MinIO directly and does not store object-storage credentials.

## Android Login Flow

1. App starts and loads any persisted token.
2. If a token exists, Android calls `GET /api/auth/me`.
3. If `/me` succeeds, the main shell opens and the profile page can render current user data.
4. If no token exists, or `/me` fails with unauthorized/expired session, Android clears the token and shows the login page.
5. The login page calls `POST /api/auth/login`.
6. On success, Android persists the access/refresh token bundle and enters the main shell immediately.
7. OkHttp attaches `Authorization: Bearer <accessToken>` to protected requests.
8. Any protected request returning `401` clears the token so the app returns to login.
9. The personal page can edit nickname and intro through `PATCH /api/auth/me/profile`.
10. Logout calls `POST /api/auth/logout`, clears local tokens, and returns to login.

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
- `我的` shows a tappable profile card, current mode, baseUrl, and logout.
- The personal page shows display name, email, avatar placeholder, intro, account, and join time.
- Editing nickname and intro updates the server and refreshes both the personal page and `我的`.
- Logout clears local auth state and returns to login.
- Restart keeps a valid saved session, or returns to login if the token is missing/invalid.
