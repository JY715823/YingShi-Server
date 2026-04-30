# Upload API Contract

## Status
- unified with current `yingshi-server` code
- local filesystem storage only

## Base Rules
- base path: `/api/uploads`
- bearer auth required for all endpoints
- current backend is a two-step local upload flow:
  1. create upload token
  2. upload multipart file to `/file`
- current backend does not expose `confirm`, `cancel`, or `status` endpoints

## Endpoints

### `POST /api/uploads/token`

Request:

```json
{
  "fileName": "春日散步-01.jpg",
  "mimeType": "image/jpeg",
  "fileSizeBytes": 3145728,
  "mediaType": "image",
  "width": 1440,
  "height": 1920,
  "durationMillis": null,
  "displayTimeMillis": 1777416400000
}
```

Response data:

```json
{
  "uploadId": "upload_001",
  "provider": "local",
  "uploadUrl": "/api/uploads/upload_001/file",
  "expireAtMillis": 1777417000000,
  "state": "waiting"
}
```

### `POST /api/uploads/{uploadId}/file`

Request:
- content type: `multipart/form-data`
- form field name: `file`

Response data:

```json
{
  "uploadId": "upload_001",
  "state": "success",
  "media": {
    "mediaId": "media_uploaded_001",
    "mediaType": "image",
    "url": "/api/media/files/media_uploaded_001",
    "previewUrl": "/api/media/files/media_uploaded_001",
    "originalUrl": "/api/media/files/media_uploaded_001",
    "videoUrl": null,
    "coverUrl": null,
    "mimeType": "image/jpeg",
    "sizeBytes": 3145728,
    "width": 1440,
    "height": 1920,
    "aspectRatio": 0.75,
    "durationMillis": null,
    "displayTimeMillis": 1777416400000,
    "postIds": []
  }
}
```

## Notes
- upload success immediately creates one `Media` row
- local files are written under the server-managed `local-storage` directory
- later object-storage integration may change `provider`, but not the current local-dev contract
- Android REAL import flow uses this upload result media id, then calls post create or add-media APIs

## Error Codes
- `UPLOAD_NOT_FOUND`
- `UPLOAD_ALREADY_COMPLETED`
- `UPLOAD_FILE_MISMATCH`
- `UPLOAD_STORAGE_ERROR`
- `VALIDATION_ERROR`
- `AUTH_UNAUTHORIZED`
