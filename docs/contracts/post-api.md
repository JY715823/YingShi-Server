# Small Album API Contract

## Status

- unified with current `yingshi-server` code
- this file keeps the historical path name `post-api.md`, but the outward contract is now fully small-album based

## Base Rules

- base path: `/api/small-albums`
- bearer auth required
- create, update, cover update, media order update, and add-media all return `PostDetailDto`

## Small Album Summary DTO

```json
{
  "smallAlbumId": "post_001",
  "title": "Night Walk",
  "summary": "A quiet walk home",
  "contributorLabel": "Demo A and Demo B",
  "displayTimeMillis": 1777412800000,
  "eventStartedAtMillis": 1777412800000,
  "eventEndedAtMillis": null,
  "displayTimeSource": "MANUAL",
  "albumId": "album_001",
  "coverMediaId": "media_001",
  "mediaCount": 3
}
```

## Small Album Detail DTO

```json
{
  "smallAlbumId": "post_001",
  "title": "Night Walk",
  "summary": "A quiet walk home",
  "contributorLabel": "Demo A and Demo B",
  "displayTimeMillis": 1777412800000,
  "eventStartedAtMillis": 1777412800000,
  "eventEndedAtMillis": null,
  "displayTimeSource": "MANUAL",
  "albumId": "album_001",
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
        "capturedAtMillis": 1777412600000,
        "importedAtMillis": 1777412800000,
        "displayTimeSource": "MANUAL",
        "smallAlbumIds": ["post_001"]
      }
    }
  ]
}
```

## Endpoints

### `GET /api/small-albums`

- returns `List<PostSummaryDto>`

### `GET /api/small-albums/{smallAlbumId}`

- returns one `PostDetailDto`

### `POST /api/small-albums`

Request:

```json
{
  "title": "Night Walk",
  "summary": "A quiet walk home",
  "contributorLabel": "Demo A and Demo B",
  "displayTimeMillis": 1777412800000,
  "eventStartedAtMillis": 1777412800000,
  "eventEndedAtMillis": null,
  "displayTimeSource": "MANUAL",
  "albumId": "album_001",
  "initialMediaIds": ["media_001", "media_002"],
  "coverMediaId": "media_001"
}
```

### `PATCH /api/small-albums/{smallAlbumId}`

Request:

```json
{
  "title": "Night Walk Updated",
  "summary": "A quiet walk home with one more note",
  "contributorLabel": "Demo A and Demo B",
  "displayTimeMillis": 1777412800000,
  "eventStartedAtMillis": 1777412800000,
  "eventEndedAtMillis": null,
  "displayTimeSource": "MANUAL",
  "albumId": "album_002"
}
```

### `PATCH /api/small-albums/{smallAlbumId}/cover`

Request:

```json
{
  "coverMediaId": "media_002"
}
```

### `PATCH /api/small-albums/{smallAlbumId}/media-order`

Request:

```json
{
  "orderedMediaIds": ["media_002", "media_001", "media_003"]
}
```

### `POST /api/small-albums/{smallAlbumId}/media`

Request:

```json
{
  "mediaIds": ["media_uploaded_001", "media_uploaded_002"],
  "coverMediaId": "media_uploaded_001"
}
```

### `DELETE /api/small-albums/{smallAlbumId}`

Behavior:

- soft deletes the small album
- keeps relations and small-album comments restorable
- creates one trash item with `itemType = smallAlbumDeleted`

### `DELETE /api/small-albums/{smallAlbumId}/media/{mediaId}?deleteMode=directory|system`

Behavior:

- `directory`: remove only this small-album/media relation and create `mediaRemoved`
- `system`: system delete the media globally and create `mediaSystemDeleted`
- current backend allows the small album to remain with zero media after deletion

## Error Codes

- `SMALL_ALBUM_NOT_FOUND`
- `SMALL_ALBUM_ALREADY_DELETED`
- `SMALL_ALBUM_MEDIA_ORDER_INVALID`
- `SMALL_ALBUM_COVER_INVALID`
- `ALBUM_ASSIGNMENT_INVALID`
- `MEDIA_NOT_FOUND`
- `MEDIA_ALREADY_DELETED`
- `VALIDATION_ERROR`
- `AUTH_UNAUTHORIZED`
