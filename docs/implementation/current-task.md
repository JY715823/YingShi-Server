本轮 Server 默认不改。  
只有 Android 发现契约或静态资源路径确实不满足展示需求时，再放这个：

```md
# Current Task: Stage 12.1 第一轮 - 媒体 URL 契约联调支持

## 背景

Android 正在进行 Stage 12.1 第一轮，目标是让 REAL 模式下真实媒体缩略图稳定显示。Server 本轮默认不扩展业务，只在媒体 URL、mimeType、静态资源访问等契约不满足 Android 展示需求时做最小修正。

## 本轮策略

默认不改业务逻辑。

仅检查：

1. 媒体 DTO 是否返回 Android 展示所需字段。
2. thumbnailUrl / originalUrl / mediaUrl / videoUrl 是否语义清晰。
3. 本地上传后的媒体路径是否可被真机通过局域网 baseUrl 访问。
4. 静态资源路径是否支持 Android 使用 `http://电脑局域网IP:8080/` 访问。
5. mimeType 是否稳定返回。
6. 图片 / 视频类型字段是否稳定返回。

## 不做内容

- 不做 OSS
- 不做转码
- 不改权限体系
- 不改删除 / 回收站规则
- 不改评论规则
- 不改上传业务主流程
- 不破坏 seed 数据

## 验收

1. mvnw test 通过。
2. integration-smoke.ps1 通过。
3. 真机可通过局域网 baseUrl 访问上传媒体。
4. Android 所需媒体字段契约清晰。
5. 未修改 Server 时，需要在输出中明确说明 Server 未改。