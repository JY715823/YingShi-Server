# Post API Contract

## Status

- unified with current `yingshi-server` code
- local-dev usable

## Base Rules

- base path: `/api/posts`
- bearer auth required for all endpoints
- `GET /api/posts` and `GET /api/posts/{postId}` are both available
- create, update, cover update, media order update, and add-media all return the same `PostDetailDto`

## Post Summary DTO

```json
{
  "postId": "post_001",
  "title": "Night Walk",
  "summary": "A quiet walk home",
  "contributorLabel": "Demo A and Demo B",
  "displayTimeMillis": 1777412800000,
  "eventStartedAtMillis": 1777412800000,
  "eventEndedAtMillis": null,
  "displayTimeSource": "MANUAL",
  "albumIds": ["album_001"],
  "coverMediaId": "media_001",
  "mediaCount": 3
}
```

说明：

- 当前通用帖子列表返回 summary，不内联 cover URL
- Android 如需稳定封面媒体细节，可继续按需拉 `GET /api/posts/{postId}`

## Post Detail DTO

```json
{
  "postId": "post_001",
  "title": "Night Walk",
  "summary": "A quiet walk home",
  "contributorLabel": "Demo A and Demo B",
  "displayTimeMillis": 1777412800000,
  "albumIds": ["album_001"],
  "coverMediaId": "media_001",
  "mediaCount": 3,
  "mediaItems": [
    {
      "sortOrder": 0,
      "isCover": true,
      "media": {
        "mediaId": "media_001",
        "mediaType": "image",
        "url": "/api/media/files/media_001",
        "previewUrl": "/api/media/files/media_001?variant=preview",
        "originalUrl": "/api/media/files/media_001",
        "videoUrl": null,
        "coverUrl": null,
        "mimeType": "image/jpeg",
        "sizeBytes": 3145728,
        "width": 1440,
        "height": 1920,
        "aspectRatio": 0.75,
        "durationMillis": null,
        "displayTimeMillis": 1777412800000,
        "postIds": ["post_001"]
      }
    }
  ]
}
```

## Endpoints

### `GET /api/posts`

Response:

- returns `List<PostSummaryDto>`
- sorted by `displayTimeMillis desc`, then `updatedAt desc`

### `GET /api/posts/{postId}`

Response:

- returns one `PostDetailDto`

### `POST /api/posts`

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

- returns one `PostDetailDto`

### `PATCH /api/posts/{postId}`

Request:

```json
{
  "title": "Night Walk Updated",
  "summary": "A quiet walk home with one more note",
  "contributorLabel": "Demo A and Demo B",
  "displayTimeMillis": 1777412800000,
  "albumIds": ["album_001", "album_002"]
}
```

Response:

- returns one `PostDetailDto`

### `PATCH /api/posts/{postId}/cover`

Request:

```json
{
  "coverMediaId": "media_002"
}
```

Response:

- returns one `PostDetailDto`

### `PATCH /api/posts/{postId}/media-order`

Request:

```json
{
  "orderedMediaIds": ["media_002", "media_001", "media_003"]
}
```

Response:

- returns one `PostDetailDto`

### `POST /api/posts/{postId}/media`

Request:

```json
{
  "mediaIds": ["media_uploaded_001", "media_uploaded_002"],
  "coverMediaId": "media_uploaded_001"
}
```

Response:

- returns one `PostDetailDto`

### `DELETE /api/posts/{postId}`

Behavior:

- soft deletes the post
- keeps relations and comments restorable
- creates one trash item with `itemType = postDeleted`

Response:

- returns one `TrashItemDto`

### `DELETE /api/posts/{postId}/media/{mediaId}?deleteMode=directory|system`

Behavior:

- `directory`: remove only this post-media relation and create `mediaRemoved`
- `system`: system delete the media globally and create `mediaSystemDeleted`
- current backend allows the post to remain with zero media after deletion

Response:

- returns one `TrashItemDto`

## Shared Library / Post Time Update

- Posts belong to the private shared library (`libraryId`); there is no public `spaceId` field
- Post DTOs and create/update requests may include:
  - `eventStartedAtMillis`
  - `eventEndedAtMillis`
  - `displayTimeMillis`
  - `displayTimeSource`

## Error Codes

- `POST_NOT_FOUND`
- `POST_ALREADY_DELETED`
- `POST_MEDIA_ORDER_INVALID`
- `POST_COVER_INVALID`
- `ALBUM_ASSIGNMENT_INVALID`
- `MEDIA_NOT_FOUND`
- `MEDIA_ALREADY_DELETED`
- `VALIDATION_ERROR`
- `AUTH_UNAUTHORIZED`
