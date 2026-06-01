# Auth API Contract

Updated: 2026-05-25

## Status

- This document matches the current `yingshi-server` code.
- Base path: `/api/auth`
- There is no `/v1` prefix yet.
- `POST /login` and `POST /refresh-token` are public.
- `GET /me`, `PATCH /me/profile`, `POST /logout`, `POST /me/avatar`, and `GET /avatar/{userId}` require bearer auth.

## Shared Demo Model

Seed accounts:

- `demo.a@yingshi.local / demo123456`
- `demo.b@yingshi.local / demo123456`

Current behavior:

- both demo users belong to the same shared library `library_shared`
- both see the same shared content set
- `/login` and `/me` include lightweight `partner` information

## Token And Session Rules

- Login issues an access token plus a refresh token.
- Refresh rotates both tokens and updates the persisted server-side auth session.
- Refresh tokens are backed by table `auth_sessions`.
- Reusing an old refresh token revokes that session and returns `AUTH_SESSION_INVALID`.
- Logout revokes the current session, so the current access token can no longer call protected APIs.
- If `POST /logout` includes a `refreshToken` body field, that refresh token must belong to the same current session.

## 1. `POST /api/auth/login`

Request:

```json
{
  "account": "demo.a@yingshi.local",
  "password": "demo123456"
}
```

Response `data` shape:

```json
{
  "userId": "user_demo_a",
  "account": "demo.a@yingshi.local",
  "displayName": "Demo A",
  "avatarUrl": "/api/auth/avatar/user_demo_a",
  "bio": "Profile bio",
  "libraryId": "library_shared",
  "libraryDisplayName": "Shared Library",
  "partner": {
    "userId": "user_demo_b",
    "account": "demo.b@yingshi.local",
    "displayName": "Demo B",
    "avatarUrl": null,
    "bio": "Profile bio"
  },
  "createdAtMillis": 1760000000000,
  "updatedAtMillis": 1760000000000,
  "accessToken": "jwt-access-token",
  "refreshToken": "jwt-refresh-token",
  "accessTokenExpireAtMillis": 1760001800000,
  "refreshTokenExpireAtMillis": 1760604800000
}
```

## 2. `POST /api/auth/refresh-token`

Request:

```json
{
  "refreshToken": "jwt-refresh-token"
}
```

Response `data`:

```json
{
  "accessToken": "jwt-access-token-new",
  "refreshToken": "jwt-refresh-token-new",
  "accessTokenExpireAtMillis": 1760005400000,
  "refreshTokenExpireAtMillis": 1760608400000
}
```

Notes:

- the old refresh token becomes invalid immediately after a successful refresh
- retrying the same old refresh token returns `401 AUTH_SESSION_INVALID`

## 3. `GET /api/auth/me`

Request header:

```http
Authorization: Bearer <accessToken>
```

Response `data`:

```json
{
  "userId": "user_demo_a",
  "account": "demo.a@yingshi.local",
  "displayName": "Demo A",
  "avatarUrl": "/api/auth/avatar/user_demo_a",
  "bio": "Profile bio",
  "libraryId": "library_shared",
  "libraryDisplayName": "Shared Library",
  "partner": {
    "userId": "user_demo_b",
    "account": "demo.b@yingshi.local",
    "displayName": "Demo B",
    "avatarUrl": null,
    "bio": "Profile bio"
  },
  "createdAtMillis": 1760000000000,
  "updatedAtMillis": 1760000000000
}
```

## 4. `PATCH /api/auth/me/profile`

Request:

```json
{
  "displayName": "Demo A",
  "bio": "Updated profile bio"
}
```

Validation:

- `displayName` is required and capped at `80`
- `bio` is optional and capped at `280`

Response:

- returns the same `AuthCurrentUserResponse` shape as `/api/auth/me`

## 5. `POST /api/auth/logout`

Body may be omitted, or may include the current refresh token:

```json
{
  "refreshToken": "jwt-refresh-token-new"
}
```

Response `data`:

```json
{
  "success": true
}
```

Notes:

- logout revokes the current auth session
- the current access token can no longer call `/api/auth/me`
- a revoked session returns `401 AUTH_SESSION_INVALID`

## 6. `POST /api/auth/me/avatar`

Request:

- content type: `multipart/form-data`
- field name must be `file`

Response:

- returns updated `AuthCurrentUserResponse`
- `avatarUrl` becomes `/api/auth/avatar/{userId}` after a successful upload

Notes:

- the backend currently normalizes avatar content to JPEG
- Android `REAL` mode now consumes this route for edit-profile avatar upload and profile/avatar display

## 7. `GET /api/auth/avatar/{userId}`

Response:

- `200 image/jpeg` when an avatar exists
- `404` when the user has no avatar yet

Notes:

- bearer auth is required
- only users inside the same shared library can read the avatar

## Not Yet Provided

- public registration
- password reset
- third-party login

## Error Codes

- `AUTH_INVALID_CREDENTIALS`
- `AUTH_TOKEN_EXPIRED`
- `AUTH_UNAUTHORIZED`
- `AUTH_SESSION_INVALID`
- `FORBIDDEN`
- `NOT_FOUND`
- `VALIDATION_ERROR`
- `SERVER_ERROR`
