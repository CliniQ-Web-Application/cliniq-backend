-- Publish clinic tables to Supabase Realtime so the frontend can subscribe to
-- postgres_changes and invalidate its query cache instead of short polling.
-- Safe to re-run: tables are only added when they are not already published.
BEGIN;

DO $$
DECLARE
    table_name text;
BEGIN
    IF EXISTS (SELECT 1 FROM pg_publication WHERE pubname = 'supabase_realtime') THEN
        FOREACH table_name IN ARRAY ARRAY[
            'appointment', 'queue', 'notification', 'staff',
            'department', 'schedule', 'auditLogs', 'audit_logs'
        ]
        LOOP
            IF EXISTS (
                SELECT 1 FROM pg_class c
                JOIN pg_namespace n ON n.oid = c.relnamespace
                WHERE c.relname = table_name AND n.nspname = 'public'
            )
            AND NOT EXISTS (
                SELECT 1 FROM pg_publication_tables
                WHERE pubname = 'supabase_realtime'
                  AND schemaname = 'public'
                  AND tablename = table_name
            )
            THEN
                EXECUTE format('ALTER PUBLICATION supabase_realtime ADD TABLE public.%I', table_name);
            END IF;
        END LOOP;
    END IF;
END $$;

COMMIT;
