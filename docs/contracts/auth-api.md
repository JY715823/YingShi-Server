# Auth API Contract

更新时间：2026-05-25

## 状态

- 已按当前 `yingshi-server` 代码同步
- 可用于本地开发和 Android `REAL` 模式联调
- 当前没有 `/v1` 前缀

## 基础规则

- 基础路径：`/api/auth`
- `POST /login`、`POST /refresh-token` 公开访问
- `GET /me`、`PATCH /me/profile`、`POST /logout`、`POST /me/avatar`、`GET /avatar/{userId}` 要求 bearer auth
- 当前后端仍是固定双 demo 账号 + 固定共享空间模型

## 固定共享空间模型

当前 seed 账号：

- `demo.a@yingshi.local / demo123456`
- `demo.b@yingshi.local / demo123456`

当前行为：

- 两个账号都属于同一个 `library_shared`
- 两个账号看到的是同一份共享内容
- `libraryDisplayName` 当前是 `我们的小空间`
- `/login` 和 `/me` 都会附带另一个成员的轻量 `partner` 信息

## 1. `POST /api/auth/login`

请求：

```json
{
  "account": "demo.a@yingshi.local",
  "password": "demo123456"
}
```

响应 `data`：

```json
{
  "userId": "user_demo_a",
  "account": "demo.a@yingshi.local",
  "displayName": "映世小屋",
  "avatarUrl": null,
  "bio": "把两个人的日常安静收进这里。",
  "libraryId": "library_shared",
  "libraryDisplayName": "我们的小空间",
  "partner": {
    "userId": "user_demo_b",
    "account": "demo.b@yingshi.local",
    "displayName": "另一半",
    "avatarUrl": null,
    "bio": "把生活里的闪光片段，也把安静和想念一起留下来。"
  },
  "createdAtMillis": 1760000000000,
  "updatedAtMillis": 1760000000000,
  "accessToken": "access-token-placeholder",
  "refreshToken": "refresh-token-placeholder",
  "accessTokenExpireAtMillis": 1760001800000,
  "refreshTokenExpireAtMillis": 1760604800000
}
```

## 2. `POST /api/auth/refresh-token`

请求：

```json
{
  "refreshToken": "refresh-token-placeholder"
}
```

响应 `data`：

```json
{
  "accessToken": "access-token-placeholder-new",
  "refreshToken": "refresh-token-placeholder-new",
  "accessTokenExpireAtMillis": 1760005400000,
  "refreshTokenExpireAtMillis": 1760608400000
}
```

说明：

- 会基于 refresh token 重新签发整套 token bundle
- 当前仍不提供服务端 token revocation 黑名单逻辑

## 3. `GET /api/auth/me`

请求头：

```http
Authorization: Bearer <accessToken>
```

响应 `data`：

```json
{
  "userId": "user_demo_a",
  "account": "demo.a@yingshi.local",
  "displayName": "映世小屋",
  "avatarUrl": null,
  "bio": "把两个人的日常安静收进这里。",
  "libraryId": "library_shared",
  "libraryDisplayName": "我们的小空间",
  "partner": {
    "userId": "user_demo_b",
    "account": "demo.b@yingshi.local",
    "displayName": "另一半",
    "avatarUrl": null,
    "bio": "把生活里的闪光片段，也把安静和想念一起留下来。"
  },
  "createdAtMillis": 1760000000000,
  "updatedAtMillis": 1760000000000
}
```

## 4. `PATCH /api/auth/me/profile`

请求：

```json
{
  "displayName": "映世小屋",
  "bio": "把两个人的日常安静收进这里。"
}
```

校验规则：

- `displayName` 必填，最大 `80`
- `bio` 可为空，最大 `280`

响应：

- 返回更新后的 `AuthCurrentUserResponse`
- 结构与 `/api/auth/me` 一致

说明：

- 当前只允许修改当前登录用户自己
- 不支持通过 path 或 body 指向其他用户

## 5. `POST /api/auth/logout`

请求体可为空，当前也兼容下面这种占位结构：

```json
{
  "refreshToken": "refresh-token-placeholder"
}
```

响应 `data`：

```json
{
  "success": true
}
```

说明：

- 当前阶段没有服务端 token 吊销能力
- Android 端退出登录时仍会本地清 token

## 6. `POST /api/auth/me/avatar`

请求：

- content type: `multipart/form-data`
- 表单字段名必须是 `file`

响应：

- 返回更新后的 `AuthCurrentUserResponse`
- 成功后 `avatarUrl` 会变成 `/api/auth/avatar/{userId}`

说明：

- 当前只接受可读图片
- 服务端会统一转成 JPEG

## 7. `GET /api/auth/avatar/{userId}`

响应：

- `200 image/jpeg`

说明：

- 当前要求 bearer auth
- 只有当前共享空间成员可读取该头像
- 未上传头像时返回 `404`

## 当前未提供的认证能力

- 注册
- 找回密码
- 第三方登录

## 错误码

- `AUTH_INVALID_CREDENTIALS`
- `AUTH_TOKEN_EXPIRED`
- `AUTH_UNAUTHORIZED`
- `AUTH_SESSION_INVALID`
- `FORBIDDEN`
- `NOT_FOUND`
- `VALIDATION_ERROR`
- `SERVER_ERROR`
