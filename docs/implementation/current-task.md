# Current Task - Server 5+6 Upload and Trash Core

## Goal
Implement local upload/media storage and delete/trash/restore core flows for posts and media.

## Scope
- local upload token + local multipart upload
- media record creation with storage metadata
- create post with media
- add media to existing post
- post media reorder + cover
- delete post
- directory delete inside a post
- system delete for media
- trash list/detail/restore/remove/undo-remove
- pending cleanup placeholder
- minimal contract sync for `upload-api.md`, `media-api.md`, `trash-api.md`, `post-api.md`

## Product Intent
- uploaded local files should create usable app-content media
- media belongs to one `spaceId` and may belong to multiple posts
- directory delete only removes one `PostMedia` relation
- system delete hides one media globally from app content
- trash must distinguish `postDeleted`, `mediaRemoved`, `mediaSystemDeleted`
- restore behavior depends on trash item type

## API Shape
- `POST /api/uploads/token`
- `POST /api/uploads/{uploadId}/file`
- `GET /api/media/feed`
- `GET /api/media/files/{mediaId}`
- `DELETE /api/media/{mediaId}`
- `POST /api/posts`
- `POST /api/posts/{postId}/media`
- `PATCH /api/posts/{postId}`
- `PATCH /api/posts/{postId}/cover`
- `PATCH /api/posts/{postId}/media-order`
- `DELETE /api/posts/{postId}`
- `DELETE /api/posts/{postId}/media/{mediaId}`
- `GET /api/trash/items`
- `GET /api/trash/items/{trashItemId}`
- `POST /api/trash/items/{trashItemId}/restore`
- `POST /api/trash/items/{trashItemId}/remove`
- `POST /api/trash/items/{trashItemId}/undo-remove`
- `GET /api/trash/pending-cleanup`

## Local Dev Assumptions
- storage is local filesystem only
- no real OSS or signed CDN URLs
- no real background cleanup worker
- pending cleanup is a state placeholder only
- comments stay in place and become visible again when their post/media target is restored

## Non-Goals
- no Android system media APIs
- no permanent delete scheduler
- no complex role/permission matrix
- no transcoding, thumbnail pipeline, or EXIF parsing

## Done When
- local upload creates media rows and stores files
- uploaded media can create a new post or join an existing post
- directory delete and system delete have different behavior
- three trash item types can be listed and restored
- remove from trash can be undone inside the placeholder window
- server builds and tests pass
