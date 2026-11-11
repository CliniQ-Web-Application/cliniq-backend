BEGIN;

ALTER TABLE notification ADD COLUMN IF NOT EXISTS staff_id uuid;
ALTER TABLE notification ALTER COLUMN is_read SET DEFAULT false;
UPDATE notification SET is_read = false WHERE is_read IS NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'notification'::regclass
          AND conname = 'fk_notification_staff'
    ) THEN
ALTER TABLE notification ADD CONSTRAINT fk_notification_staff
    FOREIGN KEY (staff_id) REFERENCES staff (staff_id);
END IF;
END $$;

COMMIT;