alter table if exists comments drop constraint if exists comments_target_type_check;

do $$
begin
    if exists (
        select 1 from information_schema.columns
        where table_schema = 'public'
          and table_name = 'trash_items'
          and column_name = 'source_post_id'
    ) then
        if exists (
            select 1 from information_schema.columns
            where table_schema = 'public'
              and table_name = 'trash_items'
              and column_name = 'source_small_album_id'
        ) then
            execute 'update trash_items set source_small_album_id = source_post_id where source_small_album_id is null';
            execute 'alter table trash_items drop column source_post_id';
        else
            execute 'alter table trash_items rename column source_post_id to source_small_album_id';
        end if;
    end if;

    if exists (
        select 1 from information_schema.columns
        where table_schema = 'public'
          and table_name = 'trash_items'
          and column_name = 'related_post_ids'
    ) then
        if exists (
            select 1 from information_schema.columns
            where table_schema = 'public'
              and table_name = 'trash_items'
              and column_name = 'related_small_album_ids'
        ) then
            execute 'update trash_items set related_small_album_ids = related_post_ids where related_small_album_ids is null';
            execute 'alter table trash_items drop column related_post_ids';
        else
            execute 'alter table trash_items rename column related_post_ids to related_small_album_ids';
        end if;
    end if;

    if exists (
        select 1 from information_schema.columns
        where table_schema = 'public'
          and table_name = 'comments'
          and column_name = 'post_id'
    ) then
        if exists (
            select 1 from information_schema.columns
            where table_schema = 'public'
              and table_name = 'comments'
              and column_name = 'small_album_id'
        ) then
            execute 'update comments set small_album_id = post_id where small_album_id is null';
            execute 'alter table comments drop column post_id';
        else
            execute 'alter table comments rename column post_id to small_album_id';
        end if;
    end if;
end $$;

do $$
begin
    if to_regclass('public.post_media') is not null
       and to_regclass('public.small_album_media') is null then
        execute 'alter table post_media rename to small_album_media';
    end if;

    if exists (
        select 1 from information_schema.columns
        where table_schema = 'public'
          and table_name = 'small_album_media'
          and column_name = 'post_id'
    ) then
        if exists (
            select 1 from information_schema.columns
            where table_schema = 'public'
              and table_name = 'small_album_media'
              and column_name = 'small_album_id'
        ) then
            execute 'update small_album_media set small_album_id = post_id where small_album_id is null';
            execute 'alter table small_album_media drop column post_id';
        else
            execute 'alter table small_album_media rename column post_id to small_album_id';
        end if;
    end if;

    if to_regclass('public.posts') is not null
       and to_regclass('public.small_albums') is null then
        execute 'alter table posts rename to small_albums';
    end if;
end $$;

alter table if exists small_albums add column if not exists album_id varchar(255);

do $$
begin
    if to_regclass('public.post_albums') is not null
       and to_regclass('public.small_albums') is not null then
        execute $sql$
            update small_albums sa
            set album_id = selected.album_id
            from (
                select distinct on (library_id, post_id)
                    library_id,
                    post_id,
                    album_id
                from post_albums
                order by library_id, post_id, created_at
            ) selected
            where sa.album_id is null
              and sa.library_id = selected.library_id
              and sa.id = selected.post_id
        $sql$;
    end if;

    if to_regclass('public.albums') is not null
       and to_regclass('public.small_albums') is not null then
        execute $sql$
            update small_albums sa
            set album_id = selected.album_id
            from (
                select distinct on (library_id)
                    library_id,
                    id as album_id
                from albums
                order by library_id, created_at
            ) selected
            where sa.album_id is null
              and sa.library_id = selected.library_id
        $sql$;
    end if;
end $$;

update small_albums
set album_id = 'album_001'
where album_id is null;

alter table if exists small_albums alter column album_id set not null;

update comments
set target_type = 'SMALL_ALBUM'
where target_type = 'POST';

alter table if exists comments
    add constraint comments_target_type_check
    check (target_type in ('SMALL_ALBUM', 'MEDIA'));

alter table if exists small_album_media
    drop constraint if exists uk_post_media_post_media;

alter table if exists small_album_media
    drop constraint if exists uk_post_media_post_sort;

do $$
begin
    if to_regclass('public.small_album_media') is not null
       and not exists (
           select 1 from pg_constraint
           where conname = 'uk_small_album_media_small_album_media'
       ) then
        execute 'alter table small_album_media add constraint uk_small_album_media_small_album_media unique (library_id, small_album_id, media_id)';
    end if;

    if to_regclass('public.small_album_media') is not null
       and not exists (
           select 1 from pg_constraint
           where conname = 'uk_small_album_media_small_album_sort'
       ) then
        execute 'alter table small_album_media add constraint uk_small_album_media_small_album_sort unique (library_id, small_album_id, sort_order)';
    end if;
end $$;

drop table if exists post_albums;
