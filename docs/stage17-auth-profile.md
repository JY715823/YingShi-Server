# Stage 17 Auth And Profile

Updated: 2026-05-25

## Scope

This document records the original Stage 17 auth/profile baseline and the current follow-up state around it.

For the exact current API contract, use `docs/contracts/auth-api.md`.

Stage 17 established:

- real account/password login
- current-user profile read/edit
- a lightweight fixed two-person shared space for `demo.a` and `demo.b`

Later backend work added:

- refresh-token exchange
- avatar upload/read
- persisted auth sessions
- refresh-token rotation
- logout session revocation

## Fixed Shared Space

Seeded demo accounts:

- `demo.a@yingshi.local / demo123456`
- `demo.b@yingshi.local / demo123456`

Behavior:

- both accounts belong to `library_shared`
- both see the same shared content
- login and `/me` include lightweight partner information

This project still does not introduce a generic invite/binding/multi-space system in the current phase.

## Current Auth Reality

The original Stage 17 baseline has now been hardened:

- `/api/auth/login` returns current-user data plus access/refresh tokens
- `/api/auth/refresh-token` rotates both tokens
- auth sessions are persisted in PostgreSQL table `auth_sessions`
- reusing an old refresh token invalidates that session
- `/api/auth/logout` revokes the current session
- a revoked session returns `AUTH_SESSION_INVALID`

So the old Stage 17 statement "no server-side token revocation" is no longer true.

## Android Behavior

Current Android `REAL` mode behavior:

1. App restores any saved token bundle.
2. App calls `GET /api/auth/me` on launch.
3. If `/me` succeeds, the main shell opens.
4. If `/me` fails with auth/session errors, Android clears the local session and returns to login.
5. Login persists the new token bundle immediately.
6. Logout clears local auth state and returns to login.
7. `RealAuthRepository` already supports refresh-token exchange, but centralized global auto-refresh/retry is still incomplete.

## Current Gaps

- no public registration
- no password reset
- no third-party login
- Android UI still does not fully expose avatar upload/read

## Acceptance Reference

Backend:

```powershell
.\mvnw.cmd test
```

Android:

```powershell
.\gradlew.bat --no-daemon assembleDebug
```
