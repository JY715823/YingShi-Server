# Upload API Contract

更新时间：2026-05-31

## 状态

- 已按当前 `yingshi-server` 代码同步
- 当前仅支持本地文件存储 provider
- Android `REAL` 模式已按本契约接入

## 基础规则

- 基础路径：`/api/uploads`
- 所有接口都要求 bearer auth
- 当前上传链路是三段：
  1. `POST /token`
  2. `POST /{uploadId}/file`
  3. 任务 `status / confirm / cancel`

## 1. `POST /api/uploads/token`

请求：

```json
{
  "fileName": "春日散步-01.jpg",
  "mimeType": "image/jpeg",
  "fileSizeBytes": 3145728,
  "mediaType": "image",
  "width": 1440,
  "height": 1920,
  "durationMillis": null,
  "displayTimeMillis": 1777416400000,
  "capturedAtMillis": 1777416000000,
  "importedAtMillis": 1777416400000,
  "displayTimeSource": "ORIGINAL",
  "sourceFingerprint": "sha256:example"
}
```

响应 `data`：

```json
{
  "uploadId": "upload_001",
  "provider": "local",
  "uploadUrl": "/api/uploads/upload_001/file",
  "expireAtMillis": 1777417000000,
  "state": "waiting"
}
```

## 2. `POST /api/uploads/{uploadId}/file`

响应 `data`：

```json
{
  "uploadId": "upload_001",
  "state": "success",
  "media": {
    "mediaId": "media_uploaded_001",
    "mediaType": "image",
    "url": "/api/media/files/media_uploaded_001",
    "previewUrl": "/api/media/files/media_uploaded_001?variant=preview",
    "originalUrl": "/api/media/files/media_uploaded_001",
    "videoUrl": null,
    "coverUrl": null,
    "mimeType": "image/jpeg",
    "width": 1440,
    "height": 1920,
    "aspectRatio": 0.75,
    "durationMillis": null,
    "displayTimeMillis": 1777416400000,
    "capturedAtMillis": 1777416000000,
    "importedAtMillis": 1777416400000,
    "displayTimeSource": "ORIGINAL",
    "smallAlbumIds": []
  }
}
```

说明：

- 上传成功后立刻创建一条媒体记录
- `smallAlbumIds` 允许为空，表示媒体已导入但尚未加入任何小相册

## 3. `GET /api/uploads/{uploadId}`

响应 `data`：

```json
{
  "uploadId": "upload_001",
  "fileName": "春日散步-01.jpg",
  "mediaType": "image",
  "objectKey": "/api/media/files/media_uploaded_001",
  "state": "success",
  "progressPercent": 100,
  "errorMessage": null
}
```

## 4. `POST /api/uploads/{uploadId}/confirm`

请求可为空，也可以附带：

```json
{
  "etag": "fake-etag-upload_001",
  "objectKey": "uploads/fake/media_001"
}
```

## 5. `POST /api/uploads/{uploadId}/cancel`

- 未完成任务可取消，取消后状态变为 `cancelled`

## Android 当前使用方式

- 导入到 App
- 系统媒体创建小相册
- 系统媒体加入已有小相册
- 传输中心取消任务 / 上传收尾

## 错误码

- `UPLOAD_NOT_FOUND`
- `UPLOAD_ALREADY_COMPLETED`
- `UPLOAD_FILE_MISMATCH`
- `UPLOAD_STORAGE_ERROR`
- `VALIDATION_ERROR`
- `AUTH_UNAUTHORIZED`
