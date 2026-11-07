-- Manual PostgreSQL migration; see backend README. Not automatically executed.
-- Links each patient row to its Supabase Auth user and stores the JWT-derived email.
-- Existing rows are left untouched (both columns are nullable so legacy data survives);
-- application code always supplies both values for new profiles.

BEGIN;

ALTER TABLE patient ADD COLUMN IF NOT EXISTS auth_user_id uuid;
ALTER TABLE patient ADD COLUMN IF NOT EXISTS email varchar(255);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'patient'::regclass AND conname = 'uq_patient_auth_user_id'
    ) THEN
        ALTER TABLE patient ADD CONSTRAINT uq_patient_auth_user_id UNIQUE (auth_user_id);
    END IF;
END $$;

COMMIT;
