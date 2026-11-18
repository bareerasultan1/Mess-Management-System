package com.giki.mess.repository.persistence;

import org.junit.jupiter.api.DisplayName;

@DisplayName("Repository contract: PostgreSQL implementations")
class PostgresRepositoryContractTest extends RepositoryContractTest {

    private PostgresDatabase db;

    @Override
    protected void createRepositories() {
        db = PostgresDatabase.connect(TestPostgres.freshDatabase());
        db.initialize();
        ledgers = new PostgresLedgerRepository(db);
        bills = new PostgresBillRepository(db);
        payments = new PostgresPaymentRepository(db);
    }

    @Override
    protected void closeRepositories() {
        db.close();
    }
}
