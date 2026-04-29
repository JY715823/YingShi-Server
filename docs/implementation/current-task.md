# Current Task - Server 2 User, Auth, and Space Foundation

## Goal
Build the minimal backend account system, JWT auth shell, and shared-space base model so local development can move off fake auth.

## In Scope
- `User`, `Space`, and `SpaceMember` base entities
- dev-only seed data for two fixed local users
- account and password login
- JWT access token generation and parsing
- refresh token issuance as a placeholder contract output
- current-user API
- logout placeholder API
- auth interceptor and current-user resolution for protected APIs
- shared `spaceId` ownership foundation for future modules
- minimal auth contract sync in `docs/contracts/auth-api.md`

## Product Intent
- Yingshi is a two-person shared memory app centered around a shared space.
- Future posts, media, albums, comments, and trash modules should all carry `spaceId` ownership.
- Auth should be intentionally simple in this stage, but should not block later refresh-token hardening or third-party auth expansion.

## Non-Goals
- no phone verification code login
- no OAuth
- no WeChat login
- no complex permission model
- no refresh-token persistence or revocation strategy
- no business module tables
- no upload implementation

## Server 2 API Shape
- `POST /api/auth/login`
- `GET /api/auth/me`
- `POST /api/auth/logout`
- `GET /api/health`

## Local Dev Assumptions
- local profile uses H2
- schema can be created from JPA entities
- Swagger/OpenAPI stays available in `dev`
- seed users belong to one shared demo space

## Done When
- server starts locally
- login succeeds for seeded users and returns `accessToken` and `refreshToken`
- bearer token can access `GET /api/auth/me`
- missing or invalid token returns `401`
- `/api/health` remains public
- auth and shared-space tables are created automatically
- project builds and tests pass
