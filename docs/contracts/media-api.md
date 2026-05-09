# Media API Contract

## Status
- unified with current `yingshi-server` code
- local-dev usable

## Base Rules
- base path: `/api/media`
- bearer auth required for all endpoints
- media feed is deduplicated by media body, not repeated per post
- there is no `GET /api/media/{mediaId}` JSON detail endpoint in current backend

## Media DTO

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

Example meaning:
- one shared media can belong to multiple posts
- REAL seed examples now use Chinese albums/posts such as `日常`, `旅行`, `春日散步`

## Endpoints

### `GET /api/media/feed`

Response data:
- array of `MediaDto`

### `GET /api/media/files/{mediaId}`

Response:
- binary file stream
- local dev currently serves stored files directly from server-managed local storage
- current-space deleted media may still be streamed by id so trash detail can render read-only original media previews
- supports standard single HTTP byte ranges through `Range: bytes=start-end`
- full responses include `Accept-Ranges: bytes`
- valid range responses return `206 Partial Content`, `Content-Range`, and the requested byte length

### `DELETE /api/media/{mediaId}`

Behavior:
- system delete one media globally in the current space
- hides it from feed and all posts
- creates one trash item with `itemType = mediaSystemDeleted`

Response:
- returns one `TrashItemDto`

## Notes
- `url` is the canonical file URL
- `postIds` only includes active posts
- system-deleted media stays restorable through trash
- Import-only media is valid. `/api/media/feed` must still return active media when `postIds` is empty.
- `GET /api/media/feed` and post detail APIs continue to hide deleted media. The binary file endpoint is allowed to read deleted current-space media only for preview / recovery surfaces such as trash detail.

## Stage 12.7-Hotfix Original Contract

- For local image media, `previewUrl` may point to `/api/media/files/{mediaId}?variant=preview`, while `originalUrl` / `mediaUrl` may point to `/api/media/files/{mediaId}`.
- Android image preview priority is `thumbnailUrl -> mediaUrl`; image original priority is `originalUrl -> mediaUrl`, but the resolved original candidate must be non-empty and different from the current preview URL.
- Video media must not be represented to users as `加载原图`. Video source fallback remains playback-only and is separate from image original loading.

## Error Codes
- `MEDIA_NOT_FOUND`
- `MEDIA_ALREADY_DELETED`
- `TRASH_ITEM_NOT_FOUND`
- `AUTH_UNAUTHORIZED`

## Shared Library / Time Field Update

- Media belongs to the private shared library (`libraryId`) and may have zero, one, or many related posts.
- The media timeline should use `displayTimeMillis`.
- Media DTOs may include:
  - `capturedAtMillis`: the media's own original/captured time.
  - `importedAtMillis`: when this file entered the app library.
  - `displayTimeSource`: `ORIGINAL`, `IMPORTED`, or `MANUAL`.
- `displayTimeMillis` is intentionally separate from `capturedAtMillis`; users can later adjust how a memory appears without losing the original file time.
- System delete is scoped to the current shared library, not to a multi-tenant space.
