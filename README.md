# YingShi Server

Updated: 2026-05-31

This repository hosts the YingShi backend API used by the Android app in `REAL` mode. The current outward content model is:

- `album = 大相册`
- `small album = 小相册`
- media can belong to multiple small albums
- comments are split into small-album comments and media comments

## Current Runtime Modes

- `dev` is still the default local profile. It uses file-based H2 plus `local-storage` for fast bootstrap.
- `docker-local` is the recommended daily integration mode on Windows: PostgreSQL and MinIO run in Docker Desktop, while the Spring Boot process runs from IDEA on Windows.
- `docker` is the full Docker Compose stack: Server container plus PostgreSQL and MinIO.

## Latest Backend Progress

- Auth is session-aware end to end: login, refresh, current user, logout, profile update, avatar upload/read.
- Refresh tokens are persisted in `auth_sessions`, rotated on every refresh, and invalidated when an old refresh token is reused.
- Small-album APIs are hard-cut to `/api/small-albums` and album-child listing is `/api/albums/{albumId}/small-albums`.
- Comment audit fields are stored in PostgreSQL, enabling notification variants for comment create, edit, and delete.
- Trash flows cover restore, remove, undo-remove, purge, and scheduled cleanup of expired `PENDING_CLEANUP` items.
- Media delivery stays behind backend URLs such as `/api/media/files/{mediaId}?variant=original|preview|cover`, with HTTP range support for large originals.

## Current API Surface

Public:

- `GET /api/health`
- `POST /api/auth/login`
- `POST /api/auth/refresh-token`

Authenticated:

- `GET /api/auth/me`
- `PATCH /api/auth/me/profile`
- `POST /api/auth/logout`
- `POST /api/auth/me/avatar`
- `GET /api/auth/avatar/{userId}`
- `GET /api/albums`
- `GET /api/albums/{albumId}/small-albums`
- `GET /api/small-albums`
- `GET /api/small-albums/{smallAlbumId}`
- `POST /api/small-albums`
- `PATCH /api/small-albums/{smallAlbumId}`
- `PATCH /api/small-albums/{smallAlbumId}/cover`
- `PATCH /api/small-albums/{smallAlbumId}/media-order`
- `POST /api/small-albums/{smallAlbumId}/media`
- `DELETE /api/small-albums/{smallAlbumId}`
- `DELETE /api/small-albums/{smallAlbumId}/media/{mediaId}`
- `GET /api/media/feed`
- `GET /api/media/files/{mediaId}`
- `DELETE /api/media/{mediaId}`
- `GET /api/small-albums/{smallAlbumId}/comments`
- `GET /api/media/{mediaId}/comments`
- `POST /api/small-albums/{smallAlbumId}/comments`
- `POST /api/media/{mediaId}/comments`
- `PATCH /api/comments/{commentId}`
- `DELETE /api/comments/{commentId}`
- `GET /api/trash/items`
- `GET /api/trash/items/{trashItemId}`
- `POST /api/trash/items/{trashItemId}/restore`
- `POST /api/trash/items/{trashItemId}/remove`
- `POST /api/trash/items/{trashItemId}/purge`
- `POST /api/trash/items/{trashItemId}/undo-remove`
- `GET /api/trash/pending-cleanup`
- `POST /api/uploads/token`
- `POST /api/uploads/{uploadId}/file`
- `GET /api/uploads/{uploadId}`
- `POST /api/uploads/{uploadId}/confirm`
- `POST /api/uploads/{uploadId}/cancel`
- `GET /api/notifications`
- `GET /api/notifications/{notificationId}`
- `POST /api/notifications/{notificationId}/read`
- `POST /api/notifications/read-all`

## Android Alignment

Already consumed by Android `REAL` repositories:

- login / refresh-token / session restore / logout
- current user / profile update / shared-library display
- large-album list, child small-album list, small-album list/detail/create/update/cover/media-order/add-media/delete
- media feed and backend media-file delivery
- small-album comments and media comments create/edit/delete
- uploads token + multipart file upload + task status + confirm + cancel
- trash list/detail/restore/remove/purge/undo-remove/pending-cleanup
- notification list/detail/read/read-all

## Daily Commands

Run default `dev` mode:

```powershell
cd E:\Study\App\YingShi-Server
.\mvnw.cmd spring-boot:run
```

Run tests:

```powershell
.\mvnw.cmd test
```

Useful URLs:

- health: `http://localhost:8080/api/health`
- Swagger UI in `dev`: `http://localhost:8080/swagger-ui.html`
- OpenAPI in `dev`: `http://localhost:8080/v3/api-docs`
- H2 Console in `dev`: `http://localhost:8080/h2-console`

## Docs

- [API Overview](docs/contracts/api-overview.md)
- [Auth API](docs/contracts/auth-api.md)
- [Album API](docs/contracts/album-api.md)
- [Small Album API](docs/contracts/post-api.md)
- [Comment API](docs/contracts/comment-api.md)
- [Media API](docs/contracts/media-api.md)
- [Upload API](docs/contracts/upload-api.md)
- [Notification API](docs/contracts/notification-api.md)
- [Trash API](docs/contracts/trash-api.md)
- [Android REAL Contract](docs/contracts/android-real-stage16-contract.md)
- [Backend Manual Testing Guide](docs/test/backend-manual-testing-guide.md)
