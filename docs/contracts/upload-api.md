# Upload API Draft

## Status
- Stage 11.5 draft only
- no live backend required
- current app still defaults to fake upload tasks

## Purpose
Reserve the contract for future direct upload to OSS or object storage while making token creation, upload confirmation, cancellation, and status query boundaries explicit.

## Endpoints

### `POST /v1/uploads/token`
- use case: request a direct-upload token and create an upload task
- auth: required

Request draft:

```json
{
  "fileName": "IMG_0001.JPG",
  "mimeType": "image/jpeg",
  "fileSizeBytes": 3145728,
  "mediaType": "image"
}
```

Response draft:

```json
{
  "requestId": "req_upload_token",
  "data": {
    "uploadId": "upload_001",
    "provider": "oss",
    "bucket": "placeholder-bucket",
    "objectKey": "uploads/2026/04/upload_001.jpg",
    "uploadUrl": "https://placeholder-upload.example.com",
    "accessKeyId": "placeholder-access-key",
    "policy": "placeholder-policy",
    "signature": "placeholder-signature",
    "expireAtMillis": 1777416400000
  }
}
```

### `POST /v1/uploads/{uploadId}/confirm`
- use case: tell the backend that upload completed successfully
- auth: required

Request draft:

```json
{
  "etag": "placeholder-etag",
  "objectKey": "uploads/2026/04/upload_001.jpg"
}
```

Response draft:

```json
{
  "requestId": "req_confirm_upload",
  "data": {
    "uploadId": "upload_001",
    "fileName": "IMG_0001.JPG",
    "mediaType": "image",
    "objectKey": "uploads/2026/04/upload_001.jpg",
    "state": "success",
    "progressPercent": 100,
    "errorMessage": null
  }
}
```

### `POST /v1/uploads/{uploadId}/cancel`
- use case: cancel one upload task
- auth: required

### `GET /v1/uploads/{uploadId}`
- use case: query one upload task status
- auth: required

## Upload State Draft
- `waiting`
- `uploading`
- `success`
- `failure`
- `cancelled`

## Field Notes
- `provider` is a placeholder and may later support `oss`, `s3`, or similar
- upload token and upload task are separate DTO shapes
- system media should only enter app-content media after confirm-upload success

## Error Code Placeholders
- `UPLOAD_TOKEN_EXPIRED`
- `UPLOAD_INVALID_MIME`
- `UPLOAD_TOO_LARGE`
- `UPLOAD_NOT_FOUND`
- `UPLOAD_ALREADY_CONFIRMED`
- `UPLOAD_CANCELLED`
- `NOT_IMPLEMENTED`

## Auth Placeholder
- bearer token required in principle
- no real token flow beyond placeholder structures in this stage

## Stage 11.5 Draft-Only Notes
- no real file transfer
- no multipart chunk strategy yet
- no resume or retry protocol yet
