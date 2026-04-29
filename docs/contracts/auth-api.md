# Auth API Draft

## Status
- Server 2 minimal implementation target
- local-dev usable
- not final production auth design

## Purpose
Define the first working backend auth contract for local development:
- account/password login
- bearer-token access
- current-user query
- logout placeholder

## Header Convention
- authenticated requests use:
  - `Authorization: Bearer <accessToken>`
- login does not require bearer auth
- logout uses bearer auth in Server 2

## Token Fields
- `accessToken`
- `refreshToken`
- `accessTokenExpireAtMillis`
- `refreshTokenExpireAtMillis`

## Space Foundation
- each user has a default `spaceId`
- current authenticated context should carry both `userId` and `spaceId`
- later business modules should keep `spaceId` as their ownership field

## Endpoints

### `POST /api/auth/login`
- use case: local account/password login

Request:

```json
{
  "account": "demo.a@yingshi.local",
  "password": "demo123456"
}
```

Response:

```json
{
  "requestId": "req_login",
  "data": {
    "userId": "user_demo_a",
    "account": "demo.a@yingshi.local",
    "displayName": "Demo A",
    "spaceId": "space_demo_shared",
    "spaceDisplayName": "Yingshi Demo Space",
    "accessToken": "access-token-placeholder",
    "refreshToken": "refresh-token-placeholder",
    "accessTokenExpireAtMillis": 1777416400000,
    "refreshTokenExpireAtMillis": 1778021200000
  }
}
```

### `GET /api/auth/me`
- use case: fetch current authenticated user and active space
- requires bearer auth

Response:

```json
{
  "requestId": "req_me",
  "data": {
    "userId": "user_demo_a",
    "account": "demo.a@yingshi.local",
    "displayName": "Demo A",
    "avatarUrl": null,
    "spaceId": "space_demo_shared",
    "spaceDisplayName": "Yingshi Demo Space"
  }
}
```

### `POST /api/auth/logout`
- use case: placeholder logout response for current bearer session
- requires bearer auth
- current stage does not revoke tokens persistently

Request:

```json
{
  "refreshToken": "refresh-token-placeholder"
}
```

Response:

```json
{
  "requestId": "req_logout",
  "data": {
    "success": true
  }
}
```

## Seed Users For Dev
- `demo.a@yingshi.local / demo123456`
- `demo.b@yingshi.local / demo123456`
- both users belong to `space_demo_shared`

## Field Naming
- JSON uses `camelCase`
- identifiers are string IDs
- expiration values use UTC milliseconds

## Error Code Placeholders
- `AUTH_INVALID_CREDENTIALS`
- `AUTH_TOKEN_EXPIRED`
- `AUTH_UNAUTHORIZED`
- `AUTH_SESSION_INVALID`
- `VALIDATION_ERROR`
- `SERVER_ERROR`

## Server 2 Notes
- no phone-code, OAuth, WeChat, or third-party login in this stage
- refresh token is issued but not backed by full session storage or revocation
- login is intentionally local-first for backend bootstrapping
