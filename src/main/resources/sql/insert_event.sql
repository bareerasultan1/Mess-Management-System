insert into campus_event(id,occurred,kind,recipient,summary,available) values (?,?,?,?,?,?)
on conflict (id) do nothing
