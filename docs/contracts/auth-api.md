# Auth API Draft

## Status
- Stage 11.2 draft only
- no live backend required
- no final login UI or account system required yet

## Purpose
Reserve the contract for future login, bearer-token injection, token refresh, logout, and current-user queries.

## Header Convention
- authenticated requests use:
  - `Authorization: Bearer <accessToken>`
- login and refresh-token requests do not require the bearer header

## Token Fields
- `accessToken`
- `refreshToken`
- `accessTokenExpireAtMillis`
- `refreshTokenExpireAtMillis`

## Endpoints

### `POST /v1/auth/login`
- use case: username/password login draft

Request draft:

```json
{
  "account": "demo@yingshi.local",
  "password": "placeholder-password",
  "deviceName": "Android Placeholder",
  "clientVersion": "1.0"
}
```

Response draft:

```json
{
  "requestId": "req_login",
  "data": {
    "userId": "user_001",
    "displayName": "Local Placeholder User",
    "spaceId": "space_001",
    "accessToken": "access-token-placeholder",
    "refreshToken": "refresh-token-placeholder",
    "accessTokenExpireAtMillis": 1777416400000,
    "refreshTokenExpireAtMillis": 1778021200000
  }
}
```

### `POST /v1/auth/refresh-token`
- use case: refresh expired access token

Request draft:

```json
{
  "refreshToken": "refresh-token-placeholder"
}
```

Response draft:

```json
{
  "requestId": "req_refresh",
  "data": {
    "accessToken": "new-access-token-placeholder",
    "refreshToken": "new-refresh-token-placeholder",
    "accessTokenExpireAtMillis": 1777418200000,
    "refreshTokenExpireAtMillis": 1778021200000
  }
}
```

### `POST /v1/auth/logout`
- use case: clear current auth session on server side

Request draft:

```json
{
  "refreshToken": "refresh-token-placeholder"
}
```

Response draft:

```json
{
  "requestId": "req_logout",
  "data": {
    "success": true
  }
}
```

### `GET /v1/auth/me`
- use case: fetch current user info

Response draft:

```json
{
  "requestId": "req_me",
  "data": {
    "userId": "user_001",
    "displayName": "Local Placeholder User",
    "avatarUrl": null,
    "spaceId": "space_001",
    "spaceDisplayName": "Yingshi Demo Space"
  }
}
```

## Field Naming
- JSON uses `camelCase`
- account identifiers are strings
- expiration values use UTC milliseconds

## Error Code Placeholders
- `AUTH_INVALID_CREDENTIALS`
- `AUTH_TOKEN_EXPIRED`
- `AUTH_REFRESH_EXPIRED`
- `AUTH_UNAUTHORIZED`
- `AUTH_SESSION_INVALID`
- `AUTH_LOGOUT_FAILED`
- `NOT_IMPLEMENTED`

## Stage 11.2 Draft-Only Notes
- no OAuth, phone-code, WeChat, or third-party login in this stage
- no persistent token storage in this stage
- no real refresh retry policy in this stage
- current login contract is a placeholder and may later switch from password to another auth method
