# Media API Draft

## Status
- Server 5+6 local-dev implementation target

## Purpose
Serve global media feed, local media file access, and media-level system delete behavior.

## Endpoints

### `GET /api/media/feed`
- use case: deduplicated global app-content media stream for the current space
- auth: required

Response item shape:

```json
{
  "mediaId": "media_001",
  "mediaType": "image",
  "url": "/api/media/files/media_001",
  "previewUrl": "/api/media/files/media_001",
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
  "postIds": ["post_001", "post_002"]
}
```

### `GET /api/media/files/{mediaId}`
- use case: fetch one locally stored file in dev
- auth: required

### `DELETE /api/media/{mediaId}`
- use case: system delete one media from the current space
- auth: required
- behavior:
  - hide media from global feed
  - hide media from all posts
  - create one `mediaSystemDeleted` trash item

Response:
- returns `TrashItemDto`

## Field Notes
- `url` is the canonical file URL for this media
- system-deleted media stays restorable through trash
- media feed excludes system-deleted media
- `postIds` only includes active posts in the current space

## Error Code Placeholders
- `MEDIA_NOT_FOUND`
- `MEDIA_ALREADY_DELETED`
- `TRASH_ITEM_NOT_FOUND`
- `AUTH_UNAUTHORIZED`
