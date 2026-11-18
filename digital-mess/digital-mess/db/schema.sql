-- Digital Mess Management System - Module 1 schema (PostgreSQL)
-- Run once:  psql -U postgres -d mess_db -f db/schema.sql

CREATE TABLE IF NOT EXISTS users (
    id             BIGSERIAL    PRIMARY KEY,
    username       VARCHAR(30)  NOT NULL UNIQUE,
    full_name      VARCHAR(100) NOT NULL,
    password_hash  VARCHAR(255) NOT NULL,
    role           VARCHAR(10)  NOT NULL CHECK (role IN ('STUDENT', 'STAFF', 'ADMIN')),
    credential_id  VARCHAR(50)  UNIQUE,            -- normalized RFID / biometric id, e.g. RFID:04A3B2C1
    active         BOOLEAN      NOT NULL DEFAULT TRUE
);

CREATE TABLE IF NOT EXISTS scan_logs (
    id          BIGSERIAL    PRIMARY KEY,
    user_id     BIGINT       NOT NULL REFERENCES users (id),
    scan_type   VARCHAR(3)   NOT NULL CHECK (scan_type IN ('IN', 'OUT')),
    meal_type   VARCHAR(10)  NOT NULL CHECK (meal_type IN ('BREAKFAST', 'LUNCH', 'DINNER')),
    scanned_at  TIMESTAMPTZ  NOT NULL,
    scan_date   DATE         NOT NULL,             -- local (mess) date, set by the application
    device_id   VARCHAR(30)  NOT NULL
);

-- Fast "last scan of this student" lookup
CREATE INDEX IF NOT EXISTS idx_scan_logs_user_time
    ON scan_logs (user_id, scanned_at DESC);

-- Database-level backstop: a student can enter a given meal only once per day,
-- even if two application instances race each other.
CREATE UNIQUE INDEX IF NOT EXISTS uq_one_entry_per_meal
    ON scan_logs (user_id, meal_type, scan_date)
    WHERE scan_type = 'IN';
