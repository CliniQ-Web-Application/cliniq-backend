BEGIN;

-- Broadcasts accept 500 characters plus doctor and schedule context.
ALTER TABLE notification ALTER COLUMN message TYPE text;

COMMIT;
