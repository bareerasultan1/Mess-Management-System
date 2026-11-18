with audience as materialized (
    select id from campus_student where id > ? order by id limit 128
), inserted as (
    insert into campus_inbox(event_id,recipient,delivered) select ?,id,? from audience on conflict do nothing
)
select id from audience order by id
