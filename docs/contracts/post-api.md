# Post API Draft

## Status
- Server 5+6 local-dev implementation target

## Purpose
Back post detail, post creation, post media edits, and post-level delete behavior with real space-scoped data.

## Endpoints

### `GET /api/posts/{postId}`
- use case: fetch one active post detail
- auth: required

### `POST /api/posts`
- use case: create one post from existing media in the current space
- auth: required

Request:

```json
{
  "title": "Night Walk",
  "summary": "A quiet walk home",
  "contributorLabel": "Demo A and Demo B",
  "displayTimeMillis": 1777412800000,
  "albumIds": ["album_001"],
  "initialMediaIds": ["media_001", "media_002"],
  "coverMediaId": "media_001"
}
```

### `POST /api/posts/{postId}/media`
- use case: append one or more existing media items to one post
- auth: required

Request:

```json
{
  "mediaIds": ["media_uploaded_001", "media_uploaded_002"],
  "coverMediaId": "media_uploaded_001"
}
```

### `PATCH /api/posts/{postId}`
- use case: update post basic fields and album membership
- auth: required

### `PATCH /api/posts/{postId}/cover`
- use case: set post cover media
- auth: required

### `PATCH /api/posts/{postId}/media-order`
- use case: update media order inside one post
- auth: required

### `DELETE /api/posts/{postId}`
- use case: delete one post into trash
- auth: required
- behavior:
  - soft delete the post
  - keep post-media and post comments restorable
  - create one `postDeleted` trash item

Response:
- returns `TrashItemDto`

### `DELETE /api/posts/{postId}/media/{mediaId}?deleteMode=directory|system`
- use case: remove one media from one post
- auth: required

Behavior:
- `directory`: only remove this `PostMedia` relation and create `mediaRemoved` trash item
- `system`: system delete this media globally and create `mediaSystemDeleted` trash item

Response:
- returns `TrashItemDto`

## Field Notes
- one media can belong to multiple posts
- media order is defined by `PostMedia.sortOrder`
- deleted posts do not appear in active post APIs
- system-deleted media does not appear in post detail until restored

## Error Code Placeholders
- `POST_NOT_FOUND`
- `POST_ALREADY_DELETED`
- `POST_MEDIA_ORDER_INVALID`
- `POST_COVER_INVALID`
- `ALBUM_ASSIGNMENT_INVALID`
- `MEDIA_NOT_FOUND`
- `MEDIA_ALREADY_DELETED`
- `VALIDATION_ERROR`
- `AUTH_UNAUTHORIZED`
