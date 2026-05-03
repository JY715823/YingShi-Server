
---

# 5. Server `current-task.md`

## Stage 12.5 Sync Note

- 本轮 Server 不改业务逻辑，只同步 Viewer 契约说明。
- `postIds` 仍是 Android Viewer “所属帖子”跳转的最小稳定字段。
- `originalUrl` 可以为空；客户端会在没有独立原图资源时隐藏“加载原图”动作。
- `thumbnailUrl / previewUrl / coverUrl` 任一存在即可帮助 Viewer 在视频首开前先显示封面；全部缺失时客户端会退回统一视频占位。

```md
# Current Task: Stage 12.5 - Viewer 产品化联调支持

## 背景

Android 进入 Stage 12.5，重点是 Viewer 原图、所属帖子、长图、视频封面和混合媒体体验收口。Server 默认不做大改，只在契约字段不足时做最小修正。

## 目标

检查 Viewer 需要的契约是否清晰：

1. thumbnailUrl / mediaUrl / originalUrl / videoUrl 语义清楚
2. 没有原图资源时 originalUrl 不应伪装为完整原图
3. 媒体所属帖子 postIds / relatedPosts 数据稳定
4. 视频 mimeType / type / coverUrl 或 thumbnailUrl 稳定
5. 查询详情后能返回 Viewer 需要的媒体字段

## 不做内容

- 不做 OSS
- 不做转码
- 不做复杂封面生成
- 不改权限体系
- 不改回收站规则
- 不做新大接口

## 验收

1. Viewer 所需媒体字段契约清晰。
2. 所属帖子数据语义清晰。
3. 视频封面字段语义清晰。
4. 如修改 Server，mvnw test 通过。
5. 如未修改 Server，最终说明 Server 未修改。
