insert into campus_rating(id,menu_id,score) select ?,id,? from campus_menu where id=? and published
