package com.giki.mess.campus.persistence;

import java.io.*;
import java.nio.charset.StandardCharsets;

/** Production JDBC and the PostgreSQL-only verification suite share these statements. */
public final class Queries {
    private Queries() {}
    public static final String PUBLISH_MENU=read("publish_menu");
    public static final String EDIT_MENU=read("edit_menu");
    public static final String INSERT_EVENT=read("insert_event");
    public static final String DELIVER_PAGE=read("deliver_page");
    public static final String CLAIM_EVENT=read("claim_event");
    public static final String ESCALATE_CASES=read("escalate_cases");
    public static final String MOVE_CASE=read("move_case");
    public static final String RATE_MEAL=read("rate_meal");
    private static String read(String name) {
        try (var stream=Queries.class.getResourceAsStream("/sql/"+name+".sql")) {
            if (stream==null) throw new IllegalStateException("Missing SQL resource "+name);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) { throw new UncheckedIOException(e); }
    }
}
