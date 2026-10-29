-- =====================================================================
-- 104 CRM - Initial PostgreSQL schema (managed by Flyway)
-- Business/transactional data lives here. Audit & API logs go to MongoDB.
-- =====================================================================

-- ---------- Users & roles ----------
-- role: ADMIN, AGENT, MEDICAL_OFFICER, COUNSELLOR, SUPERVISOR,
--       FACILITY_OFFICER, DISTRICT_OFFICER, STATE_OFFICER, MINISTER_OFFICE
CREATE TABLE app_user (
    id               BIGSERIAL PRIMARY KEY,
    username         VARCHAR(50)  NOT NULL UNIQUE,
    password_hash    VARCHAR(100) NOT NULL,           -- BCrypt hash, never plain text
    full_name        VARCHAR(120) NOT NULL,
    role             VARCHAR(30)  NOT NULL,
    phone            VARCHAR(15),
    email            VARCHAR(120),
    district         VARCHAR(60),
    extension_no     VARCHAR(10),                     -- agent telephony extension
    active           BOOLEAN      NOT NULL DEFAULT TRUE,
    failed_attempts  INT          NOT NULL DEFAULT 0, -- brute-force protection
    locked_until     TIMESTAMP,
    created_at       TIMESTAMP    NOT NULL DEFAULT now()
);

-- ---------- Public authorities (who redresses a grievance) ----------
CREATE TABLE public_authority (
    id                   BIGSERIAL PRIMARY KEY,
    name                 VARCHAR(150) NOT NULL,
    level                VARCHAR(20)  NOT NULL,   -- FACILITY / DISTRICT / STATE / MINISTER
    district             VARCHAR(60),
    officer_name         VARCHAR(120) NOT NULL,
    officer_designation  VARCHAR(120),
    officer_phone        VARCHAR(15),
    officer_email        VARCHAR(120),
    officer_user_id      BIGINT REFERENCES app_user(id),
    active               BOOLEAN NOT NULL DEFAULT TRUE
);
CREATE INDEX ix_authority_level_district ON public_authority(level, district);

-- ---------- Callers & calls ----------
CREATE TABLE caller (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(120) NOT NULL,
    age         INT,
    sex         VARCHAR(10),
    phone       VARCHAR(15)  NOT NULL,
    email       VARCHAR(120),
    district    VARCHAR(60),
    block       VARCHAR(60),
    village     VARCHAR(80),
    address     VARCHAR(300),
    created_at  TIMESTAMP NOT NULL DEFAULT now()
);
CREATE INDEX ix_caller_phone ON caller(phone);

-- pathway: GRIEVANCE, HEALTH_ADVICE, COUNSELLING, INFORMATION, TRANSFER
CREATE TABLE call_record (
    id              BIGSERIAL PRIMARY KEY,
    call_ref        VARCHAR(30)  NOT NULL UNIQUE,
    caller_id       BIGINT       NOT NULL REFERENCES caller(id),
    pathway         VARCHAR(30)  NOT NULL,
    call_type       VARCHAR(60),
    query_text      TEXT,
    advice_given    TEXT,
    algorithm_code  VARCHAR(40),              -- approved medical algorithm used
    transferred_to  VARCHAR(30),              -- MEDICAL_OFFICER / COUNSELLOR / AMBULANCE
    status          VARCHAR(20)  NOT NULL DEFAULT 'OPEN',
    agent_id        BIGINT REFERENCES app_user(id),
    handled_by_id   BIGINT REFERENCES app_user(id),
    created_at      TIMESTAMP NOT NULL DEFAULT now(),
    closed_at       TIMESTAMP
);
CREATE INDEX ix_call_created ON call_record(created_at);

-- ---------- Grievances ----------
CREATE TABLE grievance (
    id                  BIGSERIAL PRIMARY KEY,
    grievance_no        VARCHAR(30)  NOT NULL UNIQUE,   -- e.g. GRV-2026-000123
    caller_id           BIGINT REFERENCES caller(id),
    call_id             BIGINT REFERENCES call_record(id),
    patient_name        VARCHAR(120) NOT NULL,
    patient_age         INT,
    patient_sex         VARCHAR(10),
    contact_phone       VARCHAR(15)  NOT NULL,
    contact_email       VARCHAR(120),
    district            VARCHAR(60),
    block               VARCHAR(60),
    village             VARCHAR(80),
    category            VARCHAR(60)  NOT NULL,          -- nature of complaint
    complaint_against   VARCHAR(60),
    authority_id        BIGINT REFERENCES public_authority(id),
    description         TEXT         NOT NULL,
    priority            VARCHAR(10)  NOT NULL DEFAULT 'NORMAL',
    epidemic            BOOLEAN      NOT NULL DEFAULT FALSE,
    current_level       VARCHAR(20)  NOT NULL DEFAULT 'FACILITY',
    status              VARCHAR(20)  NOT NULL DEFAULT 'NEW',
    resolution_remarks  TEXT,
    supervisor_remarks  TEXT,
    created_by          VARCHAR(50),
    created_at          TIMESTAMP NOT NULL DEFAULT now(),
    updated_at          TIMESTAMP NOT NULL DEFAULT now(),
    level_since         TIMESTAMP NOT NULL DEFAULT now(),  -- when current level started
    last_reminder_at    TIMESTAMP,
    resolved_at         TIMESTAMP,
    deleted             BOOLEAN   NOT NULL DEFAULT FALSE,  -- soft delete (audit requirement)
    version             BIGINT    NOT NULL DEFAULT 0       -- optimistic locking
);
CREATE INDEX ix_grv_status ON grievance(status, deleted);
CREATE INDEX ix_grv_created ON grievance(created_at);
CREATE SEQUENCE grievance_no_seq START 1;

CREATE TABLE grievance_history (
    id            BIGSERIAL PRIMARY KEY,
    grievance_id  BIGINT NOT NULL REFERENCES grievance(id),
    action        VARCHAR(40) NOT NULL,
    from_status   VARCHAR(20),
    to_status     VARCHAR(20),
    level         VARCHAR(20),
    remarks       TEXT,
    actor         VARCHAR(50),
    created_at    TIMESTAMP NOT NULL DEFAULT now()
);
CREATE INDEX ix_grv_hist ON grievance_history(grievance_id);

-- ---------- Escalation / alert follow-up rules (configurable per role) ----------
CREATE TABLE escalation_rule (
    id                 BIGSERIAL PRIMARY KEY,
    level              VARCHAR(20)  NOT NULL UNIQUE,  -- FACILITY / DISTRICT / STATE / MINISTER
    level_order        INT          NOT NULL,
    notify_role        VARCHAR(30)  NOT NULL,         -- role that owns the grievance at this level
    start_day          INT          NOT NULL,
    end_day            INT          NOT NULL,
    notify_portal      BOOLEAN NOT NULL DEFAULT TRUE,
    notify_sms         BOOLEAN NOT NULL DEFAULT TRUE,
    notify_email       BOOLEAN NOT NULL DEFAULT TRUE,
    notify_call        BOOLEAN NOT NULL DEFAULT FALSE,
    notify_caller_sms  BOOLEAN NOT NULL DEFAULT TRUE,  -- tell the caller it was escalated
    reminder_hours     INT     NOT NULL DEFAULT 24,    -- repeat reminder frequency
    sms_template       VARCHAR(500),
    email_template     TEXT,
    active             BOOLEAN NOT NULL DEFAULT TRUE,
    updated_by         VARCHAR(50),
    updated_at         TIMESTAMP NOT NULL DEFAULT now()
);

-- ---------- Third-party integration configuration ----------
-- code: TELECOM, SMS, EMAIL, GIS, MOHFW_RBSK, MOHFW_PMJAY, AMBULANCE
CREATE TABLE integration_config (
    id                BIGSERIAL PRIMARY KEY,
    code              VARCHAR(30)  NOT NULL UNIQUE,
    display_name      VARCHAR(100) NOT NULL,
    enabled           BOOLEAN      NOT NULL DEFAULT FALSE,
    provider          VARCHAR(80),
    base_url          VARCHAR(300),
    username          VARCHAR(120),
    secret_enc        VARCHAR(1000),       -- AES-GCM encrypted; never returned by API
    settings_json     TEXT,                -- provider specific non-secret settings
    timeout_ms        INT NOT NULL DEFAULT 10000,
    retry_count       INT NOT NULL DEFAULT 3,
    last_test_at      TIMESTAMP,
    last_test_status  VARCHAR(200),
    updated_by        VARCHAR(50),
    updated_at        TIMESTAMP NOT NULL DEFAULT now()
);

-- ---------- Health facility directory (hospitals, blood banks, labs) ----------
CREATE TABLE facility (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(150) NOT NULL,
    type        VARCHAR(30)  NOT NULL,  -- HOSPITAL, BLOOD_BANK, DIAGNOSTIC, PHC, CHC
    district    VARCHAR(60),
    address     VARCHAR(300),
    phone       VARCHAR(20),
    latitude    DOUBLE PRECISION,
    longitude   DOUBLE PRECISION,
    services    VARCHAR(500),
    active      BOOLEAN NOT NULL DEFAULT TRUE
);
CREATE INDEX ix_facility_type_district ON facility(type, district);
