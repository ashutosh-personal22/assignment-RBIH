-- =============================================================
-- EXTENSIONS
-- =============================================================

CREATE EXTENSION IF NOT EXISTS "pgcrypto"; 


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
'LOW',       
'MEDIUM',    
'HIGH'       
);


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

    monthly_income  NUMERIC(15, 2)  NOT NULL
                        CONSTRAINT chk_user_income CHECK (monthly_income > 0),
    employment_type employment_type NOT NULL,
    credit_score    SMALLINT        NOT NULL
                        CONSTRAINT chk_user_credit_score CHECK (credit_score BETWEEN 300 AND 900),

    is_active       BOOLEAN         NOT NULL DEFAULT TRUE,

    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);
-- =============================================================

CREATE TABLE loan_applications (
id                          UUID                PRIMARY KEY DEFAULT gen_random_uuid(),

    user_id                     UUID                NOT NULL
                                    REFERENCES users(id) ON DELETE RESTRICT,

    applicant_age_snapshot      SMALLINT            NOT NULL
                                    CONSTRAINT chk_app_age CHECK (applicant_age_snapshot BETWEEN 21 AND 60),
    monthly_income_snapshot     NUMERIC(15, 2)      NOT NULL
                                    CONSTRAINT chk_app_income CHECK (monthly_income_snapshot > 0),
    employment_type_snapshot    employment_type     NOT NULL,
    credit_score_snapshot       SMALLINT            NOT NULL
                                    CONSTRAINT chk_app_credit CHECK (credit_score_snapshot BETWEEN 300 AND 900),

    loan_amount                 NUMERIC(15, 2)      NOT NULL
                                    CONSTRAINT chk_loan_amount CHECK (loan_amount BETWEEN 10000 AND 5000000),
    tenure_months               SMALLINT            NOT NULL
                                    CONSTRAINT chk_tenure CHECK (tenure_months BETWEEN 6 AND 360),
    loan_purpose                loan_purpose        NOT NULL,

    status                      application_status  NOT NULL,
    risk_band                   risk_band,          
    rejection_reasons           TEXT[],             

    created_at                  TIMESTAMPTZ         NOT NULL DEFAULT now(),
    updated_at                  TIMESTAMPTZ         NOT NULL DEFAULT now()
);

-- =============================================================
CREATE TABLE loan_offers (
id              UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
application_id  UUID            NOT NULL UNIQUE
REFERENCES loan_applications(id) ON DELETE CASCADE,

    interest_rate   NUMERIC(5, 2)   NOT NULL,   
    tenure_months   SMALLINT        NOT NULL,   
    emi             NUMERIC(15, 2)  NOT NULL,   
    total_payable   NUMERIC(15, 2)  NOT NULL,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);

-- =============================================================
-- INDEXES
-- =============================================================

CREATE UNIQUE INDEX idx_users_email
ON users (email);

CREATE INDEX idx_users_is_active
ON users (is_active)
WHERE is_active = TRUE;

CREATE INDEX idx_loan_apps_user_id
ON loan_applications (user_id);

CREATE INDEX idx_loan_apps_user_created
ON loan_applications (user_id, created_at DESC);

CREATE INDEX idx_loan_apps_status
ON loan_applications (status);

CREATE INDEX idx_loan_apps_credit_score
ON loan_applications (credit_score_snapshot);

CREATE INDEX idx_loan_apps_created_at
ON loan_applications (created_at DESC);

CREATE INDEX idx_loan_apps_status_created
ON loan_applications (status, created_at DESC);



-- =============================================================
-- TRIGGER FUNCTION: auto-update updated_at
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