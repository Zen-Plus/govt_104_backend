-- =====================================================================
-- V3: fields from every call-type screen in the 104 UX deck.
--  * patient registration (Patient ID "Generate ID")
--  * call record: call type / sub type, close type, CDI satisfaction,
--    follow-up, speciality group and screen-specific details (JSONB)
--  * grievance (complaint screen & SIO screen) extra fields
--  * facility directory columns shown on Hospital screen
-- =====================================================================

-- ---------- Patient registration (left panel "Registration Details") ----------
CREATE TABLE patient (
    id            BIGSERIAL PRIMARY KEY,
    patient_code  VARCHAR(20) NOT NULL UNIQUE,     -- shown as "Patient ID"
    caller_id     BIGINT REFERENCES caller(id),
    name          VARCHAR(120) NOT NULL,
    age           INT,
    age_unit      VARCHAR(10) DEFAULT 'YEARS',     -- YEARS / MONTHS / DAYS
    gender        VARCHAR(10),
    created_at    TIMESTAMP NOT NULL DEFAULT now()
);
CREATE INDEX ix_patient_caller ON patient(caller_id);
CREATE SEQUENCE patient_code_seq START 10001;

-- caller: numeric "CallerID" shown on screen is caller.id; nothing extra needed.

-- ---------- Call record ----------
ALTER TABLE call_record
    ALTER COLUMN caller_id DROP NOT NULL,          -- call exists as soon as it lands (before caller is saved)
    ADD COLUMN phone             VARCHAR(15),       -- ANI of the incoming call
    ADD COLUMN patient_id        BIGINT REFERENCES patient(id),
    ADD COLUMN sub_call_type     VARCHAR(60),       -- Sub Gen Info / Sub Call Type
    ADD COLUMN close_type        VARCHAR(40),
    ADD COLUMN cdi_satisfied     VARCHAR(12),       -- CDI: YES / NO / NOT_GIVEN
    ADD COLUMN cdi_reason        VARCHAR(300),
    ADD COLUMN speciality_group  VARCHAR(60),
    ADD COLUMN follow_up_at      TIMESTAMP,         -- Set Follow Up = Yes -> date + time
    ADD COLUMN follow_up_done    BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN details           JSONB,             -- screen-specific fields (see CallDetails in docs)
    ADD COLUMN transfer_mode     VARCHAR(20);       -- BLIND / CONFERENCE
CREATE INDEX ix_call_followup ON call_record(follow_up_at) WHERE follow_up_done = FALSE;
CREATE INDEX ix_call_status_transfer ON call_record(transferred_to, status);

-- ---------- Grievance: complaint screen + SIO screen ----------
ALTER TABLE grievance
    ADD COLUMN patient_age_unit  VARCHAR(10) DEFAULT 'YEARS',
    ADD COLUMN common_complaint  BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN pandemic          BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN pass_to           VARCHAR(30) DEFAULT 'SUPERVISOR',
    ADD COLUMN cmho_remarks      TEXT,
    ADD COLUMN forwarded_at      TIMESTAMP;

CREATE TABLE grievance_attachment (
    id            BIGSERIAL PRIMARY KEY,
    grievance_id  BIGINT NOT NULL REFERENCES grievance(id),
    file_name     VARCHAR(200) NOT NULL,
    content_type  VARCHAR(100) NOT NULL,
    size_bytes    BIGINT NOT NULL,
    data          BYTEA NOT NULL,
    uploaded_by   VARCHAR(50),
    uploaded_at   TIMESTAMP NOT NULL DEFAULT now()
);

-- ---------- Facility directory: Hospital screen columns ----------
ALTER TABLE facility
    ADD COLUMN hospital_type VARCHAR(40),     -- Govt / Private / Empanelled
    ADD COLUMN pin           VARCHAR(10),
    ADD COLUMN no_of_beds    INT;

UPDATE facility SET hospital_type = 'GOVT', pin = '492001', no_of_beds = 1200 WHERE name LIKE 'Dr. B.R. Ambedkar%';
UPDATE facility SET hospital_type = 'GOVT', pin = '491001', no_of_beds = 300  WHERE name = 'District Hospital Durg';

INSERT INTO facility (name, type, district, address, phone, services) VALUES
('Arogya Blood Center', 'BLOOD_BANK', 'Baloda Bazar', 'Gaurav Path Main Road, Baloda Bazar', NULL, 'All groups'),
('District Hospital Blood Bank Balrampur', 'BLOOD_BANK', 'Balrampur', 'Near main road, near govt high school, Balrampur', NULL, 'All groups'),
('Blood Bank Govt Medical College Dimrapal', 'BLOOD_BANK', 'Bastar', 'Late BRKM Medical College, Jagdalpur', NULL, 'All groups'),
('Maharani Hospital Blood Bank, Jagdalpur', 'BLOOD_BANK', 'Bastar', 'Jagdalpur', NULL, 'All groups'),
('CIMS Hospital Blood Bank Bilaspur', 'BLOOD_BANK', 'Bilaspur', 'Guru Ghasidas University Medical Complex, Bilaspur', NULL, 'All groups'),
('District Hospital Blood Bank Bilaspur', 'BLOOD_BANK', 'Bilaspur', 'Opposite Rajeev Plaza, near old bus stand, Bilaspur', NULL, 'All groups'),
('District Hospital Blood Bank Dantewada', 'BLOOD_BANK', 'Dantewada', 'Govt Blood Bank, District Hospital, Dantewada', NULL, 'All groups'),
('Blood Bank District Hospital Dhamtari', 'BLOOD_BANK', 'Dhamtari', 'District Hospital, Ratnabandha, Dhamtari', NULL, 'All groups'),
('District Hospital Blood Bank Durg', 'BLOOD_BANK', 'Durg', 'District Hospital Premises, Durg', NULL, 'All groups'),
('Indian Red Cross Blood Bank Durg', 'BLOOD_BANK', 'Durg', 'District Hospital Premises, Durg', NULL, 'All groups'),
('District Hospital Blood Bank Gariyaband', 'BLOOD_BANK', 'Gariyaband', 'Jail road, behind petrol pump, Gariyaband', NULL, 'All groups'),
('District Hospital Janjgir', 'BLOOD_BANK', 'Janjgir-Champa', 'Next to jila panchayat, Janjgir', NULL, 'All groups'),
('District Hospital Blood Bank Jashpur', 'BLOOD_BANK', 'Jashpur', 'Office Civil Surgeon, Jashpur', NULL, 'All groups');
