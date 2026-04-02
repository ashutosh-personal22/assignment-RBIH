-- =============================================================
-- LOAN SERVICE — COMPLETE DATABASE SCHEMA
-- Database : loan_db
-- PostgreSQL: 14+
-- Run once on a fresh database before starting the Spring Boot app.
-- JPA is set to ddl-auto: validate — all objects must exist at boot.
-- =============================================================


-- =============================================================
-- EXTENSIONS
-- =============================================================

CREATE EXTENSION IF NOT EXISTS "pgcrypto";   -- provides gen_random_uuid()


-- =============================================================
-- ENUM TYPES
-- =============================================================

CREATE TYPE employment_type AS ENUM (
'SALARIED',
'SELF_EMPLOYED'
);

CREATE TYPE loan_purpose AS ENUM (
'PERSONAL',
'HOME',
'AUTO'
);

CREATE TYPE application_status AS ENUM (
'APPROVED',
'REJECTED'
);

CREATE TYPE risk_band AS ENUM (
'LOW',       -- credit score >= 750
'MEDIUM',    -- credit score 650–749
'HIGH'       -- credit score 600–649
);


-- =============================================================
-- TABLE: users
-- One row per registered applicant.
-- Financial profile fields (income, employment, credit score) are
-- snapshotted into loan_applications at submission time so that
-- past decisions remain auditable even after a profile update.
-- =============================================================

CREATE TABLE users (
id              UUID            PRIMARY KEY DEFAULT gen_random_uuid(),

    -- Identity
    full_name       VARCHAR(255)    NOT NULL,
    date_of_birth   DATE            NOT NULL,
    phone_number    VARCHAR(15),

    -- Auth
    email           VARCHAR(255)    NOT NULL UNIQUE,
    password_hash   VARCHAR(255)    NOT NULL,

    -- Financial profile (copied as snapshots into loan_applications on submit)
    monthly_income  NUMERIC(15, 2)  NOT NULL
                        CONSTRAINT chk_user_income CHECK (monthly_income > 0),
    employment_type employment_type NOT NULL,
    credit_score    SMALLINT        NOT NULL
                        CONSTRAINT chk_user_credit_score CHECK (credit_score BETWEEN 300 AND 900),

    -- Lifecycle
    is_active       BOOLEAN         NOT NULL DEFAULT TRUE,

    -- Audit
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);

COMMENT ON TABLE  users               IS 'Registered applicants. Profile fields are snapshotted into loan_applications at submission time.';
COMMENT ON COLUMN users.date_of_birth IS 'Used to compute age at application time; the derived age is written into loan_applications.applicant_age_snapshot.';
COMMENT ON COLUMN users.password_hash IS 'BCrypt hash. Never store or log the plain-text value.';
COMMENT ON COLUMN users.is_active     IS 'FALSE = soft-deleted or suspended. Preserves full loan history without a hard delete.';


-- =============================================================
-- TABLE: loan_applications
-- One row per submitted loan application.
-- Applicant financial fields are snapshotted from the user profile
-- at submission time — they must not change after creation.
-- =============================================================

CREATE TABLE loan_applications (
id                          UUID                PRIMARY KEY DEFAULT gen_random_uuid(),

    -- Owner
    user_id                     UUID                NOT NULL
                                    REFERENCES users(id) ON DELETE RESTRICT,

    -- Applicant snapshot (point-in-time copy from users at submission)
    applicant_age_snapshot      SMALLINT            NOT NULL
                                    CONSTRAINT chk_app_age CHECK (applicant_age_snapshot BETWEEN 21 AND 60),
    monthly_income_snapshot     NUMERIC(15, 2)      NOT NULL
                                    CONSTRAINT chk_app_income CHECK (monthly_income_snapshot > 0),
    employment_type_snapshot    employment_type     NOT NULL,
    credit_score_snapshot       SMALLINT            NOT NULL
                                    CONSTRAINT chk_app_credit CHECK (credit_score_snapshot BETWEEN 300 AND 900),

    -- Loan request
    loan_amount                 NUMERIC(15, 2)      NOT NULL
                                    CONSTRAINT chk_loan_amount CHECK (loan_amount BETWEEN 10000 AND 5000000),
    tenure_months               SMALLINT            NOT NULL
                                    CONSTRAINT chk_tenure CHECK (tenure_months BETWEEN 6 AND 360),
    loan_purpose                loan_purpose        NOT NULL,

    -- Eligibility decision
    status                      application_status  NOT NULL,
    risk_band                   risk_band,          -- NULL for REJECTED applications
    rejection_reasons           TEXT[],             -- NULL for APPROVED applications

    -- Audit
    created_at                  TIMESTAMPTZ         NOT NULL DEFAULT now(),
    updated_at                  TIMESTAMPTZ         NOT NULL DEFAULT now()
);

COMMENT ON TABLE  loan_applications                           IS 'Immutable audit record per loan submission. Snapshot columns preserve the evaluation context.';
COMMENT ON COLUMN loan_applications.user_id                  IS 'FK to the applicant. ON DELETE RESTRICT prevents account deletion while applications exist.';
COMMENT ON COLUMN loan_applications.applicant_age_snapshot   IS 'Age in years at submission, derived from users.date_of_birth in the service layer.';
COMMENT ON COLUMN loan_applications.monthly_income_snapshot  IS 'Copied from users.monthly_income at submission time.';
COMMENT ON COLUMN loan_applications.employment_type_snapshot IS 'Copied from users.employment_type at submission time.';
COMMENT ON COLUMN loan_applications.credit_score_snapshot    IS 'Copied from users.credit_score at submission time.';
COMMENT ON COLUMN loan_applications.risk_band                IS 'Set only for APPROVED applications: LOW>=750, MEDIUM 650-749, HIGH 600-649.';
COMMENT ON COLUMN loan_applications.rejection_reasons        IS 'Array of reason codes e.g. {CREDIT_SCORE_TOO_LOW, EMI_EXCEEDS_LIMIT, AGE_TENURE_LIMIT_EXCEEDED}.';


-- =============================================================
-- TABLE: loan_offers
-- Exactly one offer per approved application (enforced by UNIQUE FK).
-- Only inserted when status = APPROVED and EMI <= 50% of monthly income.
-- =============================================================

CREATE TABLE loan_offers (
id              UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
application_id  UUID            NOT NULL UNIQUE
REFERENCES loan_applications(id) ON DELETE CASCADE,

    -- Offer financials (all computed with BigDecimal scale=2 HALF_UP)
    interest_rate   NUMERIC(5, 2)   NOT NULL,   -- base 12% + risk + employment + size premiums
    tenure_months   SMALLINT        NOT NULL,   -- mirrors the requested tenure
    emi             NUMERIC(15, 2)  NOT NULL,   -- monthly instalment in INR
    total_payable   NUMERIC(15, 2)  NOT NULL,   -- emi × tenure_months

    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);

COMMENT ON TABLE  loan_offers               IS 'Single loan offer generated for each approved application.';
COMMENT ON COLUMN loan_offers.interest_rate IS 'Final rate = 12% base + risk premium (0/1.5/3%) + employment premium (0/1%) + loan size premium (0/0.5%).';
COMMENT ON COLUMN loan_offers.total_payable IS 'emi × tenure_months. Does not account for prepayment.';


-- =============================================================
-- INDEXES
-- =============================================================

-- users: auth login lookup (most frequent query)
CREATE UNIQUE INDEX idx_users_email
ON users (email);

-- users: active-only filter (partial index — skips inactive rows)
CREATE INDEX idx_users_is_active
ON users (is_active)
WHERE is_active = TRUE;

-- loan_applications: all applications by owner
CREATE INDEX idx_loan_apps_user_id
ON loan_applications (user_id);

-- loan_applications: owner's applications newest-first (GET /applications/me)
CREATE INDEX idx_loan_apps_user_created
ON loan_applications (user_id, created_at DESC);

-- loan_applications: admin filter by decision outcome
CREATE INDEX idx_loan_apps_status
ON loan_applications (status);

-- loan_applications: admin/reporting filter by credit score range
CREATE INDEX idx_loan_apps_credit_score
ON loan_applications (credit_score_snapshot);

-- loan_applications: global newest-first listing
CREATE INDEX idx_loan_apps_created_at
ON loan_applications (created_at DESC);

-- loan_applications: composite status + time (common dashboard query)
CREATE INDEX idx_loan_apps_status_created
ON loan_applications (status, created_at DESC);

-- Note: loan_offers.application_id already has an implicit unique index
--       from the UNIQUE constraint — no separate index needed.


-- =============================================================
-- TRIGGER FUNCTION: auto-update updated_at
-- Shared by both users and loan_applications.
-- =============================================================

CREATE OR REPLACE FUNCTION fn_set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
NEW.updated_at = now();
RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_users_updated_at
BEFORE UPDATE ON users
FOR EACH ROW
EXECUTE FUNCTION fn_set_updated_at();

CREATE TRIGGER trg_loan_applications_updated_at
BEFORE UPDATE ON loan_applications
FOR EACH ROW
EXECUTE FUNCTION fn_set_updated_at();