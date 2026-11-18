with eligible as (
    select ref from campus_case where kind='COMPLAINT' and escalated is null
      and status in ('OPEN','IN_REVIEW') and created<=?
    order by created,ref limit 64 for update skip locked
)
update campus_case c set escalated=? from eligible where c.ref=eligible.ref
