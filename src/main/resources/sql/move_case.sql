update campus_case set status=?,resolved=case when ?='RESOLVED' then ? else resolved end
where ref=? and status=?
