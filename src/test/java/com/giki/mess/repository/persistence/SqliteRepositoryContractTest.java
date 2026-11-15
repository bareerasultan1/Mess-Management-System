package com.giki.mess.repository.persistence;

import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("Repository contract: SQLite implementations")
class SqliteRepositoryContractTest extends RepositoryContractTest {

    @TempDir
    Path folder;

    @Override
    protected void createRepositories() {
        SqliteDatabase db = SqliteDatabase.forFile(folder.resolve("mess.db"));
        db.initialize();
        ledgers = new SqliteLedgerRepository(db);
        bills = new SqliteBillRepository(db);
        payments = new SqlitePaymentRepository(db);
    }
}
