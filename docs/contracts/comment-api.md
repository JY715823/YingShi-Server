# Comment API Draft

## Status
- Server 4 minimal implementation target
- local-dev usable

## Purpose
Support separate post comments and media comments without mixing target scopes.

## Stream Separation
- post comments and media comments are separate streams
- `postId` comments only belong to the post
- `mediaId` comments only belong to the media
- one `mediaId` shares one media-comment stream across different posts
- the client must not merge post comments and media comments into one list

## Ordering
- latest comments appear first
- server returns newest-to-oldest order by default

## Pagination
- request query:
  - `page`
  - `size`
- default `page=1`
- default `size=10`

## Endpoints

### `GET /api/posts/{postId}/comments`
- use case: fetch post comment list
- auth: required

Response:

```json
{
  "requestId": "req_post_comments",
  "data": {
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
}
```

### `GET /api/media/{mediaId}/comments`
- use case: fetch media comment list
- auth: required

Response:

```json
{
  "requestId": "req_media_comments",
  "data": {
    "comments": [
      {
        "commentId": "comment_media_001",
        "targetType": "MEDIA",
        "postId": null,
        "mediaId": "media_001",
        "authorId": "user_demo_b",
        "authorName": "Demo B",
        "content": "This frame should stay in the shared media flow.",
        "createdAtMillis": 1777412900000,
        "updatedAtMillis": null,
        "isDeleted": false
      }
    ],
    "page": 1,
    "size": 10,
    "totalElements": 1,
    "hasMore": false
  }
}
```

### `POST /api/posts/{postId}/comments`
- use case: create post comment
- auth: required

Request:

```json
{
  "content": "A post comment"
}
```

Response:
- returns `CommentDto`

### `POST /api/media/{mediaId}/comments`
- use case: create media comment
- auth: required

Request:

```json
{
  "content": "A media comment"
}
```

Response:
- returns `CommentDto`

### `PATCH /api/comments/{commentId}`
- use case: edit one existing comment
- auth: required
- only the author may edit

Request:

```json
{
  "content": "Updated comment"
}
```

Response:
- returns `CommentDto`

### `DELETE /api/comments/{commentId}`
- use case: soft delete one existing comment
- auth: required
- only the author may delete

Response:
- returns `CommentDto` with `isDeleted=true`

## Field Notes
- `targetType`: `POST` or `MEDIA`
- `postId` must be set only for `POST`
- `mediaId` must be set only for `MEDIA`
- deleted comments stay soft-deleted in storage
- response fields use `camelCase`

## Error Code Placeholders
- `COMMENT_NOT_FOUND`
- `COMMENT_TARGET_NOT_FOUND`
- `COMMENT_SCOPE_MISMATCH`
- `FORBIDDEN`
- `VALIDATION_ERROR`
- `AUTH_UNAUTHORIZED`

## Server 4 Notes
- no replies or nested threads
- no moderation state
- no rich text or media attachments
