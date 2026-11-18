package com.giki.mess.campus.domain;

public final class Conflict extends RuntimeException {
    private static final long serialVersionUID = 1L;
    public Conflict() { super("The record changed or the action is unavailable. Refresh and try again."); }
}
