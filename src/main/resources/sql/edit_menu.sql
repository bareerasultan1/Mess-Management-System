update campus_menu set dishes=?,special=?,version=version+1 where id=? and version=?
returning *
