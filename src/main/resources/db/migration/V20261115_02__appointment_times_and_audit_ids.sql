-- Manual PostgreSQL migration; see backend README. Not automatically executed.
BEGIN;

ALTER TABLE appointment
    ADD COLUMN IF NOT EXISTS appointment_time time;

DO $$
BEGIN
    IF to_regclass('audit_logs') IS NOT NULL THEN
        ALTER TABLE audit_logs
            ALTER COLUMN entity_id TYPE varchar(255)
            USING entity_id::text;
    ELSIF to_regclass('auditlogs') IS NOT NULL THEN
        ALTER TABLE auditlogs
            ALTER COLUMN entity_id TYPE varchar(255)
            USING entity_id::text;
    END IF;
END $$;

COMMIT;
