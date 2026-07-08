-- Chat imported relational tables (replacing snapshot blob)

-- 1. Chat conversations
create table if not exists imported_chats (
    id                    varchar(255) not null,
    created_at            timestamp(6) with time zone not null,
    updated_at            timestamp(6) with time zone not null,
    library_id            varchar(255) not null,
    chat_stable_key       varchar(255) not null,
    display_name          varchar(500),
    chat_type             varchar(20) not null default 'UNKNOWN',
    peer_uid              varchar(255),
    self_uid              varchar(255),
    message_count         integer not null default 0,
    last_message_preview  text,
    last_import_at        timestamp(6) with time zone,
    last_inserted_count   integer default 0,
    last_merged_count     integer default 0,
    constraint imported_chats_pkey primary key (id),
    constraint uk_imported_chats_library_stable unique (library_id, chat_stable_key)
);

-- 2. Messages
create table if not exists imported_messages (
    id                      bigint generated always as identity,
    created_at              timestamp(6) with time zone not null,
    updated_at              timestamp(6) with time zone not null,
    library_id              varchar(255) not null,
    chat_id                 varchar(255) not null,
    message_stable_key      varchar(255) not null,
    source_message_id       varchar(255),
    fallback_signature      varchar(500),
    ts                      timestamp(6) with time zone not null,
    sender_stable_key       varchar(255),
    sender_display_name     varchar(255),
    sender_uin              varchar(50),
    msg_type                varchar(20) not null default 'TEXT',
    text                    text,
    html                    text,
    raw_content_json        text,
    reply_ref_message_id    varchar(255),
    reply_ref_sender_name   varchar(255),
    reply_ref_text          text,
    json_title              varchar(500),
    json_summary            text,
    call_summary            varchar(500),
    recalled                boolean not null default false,
    system_message          boolean not null default false,
    search_text             text,
    constraint imported_messages_pkey primary key (id),
    constraint uk_imported_messages_stable unique (library_id, chat_id, message_stable_key)
);

-- 3. Participants
create table if not exists imported_participants (
    id                      bigint generated always as identity,
    created_at              timestamp(6) with time zone not null,
    updated_at              timestamp(6) with time zone not null,
    library_id              varchar(255) not null,
    chat_id                 varchar(255) not null,
    participant_stable_key  varchar(255) not null,
    uid                     varchar(255),
    uin                     varchar(50),
    display_name            varchar(255),
    avatar_local_path       varchar(500),
    is_self                 boolean not null default false,
    constraint imported_participants_pkey primary key (id),
    constraint uk_imported_participants_stable unique (library_id, chat_id, participant_stable_key)
);

-- 4. Resources (image/video/audio/file)
create table if not exists imported_resources (
    id                bigint generated always as identity,
    created_at        timestamp(6) with time zone not null,
    updated_at        timestamp(6) with time zone not null,
    library_id        varchar(255) not null,
    message_id        bigint not null,
    ordinal           integer not null default 0,
    res_type          varchar(20) not null default 'UNKNOWN',
    render_kind       varchar(20) not null default 'UNKNOWN',
    stored_file_name  varchar(500),
    stored_object_key varchar(1000),
    mime_type         varchar(100),
    md5               varchar(32),
    width_px          integer,
    height_px         integer,
    duration_seconds  integer,
    file_size_bytes   bigint,
    constraint imported_resources_pkey primary key (id)
);

-- 5. Message search index
create table if not exists imported_message_search (
    message_id           bigint not null,
    library_id           varchar(255) not null,
    chat_id              varchar(255) not null,
    message_stable_key   varchar(255) not null,
    search_text          text,
    created_at           timestamp(6) with time zone not null,
    updated_at           timestamp(6) with time zone not null,
    constraint imported_message_search_pkey primary key (message_id)
);

-- Performance indexes
create index if not exists idx_imported_chats_library on imported_chats(library_id);
create index if not exists idx_imported_chats_updated on imported_chats(library_id, updated_at desc);
create index if not exists idx_imported_messages_chat_ts on imported_messages(library_id, chat_id, ts);
create index if not exists idx_imported_messages_updated on imported_messages(library_id, updated_at desc);
create index if not exists idx_imported_resources_message on imported_resources(library_id, message_id);
create index if not exists idx_imported_resources_md5 on imported_resources(library_id, md5);
create index if not exists idx_imported_resources_updated on imported_resources(library_id, updated_at desc);
create index if not exists idx_imported_participants_chat on imported_participants(library_id, chat_id);
create index if not exists idx_imported_participants_updated on imported_participants(library_id, updated_at desc);
create index if not exists idx_imported_message_search_updated on imported_message_search(library_id, updated_at desc);
