# Commerce Operating Platform

Merged Java/Spring/MySQL commerce operations project covering the Phase 0–20
implementation described in `docs/IMPLEMENTATION_ROADMAP.md`.

## Project layout

- `frontend/` — static HTML5/CSS3/Vanilla JavaScript client
- `backend/` — Spring Boot REST API
- `database/schema.sql` — MySQL 8 schema
- `docs/` — setup, workflow, API, and implementation notes

## Local setup

1. Create a MySQL 8 database.
2. Apply `database/schema.sql`.
3. Configure the backend using the variables in `backend/.env.example`.
4. Start the backend with Maven from `backend/`.
5. Serve `frontend/` from a static web server. Set the API base URL expected by
   `frontend/js/api/api.js` if the backend is not on the default local URL.

The default marketplace and WhatsApp modes are sandbox/mock modes. No provider
credentials are required for local demos. Real provider modes are opt-in via
environment variables.

## Checks

```bash
cd backend && mvn -q test
cd ../frontend && find . -name '*.js' -print0 | xargs -0 -n1 node --check
```

See `docs/CURRENT_STATUS.md` for the delivered scope and known deferred
roadmap pages.