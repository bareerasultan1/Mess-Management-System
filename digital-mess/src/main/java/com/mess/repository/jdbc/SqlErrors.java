package com.mess.repository.jdbc;

import com.mess.exception.DataAccessException;
import com.mess.exception.DuplicateEntryException;

import java.sql.SQLException;

/** Converts SQLException into the application's own exception types. */
final class SqlErrors {

    private static final String UNIQUE_VIOLATION = "23505";

    private SqlErrors() { }

    static DataAccessException translate(String action, SQLException e) {
        if (UNIQUE_VIOLATION.equals(e.getSQLState())) {
            return new DuplicateEntryException("Duplicate value while trying to " + action, e);
        }
        return new DataAccessException("Database error while trying to " + action, e);
    }
}
