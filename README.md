# CliniQ backend: patient bookings

## API contract

All `/api/bookings` endpoints require an authenticated JWT whose `sub` is a canonical UUID and the patient-only `AccessPolicy.requirePatient(UUID)` check. Staff accounts are not patient accounts. Ownership is resolved from `Patient.authUserId`, never from the request body.

- `GET /api/bookings/availability`: array of `{slotId, slotDate, startTime, endTime, slotStatus, staff: {staffId, firstName, lastName, staffRole, active, department: {departmentId, name}}}`. Only unreserved OPEN slots with a future start, valid same-day time interval, and an active DOCTOR with a department are returned. No patient details, staff email, auth IDs, or entities are serialized.
- `POST /api/bookings`: exactly `{ "slotId": "<UUID>" }`. Extra properties (including ownership/status fields) and invalid/missing slot IDs are rejected with 400. Success is 201 with `{appointmentId, appointmentStatus: "BOOKED", slotId, slotDate, startTime, endTime, departmentName, doctorName, bookedAt}`.
- `GET /api/bookings`: the authenticated patient's slot-linked bookings only, with the same response fields.
- `GET /api/bookings/{id}`: an owned slot-linked booking, or 404 (including another patient's booking).

Missing patient profiles return 409 with a message asking the user to complete their profile; availability remains browsable before profile completion. An unavailable slot returns 409. Unknown slots return 404. Invalid/missing JWT subjects return 401. Booking-controller errors use `{ "message": "..." }`; authentication/authorization filters may use the security layer's error format.

## Patient profile and legacy API access

`GET /api/patients/me` returns only the signed-in patient's profile, or 404 when
none exists. `POST /api/patients/me` accepts `{patientName, patientSurname,
nationalID, phone}` (nonblank, at most 255 characters each). Ownership and email
come from the verified JWT; submitted IDs or email fields cannot choose another
account. Existing profiles are returned unchanged. A successful response is 200.
Profile completion is explicit, so email-confirmed users can review their signup
details before a profile is created. Concurrent profile creation may return 409;
reload to check the existing profile before retrying.

The legacy root patient creation path also rejects supplied record IDs and derives
ownership/email from the JWT. Generic patient reads/updates/deletes, appointment
CRUD, and schedule CRUD are restricted to active staff. Staff mutations (including
registration) and department mutations require an active administrator. Department
reads remain available to signed-in patients. These checks use database staff
profiles, not editable Auth metadata; inactive staff cannot fall back to patient
privileges.

These are booking-related authorization boundaries, not a full security audit of
all queue, audit-log, or notification APIs. Supabase table grants/RLS must also
prevent clients from directly changing staff roles or reservations outside the
backend; application endpoint checks cannot secure direct database APIs.

## Time and lifecycle

Configure `cliniq.booking.zone-id=UTC` (default), or its environment form `CLINIQ_BOOKING_ZONE_ID=UTC`. Set an explicit IANA zone when the clinic operates in another timezone; the server's locale/timezone is not used. An invalid zone fails startup. `BookingTimeConfig` supplies an injectable clinic-zone `Clock` for deterministic tests.

`slotDate`, `startTime`, and `endTime` are clinic-local schedule values. `bookedAt` is the actual reservation timestamp expressed in that same clinic zone, **not** the scheduled appointment time. Past starts, end <= start, and nonexistent/ambiguous daylight-saving transition times are not bookable. The current API uses local date/time strings without offsets; clients must interpret them in the configured clinic zone.

Booking takes a pessimistic write lock on the schedule row, rechecks eligibility after acquiring the lock, inserts a BOOKED/NORMAL appointment with null check-in time, and marks the slot BOOKED in one transaction. A unique nullable `appointment.slot_id` FK is the final duplicate-reservation backstop. If either write fails, the transaction rolls back. Legacy appointments retain null slot IDs and are not inferred/backfilled from timestamps.

Generic appointment creation cannot supply an existing ID, slot association, or BOOKED status. Generic appointment updates cannot alter slot-linked bookings or create BOOKED appointments; deletion cannot remove slot-linked bookings. Schedule update/delete uses the same row lock and refuses BOOKED or appointment-linked slots. Generic schedule creation cannot supply an ID or BOOKED status, and updates cannot manufacture BOOKED status. Existing nonbooking walk-in CRUD remains available subject to the security layer. Cancellation, rescheduling, check-in, and releasing/reusing booked slots are intentionally not implemented; do not clear links or change these states through generic CRUD.

## Required PostgreSQL deployment migration

The application does not currently use a migration runner. The SQL below is **manual**, not automatically applied. Hibernate schema update is not a substitute: it does not reliably drop the old check-in NOT NULL constraint.

1. Back up the database and stop all application writers. Confirm the target database/schema and inspect the migration in a disposable PostgreSQL database first.
2. Apply `src/main/resources/db/migration/V20261023_01__patient_booking_slots.sql` using your approved deployment tooling, with the application schema in `search_path`. It runs atomically, locks both affected tables, adds the nullable slot FK and unique constraint, and explicitly drops `appointment.check_in_time` NOT NULL. The script is idempotent and leaves legacy rows intact. Multiple null slot IDs remain legal. Invalid existing links or duplicate nonnull slot IDs cause failure rather than deleting/reassigning data.
3. Verify both constraints exist and check-in accepts null; deploy the new application with the updated security/profile code and explicit clinic timezone, then resume writers. Never run old writers alongside the new booking lifecycle.

No database migration was executed during implementation. No production database is required for the focused tests.

## Offline validation

Use JDK 21 and cached Maven/dependencies:

```sh
./mvnw -o -Dtest='Booking*Test' test
```

These focused tests do not start Spring Boot, load deployment configuration, access a backend service, or contact a database. They verify booking ownership/eligibility, locks, transaction metadata, write ordering/failures, DTO input/output, controller responses, profile ownership, HTTP authorization boundaries, and legacy mutation guards. They do not prove PostgreSQL concurrent locking/rollback behavior: run a separate disposable-database integration check before deployment (two simultaneous bookings must produce exactly one booking and one 409; a forced second-write failure must persist neither write). No dependency was added solely for testing.
