alter table if exists small_albums
    add column if not exists last_modified_by_user_id varchar(255);

update small_albums
set last_modified_by_user_id = creator_user_id
where last_modified_by_user_id is null
  and creator_user_id is not null;
