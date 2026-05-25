# Upload API Contract

更新时间：2026-05-25

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
- `confirm` 当前是上传任务收尾 / 状态确认接口，不会再次创建媒体

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

说明：

- 时间元数据字段都是可选的
- 若 `displayTimeMillis` 为空，服务端会回退到 `capturedAtMillis`，再回退到 `importedAtMillis`

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

请求：

- content type: `multipart/form-data`
- 表单字段名必须是 `file`

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
    "postIds": []
  }
}
```

说明：

- 上传成功后立刻创建一条 `Media` 记录
- `postIds` 允许为空，表示媒体已导入但尚未挂帖

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

响应：

- 返回 `UploadTaskResponse`

说明：

- 已经 `success` 的任务上，当前接口是幂等状态确认
- `waiting` 状态下如提供 `objectKey`，服务端会做可选存在性校验

## 5. `POST /api/uploads/{uploadId}/cancel`

响应：

- 返回 `UploadTaskResponse`

说明：

- 未完成任务可取消，取消后状态变为 `cancelled`
- 已经 `success` 的任务不能再取消

## 当前本地存储布局

- `local-storage/originals/yyyy/MM/{mediaId}.{ext}`
- `local-storage/previews/yyyy/MM/{mediaId}-720.jpg`
- `local-storage/test/...`
- `local-storage/tmp/uploads/...`
- `local-storage/videos/posters/...`

## 当前上传限制

- `spring.servlet.multipart.max-file-size = 1024MB`
- `spring.servlet.multipart.max-request-size = 1100MB`

## Android 当前使用方式

- 底部 `上传媒体`
- 系统媒体 `导入到 App`
- 系统媒体创建帖子
- 系统媒体加入已有帖子
- 传输中心取消任务 / 上传收尾

## 当前未实现能力

- 云存储直传
- 转码 / CDN / 对象存储回调

## 错误码

- `UPLOAD_NOT_FOUND`
- `UPLOAD_ALREADY_COMPLETED`
- `UPLOAD_FILE_MISMATCH`
- `UPLOAD_STORAGE_ERROR`
- `VALIDATION_ERROR`
- `AUTH_UNAUTHORIZED`
