# CleanBengaluru — Smart Garbage Reporting, Collection & Monitoring System

A full-stack web application where citizens report roadside garbage with a photo and their
GPS location, an authority assigns the job to a field worker, the worker uploads an
after-cleaning photo, and the original citizen verifies the cleanup — or reopens the
complaint if it was not done properly.

**Stack:** Java 21 · Spring Boot 3.5 · Spring Security + JWT · Spring Data JPA / Hibernate ·
MySQL 8 · React 18 (Vite) · Leaflet + OpenStreetMap · Swagger/OpenAPI · JUnit 5 + Mockito · Docker

---

## Contents

- [What it does](#what-it-does)
- [Screenshots](#screenshots)
- [Architecture](#architecture)
- [Requirements](#requirements)
- [Run it locally](#run-it-locally)
- [Run it with Docker](#run-it-with-docker)
- [Environment variables](#environment-variables)
- [Demo accounts](#demo-accounts)
- [Try the full workflow](#try-the-full-workflow)
- [API documentation](#api-documentation)
- [Database](#database)
- [How the interesting parts work](#how-the-interesting-parts-work)
- [Tests](#tests)
- [Project structure](#project-structure)

---

## What it does

**Citizen** — register, log in, see nearby dustbins and open reports on a map, file a report
with a photo and auto-captured coordinates, track status, view before/after photos, verify
or reopen a cleanup, read notifications.

**Worker** — see assigned tasks with priority and location, accept or reject, start cleaning,
upload the after-cleaning photo, complete the job, update bin status in the field.

**Admin** — dashboard with live counters and analytics, all reports on a map, assign workers,
reject suspicious reports, manage bins and staff accounts, area cleanliness scores.

---

## Screenshots

Add yours here once you have run the app:

| Screen | Image |
|---|---|
| Citizen home (map + nearby reports) | `docs/screenshots/citizen-home.png` |
| Report garbage form | `docs/screenshots/report-form.png` |
| Duplicate detection prompt | `docs/screenshots/duplicate.png` |
| Before / after cleanup proof | `docs/screenshots/before-after.png` |
| Worker task detail | `docs/screenshots/worker-task.png` |
| Admin dashboard | `docs/screenshots/admin-dashboard.png` |

```bash
mkdir -p docs/screenshots   # then drop your PNGs in and commit
```

---

## Architecture

```
HTML/CSS/JavaScript (:5173)
    |  fetch, JWT in the Authorization header
    v
Spring Boot (:8080, context path /api)
    JwtAuthenticationFilter  ->  SecurityFilterChain  ->  Controller
                                                            |
                                                          Service   (all business rules)
                                                            |
                                                         Repository (Spring Data JPA)
                                                            |
                                                          MySQL 8
Uploaded photos -> ./uploads on disk, file NAME stored in MySQL
```

---

## Requirements

| Tool | Version |
|---|---|
| JDK | 21 |
| Maven | 3.9+ |
| MySQL | 8.x |
| Node.js | 20+ (only used to serve the static frontend with `npx`) |

## Run it locally

### 1. Database

Create the database in MySQL Workbench:

```sql
CREATE DATABASE cleanbengaluru CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

Set your MySQL password in `backend/src/main/resources/application.properties` (or use `DB_USERNAME` and `DB_PASSWORD`). Hibernate creates/updates the tables on first startup.

### 2. Backend

```powershell
cd backend
mvn clean install
mvn spring-boot:run
```

Check `http://localhost:8080/api/health`.

### 3. HTML/CSS/JavaScript frontend

There is no `npm install` and no React/Vite dependency in this version.

```powershell
cd frontend
npx --yes http-server . -p 5173
```

Open `http://localhost:5173`.

The frontend uses browser `fetch()` for the API and Leaflet from its CDN for the admin map.

## Run it with Docker

```bash
docker compose up --build
```

- Frontend: <http://localhost:5173>
- Backend: <http://localhost:8080/api/health>
- MySQL: published on host port **3307** (so it will not clash with a local MySQL on 3306)

---

## Environment variables

Backend (`backend/.env.example`):

| Variable | Default | Meaning |
|---|---|---|
| `DB_HOST` / `DB_PORT` | localhost / 3306 | MySQL location |
| `DB_NAME` | cleanbengaluru | Schema name |
| `DB_USERNAME` / `DB_PASSWORD` | root / root | MySQL credentials |
| `JWT_SECRET` | dev value in properties | **Must be ≥ 32 characters.** Change it for anything real |
| `JWT_EXPIRATION_MS` | 86400000 (24 h) | Token lifetime |
| `UPLOAD_DIR` | uploads | Folder for photos |

Frontend (`frontend/.env`):

| Variable | Default |
|---|---|
| `VITE_API_BASE_URL` | http://localhost:8080/api |

---

## Demo accounts

| Role | Email | Password |
|---|---|---|
| Admin | admin@cleanbengaluru.com | admin123 |
| Worker | worker1@cleanbengaluru.com | worker123 |
| Worker | worker2@cleanbengaluru.com | worker123 |
| Citizen | citizen@cleanbengaluru.com | citizen123 |

Change these before deploying anywhere public.

---

## Try the full workflow

1. Log in as the **citizen** → *Report garbage* → allow location → attach a photo → submit.
2. Submit a second report of the same type within ~100 m → you get
   **"Possible existing report found"** with a choice to link or file separately.
3. Log in as the **admin** → *Reports* → assign the report to a worker.
4. Log in as that **worker** → *My tasks* → Accept → Start cleaning → upload the
   after-photo → Complete. (Try completing with no photo: it returns 400.)
5. Back as the **citizen** → *Notifications* says "Please verify the cleanup" →
   open the report → **Yes, cleaned** (closes it) or **No, not properly cleaned**
   (reopens it and notifies the admin).
6. Admin → *Dashboard* → counters, category/area/month breakdowns and area cleanliness scores.

---

## API documentation

Swagger UI (with a working **Authorize** button for the JWT):
<http://localhost:8080/api/swagger-ui.html>
Raw OpenAPI JSON: <http://localhost:8080/api/v3/api-docs>

A written reference with request/response bodies and status codes is in [`API.md`](API.md).

Every endpoint starts with `/api` because of `server.servlet.context-path=/api`.

---

## Database

Design, DDL and relationship notes: [`database/schema.sql`](database/schema.sql)
and [`database/README-database.md`](database/README-database.md).

Six tables: `users`, `garbage_reports`, `garbage_bins`, `assignments`,
`citizen_verifications`, `notifications`.

---

## How the interesting parts work

### Location search

`GET /api/reports/nearby?latitude=12.9716&longitude=77.5946&radius=2`

Haversine gives exact great-circle distance, but no index can serve trigonometry, so
running it over every row is O(n). Instead the query first narrows to a lat/lon rectangle
that the composite index on `(latitude, longitude)` can use, then Haversine runs only on
that small candidate set and trims the corners of the rectangle. See
`util/DistanceCalculator.java`.

### Duplicate detection

Rule-based, not AI: same category, within 100 m, filed in the last 24 hours, report still
open. The nearest match wins. The citizen is shown *"Possible existing report found"* and
decides — nothing is discarded silently. Linking bumps the original's `duplicateCount`,
which raises its priority. See `service/DuplicateDetectionService.java`.

### Priority

A transparent points system — category weight + severity + road-blocked + duplicate count +
reopened — mapped to LOW / MEDIUM / HIGH / CRITICAL. See `service/PriorityService.java`.

### Status transitions

The legal moves live in one `Map` inside the `ReportStatus` enum, and `ReportService.transition()`
is the only place status changes. An illegal move (say REPORTED → CLOSED) returns HTTP 409
instead of corrupting data.

### Before / after photos

The citizen's photo is `garbage_reports.before_image`; the worker's is
`assignments.after_image`. Different tables, so the original is never overwritten. Files go
to disk, only the file name goes into MySQL.

### Area cleanliness score

Starts at 100, subtracts bounded penalties for open reports, bad bins, reopened complaints
and slow resolution, and adds credit for resolved reports. Formula is documented in
`service/AnalyticsService.java`. It is generated by this application from its own data —
it is not an official BBMP rating.

---

## Tests

```bash
cd backend
mvn test
```

Unit tests (JUnit 5 + Mockito, no Spring context, no database) cover the Haversine and
bounding-box maths, the priority rules, the status-transition table, and the duplicate
detection algorithm with a mocked repository.

Good candidates for integration tests you can add next: the auth flow end to end with
`@SpringBootTest` + MockMvc, and repository queries against Testcontainers MySQL.

---

## Project structure

```
cleanbengaluru/
├── backend/
│   └── src/main/java/com/cleanbengaluru/
│       ├── controller/   HTTP layer only
│       ├── service/      business rules
│       ├── repository/   Spring Data JPA interfaces
│       ├── entity/       JPA entities + enums
│       ├── dto/          request/response shapes
│       ├── mapper/       entity -> DTO
│       ├── security/     JWT util, filter, UserDetails
│       ├── exception/    custom exceptions + GlobalExceptionHandler
│       ├── config/       SecurityConfig, OpenApiConfig, DataSeeder
│       └── util/         DistanceCalculator
├── frontend/src/
│   ├── api/              axios client + endpoint functions
│   ├── components/       Navbar, MapView, StatusBadge, ProtectedRoute
│   ├── context/          AuthContext
│   └── pages/            citizen, worker and admin screens
├── database/             schema.sql + notes
├── docker-compose.yml
└── README.md
```

## Frontend (revised)

The frontend is plain HTML/CSS/JavaScript with no build step. See
`frontend/README.md` for the design system, the route table and the full list of
what changed — in short: locations are now placed on a map and validated in one
place before being displayed or dispatched, administrators can upload only the
cleared-site photo (never the garbage photo), and the worker app gained a duty
switch, a nearest-first route planner, an arrival check, a job timer and an
offline queue.
