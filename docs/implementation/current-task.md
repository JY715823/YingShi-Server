
---

# 4. Server `current-task.md`

```md
# Current Task: Stage 12.7-Hotfix - 传输中心与媒体交互文档同步

## 背景

Android 本轮修正传输中心 badge、上传汇总提示、时间滑条、系统媒体分区、帖子中文化和回收站入口。Server 默认不改业务逻辑，但需要同步文档并确保上传接口、媒体查询和回收站契约说明与 Android 当前行为一致。

## 目标

1. 上传接口契约保持可用。
2. Media DTO 继续支持传输中心缩略图展示。
3. 系统媒体按时间分区所需 createdAt / takenAt 字段语义清晰。
4. 回收站 24h 可撤销分类语义清晰。
5. 同步 current-task 和相关联调文档。

## 不做内容

- 不做 OSS
- 不做云端存储
- 不做新大接口
- 不改上传业务规则
- 不改回收站业务规则
- 不做转码

## 验收

1. createdAt / takenAt 字段契约说明清晰。
2. 回收站 24h 可撤销分类说明清晰。
3. 上传 Media DTO 契约说明清晰。
4. mvnw test 通过。