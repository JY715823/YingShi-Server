# Comment API Draft

## Status
- Stage 11.3 draft only
- no live backend required
- current app still defaults to fake local comments

## Purpose
Support separate post comments and media comments without mixing target scopes, while reserving list and mutation contracts for future backend integration.

## Stream Separation
- post comments and media comments are two separate streams
- `postId` comments only belong to the post
- `mediaId` comments only belong to the media
- the client must not merge post comments and media comments into one list

## Ordering
- latest comments appear first
- server returns newest-to-oldest order by default
- no alternate sort modes are reserved in this stage

## Pagination Draft
- this contract currently reserves `page` + `size`
- request query:
  - `page`
  - `size`
- response paging stays placeholder-only in this stage

## Endpoints

### `GET /v1/posts/{postId}/comments`
- use case: fetch post comment list
- auth: required
- query:
  - `page`
  - `size`

Response draft:

```json
{
  "requestId": "req_post_comments",
  "data": {
    "comments": [
      {
        "commentId": "comment_001",
        "targetType": "post",
        "targetId": "post_001",
        "authorName": "Me",
        "content": "A placeholder post comment",
        "createdAtMillis": 1777412800000,
        "updatedAtMillis": 1777412860000,
        "isDeleted": false
      }
    ]
  },
  "page": {
    "page": 1,
    "pageSize": 20,
    "nextCursor": null,
    "hasMore": false
  }
}
```

### `GET /v1/media/{mediaId}/comments`
- use case: fetch media comment list
- auth: required
- query:
  - `page`
  - `size`

Response draft:

```json
{
  "requestId": "req_media_comments",
  "data": {
    "comments": [
      {
        "commentId": "comment_101",
        "targetType": "media",
        "targetId": "media_001",
        "authorName": "You",
        "content": "A placeholder media comment",
        "createdAtMillis": 1777412800000,
        "updatedAtMillis": null,
        "isDeleted": false
      }
    ]
  },
  "page": {
    "page": 1,
    "pageSize": 20,
    "nextCursor": null,
    "hasMore": false
  }
}
```

### `POST /v1/posts/{postId}/comments`
- use case: create post comment
- auth: required

Request draft:

```json
{
  "content": "A placeholder post comment"
}
```

Response draft:

```json
{
  "requestId": "req_create_post_comment",
  "data": {
    "commentId": "comment_new_post",
    "targetType": "post",
    "targetId": "post_001",
    "authorName": "Me",
    "content": "A placeholder post comment",
    "createdAtMillis": 1777412800000,
    "updatedAtMillis": null,
    "isDeleted": false
  }
}
```

### `POST /v1/media/{mediaId}/comments`
- use case: create media comment
- auth: required

Request draft:

```json
{
  "content": "A placeholder media comment"
}
```

Response draft:

```json
{
  "requestId": "req_create_media_comment",
  "data": {
    "commentId": "comment_new_media",
    "targetType": "media",
    "targetId": "media_001",
    "authorName": "Me",
    "content": "A placeholder media comment",
    "createdAtMillis": 1777412800000,
    "updatedAtMillis": null,
    "isDeleted": false
  }
}
```

### `PATCH /v1/comments/{commentId}`
- use case: edit one existing comment
- auth: required

Request draft:

```json
{
  "content": "Updated placeholder comment"
}
```

### `DELETE /v1/comments/{commentId}`
- use case: delete one existing comment
- auth: required

Response draft:

```json
{
  "requestId": "req_delete_comment",
  "data": {
    "success": true
  }
}
```

## Shared Comment Item Draft

```json
{
  "commentId": "comment_001",
  "targetType": "media",
  "targetId": "media_001",
  "authorName": "Me",
  "content": "A placeholder comment",
  "createdAtMillis": 1777412800000,
  "updatedAtMillis": 1777412860000,
  "isDeleted": false
}
```

## Field Notes
- `targetType`: `post` or `media`
- `targetId` must match `targetType`
- comments stay flat in Stage 11.3
- request / response fields use `camelCase`

## Error Code Placeholders
- `COMMENT_NOT_FOUND`
- `COMMENT_TARGET_NOT_FOUND`
- `COMMENT_SCOPE_MISMATCH`
- `COMMENT_CONTENT_EMPTY`
- `COMMENT_CONTENT_TOO_LONG`
- `AUTH_UNAUTHORIZED`
- `NOT_IMPLEMENTED`

## Stage 11.3 Draft-Only Notes
- no replies or nested threads
- no moderation state
- no rich text or media attachments
- no final paging behavior beyond the placeholder `page` + `size` contract
