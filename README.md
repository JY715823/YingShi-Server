# YingShi Server

映时后端服务。当前服务用于支撑 Android 客户端的真实联调模式，已经覆盖认证、相册、帖子、媒体、评论、回收站、上传和健康检查等核心契约。

## 当前能力

- Java 17 + Spring Boot。
- Spring Web MVC REST API。
- Spring Data JPA。
- H2 dev 数据库和 PostgreSQL runtime 依赖。
- JWT 登录会话。
- OpenAPI / Swagger UI。
- 本地媒体文件存储。
- dev seed 数据初始化。

## 已实现接口范围

- `GET /api/health`
- `POST /api/auth/login`
- `GET /api/auth/me`
- `POST /api/auth/logout`
- `GET /api/albums`
- `GET /api/albums/{albumId}/posts`
- `GET /api/media/feed`
- `GET /api/media/files/{mediaId}`
- `DELETE /api/media/{mediaId}`
- `GET /api/posts/{postId}`
- `POST /api/posts`
- `PATCH /api/posts/{postId}`
- `PATCH /api/posts/{postId}/cover`
- `PATCH /api/posts/{postId}/media-order`
- `POST /api/posts/{postId}/media`
- `DELETE /api/posts/{postId}`
- `DELETE /api/posts/{postId}/media/{mediaId}`
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
- `POST /api/trash/items/{trashItemId}/undo-remove`
- `GET /api/trash/pending-cleanup`
- `POST /api/uploads/token`
- `POST /api/uploads/{uploadId}/file`

## 文档

- [API 契约总览](docs/contracts/api-overview.md)
- [认证契约](docs/contracts/auth-api.md)
- [相册契约](docs/contracts/album-api.md)
- [帖子契约](docs/contracts/post-api.md)
- [媒体契约](docs/contracts/media-api.md)
- [评论契约](docs/contracts/comment-api.md)
- [回收站契约](docs/contracts/trash-api.md)
- [上传契约](docs/contracts/upload-api.md)
- [前后端联调指南](docs/integration/frontend-backend-testing-guide.md)
- [Android 真机验收指南](docs/integration/android-physical-device-acceptance-guide.md)
- [后端业务规则](docs/product/backend-business-rules.md)

## 运行

Windows:

```powershell
cd E:\Study\Android\YingShiApp\yingshi-server
.\mvnw.cmd spring-boot:run
```

运行测试：

```powershell
.\mvnw.cmd test
```

默认 dev 配置见 `src/main/resources/application-dev.yml`。

## Android 联调

1. 启动后端服务。
2. 打开 Android 客户端。
3. 进入 `我的 -> 设置 -> 后端联调诊断`。
4. 确认 `baseUrl` 指向当前服务地址。
5. 切换到 `REAL` 模式。
6. 运行 health、login、albums、media、comments、trash、upload smoke check。

当前后端主要服务于本地联调，不包含 OSS、云端存储、转码、CDN 或生产级权限体系。
