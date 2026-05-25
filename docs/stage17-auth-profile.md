# Stage 17 Auth And Profile

## Scope

> Historical note: this document records the original Stage 17 scope. Later stages have since added refresh-token exchange and avatar upload. For current truth, use `docs/contracts/auth-api.md`.

Stage 17 now includes three connected parts:

- REAL-mode account/password login
- current-user profile read, edit, and logout
- a lightweight fixed two-person shared space for `demo.a` and `demo.b`

This app is currently intended only for two fixed users, so this stage does not introduce a generic relationship system, invite flow, binding review, unbind flow, or multi-space organization model.

Out of scope at Stage 17 time: registration, password reset, third-party login, chat, direct MinIO access from Android, and any media read/upload contract changes. Refresh-token exchange and avatar upload were added later.

## Fixed Shared Space

The local seed keeps two demo accounts available in `dev`, `docker`, and `docker-local`:

- `demo.a@yingshi.local / demo123456`
- `demo.b@yingshi.local / demo123456`

Seed behavior:

- both accounts always point to the same `defaultLibraryId`
- both accounts are members of `library_shared`
- the shared library display name is `我们的小空间`
- passwords are stored as BCrypt hashes through the shared `PasswordEncoder`
- if a demo password hash no longer matches `demo123456`, seed logic rewrites it with a fresh BCrypt hash

Default seeded profile copy:

- `demo.a@yingshi.local` -> `映世小屋`
- `demo.b@yingshi.local` -> `另一半`

Current media, album, post, comment, trash, upload, and media-file authorization remains library-scoped. Because both demo accounts belong to the same shared library, they see the same shared content set after login.

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

Response data now includes:

- `userId`
- `account`
- `displayName`
- `avatarUrl`
- `bio`
- `libraryId`
- `libraryDisplayName`
- `partner`
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

Returns current user basics plus:

- lightweight profile fields used by the personal page
- shared library identity
- `partner` basic profile for the other fixed member in the same shared space

Example `partner` payload:

```json
{
  "userId": "user_demo_b",
  "account": "demo.b@yingshi.local",
  "displayName": "另一半",
  "avatarUrl": null,
  "bio": "把生活里的闪光片段，也把安静和想念一起留下来。"
}
```

`PATCH /api/auth/me/profile`

Requires bearer auth.

Request:

```json
{
  "displayName": "映世小屋",
  "bio": "把两个人的日常安静收进这里。"
}
```

The server only updates the authenticated user row. There is no path-based user selection, so a client cannot edit another user by mistake or by tampering with the body.

`POST /api/auth/logout`

Requires bearer auth. The request body is optional. Stage 17 still returns:

```json
{ "success": true }
```

Server-side token revocation is not introduced in this stage.

## Auth Boundary

Only methods annotated with `@AuthRequired` require bearer auth. In this stage:

- `/api/auth/login` is public
- `/api/auth/me`, `/api/auth/me/profile`, and `/api/auth/logout` are protected
- `/api/health` is public
- albums, posts, comments, trash, upload, media feed, and media file endpoints remain protected

This pass does not loosen private media, post, comment, upload, or media file endpoints. Android still loads `original`, `preview`, and `cover` through backend media file URLs with bearer auth. It does not connect to MinIO directly and does not store object-storage credentials.

## Android Behavior

### Login And Session

1. App starts and loads any persisted token.
2. If a token exists, Android calls `GET /api/auth/me`.
3. If `/me` succeeds, the main shell opens.
4. If no token exists, or `/me` returns unauthorized, Android clears local tokens and shows the login page.
5. The login page calls `POST /api/auth/login`.
6. On success, Android persists the token bundle and enters the main shell immediately.
7. OkHttp attaches `Authorization: Bearer <accessToken>` to protected requests.
8. Any protected request returning `401` clears the token so the app returns to login.
9. Logout calls `POST /api/auth/logout`, clears local tokens, and returns to login.

### Shared Space Presentation

- `我的` shows the current user card, shared-space card, partner card, environment info, baseUrl, and logout
- the personal profile page shows current-user info plus a partner section
- FAKE mode now also uses fixed two-person demo data so the same shared-space feeling remains available without a backend
- REAL mode reads partner data from the server response

### Profile Refresh

- editing nickname and bio calls `PATCH /api/auth/me/profile`
- after save, both `我的` and the personal profile page reuse the updated current-user data
- entering the personal profile page may render cached user data first and then refresh `/me` in the background

### Error Handling

- unauthorized responses from `/me` and profile update are treated as session expiry
- wrong `baseUrl`, stopped backend services, and LAN failures should show a natural error instead of a white screen or loading loop
- REAL-mode physical-device verification should use the current LAN backend address instead of an expired temporary tunnel URL

## Acceptance

Server:

```powershell
.\mvnw.cmd test
```

Manual checks:

```powershell
curl.exe http://localhost:8080/api/health

curl.exe -X POST http://localhost:8080/api/auth/login `
  -H "Content-Type: application/json" `
  -d "{\"account\":\"demo.a@yingshi.local\",\"password\":\"demo123456\"}"

curl.exe -X POST http://localhost:8080/api/auth/login `
  -H "Content-Type: application/json" `
  -d "{\"account\":\"demo.b@yingshi.local\",\"password\":\"demo123456\"}"

curl.exe http://localhost:8080/api/auth/me `
  -H "Authorization: Bearer <accessToken>"
```

Expected server behavior:

- both demo accounts can log in
- both demo accounts return the same `libraryId` and `libraryDisplayName`
- current user can read partner basic profile from login and `/me`
- unauthenticated access to account and content APIs fails
- `GET /api/health` stays public

Android:

```powershell
.\gradlew.bat --no-daemon assembleDebug
```

Expected Android behavior:

- app opens to login when there is no valid session
- demo quick fill uses `demo.a@yingshi.local / demo123456`
- REAL mode login succeeds when `baseUrl` points to the running backend
- both demo accounts can log in
- `我的` shows current account, partner info, shared-space hint, account state, and backend info
- the personal profile page shows current-user info and partner info together
- editing nickname and intro updates the server and refreshes both profile surfaces
- logout clears local auth state and returns to login
- restart keeps a valid saved session, or returns to login if the token is missing or invalid
