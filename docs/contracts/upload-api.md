# Upload API Draft

## Status
- Server 5 local-dev implementation target
- local filesystem storage only

## Purpose
Create a small upload flow that works in local development and immediately creates app-content media records.

## Endpoints

### `POST /api/uploads/token`
- use case: create one local upload task
- auth: required

Request:

```json
{
  "fileName": "IMG_0001.JPG",
  "mimeType": "image/jpeg",
  "fileSizeBytes": 3145728,
  "mediaType": "image",
  "width": 1440,
  "height": 1920,
  "durationMillis": null,
  "displayTimeMillis": 1777416400000
}
```

Response:

```json
{
  "requestId": "req_upload_token",
  "data": {
    "uploadId": "upload_001",
    "provider": "local",
    "uploadUrl": "/api/uploads/upload_001/file",
    "expireAtMillis": 1777417000000,
    "state": "waiting"
  }
}
```

### `POST /api/uploads/{uploadId}/file`
- use case: upload one local multipart file and create one `Media`
- auth: required
- content type: `multipart/form-data`
- form field:
  - `file`

Response:

```json
{
  "requestId": "req_upload_file",
  "data": {
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
}
```

## Notes
- local upload writes files under a server-managed dev directory
- upload success immediately creates one `Media` row
- later integration may replace `provider=local` with real object storage

## Error Code Placeholders
- `UPLOAD_NOT_FOUND`
- `UPLOAD_ALREADY_COMPLETED`
- `UPLOAD_FILE_MISMATCH`
- `UPLOAD_STORAGE_ERROR`
- `VALIDATION_ERROR`
- `AUTH_UNAUTHORIZED`
