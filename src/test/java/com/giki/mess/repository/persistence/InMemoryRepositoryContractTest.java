package com.giki.mess.repository.persistence;

import org.junit.jupiter.api.DisplayName;

@DisplayName("Repository contract: in-memory implementations")
class InMemoryRepositoryContractTest extends RepositoryContractTest {

    @Override
    protected void createRepositories() {
        ledgers = new InMemoryLedgerRepository();
        bills = new InMemoryBillRepository();
        payments = new InMemoryPaymentRepository();
    }
}
