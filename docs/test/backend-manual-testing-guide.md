# Yingshi Server 手动测试与联调教程

## 1. 文档目的

这是一份给开发者自测后端与联调 Android REAL 模式的手册。

推荐顺序：

1. 启动后端
2. 用 Swagger 或 Postman 自测
3. 确认登录、大相册、小相册、媒体、评论、上传、回收站都正常
4. 再接客户端

## 2. 当前后端支持的核心能力

- 健康检查
- 登录与当前用户
- 大相册列表
- 大相册下小相册列表
- 小相册详情
- 全局媒体流
- 小相册评论和媒体评论
- 本地上传
- 创建小相册
- 向已有小相册加媒体
- 调整小相册内媒体顺序
- 设置小相册封面
- 删除小相册
- 小相册内目录删媒体
- 媒体系统删
- 回收站列表、详情、恢复、移出、撤销移出

## 3. 启动

```powershell
.\mvnw.cmd spring-boot:run
```

如需先跑测试：

```powershell
.\mvnw.cmd test
```

## 4. Seed 数据

### 4.1 测试账号

- `demo.a@yingshi.local / demo123456`
- `demo.b@yingshi.local / demo123456`

### 4.2 大相册

- `album_001` `Spring Window`
- `album_002` `Weekend Notes`
- `album_003` `Gear Edit Picks`

### 4.3 小相册

- `post_001` `Night Walk`
- `post_002` `Desk Light`
- `post_003` `Train Window`

### 4.4 媒体

- `media_001` 到 `media_006`

## 5. 推荐 Swagger 流程

按这个顺序验证：

1. `GET /api/auth/me`
2. `GET /api/albums`
3. `GET /api/albums/{albumId}/small-albums`
4. `GET /api/small-albums/{smallAlbumId}`
5. `GET /api/media/feed`
6. `GET /api/small-albums/{smallAlbumId}/comments`
7. `GET /api/media/{mediaId}/comments`
8. `POST /api/uploads/token`
9. `POST /api/uploads/{uploadId}/file`
10. `POST /api/small-albums/{smallAlbumId}/media`
11. `DELETE /api/small-albums/{smallAlbumId}/media/{mediaId}`
12. `DELETE /api/media/{mediaId}`
13. `GET /api/trash/items`
14. `POST /api/trash/items/{trashItemId}/restore`

## 6. 关键手测点

### 6.1 大相册与小相册

- `GET /api/albums` 应返回 `smallAlbumCount`
- `GET /api/albums/album_001/small-albums` 应能看到 `post_001` 和 `post_002`
- `GET /api/small-albums/post_001` 应返回 `albumId`、`coverMediaId` 与 `mediaItems`

### 6.2 评论分流

- `GET /api/small-albums/post_001/comments` 的 `targetType` 应为 `SMALL_ALBUM`
- `GET /api/media/media_001/comments` 的 `targetType` 应为 `MEDIA`

### 6.3 上传闭环

- 先调用 `POST /api/uploads/token`
- 再用 `POST /api/uploads/{uploadId}/file` 上传 `multipart/form-data` 字段 `file`
- 成功后应返回 `media.mediaId`
- 返回的媒体 `smallAlbumIds` 初始可以为空

### 6.4 加入已有小相册

```text
POST /api/small-albums/post_003/media
```

预期：

- 返回 200
- `coverMediaId` 与 `mediaItems` 更新成功
- `GET /api/media/feed` 中对应媒体的 `smallAlbumIds` 包含 `post_003`

### 6.5 创建小相册

```text
POST /api/small-albums
```

请求里只传一个 `albumId`，不再支持多大相册归属。

### 6.6 目录删与系统删

- `DELETE /api/small-albums/post_001/media/media_002?deleteMode=directory`
  - 只删除当前小相册关系，返回 `mediaRemoved`
- `DELETE /api/media/media_001`
  - 全局删除媒体，返回 `mediaSystemDeleted`

### 6.7 回收站恢复

- `smallAlbumDeleted` 恢复小相册本体、评论和关系
- `mediaRemoved` 恢复小相册内媒体关系
- `mediaSystemDeleted` 恢复媒体本体及关联关系

## 7. curl 简例

```powershell
curl.exe http://localhost:8080/api/albums -H "Authorization: Bearer <accessToken>"
curl.exe http://localhost:8080/api/small-albums/post_001 -H "Authorization: Bearer <accessToken>"
curl.exe http://localhost:8080/api/media/feed -H "Authorization: Bearer <accessToken>"
curl.exe http://localhost:8080/api/small-albums/post_001/comments -H "Authorization: Bearer <accessToken>"
```

## 8. Android 联调建议

推荐顺序：

1. 登录页接 `POST /api/auth/login` 与 `GET /api/auth/me`
2. 相册页接 `GET /api/albums`
3. 大相册下小相册列表接 `GET /api/albums/{albumId}/small-albums`
4. 小相册详情接 `GET /api/small-albums/{smallAlbumId}`
5. 照片流接 `GET /api/media/feed`
6. 评论区接小相册评论与媒体评论接口
7. 再接上传、加入小相册、删除和回收站

## 9. 回归建议

完整回归建议按下面顺序：

1. `health`
2. `login`
3. `me`
4. `albums`
5. `album small albums`
6. `small album detail`
7. `media feed`
8. `small album comments`
9. `media comments`
10. `upload token`
11. `upload file`
12. `attach media to small album`
13. `create small album`
14. `update cover`
15. `update media order`
16. `directory delete`
17. `system delete`
18. `trash list`
19. `trash detail`
20. `restore`
21. `remove from trash`
22. `undo remove`
