select * from campus_event where state='READY' and available<=?
order by available,id limit 1 for update skip locked
