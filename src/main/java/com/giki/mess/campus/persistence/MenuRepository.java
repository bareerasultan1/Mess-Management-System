package com.giki.mess.campus.persistence;

import com.giki.mess.campus.menu.Menus.Menu;
import java.time.LocalDate;
import java.util.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class MenuRepository {
    private final JdbcClient db;
    public MenuRepository(JdbcClient db) { this.db=db; }

    public void insert(UUID id, LocalDate day, String meal, String dishes, boolean special) {
        db.sql("insert into campus_menu(id,day,meal,dishes,special) values (?,?,?,?,?)")
            .params(id,day,meal,dishes,special).update();
    }
    public Optional<Menu> edit(UUID id,long version,String dishes,boolean special) {
        return db.sql(Queries.EDIT_MENU).params(dishes,special,id,version).query(Menu.class).optional();
    }
    public Optional<Menu> publish(UUID id,long version) {
        return db.sql(Queries.PUBLISH_MENU).params(id,version).query(Menu.class).optional();
    }
    public List<Menu> list(LocalDate day,String meal,boolean staff,int offset) {
        var query=new StringBuilder("select * from campus_menu where true");
        if (!staff) query.append(" and published");
        if (day!=null) query.append(" and day=:day");
        if (meal!=null) query.append(" and meal=:meal");
        var sql=db.sql(query+" order by day desc,meal limit 20 offset :offset").param("offset",offset);
        if (day!=null) sql=sql.param("day",day);
        if (meal!=null) sql=sql.param("meal",meal);
        return sql.query(Menu.class).list();
    }
}
