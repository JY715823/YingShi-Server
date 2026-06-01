# Comment API Contract

## Status

- unified with current `yingshi-server` code
- post wording is retired from the outward contract

## Base Rules

- small-album comments and media comments are separate streams
- bearer auth required for all endpoints
- base paths:
  - `/api/small-albums/{smallAlbumId}/comments`
  - `/api/media/{mediaId}/comments`
  - `/api/comments/{commentId}`
- default ordering is newest first
- default pagination is `page=1`, `size=10`

## Comment DTO

```json
{
  "commentId": "comment_post_001",
  "targetType": "SMALL_ALBUM",
  "smallAlbumId": "post_001",
  "mediaId": null,
  "authorId": "user_demo_a",
  "authorName": "小雨",
  "content": "今天阳光很好，散步回来心情也慢下来了。",
  "createdAtMillis": 1777412800000,
  "updatedAtMillis": 1777412860000,
  "isDeleted": false
}
```

Notes:

- `targetType` values are uppercase: `SMALL_ALBUM`, `MEDIA`
- `smallAlbumId` is only set for small-album comments
- `mediaId` is only set for media comments
- after soft delete, `isDeleted=true` and `content` may be `null`

## Endpoints

### `GET /api/small-albums/{smallAlbumId}/comments`
### `GET /api/media/{mediaId}/comments`

Response data:

```json
{
  "comments": [
    {
      "commentId": "comment_post_001",
      "targetType": "SMALL_ALBUM",
      "smallAlbumId": "post_001",
      "mediaId": null,
      "authorId": "user_demo_a",
      "authorName": "小雨",
      "content": "今天阳光很好，散步回来心情也慢下来了。",
      "createdAtMillis": 1777412800000,
      "updatedAtMillis": 1777412860000,
      "isDeleted": false
    }
  ],
  "page": 1,
  "size": 10,
  "totalElements": 1,
  "hasMore": false
}
```

### `POST /api/small-albums/{smallAlbumId}/comments`
### `POST /api/media/{mediaId}/comments`

Request:

```json
{
  "content": "这张照片让我想起那天的风。"
}
```

### `PATCH /api/comments/{commentId}`

Request:

```json
{
  "content": "补一句，这个角度也很适合放进日常小相册。"
}
```

### `DELETE /api/comments/{commentId}`

- returns one soft-deleted `CommentDto`

## Error Codes

- `COMMENT_NOT_FOUND`
- `COMMENT_TARGET_NOT_FOUND`
- `COMMENT_SCOPE_MISMATCH`
- `FORBIDDEN`
- `VALIDATION_ERROR`
- `AUTH_UNAUTHORIZED`
