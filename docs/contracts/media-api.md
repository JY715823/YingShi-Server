# Media API Draft

## Status
- Server 3 minimal implementation target
- local-dev usable

## Purpose
Serve real app-content media metadata for the global photo stream.

## Endpoints

### `GET /api/media/feed`
- use case: deduplicated global app-content media stream for the current space
- auth: required

Response:

```json
{
  "requestId": "req_media_feed",
  "data": [
    {
      "mediaId": "media_001",
      "mediaType": "image",
      "previewUrl": "https://demo.yingshi.local/media_001_preview.jpg",
      "originalUrl": "https://demo.yingshi.local/media_001_original.jpg",
      "videoUrl": null,
      "coverUrl": null,
      "width": 1440,
      "height": 1920,
      "aspectRatio": 0.75,
      "displayTimeMillis": 1777412800000,
      "postIds": ["post_001", "post_002"]
    }
  ]
}
```

## Field Notes
- every media row belongs to one `spaceId`
- one media item may appear in multiple posts
- global media feed is deduplicated by `mediaId`
- `postIds` lists the posts in the current space that reference the media
- Server 3 does not include upload, delete, or single-media detail endpoints

## Error Code Placeholders
- `MEDIA_NOT_FOUND`
- `AUTH_UNAUTHORIZED`

## Server 3 Notes
- URLs are local seed placeholders in this stage
- no real CDN, signed URL, or upload-confirm flow is part of this stage
