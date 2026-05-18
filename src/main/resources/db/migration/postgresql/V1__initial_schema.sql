create table albums (
    id varchar(255) not null,
    created_at timestamp(6) with time zone not null,
    updated_at timestamp(6) with time zone not null,
    library_id varchar(255) not null,
    cover_media_id varchar(255),
    subtitle varchar(255),
    title varchar(120) not null,
    constraint albums_pkey primary key (id)
);

create table comments (
    id varchar(255) not null,
    created_at timestamp(6) with time zone not null,
    updated_at timestamp(6) with time zone not null,
    library_id varchar(255) not null,
    author_id varchar(255) not null,
    content varchar(2000),
    deleted_at timestamp(6) with time zone,
    media_id varchar(255),
    post_id varchar(255),
    target_type varchar(20) not null,
    constraint comments_pkey primary key (id),
    constraint comments_target_type_check check (target_type in ('POST', 'MEDIA'))
);

create table media (
    id varchar(255) not null,
    created_at timestamp(6) with time zone not null,
    updated_at timestamp(6) with time zone not null,
    library_id varchar(255) not null,
    aspect_ratio double precision not null,
    bucket varchar(120),
    captured_at_millis bigint,
    checksum varchar(128),
    cover_object_key varchar(512),
    cover_url varchar(512),
    deleted_at timestamp(6) with time zone,
    display_time_millis bigint not null,
    display_time_source varchar(20) not null,
    duration_millis bigint,
    height integer not null,
    imported_at_millis bigint not null,
    media_type varchar(20) not null,
    mime_type varchar(120) not null,
    original_object_key varchar(512),
    original_url varchar(512),
    preview_object_key varchar(512),
    preview_url varchar(512) not null,
    size_bytes bigint not null,
    source_fingerprint varchar(128),
    storage_path varchar(512) not null,
    storage_provider varchar(40),
    url varchar(512) not null,
    video_url varchar(512),
    width integer not null,
    constraint media_pkey primary key (id),
    constraint media_media_type_check check (media_type in ('IMAGE', 'VIDEO'))
);

create table post_albums (
    id varchar(255) not null,
    created_at timestamp(6) with time zone not null,
    updated_at timestamp(6) with time zone not null,
    library_id varchar(255) not null,
    album_id varchar(255) not null,
    post_id varchar(255) not null,
    constraint post_albums_pkey primary key (id),
    constraint uk_post_album_post_album unique (library_id, post_id, album_id)
);

create table post_media (
    id varchar(255) not null,
    created_at timestamp(6) with time zone not null,
    updated_at timestamp(6) with time zone not null,
    library_id varchar(255) not null,
    media_id varchar(255) not null,
    post_id varchar(255) not null,
    sort_order integer not null,
    constraint post_media_pkey primary key (id),
    constraint uk_post_media_post_media unique (library_id, post_id, media_id),
    constraint uk_post_media_post_sort unique (library_id, post_id, sort_order)
);

create table posts (
    id varchar(255) not null,
    created_at timestamp(6) with time zone not null,
    updated_at timestamp(6) with time zone not null,
    library_id varchar(255) not null,
    contributor_label varchar(120),
    cover_media_id varchar(255),
    deleted_at timestamp(6) with time zone,
    display_time_millis bigint not null,
    display_time_source varchar(20) not null,
    event_ended_at_millis bigint,
    event_started_at_millis bigint,
    summary varchar(1000),
    title varchar(120) not null,
    constraint posts_pkey primary key (id)
);

create table shared_libraries (
    id varchar(255) not null,
    created_at timestamp(6) with time zone not null,
    updated_at timestamp(6) with time zone not null,
    display_name varchar(120) not null,
    constraint shared_libraries_pkey primary key (id)
);

create table shared_library_members (
    id varchar(255) not null,
    created_at timestamp(6) with time zone not null,
    updated_at timestamp(6) with time zone not null,
    library_id varchar(255) not null,
    role varchar(20) not null,
    user_id varchar(255) not null,
    constraint shared_library_members_pkey primary key (id),
    constraint shared_library_members_role_check check (role in ('OWNER', 'MEMBER')),
    constraint uk_shared_library_member_library_user unique (library_id, user_id)
);

create table trash_items (
    id varchar(255) not null,
    created_at timestamp(6) with time zone not null,
    updated_at timestamp(6) with time zone not null,
    library_id varchar(255) not null,
    deleted_at timestamp(6) with time zone not null,
    item_type varchar(40) not null,
    preview_info varchar(255) not null,
    related_media_ids varchar(2000),
    related_post_ids varchar(2000),
    removed_at timestamp(6) with time zone,
    restored_at timestamp(6) with time zone,
    snapshot_json oid not null,
    source_media_id varchar(255),
    source_post_id varchar(255),
    state varchar(40) not null,
    title varchar(255) not null,
    undo_deadline_at timestamp(6) with time zone,
    constraint trash_items_pkey primary key (id),
    constraint trash_items_item_type_check check (item_type in ('POST_DELETED', 'MEDIA_REMOVED', 'MEDIA_SYSTEM_DELETED')),
    constraint trash_items_state_check check (state in ('IN_TRASH', 'PENDING_CLEANUP', 'RESTORED'))
);

create table upload_tasks (
    id varchar(255) not null,
    created_at timestamp(6) with time zone not null,
    updated_at timestamp(6) with time zone not null,
    library_id varchar(255) not null,
    captured_at_millis bigint,
    completed_at timestamp(6) with time zone,
    display_time_millis bigint not null,
    display_time_source varchar(20) not null,
    duration_millis bigint,
    expire_at timestamp(6) with time zone not null,
    file_name varchar(255) not null,
    file_size_bytes bigint not null,
    height integer not null,
    imported_at_millis bigint not null,
    media_id varchar(255),
    media_type varchar(20) not null,
    mime_type varchar(120) not null,
    source_fingerprint varchar(128),
    state varchar(20) not null,
    stored_path varchar(512),
    width integer not null,
    constraint upload_tasks_pkey primary key (id),
    constraint upload_tasks_media_type_check check (media_type in ('IMAGE', 'VIDEO')),
    constraint upload_tasks_state_check check (state in ('WAITING', 'SUCCESS', 'CANCELLED'))
);

create table users (
    id varchar(255) not null,
    created_at timestamp(6) with time zone not null,
    updated_at timestamp(6) with time zone not null,
    account varchar(120) not null,
    avatar_url varchar(512),
    default_library_id varchar(255) not null,
    display_name varchar(80) not null,
    password_hash varchar(255) not null,
    constraint users_pkey primary key (id),
    constraint uk_users_account unique (account)
);
