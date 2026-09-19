# CleanBengaluru API reference

Base URL: `http://localhost:8080/api`
Interactive docs: `http://localhost:8080/api/swagger-ui.html`

Every path below is relative to `/api` (the context path), so `/auth/login` is really
`http://localhost:8080/api/auth/login`.

---

## Conventions

**Success envelope** — every successful response looks like this:

```json
{
  "success": true,
  "message": "Login successful",
  "data": { },
  "timestamp": "2026-09-17T11:42:03.118"
}
```

**Error envelope** — produced by `GlobalExceptionHandler` for every failure:

```json
{
  "success": false,
  "message": "Report not found with id: 99",
  "status": 404,
  "path": "/api/reports/99",
  "timestamp": "2026-09-17T11:42:03.118"
}
```

Validation failures add a `fieldErrors` object:

```json
{
  "success": false,
  "message": "Validation failed",
  "status": 400,
  "path": "/api/auth/register",
  "fieldErrors": { "email": "Email must be valid", "password": "Password must be at least 6 characters" },
  "timestamp": "2026-09-17T11:42:03.118"
}
```

**Authentication** — send the JWT on every protected call:

```
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9....
```

**Status codes used**

| Code | When |
|---|---|
| 200 | OK |
| 201 | Created (register, new report, new bin, new staff account) |
| 400 | Validation error or a broken business rule (e.g. completing with no after-photo) |
| 401 | Missing/expired/invalid token, or wrong email+password |
| 403 | Logged in, but your role is not allowed here |
| 404 | Id does not exist (or does not belong to you) |
| 409 | Duplicate email/bin code, or an illegal status transition |
| 413 | Uploaded file over 5 MB |

---

## 1. Authentication

### POST `/auth/register` — public

Creates a **citizen** account. The `role` field is ignored: workers and admins are created by an admin.

Request:
```json
{
  "name": "Anita Rao",
  "email": "anita@example.com",
  "password": "secret123",
  "phone": "9876543210",
  "areaName": "Rajajinagar"
}
```

`201 Created`:
```json
{
  "success": true,
  "message": "Registration successful",
  "data": {
    "token": "eyJhbGciOiJIUzI1NiJ9...",
    "tokenType": "Bearer",
    "userId": 5,
    "name": "Anita Rao",
    "email": "anita@example.com",
    "role": "CITIZEN",
    "areaName": "Rajajinagar",
    "expiresInMs": 86400000
  }
}
```

Errors: `400` validation, `409` email already registered.

### POST `/auth/login` — public

```json
{ "email": "citizen@cleanbengaluru.com", "password": "citizen123" }
```

`200 OK` — same `data` shape as register. `401` on wrong credentials or a deactivated account.

### GET `/auth/me` — any logged-in user

`200 OK`:
```json
{
  "success": true,
  "message": "Current user",
  "data": {
    "id": 4, "name": "Demo Citizen", "email": "citizen@cleanbengaluru.com",
    "phone": null, "role": "CITIZEN", "areaName": "Rajajinagar",
    "active": true, "createdAt": "2026-09-17T10:02:11.004"
  }
}
```

The password hash is never returned by any endpoint.

---

## 2. Reports

### POST `/reports` — CITIZEN, ADMIN — `multipart/form-data`

Form fields: `garbageType`, `description`, `latitude`, `longitude`, `address`, `areaName`,
`severity` (1–5), `roadBlocked` (true/false), `forceCreate` (true/false).
File part: `image` (optional, JPG/PNG/WEBP, ≤ 5 MB).

**Two possible outcomes.**

`201 Created` — nothing similar nearby, the report was saved:
```json
{
  "success": true,
  "message": "Report submitted successfully",
  "data": {
    "created": true,
    "report": {
      "id": 12, "reporterId": 4, "reporterName": "Demo Citizen",
      "garbageType": "ROADSIDE_GARBAGE", "description": "Pile near the bus stop",
      "beforeImage": "3f0c9a12ab4e4d0e8b77.jpg", "afterImage": null,
      "latitude": 12.9716, "longitude": 77.5946,
      "address": "Near bus stop", "areaName": "Rajajinagar",
      "status": "REPORTED", "priority": "MEDIUM", "severity": 3, "roadBlocked": false,
      "duplicateCount": 0, "reopenCount": 0, "parentReportId": null, "rejectionReason": null,
      "assignmentId": null, "workerId": null, "workerName": null, "assignmentStatus": null,
      "distanceMetres": null,
      "createdAt": "2026-09-17T11:40:02.551", "updatedAt": "2026-09-17T11:40:02.551",
      "resolvedAt": null
    },
    "possibleDuplicate": null,
    "duplicateDistanceMetres": null
  }
}
```

`200 OK` — **a possible duplicate was found and nothing was saved**. Resend the same form
with `forceCreate=true` to file it anyway:
```json
{
  "success": true,
  "message": "Possible existing report found near this location",
  "data": {
    "created": false,
    "report": null,
    "possibleDuplicate": { "id": 11, "garbageType": "ROADSIDE_GARBAGE", "status": "ASSIGNED", "...": "..." },
    "duplicateDistanceMetres": 23.4
  }
}
```

Errors: `400` validation or a bad image type, `413` file too large.

### GET `/reports/my?page=0&size=10` — my reports, newest first

Returns a Spring `Page`: `data.content` is the array, plus `data.totalElements`,
`data.totalPages`, `data.number`.

### GET `/reports?status=REPORTED&page=0&size=10` — all reports, optional status filter

### GET `/reports/nearby?latitude=12.9716&longitude=77.5946&radius=2`

`radius` is in **kilometres** (default 2, max 20). Returns open reports only, nearest first,
each with `distanceMetres`. `400` if the radius is outside the allowed range.

### GET `/reports/{id}` — one report

### PUT `/reports/{id}` — edit my own report (JSON body, same fields as create)

Allowed only while the report is `REPORTED` or `UNDER_REVIEW`, otherwise `400`.

### DELETE `/reports/{id}` — delete my own report while still `REPORTED`

### POST `/reports/{id}/verify` — CITIZEN (the reporter)

```json
{ "cleaned": true }
```
```json
{ "cleaned": false, "reason": "Only half of it was picked up" }
```

`cleaned: true` → `VERIFIED` → `CLOSED` and `resolvedAt` is stamped.
`cleaned: false` → `REOPENED`, `reopenCount` increases, admins are notified.

`400` if the report is not currently `VERIFICATION_PENDING`, or if it is not yours.

### POST `/reports/{id}/reopen` — CITIZEN

```json
{ "reason": "Garbage is back at the same spot" }
```

---

## 3. Bins

| Method | Path | Who |
|---|---|---|
| GET | `/bins` | any logged-in user |
| GET | `/bins/nearby?latitude=&longitude=&radius=1` | any logged-in user |
| GET | `/bins/{id}` | any logged-in user |
| POST | `/bins` | ADMIN |
| PUT | `/bins/{id}` | ADMIN |
| PUT | `/bins/{id}/status` | WORKER, ADMIN |
| DELETE | `/bins/{id}` | ADMIN |

Create/update body:
```json
{
  "code": "BLR-RJN-004",
  "locationName": "Rajajinagar 2nd Block",
  "latitude": 12.9915, "longitude": 77.5528,
  "binType": "MIXED",
  "capacityLitres": 240,
  "status": "NORMAL",
  "areaName": "Rajajinagar"
}
```

Status update body (`collected: true` stamps `lastCollectionAt`):
```json
{ "status": "EMPTY", "collected": true }
```

Bin response adds `distanceMetres` on the `/nearby` call. `409` if the bin code already exists.

`binType`: `DRY_WASTE | WET_WASTE | MIXED | RECYCLABLE | HAZARDOUS`
`status`: `EMPTY | NORMAL | NEAR_FULL | FULL | OVERFLOWING | DAMAGED`

---

## 4. Worker tasks — WORKER, ADMIN

### GET `/workers/tasks?activeOnly=true`

```json
{
  "success": true,
  "message": "My tasks",
  "data": [{
    "taskId": 3, "reportId": 12,
    "garbageType": "ROADSIDE_GARBAGE", "description": "Pile near the bus stop",
    "priority": "HIGH", "reportStatus": "ASSIGNED", "taskStatus": "ASSIGNED",
    "latitude": 12.9716, "longitude": 77.5946,
    "address": "Near bus stop", "areaName": "Rajajinagar",
    "beforeImage": "3f0c9a12ab4e4d0e8b77.jpg", "afterImage": null, "workerNotes": null,
    "reportCreatedAt": "2026-09-17T11:40:02.551",
    "assignedAt": "2026-09-17T11:55:10.220",
    "acceptedAt": null, "startedAt": null, "completedAt": null
  }]
}
```

### GET `/tasks/{id}`

### PUT `/tasks/{id}/accept` — task `ASSIGNED` → `ACCEPTED`, report → `WORKER_ACCEPTED`

### PUT `/tasks/{id}/reject`

```json
{ "reason": "Wrong location, nothing here" }
```
Task → `REJECTED`, report goes back to `UNDER_REVIEW` for reassignment.

### PUT `/tasks/{id}/start` — task → `IN_PROGRESS`, report → `CLEANING_IN_PROGRESS`

### PUT `/tasks/{id}/photo` — `multipart/form-data`, file part `image`

Uploads the after-cleaning photo without completing the task yet.

### PUT `/tasks/{id}/complete` — `multipart/form-data`

File part `image` (required unless already uploaded via `/photo`), form field `notes` (optional).

Report moves `CLEANING_COMPLETED` → `VERIFICATION_PENDING` and the citizen is notified.
**`400` if there is no after-photo** — this rule is the whole point of the proof flow.

`404` if the task belongs to another worker (deliberately not `403`, so we do not confirm
the task exists).

---

## 5. Admin — all under `/admin/**`, ADMIN only

### GET `/admin/dashboard` (and `/admin/analytics`, same payload)

```json
{
  "totalReports": 24, "pendingReports": 5, "assignedReports": 3, "inProgressReports": 2,
  "completedReports": 12, "reopenedReports": 1, "rejectedReports": 1,
  "totalBins": 10, "overflowingBins": 1, "damagedBins": 1,
  "totalCitizens": 6, "totalWorkers": 2,
  "averageResolutionHours": 18.4,
  "reportsByCategory": { "ROADSIDE_GARBAGE": 9, "PLASTIC_WASTE": 4 },
  "reportsByArea": { "Rajajinagar": 11, "Indiranagar": 6 },
  "reportsByMonth": { "2026-08": 7, "2026-09": 17 },
  "workerStats": [
    { "workerId": 2, "workerName": "Ravi Kumar", "areaName": "Rajajinagar",
      "totalTasks": 9, "completedTasks": 7, "activeTasks": 2 }
  ],
  "areaScores": [
    { "areaName": "Indiranagar", "score": 88.5, "grade": "A", "openReports": 1,
      "resolvedReports": 5, "reopenedReports": 0, "overflowingBins": 0,
      "averageResolutionHours": 9.2 }
  ]
}
```

### GET `/admin/analytics/areas` — just the area cleanliness scores
### GET `/admin/analytics/workers` — just the worker statistics
### GET `/admin/reports?status=&page=&size=` — paged report list

### POST `/admin/reports/{id}/review` — move to `UNDER_REVIEW`

### POST `/admin/reports/{id}/assign`

```json
{ "workerId": 2 }
```
Report → `ASSIGNED`, a task row is created, worker and citizen are both notified.
`400` if that user is not a worker or is deactivated, `409` if the report cannot legally
move to `ASSIGNED` from its current status.

### POST `/admin/reports/{id}/reject`

```json
{ "reason": "Photo does not show any garbage" }
```

### GET `/admin/users?role=WORKER` · GET `/admin/workers`

### POST `/admin/users` — create a WORKER or ADMIN

```json
{
  "name": "Ravi Kumar",
  "email": "ravi@cleanbengaluru.com",
  "password": "worker123",
  "phone": "9876500000",
  "areaName": "Rajajinagar",
  "role": "WORKER"
}
```
`400` if `role` is `CITIZEN` (citizens self-register), `409` if the email exists.

### PUT `/admin/users/{id}/active?active=false` — deactivate (soft delete, history is kept)

---

## 6. Notifications — any logged-in user

| Method | Path | Purpose |
|---|---|---|
| GET | `/notifications` | my notifications, newest first |
| GET | `/notifications/unread-count` | `{ "count": 3 }` |
| PUT | `/notifications/{id}/read` | mark one as read |
| PUT | `/notifications/read-all` | mark all as read |

---

## 7. Files

### GET `/files/{fileName}` — public

Serves an uploaded photo. Use it directly in an `<img src>`:
`http://localhost:8080/api/files/3f0c9a12ab4e4d0e8b77.jpg`

---

## 8. Health

### GET `/health` — public, no token needed

---

## Enums

| Enum | Values |
|---|---|
| `Role` | CITIZEN, WORKER, ADMIN |
| `GarbageType` | ROADSIDE_GARBAGE, OVERFLOWING_DUSTBIN, PLASTIC_WASTE, CONSTRUCTION_WASTE, GARDEN_WASTE, ILLEGAL_DUMPING, UNCLEAN_ROAD, MISSED_COLLECTION, DAMAGED_BIN |
| `ReportStatus` | REPORTED, UNDER_REVIEW, ASSIGNED, WORKER_ACCEPTED, CLEANING_IN_PROGRESS, CLEANING_COMPLETED, VERIFICATION_PENDING, VERIFIED, CLOSED, REJECTED, REOPENED |
| `Priority` | LOW, MEDIUM, HIGH, CRITICAL |
| `AssignmentStatus` | ASSIGNED, ACCEPTED, REJECTED, IN_PROGRESS, COMPLETED |
| `BinType` | DRY_WASTE, WET_WASTE, MIXED, RECYCLABLE, HAZARDOUS |
| `BinStatus` | EMPTY, NORMAL, NEAR_FULL, FULL, OVERFLOWING, DAMAGED |

---

## Postman quick start

1. `POST /api/auth/login` with the citizen credentials → copy `data.token`.
2. In Postman, set collection-level Authorization → Type **Bearer Token** → paste it.
3. `POST /api/reports` → Body → **form-data** → add the text fields, then add a row named
   `image` with type **File** and pick a photo.
4. Log in as the admin, `POST /api/admin/reports/1/assign` with `{"workerId": 2}`.
5. Log in as that worker, walk the task through accept → start → complete (form-data with `image`).
6. Back as the citizen, `POST /api/reports/1/verify` with `{"cleaned": true}`.
