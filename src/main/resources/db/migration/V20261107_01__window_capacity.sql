-- Manual PostgreSQL migration; see backend README. Not automatically executed.
-- Turns a schedule slot into a bookable window: many appointments may reference one
-- schedule row, bounded by schedule.capacity (NULL = legacy single appointment).
BEGIN;

LOCK TABLE schedule, appointment IN ACCESS EXCLUSIVE MODE;

ALTER TABLE appointment DROP CONSTRAINT IF EXISTS uq_appointment_slot;
ALTER TABLE appointment DROP CONSTRAINT IF EXISTS ukbpeuy47ge8oi9u1tlvtxelh9u;

-- Any remaining unique constraint on appointment.slot_id is removed too.
DO $$
DECLARE
    hit record;
BEGIN
    FOR hit IN
        SELECT c.conname
        FROM pg_constraint c
        WHERE c.conrelid = 'appointment'::regclass
          AND c.contype = 'u'
          AND c.conkey = ARRAY(SELECT a.attnum FROM pg_attribute a
                               WHERE a.attrelid = 'appointment'::regclass AND a.attname = 'slot_id')
    LOOP
        EXECUTE format('ALTER TABLE appointment DROP CONSTRAINT IF EXISTS %I', hit.conname);
    END LOOP;
    -- Unique indexes not owned by a constraint (dropping a constraint-owned index would fail, so skip those).
    FOR hit IN
        SELECT c.relname AS name
        FROM pg_class c
        JOIN pg_index i ON i.indexrelid = c.oid
        WHERE i.indrelid = 'appointment'::regclass
          AND i.indisunique
          AND i.indnatts = 1
          AND (string_to_array(i.indkey::text, ' '))[1]::int =
              (SELECT a.attnum FROM pg_attribute a
               WHERE a.attrelid = 'appointment'::regclass AND a.attname = 'slot_id')
          AND NOT EXISTS (SELECT 1 FROM pg_constraint con
                          JOIN pg_class ic ON ic.oid = con.conindid
                          WHERE ic.relname = c.relname)
    LOOP
        EXECUTE format('DROP INDEX IF EXISTS public.%I', hit.name);
    END LOOP;
END $$;

ALTER TABLE schedule ADD COLUMN IF NOT EXISTS capacity integer;

ALTER TABLE schedule DROP CONSTRAINT IF EXISTS schedule_capacity_check;
ALTER TABLE schedule ADD CONSTRAINT schedule_capacity_check CHECK (capacity IS NULL OR capacity >= 1);

COMMIT;
