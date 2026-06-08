update trash_items
set snapshot_json = json_build_object('smallAlbumId', source_small_album_id)::text
where item_type = 'SMALL_ALBUM_DELETED'
  and source_small_album_id is not null
  and snapshot_json ~ '^[0-9]+$';

update trash_items
set snapshot_json = replace(snapshot_json, '"postId":', '"smallAlbumId":')
where item_type in ('SMALL_ALBUM_DELETED', 'MEDIA_REMOVED')
  and snapshot_json like '%"postId":%';
