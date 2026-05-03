# API Overview Draft

## Status
- Stage 11.1 draft only
- no live backend required
- no final auth or pagination behavior locked yet

## Purpose
This document defines the first client-side API boundary for future backend integration.
The goal is to keep UI models separate from transport models while preserving the current fake-first app flow.

## Stage 12.5 Viewer Sync
- Android Viewer 以 `thumbnailUrl -> mediaUrl -> originalUrl` 作为图片预览优先级。
- `originalUrl` 为空，或与当前预览图地址相同，都视为“没有独立原图资源”，客户端会隐藏原图按钮。
- `postIds` 仍是 Viewer 所属帖子跳转的最小后端契约。
- 系统媒体 Viewer 不消费帖子评论、所属帖子、原图按钮等 app 内容专属字段。

## Base URL
- placeholder only: `https://api-placeholder.yingshi.local/`
- must be configurable
- must not hardcode a production server

## Auth
- bearer token placeholder
- request header draft:
  - `Authorization: Bearer <token>`
- auth contract details now live in `auth-api.md`
- token acquisition and refresh remain placeholder-only in Stage 11.2

## Envelope Draft

Successful response draft:

```json
{
  "requestId": "req_123",
  "data": {},
  "page": {
    "page": 1,
    "pageSize": 20,
    "nextCursor": null,
    "hasMore": false
  }
}
```

Error response draft:

```json
{
  "requestId": "req_123",
  "error": {
    "code": "NOT_IMPLEMENTED",
    "message": "Placeholder error",
    "details": null
  }
}
```

## Naming Rules
- JSON fields use `camelCase`
- IDs use string form such as `mediaId`, `postId`, `commentId`
- timestamps use UTC milliseconds or ISO-8601 string
- booleans use explicit names such as `isDeleted`, `isRead`, `hasMore`

## Pagination Draft
- page-number and cursor styles are both reserved
- list endpoints in Stage 11.1 expose placeholder params:
  - `page`
  - `pageSize`
  - `cursor`
- final backend can choose one style later, but the contract docs should mention the placeholder path now

## Error Code Placeholders
- `UNAUTHORIZED`
- `FORBIDDEN`
- `NOT_FOUND`
- `VALIDATION_ERROR`
- `RATE_LIMITED`
- `SERVER_ERROR`
- `NOT_IMPLEMENTED`

## Stage 11.1 Draft-Only APIs
- upload token issue and upload completion
- delete / restore mutations
- comment create / update / delete
- notification APIs are not part of this contract pass yet
## Stage 12.3 约定补充

- Media feed 只面向已经进入内容主链路的媒体，未挂帖媒体不属于主照片流返回范围。
- 删除媒体时如果会让帖子变成空帖，服务端返回 `409 DELETE_CONFLICT`，由客户端展示空帖保护提示。
- 系统媒体批量导入属于“先上传、再挂帖”的两阶段动作，客户端应在第二阶段成功后再视为最终完成。
## Stage 12.4 Client Cleanup Note

- Stage 12.4 does not introduce server API changes.
- Android now expects media URL normalization and media kind fallback to stay contract-compatible across `mediaType/type`, `url/mediaUrl`, `previewUrl/thumbnailUrl`, and `videoUrl/originalUrl`.


## Stage 12.6 Add-Flow Note

- Android ?????????????????????????
  1. ?????????
  2. ??????????????
- ???????????????????????????????????????????????
- ???????????????????????????????????????????????????????
