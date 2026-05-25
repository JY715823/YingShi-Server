create table notification_reads (
    id varchar(255) not null,
    created_at timestamp(6) with time zone not null,
    updated_at timestamp(6) with time zone not null,
    notification_id varchar(255) not null,
    read_at timestamp(6) with time zone not null,
    user_id varchar(255) not null,
    constraint notification_reads_pkey primary key (id),
    constraint uk_notification_read_user_notification unique (user_id, notification_id)
);

create index idx_notification_reads_user_id on notification_reads (user_id);
create index idx_notification_reads_notification_id on notification_reads (notification_id);
