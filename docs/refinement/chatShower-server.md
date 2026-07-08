# ChatShower — Server 端精修

> 配合客户端 brief `E:\Study\App\YingShi\docs\refinement\chatShower.md` 使用。

- Module key: `chatShower-server`
- Status: `planning`
- Last updated: `2026-07-02`
- Primary surfaces: `server`

## 目标

将聊天数据从 snapshot blob 拆为 5 张关系表 + 行级增量同步 + 独立媒体通道 + Server 端 ZIP 解析。

## 当前状态

- `chat_snapshots` 表: 每 library 一条记录，`payload_json` TEXT 存整个客户端 DB 序列化 JSON
- API: `GET/PUT /api/chat/imported/snapshot`
- 无媒体存储
- 无行级同步

## 新增数据表

### V__migration: chat_imported_tables

```sql
-- 1. 聊天会话
CREATE TABLE imported_chats (
    id              VARCHAR(255) PRIMARY KEY,
    library_id      VARCHAR(255) NOT NULL,
    chat_stable_key VARCHAR(255) NOT NULL,
    display_name    VARCHAR(500),
    chat_type       VARCHAR(20) NOT NULL DEFAULT 'UNKNOWN',  -- PRIVATE/GROUP/UNKNOWN
    peer_uid        VARCHAR(255),
    self_uid        VARCHAR(255),
    message_count   INT NOT NULL DEFAULT 0,
    last_message_preview TEXT,
    last_import_at  TIMESTAMPTZ,
    last_inserted_count INT DEFAULT 0,
    last_merged_count INT DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(library_id, chat_stable_key)
);

-- 2. 消息
CREATE TABLE imported_messages (
    id                      BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    library_id              VARCHAR(255) NOT NULL,
    chat_id                 VARCHAR(255) NOT NULL REFERENCES imported_chats(id),
    message_stable_key      VARCHAR(255) NOT NULL,
    source_message_id       VARCHAR(255),
    fallback_signature      VARCHAR(500),
    timestamp               TIMESTAMPTZ NOT NULL,
    sender_stable_key       VARCHAR(255),
    sender_display_name     VARCHAR(255),
    sender_uin              VARCHAR(50),
    type                    VARCHAR(20) NOT NULL DEFAULT 'TEXT',
    text                    TEXT,
    html                    TEXT,
    raw_content_json        TEXT,
    reply_ref_message_id    VARCHAR(255),
    reply_ref_sender_name   VARCHAR(255),
    reply_ref_text          TEXT,
    json_title              VARCHAR(500),
    json_summary            TEXT,
    call_summary            VARCHAR(500),
    recalled                BOOLEAN NOT NULL DEFAULT FALSE,
    system_message          BOOLEAN NOT NULL DEFAULT FALSE,
    search_text             TEXT,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(library_id, chat_id, message_stable_key)
);

-- 3. 参与者
CREATE TABLE imported_participants (
    id                      BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    library_id              VARCHAR(255) NOT NULL,
    chat_id                 VARCHAR(255) NOT NULL REFERENCES imported_chats(id),
    participant_stable_key  VARCHAR(255) NOT NULL,
    uid                     VARCHAR(255),
    uin                     VARCHAR(50),
    display_name            VARCHAR(255),
    avatar_local_path       VARCHAR(500),
    is_self                 BOOLEAN NOT NULL DEFAULT FALSE,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(library_id, chat_id, participant_stable_key)
);

-- 4. 资源（图片/视频/音频/文件）
CREATE TABLE imported_resources (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    library_id      VARCHAR(255) NOT NULL,
    message_id      BIGINT NOT NULL REFERENCES imported_messages(id),
    ordinal         INT NOT NULL DEFAULT 0,
    type            VARCHAR(20) NOT NULL DEFAULT 'UNKNOWN',
    render_kind     VARCHAR(20) NOT NULL DEFAULT 'UNKNOWN',
    stored_file_name VARCHAR(500),
    stored_file_path VARCHAR(1000),   -- Server 端存储 key
    mime_type       VARCHAR(100),
    md5             VARCHAR(32),
    width_px        INT,
    height_px       INT,
    duration_seconds INT,
    file_size_bytes BIGINT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 5. 消息搜索索引
CREATE TABLE imported_message_search (
    message_id      BIGINT PRIMARY KEY REFERENCES imported_messages(id),
    library_id      VARCHAR(255) NOT NULL,
    chat_id         VARCHAR(255) NOT NULL,
    message_stable_key VARCHAR(255) NOT NULL,
    search_text     TEXT
);

-- 性能索引
CREATE INDEX idx_imported_chats_library ON imported_chats(library_id);
CREATE INDEX idx_imported_messages_chat_ts ON imported_messages(library_id, chat_id, timestamp);
CREATE INDEX idx_imported_messages_search ON imported_message_search USING GIN(to_tsvector('simple', search_text));
CREATE INDEX idx_imported_resources_message ON imported_resources(library_id, message_id);
CREATE INDEX idx_imported_resources_md5 ON imported_resources(library_id, md5);
CREATE INDEX idx_imported_participants_chat ON imported_participants(library_id, chat_id);
```

## 新增 API

### 行级同步

```
POST /api/chat/imported/sync
Request:  ChatImportedSyncRequest  { lastSyncVersionMillis, changes: { chats, messages, participants, resources, messageSearch, deletedRowIds } }
Response: ChatImportedSyncResponse { versionMillis, changes: { ... } }
```

逻辑: 和 LedgerSyncService 完全一致
1. Apply client upserts (per table)
2. Apply client deletes
3. Query server changes since `lastSyncVersionMillis`
4. Return new version + server changes

### ZIP 上传解析

```
POST /api/chat/imported/upload-zip
Content-Type: multipart/form-data
Body: file=<zip-file>
Response: { success: true, stats: { chats, messages, resources, mediaStored } }
```

逻辑:
1. 接收 ZIP 文件到临时目录
2. 解析 manifest.json
3. 流式读取 chunks/*.jsonl
4. 写入 5 张关系表（批量 INSERT）
5. 提取媒体文件到存储目录 `chat-imports/{libraryId}/{chatStableKey}/resources/`
6. 清理临时文件
7. 返回统计

### 媒体通道

```
POST /api/chat/imported/media/upload    -- 上传（multipart, 客户端 fallback 用）
GET  /api/chat/imported/media/{key}     -- 下载（流式）
HEAD /api/chat/imported/media/{key}     -- 存在性检查
```

存储: 复用现有 ObjectStorageService，key 前缀 `chat-imports/`

## 媒体存储架构全景

整个 app 共用一个 MinIO 桶 `yingshi-media`（或本地 `local-storage/`），通过 key 前缀隔离各模块：

```
yingshi-media/
├── originals/          ← 照片/相册模块（主媒体流）
│   └── {yyyy}/{MM}/{mediaId}{ext}
├── previews/           ← 照片预览/封面（自动生成，1280px）
│   └── {yyyy}/{MM}/{mediaId}-preview-v2-1280.jpg
├── avatars/            ← 用户头像
│   └── {userId}.jpg
├── chat-imports/       ← 【新增】聊天媒体（和照片完全隔离）
│   └── {libraryId}/{chatStableKey}/
│       ├── resources/{storedFileName}
│       └── avatars/{uin}.{ext}
└── seed/               ← 种子数据
```

**和照片模块的关系**：
- 共用同一个 `ObjectStorageService`（策略模式：Local / MinIO / S3）
- 聊天媒体不走照片的 UploadService / MediaEntity 体系，有独立的 ChatMediaService
- 照片有上传状态机、去重检测、预览生成（1280px）、视频封面提取（ffmpeg）
- 聊天媒体不需要这些——文件从 QCE 导出时已是最终形态，只做 存储 + 下载 + MD5 去重
- 访问 URL 也独立：照片走 `/api/media/files/{mediaId}`，聊天走 `/api/chat/imported/media/{key}`

## 实施顺序

1. Migration + Entity + Repository（5 张表）
2. ChatImportedSyncService + Controller（行级同步）
3. ChatImportedZipService + upload-zip 端点（ZIP 解析）
4. ChatMediaService + 媒体端点（上传/下载/去重）
5. 旧 snapshot API 保留，标记 @Deprecated
6. 数据迁移工具：将现有 snapshot blob 数据迁移到关系表

## 关键决策

- 媒体存储在同一个 MinIO 桶 `yingshi-media`，通过 key 前缀 `chat-imports/` 和照片模块 `originals/` 完全隔离
- 行级同步协议直接复用 ledger 的 changelog 模式
- Server 端 ZIP 解析独立实现（Java ZipInputStream + Jackson JSON）
- 旧 snapshot API 保留向后兼容，新客户端优先使用 sync + upload-zip
