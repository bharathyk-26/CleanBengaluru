# CleanBengaluru — frontend

Plain HTML, CSS and JavaScript. No npm project, no build step, no framework.
Leaflet is the only runtime dependency and it loads from a CDN.

```powershell
cd frontend
npx --yes http-server . -p 5173
```

Open `http://localhost:5173`. The backend must be running on port 8080.

## Files

| File | What is in it |
|---|---|
| `index.html` | The shell. Fonts, Leaflet, and the four scripts below. |
| `style.css` | The whole design system: tokens, components, dark mode, responsive rules. |
| `app.js` | State, API client, UI primitives (modals, toasts, photo handling), map helpers, router, app shell. |
| `views-auth.js` | Portal picker, sign in per role, citizen registration. |
| `views-citizen.js` | Citizen home, the report form, report list and detail, bins, updates. |
| `views-worker.js` | Duty board, route planner, task detail, completion flow, personal record. |
| `views-admin.js` | Operations dashboard, dispatch, city map, bin network, people. |

Routing is still hash-based, so there is still exactly one HTML file:

```
#/enter            portal picker          #/worker          duty board
#/enter/citizen    citizen sign in        #/worker/route    route planner
#/enter/worker     worker sign in         #/worker/record   personal record
#/enter/admin      admin sign in          #/tasks/:id       one job
#/join             citizen registration   #/admin           operations
#/home             citizen home           #/admin/reports   dispatch queue
#/report           file a report          #/admin/map       city map
#/my-reports       my reports             #/admin/bins      bin network
#/reports/:id      one report             #/admin/users     people
#/bins  #/notifications
```

## The design

The subject is municipal sanitation, so the interface borrows from municipal
paperwork rather than from dashboard templates.

- **Colour comes from the waste segregation code.** Green for wet waste and for
  the citizen portal, blue for dry waste and the control room, red for hazard
  and anything overflowing. Worker screens use high-visibility-vest yellow,
  because that is what the person holding the phone is wearing.
- **Type** is Archivo for headings — a signage-weight grotesk — over IBM Plex
  Sans for reading, with IBM Plex Mono reserved for coordinates and record
  numbers, which are the only genuinely tabular data on screen.
- **A report is drawn as a job docket**: a status stripe down the left edge, a
  record number, and the metadata a supervisor would scan first.
- **The lifecycle is drawn as a route with stops**, not a progress bar, because
  a report moves between people rather than filling up.
- Dark mode follows a stored preference; `prefers-reduced-motion` is respected;
  the layout collapses to a bottom tab bar under 820px, which is how field
  workers actually hold the phone.

## What changed in this revision

**Location is no longer typed.** The old form had two number boxes, which is
how a report ended up at 5°N 5°E in the Gulf of Guinea. Now:

- reports and bins are placed by tapping a map or by taking a GPS fix,
- every coordinate passes through one validator before it is displayed,
  mapped or linked — out-of-range, null-island and outside-Bengaluru values are
  each reported with the specific reason,
- locations are shown as an embedded map plus a readout with a copy control and
  a directions link, instead of a raw link into openstreetmap.org,
- a place search resolves a landmark to a pin, and the street name is filled in
  from the coordinates,
- the admin city map lists unmappable reports in a table underneath rather than
  silently dropping them,
- `ReportRequest` on the server now bounds coordinates to the Bengaluru service
  area, so a bad value is rejected rather than stored.

**Administrators no longer touch the garbage photo.** There is no "report"
entry in the admin navigation and `#/report` redirects an administrator to the
dispatch queue. On a report, an administrator can upload only the **cleared
site** photo and mark the cleaning complete; the resident's original photo stays
untouched as the "before" record.

**For field workers**, added:

- an on-duty / off-duty switch,
- a route planner that orders open jobs nearest-first from the worker's current
  position and draws the chain on a map with per-leg distances,
- an arrival check — starting a job more than 120 m from the pin asks for
  confirmation first,
- a running timer on the job in progress,
- a completion step that will not close a job without a photo, with quick-note
  chips for the common cases,
- an offline queue: status changes made without signal are stored on the device
  and replayed when the connection returns,
- a personal record page with median time on site, a day streak and milestones.

**Everywhere:** a drag-to-wipe before/after photo comparison, in-browser image
compression before upload, styled dialogs in place of `alert`/`prompt`/`confirm`,
skeleton loaders, a `/` command palette, and CSV export of the report queue.

## Demo accounts

- Administrator — `admin@cleanbengaluru.com` / `admin123`
- Field worker — `worker1@cleanbengaluru.com` / `worker123`
- Resident — `citizen@cleanbengaluru.com` / `citizen123`

## Notes

- Place search and street names use the public Nominatim service and fail
  quietly when it is unavailable; the map and the coordinates still work.
- Geolocation needs `localhost` or HTTPS. On `file://` the browser blocks it.
