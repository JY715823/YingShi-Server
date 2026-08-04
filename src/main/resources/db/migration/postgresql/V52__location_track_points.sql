-- V52: 足迹定位轨迹点 + 媒体位置来源标记
--
-- location_track_points: App 后台定时采样记录的用户轨迹（客户端 GCJ-02 坐标），
-- 用于未来的足迹地图，以及上传无 GPS 媒体时按拍摄时间就近回填位置。
-- 唯一约束 (user_id, recorded_at) 保证客户端重传幂等。

create table if not exists location_track_points (
    id bigserial primary key,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    library_id varchar(255) not null,
    user_id varchar(255) not null,
    latitude double precision not null,
    longitude double precision not null,
    accuracy real,
    source varchar(32) not null default 'alarm',
    recorded_at timestamptz not null
);

create index if not exists idx_location_track_library_user_time
    on location_track_points (library_id, user_id, recorded_at desc);

create unique index if not exists uq_location_track_user_recorded
    on location_track_points (user_id, recorded_at);

-- 媒体位置来源：exif=媒体自带GPS/拍摄时实时定位, inferred=无GPS时按拍摄时间用轨迹点推断, manual=用户手动修改
alter table upload_tasks add column if not exists location_source varchar(16);
alter table media add column if not exists location_source varchar(16);
