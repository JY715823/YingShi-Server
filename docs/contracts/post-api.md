# Post API Draft

## Status
- Server 3 minimal implementation target
- local-dev usable
- not final production post schema

## Purpose
Define the first working backend contract for post detail and post editing backed by real space-scoped content tables.

## Endpoints

### `GET /api/posts/{postId}`
- use case: fetch one post detail
- auth: required

Response:

```json
{
  "requestId": "req_post_detail",
  "data": {
    "postId": "post_001",
    "title": "Night Walk",
    "summary": "A quiet walk home",
    "contributorLabel": "Demo A and Demo B",
    "displayTimeMillis": 1777412800000,
    "albumIds": ["album_001", "album_002"],
    "coverMediaId": "media_001",
    "mediaCount": 3,
    "mediaItems": [
      {
        "sortOrder": 1,
        "isCover": true,
        "media": {
          "mediaId": "media_001",
          "mediaType": "image",
          "previewUrl": "https://demo.yingshi.local/media_001_preview.jpg",
          "originalUrl": "https://demo.yingshi.local/media_001_original.jpg",
          "videoUrl": null,
          "coverUrl": null,
          "width": 1440,
          "height": 1920,
          "aspectRatio": 0.75,
          "displayTimeMillis": 1777412800000
        }
      }
    ]
  }
}
```

### `POST /api/posts`
- use case: create a post from existing media in the current space
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

Response:
- returns `PostDetailDto`

### `PATCH /api/posts/{postId}`
- use case: update post basic fields and album membership
- auth: required

Request:

```json
{
  "title": "Night Walk Updated",
  "summary": "A quiet walk home with one more note",
  "contributorLabel": "Demo A and Demo B",
  "displayTimeMillis": 1777412800000,
  "albumIds": ["album_001", "album_003"]
}
```

Response:
- returns `PostDetailDto`

### `PATCH /api/posts/{postId}/cover`
- use case: set post cover media
- auth: required

Request:

```json
{
  "coverMediaId": "media_002"
}
```

Response:
- returns `PostDetailDto`

### `PATCH /api/posts/{postId}/media-order`
- use case: update media order inside one post
- auth: required

Request:

```json
{
  "orderedMediaIds": ["media_002", "media_001", "media_003"]
}
```

Response:
- returns `PostDetailDto`

## Field Notes
- every post belongs to the authenticated user's `spaceId`
- one post can belong to multiple albums
- media order is defined by `PostMedia.sortOrder`
- `coverMediaId` must point to one media already attached to the same post
- Server 3 does not include delete, comments, or rich editor block payloads

## Error Code Placeholders
- `POST_NOT_FOUND`
- `POST_MEDIA_ORDER_INVALID`
- `POST_COVER_INVALID`
- `ALBUM_ASSIGNMENT_INVALID`
- `MEDIA_NOT_FOUND`
- `VALIDATION_ERROR`
- `AUTH_UNAUTHORIZED`

## Server 3 Notes
- create and update only work with media already present in the current space
- no delete endpoint in this stage
