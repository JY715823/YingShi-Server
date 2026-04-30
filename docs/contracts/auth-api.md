# Auth API Contract

## Status
- unified with current `yingshi-server` code
- local-dev usable
- no `/v1` prefix

## Base Rules
- base path: `/api/auth`
- login is public
- `GET /me` and `POST /logout` require `Authorization: Bearer <accessToken>`
- there is no refresh-token endpoint in current backend code

## Endpoints

### `POST /api/auth/login`

Request:

```json
{
  "account": "demo.a@yingshi.local",
  "password": "demo123456"
}
```

Response data:

```json
{
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
```

### `GET /api/auth/me`

Response data:

```json
{
  "userId": "user_demo_a",
  "account": "demo.a@yingshi.local",
  "displayName": "Demo A",
  "avatarUrl": null,
  "spaceId": "space_demo_shared",
  "spaceDisplayName": "Yingshi Demo Space"
}
```

### `POST /api/auth/logout`

Notes:
- bearer auth required
- request body is optional
- backend currently ignores the request payload and returns a placeholder success

Optional request body:

```json
{
  "refreshToken": "refresh-token-placeholder"
}
```

Response data:

```json
{
  "success": true
}
```

## Dev Seed Accounts
- `demo.a@yingshi.local / demo123456`
- `demo.b@yingshi.local / demo123456`

## Error Codes
- `AUTH_INVALID_CREDENTIALS`
- `AUTH_TOKEN_EXPIRED`
- `AUTH_UNAUTHORIZED`
- `AUTH_SESSION_INVALID`
- `VALIDATION_ERROR`
- `SERVER_ERROR`
