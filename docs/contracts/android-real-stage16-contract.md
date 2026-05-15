# Android REAL Stage 16 API Contract

## Purpose

This document lists the Server APIs currently consumed by Android REAL mode and marks which endpoints must stay compatible when Server storage moves from local files to `ObjectStorageService`, MinIO, and later OSS.

Android REAL must keep using backend APIs. It must not direct-connect to MinIO or OSS and must not store object-storage credentials.

## Base URL

Android REAL has a configurable backend `baseUrl` in the diagnostics/settings flow.

- The default/debug base URL is only a starting value.
- Users can edit and persist another backend URL, such as emulator loopback, a LAN IP, or a Cloudflare Tunnel URL.
- Relative media paths returned by Server are resolved against this backend `baseUrl`.
- Android must not infer object storage endpoints from media ids, object keys, or provider names.

## Auth Dependency

Android REAL uses bearer auth for protected APIs:

- `POST /api/auth/login`
- `GET /api/auth/me`
- `POST /api/auth/logout`

The token is attached by Android's auth interceptor. Media image/video requests also attach `Authorization` headers when loading backend HTTP URLs through Coil or Media3.

## Health And Diagnostics

Diagnostics uses:

- `GET /api/health`

The diagnostics smoke path also exercises login, albums, media, comments, trash, upload token, and multipart upload.

## Media Feed

Photo feed REAL uses:

- `GET /api/media/feed?cursor={cursor}&pageSize={pageSize}`

Compatibility requirements:

- Response envelope `data` is a list of media DTOs.
- Pagination metadata may provide `page.nextCursor` and `page.hasMore`.
- Media ids stay stable.
- `postIds` may be empty for import-only media.
- Server-side storage migration must not require Android to know `bucket` or `objectKey`.

Current Android URL field compatibility:

- Canonical media URL: `mediaUrl` or `url`
- Preview/poster URL: `thumbnailUrl` or `previewUrl`
- Original image URL: `originalUrl`
- Video playback URL: `videoUrl`, then `mediaUrl`, then `originalUrl`
- Video poster URL: `thumbnailUrl`, `previewUrl`, or `coverUrl`

Server may keep returning relative paths such as `/api/media/files/{mediaId}?variant=preview`. Android will join them against the configured backend `baseUrl`.

## Media Binary Access

Android media rendering depends on backend-owned file endpoints:

- `GET /api/media/files/{mediaId}`
- `GET /api/media/files/{mediaId}?variant=original`
- `GET /api/media/files/{mediaId}?variant=preview`
- `GET /api/media/files/{mediaId}?variant=cover`

Storage migration compatibility requirements:

- Android must still fetch media through backend endpoints.
- Android must not construct MinIO/OSS URLs.
- Android must not receive object storage credentials.
- The endpoint may internally proxy, stream, redirect to a backend-controlled signed URL, or use another server-owned strategy later, but Android contract remains backend API based.
- Byte range support for videos should remain available.

Image Viewer rules:

- Preview priority: `thumbnailUrl -> mediaUrl`.
- Original priority: `originalUrl -> mediaUrl`.
- The original action appears only when the original candidate is non-empty and different from the preview candidate.
- Videos do not show image original-loading actions.

## Upload And Import

Android REAL upload/import uses:

- `POST /api/uploads/token`
- `POST /api/uploads/{uploadId}/file`

Current upload flow:

1. Android reads local system media metadata.
2. Android requests an upload token with `fileName`, `mimeType`, `fileSizeBytes`, `mediaType`, dimensions, time metadata, and optional `sourceFingerprint`.
3. Android uploads multipart content with form field name `file`.
4. Server returns a media DTO.
5. Android uses returned `mediaId` for import-only, post creation, or add-to-post finalization.

Compatibility requirements:

- `uploadId` and returned `media.mediaId` remain the stable client-facing identifiers.
- `provider` in upload-token response may change later, but Android must not use it to select MinIO/OSS clients in this stage.
- `uploadUrl` remains a backend API path or backend URL.
- Multipart field name remains `file` unless a separate contract migration is planned.
- Upload success must return enough media fields for photo feed and Viewer rendering.

## Post Create, Join, And Management

Android REAL uses:

- `GET /api/posts/{postId}`
- `POST /api/posts`
- `PATCH /api/posts/{postId}`
- `PATCH /api/posts/{postId}/cover`
- `PATCH /api/posts/{postId}/media-order`
- `POST /api/posts/{postId}/media`
- `DELETE /api/posts/{postId}`
- `DELETE /api/posts/{postId}/media/{mediaId}?deleteMode=directory|system`

Create-post and add-to-post use media ids returned by upload. Android does not send object keys.

Compatibility requirements:

- `PostDetailDto.mediaItems[].media` keeps the same media URL compatibility rules as `GET /api/media/feed`.
- `coverMediaId` remains a media id.
- `initialMediaIds`, `mediaIds`, and `orderedMediaIds` remain media ids.
- Directory delete and system delete semantics remain unchanged.

## Album Dependency

Android REAL album page uses:

- `GET /api/albums`
- `GET /api/albums/{albumId}/posts`
- `GET /api/posts/{postId}` for visible cover backfill when a summary has `coverMediaId`

Compatibility requirements:

- Album summaries may stay URL-free.
- Cover lookup can continue through post detail media DTOs.

## Comments

Android REAL uses:

- `GET /api/posts/{postId}/comments?page={page}&size={size}`
- `POST /api/posts/{postId}/comments`
- `GET /api/media/{mediaId}/comments?page={page}&size={size}`
- `POST /api/media/{mediaId}/comments`
- `PATCH /api/comments/{commentId}`
- `DELETE /api/comments/{commentId}`

Storage migration should not affect comment APIs.

## Trash

Android REAL uses:

- `GET /api/trash/items`
- `GET /api/trash/items?itemType={itemType}`
- `GET /api/trash/items/{trashItemId}`
- `POST /api/trash/items/{trashItemId}/restore`
- `POST /api/trash/items/{trashItemId}/remove`
- `POST /api/trash/items/{trashItemId}/purge`
- `POST /api/trash/items/{trashItemId}/undo-remove`
- `GET /api/trash/pending-cleanup`

Android trash preview behavior:

- For media trash entries, Android may reconstruct backend file URLs from `sourceMediaId` or related media ids:
  - `/api/media/files/{mediaId}?variant=preview`
  - `/api/media/files/{mediaId}`
  - `/api/media/files/{mediaId}?variant=cover`
- These are backend API URLs, not object-storage URLs.

Compatibility requirements:

- Deleted current-library media referenced by trash should remain readable by backend file endpoint for read-only trash previews until purge.
- Purge behavior may delete original/preview/cover objects internally, but Android should see only trash API success/failure.

## Explicit Non Dependencies

Android REAL currently does not depend on:

- local server disk paths such as `local-storage/originals/...`
- MinIO endpoints
- OSS endpoints
- storage bucket names
- object keys
- object storage access key or secret key
- provider-specific SDKs

Any future use of direct-to-object-storage upload must be a separate contract revision. Stage 16 step 1 keeps Android behind backend APIs.
