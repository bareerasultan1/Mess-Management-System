update campus_menu set published=true,version=version+1 where id=? and version=? and not published
returning *
