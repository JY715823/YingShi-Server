# Album API Draft

## Status
- Stage 11.4 draft only
- no live backend required
- current app still defaults to fake local album and post data

## Purpose
Define the first contract boundary for album directory browsing, album-scoped post lists, and post-to-album relationship updates.

## Endpoints

### `GET /v1/albums`
- use case: fetch album directory
- auth: required
- query:
  - `page`
  - `size`

Response draft:

```json
{
  "requestId": "req_albums",
  "data": [
    {
      "albumId": "album_001",
      "title": "Spring Window",
      "subtitle": "Light and slow daily fragments",
      "coverMediaId": "media_001",
      "postCount": 8
    }
  ],
  "page": {
    "page": 1,
    "pageSize": 20,
    "nextCursor": null,
    "hasMore": false
  }
}
```

### `GET /v1/albums/{albumId}/posts`
- use case: fetch posts under one album
- auth: required
- query:
  - `page`
  - `size`

Response draft:

```json
{
  "requestId": "req_album_posts",
  "data": [
    {
      "postId": "post_001",
      "title": "Night Walk",
      "summary": "A quiet walk home",
      "contributorLabel": "You and Me",
      "displayTimeMillis": 1777412800000,
      "albumIds": ["album_001"],
      "coverMediaId": "media_001",
      "mediaCount": 6
    }
  ],
  "page": {
    "page": 1,
    "pageSize": 20,
    "nextCursor": null,
    "hasMore": false
  }
}
```

### `PATCH /v1/posts/{postId}/albums`
- use case: update which albums a post belongs to
- auth: required

Request draft:

```json
{
  "albumIds": ["album_001", "album_002"]
}
```

Response draft:

```json
{
  "requestId": "req_update_post_albums",
  "data": {
    "postId": "post_001",
    "title": "Night Walk",
    "summary": "A quiet walk home",
    "contributorLabel": "You and Me",
    "displayTimeMillis": 1777412800000,
    "albumIds": ["album_001", "album_002"],
    "coverMediaId": "media_001",
    "mediaCount": 6
  }
}
```

## Field Notes
- album APIs do not own post detail media lists
- album relationship updates only change `albumIds`
- post cover and media order remain post APIs

## Error Code Placeholders
- `ALBUM_NOT_FOUND`
- `POST_NOT_FOUND`
- `ALBUM_ASSIGNMENT_INVALID`
- `VALIDATION_ERROR`
- `AUTH_UNAUTHORIZED`
- `NOT_IMPLEMENTED`

## Stage 11.4 Draft-Only Notes
- no album create, rename, delete, or cover management in this stage
- no album member or permission model in this stage
