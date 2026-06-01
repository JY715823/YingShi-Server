# Media API Contract

## Status

- unified with current `yingshi-server` code
- local-dev usable

## Base Rules

- base path: `/api/media`
- bearer auth required for all endpoints
- media feed is deduplicated by media body, not repeated per small album
- there is no `GET /api/media/{mediaId}` JSON detail endpoint in current backend

## Media DTO

```json
{
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
  "smallAlbumIds": ["post_001", "post_002"]
}
```

## Endpoints

### `GET /api/media/feed`

- returns `List<MediaDto>`
- import-only media is valid, so `smallAlbumIds` may be empty

### `GET /api/media/files/{mediaId}`

- returns binary file stream
- supports `variant=original|preview|cover`
- supports standard single HTTP byte ranges for video playback and partial reads

### `DELETE /api/media/{mediaId}`

Behavior:

- system delete one media globally in the current shared library
- hides it from feed and all related small albums
- creates one trash item with `itemType = mediaSystemDeleted`

## Error Codes

- `MEDIA_NOT_FOUND`
- `MEDIA_ALREADY_DELETED`
- `TRASH_ITEM_NOT_FOUND`
- `AUTH_UNAUTHORIZED`
