# Comment API Contract

## Status
- unified with current `yingshi-server` code
- local-dev usable

## Base Rules
- post comments and media comments are separate streams
- bearer auth required for all endpoints
- base paths:
  - `/api/posts/{postId}/comments`
  - `/api/media/{mediaId}/comments`
  - `/api/comments/{commentId}`
- default ordering is newest first
- default pagination is `page=1`, `size=10`

## Comment DTO

```json
{
  "commentId": "comment_post_001",
  "targetType": "POST",
  "postId": "post_001",
  "mediaId": null,
  "authorId": "user_demo_a",
  "authorName": "Demo A",
  "content": "The night colors feel warm.",
  "createdAtMillis": 1777412800000,
  "updatedAtMillis": 1777412860000,
  "isDeleted": false
}
```

Notes:
- `targetType` values are uppercase: `POST`, `MEDIA`
- `postId` is only set for post comments
- `mediaId` is only set for media comments
- after soft delete, `isDeleted=true` and `content` may be `null`

## Endpoints

### `GET /api/posts/{postId}/comments`
### `GET /api/media/{mediaId}/comments`

Response data:

```json
{
  "comments": [
    {
      "commentId": "comment_post_001",
      "targetType": "POST",
      "postId": "post_001",
      "mediaId": null,
      "authorId": "user_demo_a",
      "authorName": "Demo A",
      "content": "The night colors feel warm.",
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

### `POST /api/posts/{postId}/comments`
### `POST /api/media/{mediaId}/comments`

Request:

```json
{
  "content": "A comment"
}
```

Response:
- returns one `CommentDto`

### `PATCH /api/comments/{commentId}`

Request:

```json
{
  "content": "Updated comment"
}
```

Response:
- returns one `CommentDto`

### `DELETE /api/comments/{commentId}`

Response:
- returns one soft-deleted `CommentDto`

## Error Codes
- `COMMENT_NOT_FOUND`
- `COMMENT_TARGET_NOT_FOUND`
- `COMMENT_SCOPE_MISMATCH`
- `FORBIDDEN`
- `VALIDATION_ERROR`
- `AUTH_UNAUTHORIZED`
