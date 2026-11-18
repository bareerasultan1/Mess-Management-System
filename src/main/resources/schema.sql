-- Relational schema of the Billing and Payment modules (PostgreSQL).
--
-- Money is stored as whole paisa in BIGINT columns (1 PKR = 100 paisa), so no rounding error can
-- occur and amounts up to about 92 quadrillion paisa fit. Dates use DATE, moments use TIMESTAMP.
-- Every statement ends with a semicolon at the end of a line. The script is safe to run repeatedly
-- (IF NOT EXISTS), so the application executes it at every start-up.

CREATE TABLE IF NOT EXISTS student_ledgers (
    student_id      VARCHAR(20) PRIMARY KEY,
    category        VARCHAR(20) NOT NULL CHECK (category IN ('REGULAR','SCHOLAR','SUBSIDIZED','FACULTY_GUEST')),
    balance_paisa   BIGINT      NOT NULL CHECK (balance_paisa >= 0),
    unbilled_meals  INTEGER     NOT NULL CHECK (unbilled_meals >= 0),
    status          VARCHAR(20) NOT NULL CHECK (status IN ('ACTIVE','SUSPENDED')),
    updated_at      TIMESTAMP   NOT NULL
);

CREATE TABLE IF NOT EXISTS bills (
    bill_id          VARCHAR(40) PRIMARY KEY,
    student_id       VARCHAR(20) NOT NULL REFERENCES student_ledgers(student_id),
    month            INTEGER     NOT NULL CHECK (month BETWEEN 1 AND 12),
    year             INTEGER     NOT NULL CHECK (year >= 2020),
    due_date         DATE        NOT NULL,
    base_paisa       BIGINT      NOT NULL CHECK (base_paisa >= 0),
    rebate_paisa     BIGINT      NOT NULL CHECK (rebate_paisa >= 0),
    fine_paisa       BIGINT      NOT NULL CHECK (fine_paisa >= 0),
    discount_paisa   BIGINT      NOT NULL CHECK (discount_paisa >= 0),
    net_paisa        BIGINT      NOT NULL CHECK (net_paisa >= 0),
    status           VARCHAR(20) NOT NULL CHECK (status IN ('UNPAID','PARTIALLY_PAID','PAID','OVERDUE')),
    CONSTRAINT uq_bill_student_period UNIQUE (student_id, month, year)
);

CREATE TABLE IF NOT EXISTS bill_items (
    item_id      VARCHAR(80) NOT NULL,
    bill_id      VARCHAR(40) NOT NULL REFERENCES bills(bill_id) ON DELETE CASCADE,
    student_id   VARCHAR(20) NOT NULL,
    scan_time    TIMESTAMP   NOT NULL,
    meal_type    VARCHAR(20) NOT NULL CHECK (meal_type IN ('BREAKFAST','LUNCH','DINNER')),
    rate_paisa   BIGINT      NOT NULL CHECK (rate_paisa > 0),
    PRIMARY KEY (bill_id, item_id)
);

CREATE TABLE IF NOT EXISTS payment_transactions (
    transaction_id   VARCHAR(60) PRIMARY KEY,
    student_id       VARCHAR(20) NOT NULL REFERENCES student_ledgers(student_id),
    bill_id          VARCHAR(40) NOT NULL REFERENCES bills(bill_id),
    amount_paisa     BIGINT      NOT NULL CHECK (amount_paisa > 0 AND amount_paisa <= 5000000),
    channel          VARCHAR(20) NOT NULL CHECK (channel IN ('JAZZCASH','EASYPAISA','BANK_TRANSFER')),
    idempotency_key  VARCHAR(64) NOT NULL,
    status           VARCHAR(20) NOT NULL CHECK (status IN ('INITIATED','SUCCESS','FAILED','RECONCILED','DUPLICATE')),
    created_at       TIMESTAMP   NOT NULL,
    CONSTRAINT uq_payment_idempotency UNIQUE (idempotency_key)
);

CREATE INDEX IF NOT EXISTS idx_bills_student ON bills(student_id);
CREATE INDEX IF NOT EXISTS idx_payments_bill ON payment_transactions(bill_id);
