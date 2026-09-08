# Current status

Last updated: 2026-09-02

## Delivered scope

`Commerce_Operating_Platform` is the merged Phase 0–20 source project:

- `frontend/` — canonical static HTML5/CSS3/Vanilla JavaScript client
- `backend/` — Spring Boot 3 / Java 17 REST API
- `database/schema.sql` — MySQL 8 schema
- `docs/` — API, button/action, workflow, roadmap, and setup references

The Phase 18–20 implementation is layered onto the cumulative Phase 0–10
implementation. Restored Phase 12–14 backend packages provide automation,
competitor monitoring, and notifications. Analytics and BI now have live,
tenant-scoped read APIs, and the corresponding frontend pages consume those
APIs instead of generated report data.

## Live backend modules

Authentication and workspace setup, user roles, dashboard, marketplaces,
products/categories/variants, inventory/warehouses, orders, returns, CRM
customers/notes, rule-based segments, competitor tracking, automation rules,
notifications, analytics reports, BI results, WhatsApp provider abstraction,
message templates, and retention campaigns are implemented under
`backend/src/main/java/com/rastudio/commerce`.

The default marketplace and WhatsApp providers are sandbox adapters. They
require no external credentials and make the complete local workflow
exercisable. Production credentials can be supplied through environment
variables; see `backend/.env.example`.

## Canonical frontend status

The static frontend is the deliverable client. Dashboard, products, inventory,
orders, returns, customer CRM, segments, marketplaces, settings, competitors,
automation, notifications, analytics, BI, message templates, and retention
journeys use the Java API. Loading, empty, and API-error states are shown
where data is asynchronous. Login no longer contains prefilled credentials.

The standalone React artifact under `artifacts/commerce-platform` is not part
of this ZIP and is not the canonical client for this merged Java project.

## Deliberately deferred

The existing `frontend/broadcasts/` pages and one-off messaging blast pages
are roadmap UI for later phases. Their `bulk_send` tables do not have a
backend service in the Phase 1–20 source, so they are clearly marked as
deferred in `docs/API_MAP.md` rather than pretending to send messages.

Courier tracking and marketplace synchronization use honest local sandbox
adapters. Real provider credentials and provider-specific webhook correlation
are optional extensions, not hidden mock data presented as live integrations.

## Verification

Run these checks from the project root:

```bash
cd backend
mvn -q test
mvn -q -DskipTests package

cd ../frontend
find . -name '*.js' -print0 | xargs -0 -n1 node --check
```

Before starting Spring Boot, create a MySQL 8 database and apply
`database/schema.sql`. The backend uses `ddl-auto: validate`, so missing or
incompatible schema objects fail at startup instead of being silently created.

No live database credentials are included in this deliverable. Copy
`backend/.env.example` to the environment used to run the API and provide
local values through the runtime secret/environment mechanism.