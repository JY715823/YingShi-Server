# Auth API Contract

Updated: 2026-06-09

## Status

- Base path: `/api/auth`
- Public routes: `POST /login/challenge`, `POST /login/challenge/resend`, `POST /login/verify`, `POST /login/remembered`, `POST /refresh-token`
- Bearer-auth routes: `GET /me`, `PATCH /me/profile`, `POST /logout`, `POST /me/avatar`, `GET /avatar/{userId}`
- login is no longer single-step token issuance

## Shared Two-Person Model

Seed accounts:

- `1085060329@qq.com / 123456`
- `2926315047@qq.com / 123456`

Current behavior:

- both users belong to the same shared library `library_shared`
- both see the same shared content set
- `/login/verify` and `/me` include lightweight `partner` information

## Login Code Rules

- code length: `6`
- code TTL: `5 minutes`
- resend cooldown: `60 seconds`
- per-account limit: `30 minutes up to 5 sends`
- per-challenge wrong attempts: `5`
- only hashed codes are stored in `auth_login_challenges`

## Token And Session Rules

- `POST /login/verify` issues the access token plus refresh token
- verified login also issues a short-term same-device remembered-login token
- `POST /login/remembered` can create a new session on the same device without resending email code while the remembered token is still valid
- refresh rotates both tokens and updates the persisted `auth_sessions` record
- reusing an old refresh token revokes that session and returns `AUTH_SESSION_INVALID`
- logout revokes the current session
- if `POST /logout` includes a `refreshToken`, it must belong to the same session

## 1. `POST /api/auth/login/challenge`

Request:

```json
{
  "account": "1085060329@qq.com",
  "password": "123456"
}
```

Response:

```json
{
  "challengeId": "login_challenge_xxx",
  "maskedEmail": "108***29@qq.com",
  "expireAtMillis": 1780000000000,
  "resendAvailableAtMillis": 1780000060000
}
```

Notes:

- validates account/password
- invalidates prior active challenges for the same account
- sends a real QQ email code through configured SMTP
- does not create `auth_sessions`

## 2. `POST /api/auth/login/challenge/resend`

Request:

```json
{
  "challengeId": "login_challenge_xxx"
}
```

Response shape matches `/api/auth/login/challenge`.

## 3. `POST /api/auth/login/verify`

Request:

```json
{
  "challengeId": "login_challenge_xxx",
  "code": "123456",
  "deviceId": "android-install-id"
}
```

Response `data` shape:

```json
{
  "userId": "user_demo_a",
  "account": "1085060329@qq.com",
  "displayName": "映世小屋",
  "avatarUrl": "/api/auth/avatar/user_demo_a",
  "bio": "Profile bio",
  "libraryId": "library_shared",
  "libraryDisplayName": "我们的小空间",
  "partner": {
    "userId": "user_demo_b",
    "account": "2926315047@qq.com",
    "displayName": "另一半",
    "avatarUrl": null,
    "bio": "Profile bio"
  },
  "createdAtMillis": 1760000000000,
  "updatedAtMillis": 1760000000000,
  "rememberedLoginToken": "opaque-remembered-login-token",
  "rememberedLoginExpireAtMillis": 1760604800000,
  "accessToken": "jwt-access-token",
  "refreshToken": "jwt-refresh-token",
  "accessTokenExpireAtMillis": 1760001800000,
  "refreshTokenExpireAtMillis": 1760604800000
}
```

## 4. `POST /api/auth/login/remembered`

Request:

```json
{
  "account": "1085060329@qq.com",
  "password": "123456",
  "deviceId": "android-install-id",
  "rememberedLoginToken": "opaque-remembered-login-token"
}
```

Response shape matches `/api/auth/login/verify`.

## 5. `POST /api/auth/refresh-token`

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

## 6. `GET /api/auth/me`

Request header:

```http
Authorization: Bearer <accessToken>
```

Response:

- same current-user shape as the verify-login response without token fields

## 7. `PATCH /api/auth/me/profile`

Request:

```json
{
  "displayName": "映世小屋",
  "bio": "Updated profile bio"
}
```

Validation:

- `displayName` required, max `80`
- `bio` optional, max `280`

## 8. `POST /api/auth/logout`

Body may be omitted, or may include the current refresh token:

```json
{
  "refreshToken": "jwt-refresh-token-new"
}
```

Response:

```json
{
  "success": true
}
```

## 9. `POST /api/auth/me/avatar`

Request:

- content type: `multipart/form-data`
- field name: `file`

Response:

- updated `AuthCurrentUserResponse`

## 10. `GET /api/auth/avatar/{userId}`

Response:

- `200 image/jpeg` when an avatar exists
- `404` when the user has no avatar yet

## SMTP And Config

- SMTP config group: `app.auth.mail.*`
- login-code config group: `app.auth.login-code.*`
- remembered-login config group: `app.auth.remembered-login.*`
- default sender for this round is planned around `1085060329@qq.com`
- local/dev deployment still requires a valid QQ SMTP authorization code in environment or config override

## Not Yet Provided

- public registration
- password reset
- third-party login

## Error Codes

- `AUTH_ACCOUNT_LOCKED`
- `AUTH_INVALID_CREDENTIALS`
- `AUTH_LOGIN_CHALLENGE_INVALID`
- `AUTH_LOGIN_CODE_EXPIRED`
- `AUTH_LOGIN_CODE_INVALID`
- `AUTH_LOGIN_CODE_RATE_LIMITED`
- `AUTH_LOGIN_CODE_RESEND_TOO_FAST`
- `AUTH_LOGIN_CODE_SEND_FAILED`
- `AUTH_REMEMBERED_LOGIN_EXPIRED`
- `AUTH_REMEMBERED_LOGIN_INVALID`
- `AUTH_TOKEN_EXPIRED`
- `AUTH_UNAUTHORIZED`
- `AUTH_SESSION_INVALID`
- `FORBIDDEN`
- `NOT_FOUND`
- `VALIDATION_ERROR`
- `SERVER_ERROR`

## Account Lockout

- Triggered by `POST /api/auth/login/challenge` and `POST /api/auth/login/remembered` after `app.auth.account-lockout.max-attempts` (default 5) consecutive password failures
- HTTP status: `429 Too Many Requests`
- Lock duration: `app.auth.account-lockout.lock-duration` (default 15 minutes)
- Failed counter resets to 0 on successful password validation or after lock expiry
- Verification code failures do NOT trigger account lockout (only invalidate the challenge)
- Response body does not include `lockedUntil` / remaining time — clients should show a generic "try again later" message
- Lock check happens before password validation in `AuthService.requireAccountNotLocked`
