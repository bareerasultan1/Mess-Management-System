-- Relational schema of the Billing and Payment modules (SQLite).
-- Money is stored as whole paisa (INTEGER) so no rounding error can occur.
-- Dates and times are ISO-8601 text. Statements are separated by semicolons at the end of a line.

CREATE TABLE IF NOT EXISTS student_ledgers (
    student_id      TEXT    PRIMARY KEY,
    category        TEXT    NOT NULL CHECK (category IN ('REGULAR','SCHOLAR','SUBSIDIZED','FACULTY_GUEST')),
    balance_paisa   INTEGER NOT NULL CHECK (balance_paisa >= 0),
    unbilled_meals  INTEGER NOT NULL CHECK (unbilled_meals >= 0),
    status          TEXT    NOT NULL CHECK (status IN ('ACTIVE','SUSPENDED')),
    updated_at      TEXT    NOT NULL
);

CREATE TABLE IF NOT EXISTS bills (
    bill_id          TEXT    PRIMARY KEY,
    student_id       TEXT    NOT NULL REFERENCES student_ledgers(student_id),
    month            INTEGER NOT NULL CHECK (month BETWEEN 1 AND 12),
    year             INTEGER NOT NULL CHECK (year >= 2020),
    due_date         TEXT    NOT NULL,
    base_paisa       INTEGER NOT NULL CHECK (base_paisa >= 0),
    rebate_paisa     INTEGER NOT NULL CHECK (rebate_paisa >= 0),
    fine_paisa       INTEGER NOT NULL CHECK (fine_paisa >= 0),
    discount_paisa   INTEGER NOT NULL CHECK (discount_paisa >= 0),
    net_paisa        INTEGER NOT NULL CHECK (net_paisa >= 0),
    status           TEXT    NOT NULL CHECK (status IN ('UNPAID','PARTIALLY_PAID','PAID','OVERDUE')),
    UNIQUE (student_id, month, year)
);

CREATE TABLE IF NOT EXISTS bill_items (
    item_id      TEXT    NOT NULL,
    bill_id      TEXT    NOT NULL REFERENCES bills(bill_id) ON DELETE CASCADE,
    student_id   TEXT    NOT NULL,
    scan_time    TEXT    NOT NULL,
    meal_type    TEXT    NOT NULL CHECK (meal_type IN ('BREAKFAST','LUNCH','DINNER')),
    rate_paisa   INTEGER NOT NULL CHECK (rate_paisa > 0),
    PRIMARY KEY (bill_id, item_id)
);

CREATE TABLE IF NOT EXISTS payment_transactions (
    transaction_id   TEXT    PRIMARY KEY,
    student_id       TEXT    NOT NULL REFERENCES student_ledgers(student_id),
    bill_id          TEXT    NOT NULL REFERENCES bills(bill_id),
    amount_paisa     INTEGER NOT NULL CHECK (amount_paisa > 0 AND amount_paisa <= 5000000),
    channel          TEXT    NOT NULL CHECK (channel IN ('JAZZCASH','EASYPAISA','BANK_TRANSFER')),
    idempotency_key  TEXT    NOT NULL UNIQUE,
    status           TEXT    NOT NULL CHECK (status IN ('INITIATED','SUCCESS','FAILED','RECONCILED','DUPLICATE')),
    created_at       TEXT    NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_bills_student ON bills(student_id);
CREATE INDEX IF NOT EXISTS idx_payments_bill ON payment_transactions(bill_id);
