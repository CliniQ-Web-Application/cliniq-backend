-- Manual PostgreSQL migration; see backend README. Not automatically executed.
BEGIN;

-- Stop application writers before applying. Serialize schema/backfill activity as well.
LOCK TABLE schedule, appointment IN ACCESS EXCLUSIVE MODE;
ALTER TABLE appointment ADD COLUMN IF NOT EXISTS slot_id uuid;
ALTER TABLE appointment ALTER COLUMN check_in_time DROP NOT NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'appointment'::regclass AND conname = 'uq_appointment_slot'
    ) THEN
        ALTER TABLE appointment ADD CONSTRAINT uq_appointment_slot UNIQUE (slot_id);
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'appointment'::regclass AND conname = 'fk_appointment_slot'
    ) THEN
        ALTER TABLE appointment ADD CONSTRAINT fk_appointment_slot
            FOREIGN KEY (slot_id) REFERENCES schedule (slot_id);
    END IF;
END $$;

COMMIT;
