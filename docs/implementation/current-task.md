
---

# 4. Server `current-task.md`

```md
# Current Task: Stage 12.4 - 架构减重联调支持

## 背景

Android 进入 Stage 12.4，重点是架构减重、边界整理、文档和契约收口。Server 本阶段默认不改业务逻辑。

## 目标

1. 检查媒体 URL、媒体类型、删除、恢复、评论、上传相关契约文档是否和 Android 当前实现一致。
2. 清理过期联调说明。
3. 如无必要，不修改 Server 代码。

## 不做内容

- 不改业务规则
- 不做 OSS
- 不做转码
- 不改权限体系
- 不做新接口
- 不做大重构

## 验收

1. 契约文档与 Android 实现一致。
2. 如修改 Server，mvnw test 通过。
3. 如未修改 Server，最终说明 Server 未修改。
## Stage 12.4 Sync Note

- 本轮 Stage 12.4 以 Android 架构减重为主，Server 默认不改接口和业务规则。
- Android 侧仍依赖 `mediaType/type`、`previewUrl/thumbnailUrl`、`url/mediaUrl`、`videoUrl/originalUrl` 等兼容字段；如 Server 后续调整字段，请先同步契约文档。
