# Android REAL Stage 16 API Contract

## Purpose

This document lists the server APIs currently consumed by Android REAL mode.

## Base URL

Android REAL has a configurable backend `baseUrl` in the diagnostics/settings flow.

## Media Feed

Photo feed REAL uses:

- `GET /api/media/feed?cursor={cursor}&pageSize={pageSize}`

Compatibility requirements:

- response envelope `data` is a list of media DTOs
- pagination metadata may provide `page.nextCursor` and `page.hasMore`
- `smallAlbumIds` may be empty for import-only media
- Android must not infer object storage endpoints from media ids or object keys

## Upload And Import

Android REAL upload/import uses:

- `POST /api/uploads/token`
- `POST /api/uploads/{uploadId}/file`

Current upload flow:

1. Android reads local system media metadata.
2. Android requests an upload token.
3. Android uploads multipart content with form field name `file`.
4. Server returns a media DTO.
5. Android uses returned `mediaId` for import-only, small-album creation, or add-to-small-album finalization.

## Small Album Create, Join, And Management

Android REAL uses:

- `GET /api/small-albums/{smallAlbumId}`
- `POST /api/small-albums`
- `PATCH /api/small-albums/{smallAlbumId}`
- `PATCH /api/small-albums/{smallAlbumId}/cover`
- `PATCH /api/small-albums/{smallAlbumId}/media-order`
- `POST /api/small-albums/{smallAlbumId}/media`
- `DELETE /api/small-albums/{smallAlbumId}`
- `DELETE /api/small-albums/{smallAlbumId}/media/{mediaId}?deleteMode=directory|system`

Compatibility requirements:

- `PostDetailDto.mediaItems[].media` keeps the same media URL compatibility rules as `GET /api/media/feed`
- `coverMediaId` remains a media id
- `initialMediaIds`, `mediaIds`, and `orderedMediaIds` remain media ids

## Album Dependency

Android REAL album page uses:

- `GET /api/albums`
- `GET /api/albums/{albumId}/small-albums`
- `GET /api/small-albums/{smallAlbumId}` for visible cover backfill when a summary has `coverMediaId`

## Comments

Android REAL uses:

- `GET /api/small-albums/{smallAlbumId}/comments?page={page}&size={size}`
- `POST /api/small-albums/{smallAlbumId}/comments`
- `GET /api/media/{mediaId}/comments?page={page}&size={size}`
- `POST /api/media/{mediaId}/comments`
- `PATCH /api/comments/{commentId}`
- `DELETE /api/comments/{commentId}`

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

Compatibility requirements:

- trash items expose `sourceSmallAlbumId` and `relatedSmallAlbumIds`
- deleted current-library media referenced by trash should remain readable for read-only trash previews until purge
