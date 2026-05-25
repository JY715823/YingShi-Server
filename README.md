# YingShi Server

映时后端服务。当前代码主要服务于 Android `REAL` 模式联调，已经覆盖认证、共享空间资料、相册、帖子、媒体、评论、回收站、上传、通知和媒体文件读取等核心能力。

## 当前状态

- 技术栈：Java 17、Spring Boot、Spring Web MVC、Spring Data JPA
- 默认 profile：`dev`
- 当前本地开发默认使用 H2 + `local-storage`
- 已提供两组固定 demo 账号，共享同一个 `library_shared`
- 当前是“固定双人共享空间”模型，不是通用多空间/邀请系统

## 最新进度概览

- 认证链路已打通：登录、refresh-token、当前用户、退出登录、编辑个人资料、头像上传/读取
- 当前用户返回中已经包含：
  - `avatarUrl`
  - `bio`
  - `libraryId`
  - `libraryDisplayName`
  - `partner`
  - `createdAtMillis`
  - `updatedAtMillis`
- 照片主链路已打通：相册、帖子、媒体、评论、上传、回收站
- 上传任务已补齐 `status / confirm / cancel`
- 通知接口已提供：列表、详情、单条已读、全部已读
- 回收站已打通“恢复 / 移出 / 24h 撤销 / 永久删除”全流程
- 本地媒体文件读取已支持 `original / preview / cover` 变体和 HTTP range

## 当前已实现接口

### Public

- `GET /api/health`
- `POST /api/auth/login`
- `POST /api/auth/refresh-token`

### Authenticated

- `GET /api/auth/me`
- `PATCH /api/auth/me/profile`
- `POST /api/auth/logout`
- `POST /api/auth/me/avatar`
- `GET /api/auth/avatar/{userId}`
- `GET /api/albums`
- `GET /api/albums/{albumId}/posts`
- `GET /api/posts`
- `GET /api/posts/{postId}`
- `POST /api/posts`
- `PATCH /api/posts/{postId}`
- `PATCH /api/posts/{postId}/cover`
- `PATCH /api/posts/{postId}/media-order`
- `POST /api/posts/{postId}/media`
- `DELETE /api/posts/{postId}`
- `DELETE /api/posts/{postId}/media/{mediaId}`
- `GET /api/media/feed`
- `GET /api/media/files/{mediaId}`
- `DELETE /api/media/{mediaId}`
- `GET /api/posts/{postId}/comments`
- `GET /api/media/{mediaId}/comments`
- `POST /api/posts/{postId}/comments`
- `POST /api/media/{mediaId}/comments`
- `PATCH /api/comments/{commentId}`
- `DELETE /api/comments/{commentId}`
- `GET /api/trash/items`
- `GET /api/trash/items/{trashItemId}`
- `POST /api/trash/items/{trashItemId}/restore`
- `POST /api/trash/items/{trashItemId}/remove`
- `POST /api/trash/items/{trashItemId}/purge`
- `POST /api/trash/items/{trashItemId}/undo-remove`
- `GET /api/trash/pending-cleanup`
- `POST /api/uploads/token`
- `POST /api/uploads/{uploadId}/file`
- `GET /api/uploads/{uploadId}`
- `POST /api/uploads/{uploadId}/confirm`
- `POST /api/uploads/{uploadId}/cancel`
- `GET /api/notifications`
- `GET /api/notifications/{notificationId}`
- `POST /api/notifications/{notificationId}/read`
- `POST /api/notifications/read-all`

## 与 Android 当前对齐情况

已经和 Android `REAL` 仓库对齐的能力：

- 真实登录 / refresh-token / 会话恢复 / 退出登录
- 我的 / 个人资料编辑 / 搭子与共享空间展示
- 照片流、相册、帖子列表、帖子详情、评论
- 帖子创建、编辑、封面、排序、加媒体、删除
- 系统媒体上传 token + multipart 上传 + 上传任务状态/confirm/cancel
- 回收站列表、详情、恢复、移出、永久删除、撤销移出

后端已提供、但 Android UI 还没有完全消费的能力：

- 通知接口
- 头像上传与头像图片展示

当前仍未覆盖的更后续能力：

- 通用注册 / 找回密码 / 第三方登录
- 多空间 / 邀请 / 绑定模型
- 对象存储直连、转码 / CDN
- 远程内容离线同步

## 本地开发说明

- `spring.servlet.multipart.max-file-size`：`1024MB`
- `spring.servlet.multipart.max-request-size`：`1100MB`
- 本地文件默认写入 `local-storage`
- `dev` profile 下启用 Swagger UI、OpenAPI 和 H2 Console

常用地址：

- health：`http://localhost:8080/api/health`
- Swagger UI：`http://localhost:8080/swagger-ui.html`
- OpenAPI：`http://localhost:8080/v3/api-docs`
- H2 Console：`http://localhost:8080/h2-console`

## 文档入口

- [API 契约总览](docs/contracts/api-overview.md)
- [Auth 契约](docs/contracts/auth-api.md)
- [Post 契约](docs/contracts/post-api.md)
- [Upload 契约](docs/contracts/upload-api.md)
- [Notification 契约](docs/contracts/notification-api.md)
- [Trash 契约](docs/contracts/trash-api.md)
- [前后端联调指南](docs/integration/frontend-backend-testing-guide.md)
- [Android 真机联调指南](docs/integration/android-physical-device-acceptance-guide.md)
- [Stage 17 Auth / Profile 说明](docs/stage17-auth-profile.md)
- [协作说明](AGENTS.md)

## 运行

Windows:

```powershell
cd E:\Study\App\YingShi-Server
.\mvnw.cmd spring-boot:run
```

测试：

```powershell
.\mvnw.cmd test
```

## Android 联调

1. 启动后端服务
2. 打开 Android App
3. 进入 `我的 -> 设置 -> 后端联调诊断`
4. 把 `Base URL` 指向当前后端
5. 点击 `保存并重登`
6. 先做 `health` 检查
7. 切到 `REAL`
8. 重新打开要联调的页面，验证照片流、相册、帖子详情、上传和回收站
