# CliniQ backend: patient bookings

## API contract

All `/api/bookings` endpoints require an authenticated JWT whose `sub` is a canonical UUID and the patient-only `AccessPolicy.requirePatient(UUID)` check. Staff accounts are not patient accounts. Ownership is resolved from `Patient.authUserId`, never from the request body.

- `GET /api/bookings/availability`: array of `{slotId, slotDate, startTime, endTime, slotStatus, capacity, bookedCount, staff: {staffId, firstName, lastName, staffRole, availabilityStatus, active, department: {departmentId, name}}}`. Only OPEN windows/slots with a future start, valid same-day time interval, an active DOCTOR with a department, and at least one remaining place are returned. No patient details, staff email, auth IDs, or entities are serialized.
- `POST /api/bookings`: exactly `{ "slotId": "<UUID>" }`. Extra properties (including ownership/status fields) and invalid/missing slot IDs are rejected with 400. Success is 201 with `{appointmentId, appointmentStatus: "BOOKED", slotId, slotDate, startTime, endTime, departmentName, doctorName, bookedAt}`.
- `GET /api/bookings`: the authenticated patient's slot-linked bookings only, with the same response fields.
- `GET /api/bookings/{id}`: an owned slot-linked booking, or 404 (including another patient's booking).

## Multi-patient windows

A schedule window (e.g. 09:00–15:00) can hold multiple bookings. Doctors set a `capacity` (>= 1) when creating the window; `NULL` means the legacy single-appointment behaviour. Each booking is counted inside a pessimistic write lock on the window row, so concurrent bookings cannot overfill it. The window flips to `BOOKED` (and disappears from availability) once full. Patients are served in booking order (urgent appointments first); the waiting queue is ordered by `bookedAt` (fallback: `checkInTime`, then `createdAt`). Applying `V20261107_01__window_capacity.sql` is required before deploying this change.

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

Configure `cliniq.booking.zone-id=Africa/Gaborone` (default), or its environment form `CLINIQ_BOOKING_ZONE_ID=Africa/Gaborone`. Set an explicit IANA zone when the clinic operates in another timezone; the server's locale/timezone is not used. An invalid zone fails startup. `BookingTimeConfig` supplies an injectable clinic-zone `Clock` for deterministic tests.

`slotDate`, `startTime`, and `endTime` are clinic-local schedule values. `appointmentTime` is the patient's selected appointment start inside that window and must fall on a 5-minute boundary. `bookedAt` is the actual reservation timestamp expressed in that same clinic zone, **not** the scheduled appointment time. Past starts, end <= start, and nonexistent/ambiguous daylight-saving transition times are not bookable. The current API uses local date/time strings without offsets; clients must interpret them in the configured clinic zone.

Booking takes a pessimistic write lock on the schedule row, rechecks eligibility after acquiring the lock, enforces the window's `capacity` (absent means one booking), inserts a BOOKED/NORMAL appointment with null check-in time, and marks the slot BOOKED only once its capacity is reached — all in one transaction. Appointments reference `appointment.slot_id`, which is deliberately not unique since a window exists to be shared by its bookings. If either write fails, the transaction rolls back. Legacy appointments retain null slot IDs and are not inferred/backfilled from timestamps. Note the queue is ordered by booking time with urgent appointments first.

Generic appointment creation cannot supply an existing ID, slot association, or BOOKED status. Generic appointment updates cannot alter slot-linked bookings or create BOOKED appointments; deletion cannot remove slot-linked bookings. Schedule update/delete uses the same row lock and refuses BOOKED or appointment-linked slots. Generic schedule creation cannot supply an ID or BOOKED status, and updates cannot manufacture BOOKED status. Existing nonbooking walk-in CRUD remains available subject to the security layer. Use the dedicated operational endpoints for cancellation and check-in; do not clear links or change these states through generic CRUD. Rescheduling is not implemented.

## Cancellation and live patient queue snapshots

`PATCH /api/appointments/{id}/cancel` requires the authenticated patient owner and a `BOOKED` appointment (403 for another patient, 409 for another status, 404 if missing). Cancellation uses the same schedule-row write lock as booking, then locks/reads the appointment before changing it. The appointment, slot link, and audit history are retained. Both booking and grouped availability count all appointments **except CANCELLED**. Only a `BOOKED` slot whose remaining occupancy is below its effective capacity reopens; a `BLOCKED` slot never reopens through cancellation. Appointment, slot, and audit writes share one transaction.

`GET /api/appointments/{id}/queue-position` requires authenticated ownership and returns only `{appointmentId, queuePosition, queueStatus, estimatedWaitMinutes}`. Unknown appointments and appointments without a queue entry return 404; another patient's appointment returns 403. `queueStatus` is the appointment status. Only remaining `WAITING` entries in the same **queue department** receive a 1-based position: urgent first, then `bookedAt` (fallback `checkInTime`, then `createdAt`), then appointment UUID and queue UUID as stable tie-breakers. Missing legacy timestamps sort last within their priority; missing priority is treated as normal. Call-next uses the identical ordering. GET never updates the stored queue position or any managed entity.

For `WAITING`, the nonnegative integer estimate is rounded up from `(patientsAhead + inProgressConsultations) * averageMinutes`. The departmental average uses only positive `completedTime - calledTime` durations from completed, queued consultations in that queue department; absent/invalid history falls back to **15 minutes**. Each in-progress consultation contributes one full average consultation as a deliberately approximate workload allowance; this does not model elapsed consultation time or parallel doctors and is not a precise prediction. Queued `INCONSULTATION`, `COMPLETED`, `CANCELLED`, and `NOSHOW` records have null position and zero estimate; other non-waiting statuses have null position and estimate. No ETA field or migration is added. Existing staff queue entity reads are unchanged; this live DTO is the patient snapshot contract.

## Current doctor availability (PP-03)

`GET /api/bookings/availability` includes `staff.availabilityStatus`, directly from the doctor. The actual enum values are `AVAILABLE`, `ONCALL`, `EMERGENCY`, and `UNAVAILABLE`. This is **informational current status**, not a prediction for a future window: an active doctor with a department may still accept future bookings regardless of current availability status. Existing slot/time/capacity eligibility rules are unchanged.

## Schedule delay and absence broadcasts (PP-12)

`POST /api/schedules/{id}/broadcast` accepts:

```json
{"type":"DELAY","message":"Please arrive later than planned","delayMinutes":30}
```

- `type` is exactly `DELAY` or `ABSENCE`. `message` must be nonblank and at most 500 characters.
- `DELAY` requires a JSON integer `delayMinutes` in **1–1440**. For `ABSENCE`, omit `delayMinutes` or send null. Decimal and numeric-string values are rejected.
- Only an **active doctor owning that window** or an **active ADMIN** may broadcast. Database profiles, not JWT role metadata, authorize both the security filter and service.
- Success is 200 with **only** `{"recipientCount":number}`. An empty recipient set returns zero. No patient listing or patient identifiers are returned.
- Within one transaction, the service locks the schedule row, selects appointments in that window with status `BOOKED`, `CHECKEDIN`, or `WAITING`, and creates one notification per **distinct patient**, linked to a representative eligible appointment. Cancelled, completed, no-show, and in-consultation appointments are excluded.
- Notifications use `DOCTOR_DELAY` / `DOCTOR_ABSENCE`, contain doctor/date/window context plus delay/custom message, are unread, and are patient-linked only (not delivered to the doctor's notification inbox). The broadcast is audited with the acting JWT. Notification and audit failures roll back the transaction.
- This does **not** cancel/reschedule appointments, modify slot status/capacity, or change global doctor availability. Broadcasting is an explicit action; repeating a request sends another broadcast.

## Admin doctor schedules (AP-03)

All `/api/admin/**` endpoints require an **active ADMIN**, enforced in the security chain and again in services. Other staff, doctors, patients, and inactive admins are denied. Editable Auth/JWT role claims do not grant access.

- `GET /api/admin/schedules`: returns a ScheduleSlot-shaped array, ordered by scheduled date/start/ID:
  `{slotId, slotDate, startTime, endTime, slotStatus, capacity, staff: {staffId, firstName, lastName, staffRole, availabilityStatus, active, department: {departmentId, name}|null}}`.
  No auth IDs, staff emails, patient records, or raw entities are exposed. Existing legacy null capacity is displayed as one.
- `POST /api/admin/schedules`: accepts `{staffId, slotDate, startTime, endTime, capacity, slotStatus?}`; returns 201 with the same DTO as GET.
- `PUT /api/admin/schedules/{id}`: accepts the same complete body; returns 200 with the same DTO. Administrators may reassign an unused window to another eligible doctor.
- `DELETE /api/admin/schedules/{id}`: returns 204.

Targets must be **active DOCTORs with a department**, not nurses/admins. Capacity is a required JSON integer in **1–10,000**. `slotStatus` may be `OPEN` or `BLOCKED` and defaults to `OPEN`; clients cannot forge `BOOKED` (409). Dates/times must form a valid same-day interval, `endTime > startTime`, and a strictly future start in the configured booking `Clock` zone; nonexistent/ambiguous DST times are rejected (400). Missing records return 404.

Edit/delete acquire the schedule-row write lock and preserve the existing **any linked appointment** guard, including cancelled/completed history (409); history cannot be rewritten or deleted. Successful writes are audited with the admin JWT. All DTO conversion happens inside transactions.

Existing doctor `POST /api/schedules` remains self-scheduling: the owner is derived from the active doctor profile and status is forced to OPEN. Dates/times and explicit capacity receive the same validation; absent/null capacity retains the legacy default of one. Generic HTTP update/delete now require an active owner doctor or active admin, retain row locks/history guards, and cannot reassign a window through nested staff fields; use the dedicated admin endpoint for reassignment. Generic updates preserve existing capacity when omitted. Legacy non-JWT in-process update/delete overloads remain for compatibility and are **not HTTP handlers**.

## Hospital-wide admin appointments (AP-08)

`GET /api/admin/appointments` accepts optional query parameters:

- `from`, `to`: ISO dates, inclusive **scheduled slot date**; slotless walk-ins fall back to the date of `createdAt`. Reservation date (`bookedAt`) is not the date filter. `from` must not be after `to`.
- `status`: exact appointment enum (`BOOKED`, `CHECKEDIN`, `WAITING`, `INCONSULTATION`, `COMPLETED`, `CANCELLED`, `NOSHOW`).
- `departmentId`: UUID filter on the effective treatment department described below. `doctorId`: UUID filter on the assigned staff.
- `search`: case-insensitive substring search of patient full name/email/phone, doctor/staff full name, or appointment UUID. Blank search is ignored; `%`, `_`, and `!` are treated literally.
- `page`: default 0, must be nonnegative. `size`: default 20, must be **1–100**. Invalid filters/page bounds return 400.

Filtering, counting, and pagination run in the database, ordered by `coalesce(bookedAt, createdAt) DESC NULLS LAST`, then appointment ID descending for stable ties. Response:

```text
{items: AdminAppointment[], page, size, totalElements, totalPages}
```

`AdminAppointment` is **flat**, with exactly these fields (no auth IDs or nested entities):

```text
appointmentId, appointmentStatus, priority, bookedAt, createdAt, appointmentTime,
checkInTime, calledTime, completedTime, slotId, slotDate, startTime, endTime,
doctorId, doctorName, departmentId, departmentName, patientId, patientName,
patientEmail, patientPhone
```

`patientName` is the full name. Missing relationships/timestamps are null; slotless walk-ins do not receive invented slot values, and non-doctor staff are not represented as doctors. For **queued slotless appointments**, department comes from `Queue.department`, not the registering staff's department (walk-ins may target another department). Staff department is used for scheduled appointments and slotless appointments with **no queue**. A queue with a missing department produces null, not a registrar-department fallback. Filtering and DTOs use the same rule for list/detail/cancel. If legacy data contains multiple queue rows for one appointment, the row with the smallest queue UUID is selected deterministically in both paths; it is not inferred to be the earliest by time. Filtering uses correlated subqueries rather than joining queue rows into the paged/count root, so duplicate queues cannot multiply appointments or totals. DTO mapping occurs in the service transaction with one bounded department-projection query for the current page's slotless appointment IDs (or the single detail/cancel ID), not per-appointment lookups or unbounded queue history.

- `GET /api/admin/appointments/{id}`: returns the same DTO for inspection, or 404.
- `PATCH /api/admin/appointments/{id}/cancel`: returns the same DTO after cancellation. Only `BOOKED` appointments can be cancelled (409 otherwise). Reuses patient cancellation's private atomic core: lock slot first, then reload/lock appointment, flush cancellation, recompute occupancy and reopen only a full BOOKED window with room, and audit the **admin actor** in the same transaction. History and slot links are retained; BLOCKED windows remain blocked. The patient cancellation endpoint still requires patient ownership and does not grant administrators an ownership bypass there.

No admin no-show or rescheduling action is added in this scope.

New schedule/admin/broadcast controllers return safe `{message}` bodies for service errors and malformed JSON; security-layer and query-binding errors may use Spring's default error format.

## Required PostgreSQL deployment migration

Migrations in `src/main/resources/db/migration/` run **automatically at startup** through Flyway (`spring.flyway.enabled=true`, default location `classpath:db/migration`, `baseline-on-migrate=true`, baseline version `20261116`). The older scripts were previously applied manually and only ALTER existing tables, so the baseline marks them as applied; on a fresh database Hibernate's `ddl-auto=update` creates the base tables and Flyway still applies the newer versioned scripts. **All future schema changes belong in versioned SQL files**, not in `ddl-auto`.

1. Back up the database and stop all application writers. Confirm the target database/schema and inspect the migration in a disposable PostgreSQL database first.
2. Apply `src/main/resources/db/migration/V20261023_01__patient_booking_slots.sql` using your approved deployment tooling, with the application schema in `search_path`. It runs atomically, locks both affected tables, adds the nullable slot FK and unique constraint, and explicitly drops `appointment.check_in_time` NOT NULL. The script is idempotent and leaves legacy rows intact. Multiple null slot IDs remain legal. Invalid existing links or duplicate nonnull slot IDs cause failure rather than deleting/reassigning data.
2a. For multi-patient windows, also apply `V20261107_01__window_capacity.sql` (drops the slot uniqueness on `appointment.slot_id`, adds `schedule.capacity` with a `capacity >= 1` check). It is idempotent and also leaves existing rows intact.
2b. Apply `V20261115_02__appointment_times_and_audit_ids.sql` to add the selected appointment time and allow audit entries to reference UUID entities. It is idempotent.
2c. Apply `V20261116_01__broadcast_notification_message.sql` to widen `notification.message` from the legacy 255-character limit to PostgreSQL `text`, preserving existing records. This small idempotent migration is necessary to store a 500-character broadcast plus doctor/window context without truncation. No other schema change or dependency is introduced for PP-03/PP-12/AP-03/AP-08.
3. Verify both constraints exist and check-in accepts null; deploy the new application with the updated security/profile code and explicit clinic timezone, then resume writers. Never run old writers alongside the new booking lifecycle.

List endpoints (`/api/appointments`, `/api/queues`, `/api/staff`, `/api/patients`, `/api/departments`, `/api/schedules`, `/api/audit-logs`) accept `page`, `size` and `sort` and return a stable envelope: `{ content, page, size, totalElements, totalPages, first, last }`. Responses are DTOs; internal fields such as `authUserId` are never serialized. `DELETE /api/staff/{id}` now soft-deletes (sets `active=false`, marks the account UNAVAILABLE, frees the unique email) and bans the matching Supabase Auth user; the row is retained so audit logs, appointments and schedules keep their foreign-key target.

## Offline validation

Use JDK 21 and cached Maven/dependencies:

```sh
./mvnw -o '-Dtest=*Test,*Tests,!BackendApplicationTests' test
```

These focused tests do not start Spring Boot, load deployment configuration, access a backend service, or contact a database. They verify booking ownership/eligibility, locks, transaction metadata, write ordering/failures, DTO input/output, controller responses, profile ownership, HTTP authorization boundaries, and legacy mutation guards. They do not prove PostgreSQL concurrent locking/rollback behavior: run a separate disposable-database integration check before deployment (two simultaneous bookings must produce exactly one booking and one 409; a forced second-write failure must persist neither write). Operational tests also cover cancellation/rebooking, double-cancel protection, cancellation transaction interception, live queue progression, priority/tie ordering, departmental estimates, snapshot ownership, and read-only behavior. Repository JPQL is parsed against the current Hibernate metadata with JDBC metadata access disabled; no database connection is opened. No dependency was added solely for testing. Admin/broadcast regressions also cover exact JSON contracts and HTTP status codes through the production security chain, active-admin enforcement, owner-only broadcast/self-schedule mutations, strict integer validation, clinic-zone/DST schedule validation, any-appointment history guards, safe distinct-patient notifications, transaction interception on save/audit failures, shared admin cancellation, and database-paged appointment filter/count query parsing. `BackendApplicationTests` is intentionally excluded because it starts the environment-dependent full Spring Boot context.

## Check-in QR codes

`GET /api/appointments/{id}/qr` returns a 300x300 PNG QR encoding `<app-base-url>/check-in?id=<appointmentId>` (the shared check-in page in the frontend). The QR is generated on demand and never stored. Callers must be the appointment's patient owner or an active staff account; otherwise 403 (404 for unknown IDs, 401 when signed out). `cliniq.app-base-url` (or `CLINIQ_APP_BASE_URL`, default `http://localhost:3000`) sets the public frontend origin used in the encoded link — set it when deploying somewhere other than localhost.
