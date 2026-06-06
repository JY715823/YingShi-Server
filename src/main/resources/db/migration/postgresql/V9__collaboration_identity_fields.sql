alter table if exists small_albums
    add column if not exists creator_user_id varchar(255);

alter table if exists small_albums
    add column if not exists participant_user_ids varchar(2000);

alter table if exists trash_items
    add column if not exists actor_user_id varchar(255);
