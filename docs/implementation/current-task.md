# Current Task: Backend Hardening And Cloudlike Ops Closure

Updated: 2026-05-25

## Background

The core Android-facing APIs are already usable. This pass closes the remaining backend hardening and local cloudlike operations gaps so the project can keep building on a stable base instead of repeatedly revisiting auth, trash cleanup, or Docker storage setup.

## Goals

1. Keep auth session state on the server, not only inside client-side JWTs.
2. Revoke sessions on logout and invalidate reused refresh tokens.
3. Persist comment audit fields so notifications can distinguish comment create, edit, and delete.
4. Automatically purge expired trash items that are waiting in `PENDING_CLEANUP`.
5. Make the Docker runtime image support video-cover generation with `ffmpeg`.
6. Add a repeatable backup path for PostgreSQL metadata plus MinIO objects.
7. Update backend and Android documentation so the repos reflect the current truth.

## Scope

- `AuthService`, JWT/session chain, and PostgreSQL migration ownership for auth session data
- comment audit persistence and notification materialization
- trash pending-cleanup scheduler
- Docker runtime image and local cloudlike scripts
- cross-repo status, contract, and integration docs

## Non Goals

- no generic registration flow
- no third-party login
- no direct Android-to-MinIO or Android-to-OSS access
- no production cloud deployment yet

## Acceptance

1. Logging out makes the current access token fail protected requests with `AUTH_SESSION_INVALID`.
2. Refresh rotates tokens and invalidates the old refresh token.
3. Notifications can surface comment create, comment edit, and comment delete variants.
4. Expired `PENDING_CLEANUP` trash items can be purged by the scheduler path.
5. The Docker server image contains `ffmpeg`.
6. A single script can back up PostgreSQL and MinIO together.
7. `README` and relevant implementation / contract / integration docs match the current code, including Android notification integration status.

## Recommended Follow-Ups After This Pass

1. Run a full restore drill from backup artifacts into a clean PostgreSQL + MinIO environment.
2. Only design ledger backend after the redesigned Android UI and data model are stable.
