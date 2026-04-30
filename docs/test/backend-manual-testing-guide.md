# Yingshi Server 手动测试与联调教程

## 1. 这份文档是干什么的

这是一份给开发者自己使用的后端手动测试和客户端联调教程。

目标有两个：

1. 先确认 `yingshi-server` 本身能正常启动、登录、读写数据、上传文件、删除和恢复。
2. 再把 Android 客户端或别的前端指到这个服务上，做真正联调。

建议你不要一上来就直接打开客户端点页面。

最稳的顺序是：

1. 启动后端
2. 用 Swagger 或 Postman 自测
3. 确认核心接口都通
4. 再接客户端

---

## 2. 当前后端已经支持什么

截至现在，这个后端已经有这些能力：

- 健康检查
- Swagger / OpenAPI
- 账号密码登录
- `current user`
- 相册列表
- 相册下帖子列表
- 帖子详情
- 全局媒体流
- 帖子评论和媒体评论
- 本地上传
- 创建帖子
- 向已有帖子加媒体
- 调整帖子内媒体顺序
- 设置帖子封面
- 删除帖子
- 帖子内目录删媒体
- 媒体系统删
- 回收站列表、详情、恢复、移出回收站、撤销移出

当前开发环境特征：

- 端口固定是 `8080`
- profile 默认是 `dev`
- 数据库是内存 H2
- JPA 是 `create-drop`
- 本地上传文件写到项目根目录下的 `local-storage`

这几个点很重要：

- 重启服务后，数据库里的数据会重置回 seed 初始状态
- 但是 `local-storage` 目录里的文件不会自动回滚
- 所以如果你想彻底回到初始环境，除了重启服务，也可以手动删掉 `local-storage`

---

## 3. 你需要准备什么

### 3.1 本机环境

- Java 17
- 可用的 `8080` 端口
- Windows PowerShell、Terminal、Git Bash 任意一个

### 3.2 你至少准备一种调接口工具

推荐优先级：

1. Swagger UI
2. Postman / Apifox
3. `curl.exe`

如果你在 Windows PowerShell 里直接敲 `curl`，它可能会走 PowerShell 自己的别名，不一定是你想要的 cURL。

如果你想严格按命令行来，建议用：

```powershell
curl.exe
```

或者直接用 Swagger，最省心。

### 3.3 准备一个本地测试文件

为了测试上传，建议你提前准备：

- 一张小图片，比如 `demo.jpg`
- 或一个很小的视频，比如 `demo.mp4`

注意：

- 上传接口会校验 `fileSizeBytes`
- 所以你在申请上传凭证时，填的大小必须和实际文件大小一致

---

## 4. 如何启动后端

在项目根目录执行：

```powershell
.\mvnw.cmd spring-boot:run
```

启动成功后，优先检查下面几个地址：

- 健康检查：http://localhost:8080/api/health
- Swagger UI：http://localhost:8080/swagger-ui.html
- OpenAPI 文档：http://localhost:8080/v3/api-docs
- H2 Console：http://localhost:8080/h2-console

如果你想先确认构建和测试没问题，可以先跑：

```powershell
.\mvnw.cmd test
```

---

## 5. 先认识一下 seed 测试数据

### 5.1 测试账号

- `demo.a@yingshi.local / demo123456`
- `demo.b@yingshi.local / demo123456`

### 5.2 当前共享空间

- `space_demo_shared`

### 5.3 现成可用的相册

- `album_001` `Spring Window`
- `album_002` `Weekend Notes`
- `album_003` `Gear Edit Picks`

### 5.4 现成可用的帖子

- `post_001` `Night Walk`
- `post_002` `Desk Light`
- `post_003` `Train Window`

### 5.5 现成可用的媒体

- `media_001`
- `media_002`
- `media_003`
- `media_004`
- `media_005`
- `media_006`

### 5.6 现成评论

- `post_001` 下已经有 2 条帖子评论
- `media_001` 下已经有 2 条媒体评论

---

## 6. 最推荐的测试方式：先走 Swagger

### 6.1 打开 Swagger

浏览器打开：

```text
http://localhost:8080/swagger-ui.html
```

### 6.2 先登录

找到：

- `POST /api/auth/login`

请求体填：

```json
{
  "account": "demo.a@yingshi.local",
  "password": "demo123456"
}
```

执行成功后，返回里会有：

- `accessToken`
- `refreshToken`

### 6.3 设置 Bearer Token

点击 Swagger 页面右上角的 `Authorize`。

把 `accessToken` 填进去。

建议只填 token 本体，不要自己手动加 `Bearer ` 前缀。Swagger 会自动按 bearer 方式带出去。

### 6.4 然后按这个顺序点

建议你按下面顺序测试：

1. `GET /api/auth/me`
2. `GET /api/albums`
3. `GET /api/albums/{albumId}/posts`
4. `GET /api/posts/{postId}`
5. `GET /api/media/feed`
6. `GET /api/posts/{postId}/comments`
7. `GET /api/media/{mediaId}/comments`
8. `POST /api/uploads/token`
9. `POST /api/uploads/{uploadId}/file`
10. `POST /api/posts/{postId}/media`
11. `DELETE /api/posts/{postId}/media/{mediaId}`
12. `DELETE /api/media/{mediaId}`
13. `GET /api/trash/items`
14. `POST /api/trash/items/{trashItemId}/restore`

如果这 14 步都通，后端主链路基本就顺了。

---

## 7. 一套完整的手动测试流程

这一段是最核心的，你可以直接照着做。

### 7.1 第一步：确认服务活着

访问：

```text
GET http://localhost:8080/api/health
```

你应该看到：

- HTTP 200
- `data.status = "UP"`
- 响应头里有 `X-Request-Id`

### 7.2 第二步：登录

请求：

```text
POST http://localhost:8080/api/auth/login
```

请求体：

```json
{
  "account": "demo.a@yingshi.local",
  "password": "demo123456"
}
```

你要重点确认：

- 返回 200
- `data.userId = user_demo_a`
- `data.spaceId = space_demo_shared`
- 有 `accessToken`

### 7.3 第三步：验证鉴权是否生效

带 token 请求：

```text
GET /api/auth/me
```

预期：

- 返回 200
- 能看到当前用户信息

再故意不带 token 调一次：

```text
GET /api/auth/me
```

预期：

- 返回 401
- `error.code = AUTH_UNAUTHORIZED`

这一步是为了确认“公开接口”和“受保护接口”分界没坏。

### 7.4 第四步：验证现成内容数据

先调：

```text
GET /api/albums
```

预期：

- 至少能看到 3 个相册

再调：

```text
GET /api/albums/album_001/posts
```

预期：

- 能看到 `post_001`
- 能看到 `post_002`

再调：

```text
GET /api/posts/post_001
```

预期：

- `coverMediaId = media_001`
- `mediaItems` 有 3 条
- 顺序是按 `sortOrder` 返回

再调：

```text
GET /api/media/feed
```

预期：

- 能看到 6 条全局媒体
- `media_001` 会带多个 `postIds`
- 这是按媒体去重后的流，不是按帖子重复铺开

### 7.5 第五步：验证评论分流

调帖子评论：

```text
GET /api/posts/post_001/comments
```

预期：

- `targetType` 都是 `POST`
- `postId` 有值
- `mediaId` 为 `null`

调媒体评论：

```text
GET /api/media/media_001/comments
```

预期：

- `targetType` 都是 `MEDIA`
- `mediaId` 有值
- `postId` 为 `null`

这一步主要确认评论流没有混。

### 7.6 第六步：测试上传闭环

先准备一个本地图片，比如：

```text
E:\tmp\demo.jpg
```

先获取文件真实大小。

在 PowerShell 可以这样看：

```powershell
(Get-Item E:\tmp\demo.jpg).Length
```

假设大小是 `12345`，那就去申请上传凭证。

请求：

```text
POST /api/uploads/token
```

请求体示例：

```json
{
  "fileName": "demo.jpg",
  "mimeType": "image/jpeg",
  "fileSizeBytes": 12345,
  "mediaType": "image",
  "width": 800,
  "height": 600,
  "durationMillis": null,
  "displayTimeMillis": 1777416600000
}
```

预期：

- 返回 200
- `provider = local`
- `state = waiting`
- 返回一个 `uploadId`

然后上传文件：

```text
POST /api/uploads/{uploadId}/file
```

这是 `multipart/form-data`，字段名必须是：

```text
file
```

预期：

- 返回 200
- `state = success`
- 返回 `media.mediaId`
- `media.url` 是 `/api/media/files/{mediaId}`

然后马上访问：

```text
GET /api/media/files/{mediaId}
```

预期：

- 返回 200
- `Content-Type` 是你上传时的 `mimeType`

### 7.7 第七步：把上传后的媒体加入已有帖子

请求：

```text
POST /api/posts/post_003/media
```

请求体：

```json
{
  "mediaIds": ["刚刚上传得到的 mediaId"],
  "coverMediaId": "刚刚上传得到的 mediaId"
}
```

预期：

- 返回 200
- `coverMediaId` 变成你这次上传的媒体
- `mediaItems` 数量增加

然后再查：

```text
GET /api/media/feed
```

预期：

- 新上传的媒体应该出现在媒体流里
- `postIds` 里应该包含 `post_003`

### 7.8 第八步：创建一个新帖子

如果你已经有现成媒体 ID，可以直接建一个帖子。

请求：

```text
POST /api/posts
```

请求体示例：

```json
{
  "title": "Manual Test Post",
  "summary": "Created during backend manual testing",
  "contributorLabel": "Demo A",
  "displayTimeMillis": 1777413000000,
  "albumIds": ["album_001"],
  "initialMediaIds": ["media_003", "media_005"],
  "coverMediaId": "media_005"
}
```

预期：

- 返回 200
- 帖子创建成功
- `albumIds` 绑定成功
- `coverMediaId` 正常回显

### 7.9 第九步：测试帖子媒体排序和封面

改封面：

```text
PATCH /api/posts/{postId}/cover
```

请求体：

```json
{
  "coverMediaId": "media_003"
}
```

改顺序：

```text
PATCH /api/posts/{postId}/media-order
```

请求体：

```json
{
  "orderedMediaIds": ["media_005", "media_003"]
}
```

预期：

- 返回 200
- 帖子详情里的 `mediaItems` 顺序改变
- 封面变成新的 `coverMediaId`

### 7.10 第十步：测试目录删

目录删代表“只把这张媒体从这个帖子里拿掉”，不是全局删除。

请求：

```text
DELETE /api/posts/post_001/media/media_002?deleteMode=directory
```

预期：

- 返回 200
- 返回一个 `trashItemId`
- `itemType = mediaRemoved`

然后再查：

```text
GET /api/posts/post_001
```

预期：

- `media_002` 不在这个帖子里了
- 但它如果还属于别的帖子，或者还在全局媒体里，依然应该可见

### 7.11 第十一步：测试系统删

系统删代表“这个媒体全局失效”。

请求：

```text
DELETE /api/media/media_001
```

预期：

- 返回 200
- `itemType = mediaSystemDeleted`
- 返回一个 `trashItemId`

然后检查几个地方：

1. `GET /api/media/feed`
   预期：`media_001` 不见了

2. `GET /api/posts/post_001`
   预期：这个帖子里 `media_001` 不见了，封面可能自动换成别的媒体

3. `GET /api/media/media_001/comments`
   预期：返回 404

这三步很关键，因为它们能验证系统删和目录删的区别。

### 7.12 第十二步：测试回收站

先看列表：

```text
GET /api/trash/items
```

预期：

- 能看到刚才删出来的条目

再看详情：

```text
GET /api/trash/items/{trashItemId}
```

预期：

- 能看到 `itemType`
- 能看到关联的 `postId` 或 `mediaId`
- 能看到是否可恢复

### 7.13 第十三步：测试恢复

恢复目录删：

```text
POST /api/trash/items/{trashItemId}/restore
```

预期：

- 返回 200
- `state = restored`

再查原帖子：

```text
GET /api/posts/post_001
```

预期：

- 原来的媒体关系回来了
- 原 `sortOrder` 尽量恢复

恢复系统删也是同一个接口。

恢复后你要再看：

1. `GET /api/media/feed`
2. `GET /api/posts/{postId}`
3. `GET /api/media/{mediaId}/comments`

预期：

- 媒体重新出现
- 相关帖子重新挂回该媒体
- 媒体评论入口重新可访问

### 7.14 第十四步：测试“移出回收站”和“撤销移出”

先移出回收站：

```text
POST /api/trash/items/{trashItemId}/remove
```

预期：

- 返回 `removedAtMillis`
- 返回 `undoDeadlineMillis`

再查：

```text
GET /api/trash/pending-cleanup
```

预期：

- 能看到刚刚移出的条目

然后撤销：

```text
POST /api/trash/items/{trashItemId}/undo-remove
```

预期：

- 条目重新回到 `inTrash`

注意：

- 现在还没有真实的 24 小时后台清理
- `pending cleanup` 目前只是状态占位

---

## 8. 如果你想用命令行测，推荐这样做

如果你不想全靠 Swagger，建议至少把登录和 token 保存跑一下。

### 8.1 登录

```powershell
curl.exe -X POST http://localhost:8080/api/auth/login `
  -H "Content-Type: application/json" `
  -d "{\"account\":\"demo.a@yingshi.local\",\"password\":\"demo123456\"}"
```

### 8.2 之后所有受保护接口都带上

```text
Authorization: Bearer <accessToken>
```

### 8.3 一个简单自测组合

```powershell
curl.exe http://localhost:8080/api/albums -H "Authorization: Bearer <accessToken>"
curl.exe http://localhost:8080/api/posts/post_001 -H "Authorization: Bearer <accessToken>"
curl.exe http://localhost:8080/api/media/feed -H "Authorization: Bearer <accessToken>"
curl.exe http://localhost:8080/api/posts/post_001/comments -H "Authorization: Bearer <accessToken>"
```

---

## 9. 客户端联调怎么做

推荐按“先确认网络通，再确认登录通，再确认页面通”的顺序来。

### 9.1 先决定客户端访问哪个 base URL

如果客户端和后端都在同一台电脑上：

- 浏览器 / Postman：`http://localhost:8080`

如果是 Android Emulator：

- 通常用：`http://10.0.2.2:8080`

如果是真机：

- 用你电脑在局域网里的 IP，比如：`http://192.168.1.100:8080`

你要保证：

- 手机和电脑在同一个局域网
- 电脑防火墙允许 `8080`

### 9.2 在客户端里先只接登录

先只打通：

- `POST /api/auth/login`
- `GET /api/auth/me`

这一阶段先不要急着接复杂页面。

先确认：

- 登录成功能拿到 token
- token 能持久化
- 后续请求会自动带 `Authorization: Bearer <token>`
- 401 时客户端能正确处理

### 9.3 再接只读页面

推荐顺序：

1. 相册页接 `GET /api/albums`
2. 相册详情页接 `GET /api/albums/{albumId}/posts`
3. 帖子详情页接 `GET /api/posts/{postId}`
4. 照片流接 `GET /api/media/feed`
5. 评论区接 `GET /api/posts/{postId}/comments` 或 `GET /api/media/{mediaId}/comments`

这样做的好处是：

- 先把读接口稳定下来
- 再去碰上传、删除、恢复这些状态更复杂的能力

### 9.4 再接写接口

推荐顺序：

1. `POST /api/posts/{postId}/comments`
2. `PATCH /api/comments/{commentId}`
3. `DELETE /api/comments/{commentId}`
4. `POST /api/uploads/token`
5. `POST /api/uploads/{uploadId}/file`
6. `POST /api/posts/{postId}/media`
7. `PATCH /api/posts/{postId}/cover`
8. `PATCH /api/posts/{postId}/media-order`
9. 删除和回收站接口

### 9.5 如果客户端要做上传

本地上传链路是两段式：

1. 先申请上传任务
2. 再把文件传到 `uploadUrl`

所以客户端要做的不是“直接传文件到一个固定地址”，而是：

1. 调 `POST /api/uploads/token`
2. 拿到 `uploadId` 和 `uploadUrl`
3. 用 multipart 上传 `file`
4. 从返回里拿 `mediaId`
5. 再把这个 `mediaId` 挂到帖子

### 9.6 如果联调时页面表现不对，先这样排查

如果页面空白、列表为空、详情打不开，优先按这个顺序查：

1. 请求 URL 对不对
2. token 有没有带上
3. 是否误用了 `localhost`
4. 目标 `postId` / `albumId` / `mediaId` 是否存在
5. 该数据是否被你前一步删进回收站了
6. 服务是否中途重启，导致 H2 数据被重置

---

## 10. 我推荐你按这个回归顺序测一遍

如果你想做一次比较完整的回归，建议照下面顺序：

1. `health`
2. `login`
3. `me`
4. `albums`
5. `album posts`
6. `post detail`
7. `media feed`
8. `post comments`
9. `media comments`
10. `upload token`
11. `upload file`
12. `attach media to post`
13. `create post`
14. `update cover`
15. `update media order`
16. `directory delete`
17. `system delete`
18. `trash list`
19. `trash detail`
20. `restore`
21. `remove from trash`
22. `undo remove`

你每完成一个步骤，最好记一下：

- 请求体
- 返回体
- 关键 ID
- 这个操作是否影响了后面的测试数据

这样出了问题会很好定位。

---

## 11. 常见坑

### 11.1 重启服务后数据没了

这是正常的。

因为开发环境是：

- H2 内存库
- `ddl-auto: create-drop`

所以重启后数据会回到 seed 初始状态。

### 11.2 上传时报文件大小不匹配

最常见原因是：

- `fileSizeBytes` 不是实际文件大小
- `mimeType` 填错

### 11.3 Swagger 里明明登录了，接口还是 401

优先检查：

- 有没有点 `Authorize`
- 填进去的是不是 `accessToken`
- 有没有把 `refreshToken` 误填进去

### 11.4 真机联调一直连不上

优先检查：

- 后端是不是监听成功了
- 手机和电脑是否同网段
- base URL 是否写成了 `localhost`
- 电脑防火墙有没有拦 `8080`

### 11.5 发现评论或媒体“突然没了”

先看是不是你刚做过：

- 删帖子
- 系统删媒体
- 恢复回收站

现在评论和媒体可见性会跟随帖子/媒体状态变化。

---

## 12. 建议你记录一份联调结果

每次联调完成后，建议你至少记这几项：

- 后端分支名或 commit
- 客户端分支名或 commit
- 测试时间
- base URL
- 登录是否通过
- 上传是否通过
- 创建帖子是否通过
- 删除和恢复是否通过
- 未解决问题列表

如果后面要多人协作，这份记录会非常有用。

---

## 13. 一句话版建议

先用 Swagger 把登录、相册、帖子、评论、上传、删除、回收站全点通，再让客户端接 `http://10.0.2.2:8080` 或你电脑局域网 IP，这样联调最稳。
