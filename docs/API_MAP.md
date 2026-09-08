# API map

This map describes the Java/Spring API used by the canonical static frontend. Every authenticated business request derives its organization from the JWT; organization IDs are never accepted from the browser.

| Frontend area | Action | API | Source table(s) | Role |
|---|---|---|---|---|
| Login | Register workspace | `POST /api/auth/register` | `organization`, `app_user`, `system_config` | Public |
| Login | Verify email | `POST /api/auth/verify-email` | `app_user` | Public |
| Login | Sign in / refresh / sign out / current user | `POST /api/auth/login`, `/refresh`, `/logout`; `GET /me` | `app_user`, `refresh_token` | Public / authenticated |
| All authenticated pages | Health / session guard | `GET /api/health`; bearer JWT | — | Public / authenticated |
| Settings | Read organization/configuration | `GET /api/organization`, `GET /api/organization/config` | `organization`, `system_config` | Any authenticated user |
| Settings | Save configuration | `PUT /api/organization/config` | `system_config` | Business Owner/Admin |
| Dashboard | Filters, refresh, drill-down | `GET /api/dashboard/summary`, `/recent-activity` | orders, inventory, notifications | View roles |
| Marketplace | Connect, test, sync, logs, disconnect | `/api/marketplaces`, `/api/marketplaces/{name}/logs` | `marketplace_connection`, `marketplace_sync_log` | Ops/Admin |
| Products | List/detail/create/edit/delete/publish | `/api/products`, `/api/products/{id}/variants`, `/listings` | product, category, variants, listings | Ops/Admin |
| Inventory | Adjust, threshold, warehouse report/export | `/api/inventory`, `/api/inventory/movements`, `/warehouses` | inventory_item, inventory_movement, warehouse | Warehouse/Ops/Admin |
| Orders | Ingest, list/detail, confirm/pack/ship/track | `/api/orders`, `/api/orders/{id}/confirm|pack|ship|track` | customer_order, order_item, order_status_history | Warehouse/Ops/Admin |
| Returns | Create, approve/reject, receive, resolve/close | `/api/returns` | return_request, inventory_movement | Support/Warehouse/Admin |
| CRM | Customers, notes, rule-based segments | `/api/customers`, `/api/customers/{id}/notes`, `/api/segments` | customer, customer_note, segment | Support/Marketing/Admin |
| Competitors | Track, alert, reprice/history | `/api/competitors`, `/api/competitors/{id}/history` | competitor_tracked_product, competitor_price_history | Ops/Admin |
| Automation | Create/edit/toggle/delete/log | `/api/automation/rules`, `/run-log` | automation_rule, automation_run_log | Ops/Marketing/Admin |
| Notifications | List, unread count, read/read-all | `/api/notifications` | notification | Authenticated |
| Analytics | Filtered report and CSV export | `/api/analytics/report`, `/api/analytics/report.csv` | orders, order_item, products, inventory | Analyst/Ops/Admin |
| BI | Live insight/forecast overview and dismiss | `/api/bi/overview`, `/api/bi/recommendations/{id}/dismiss` | bi_insight, bi_forecast_point | Analyst/Ops/Admin |
| Retention / Messaging | Campaigns, templates, approval, journey messages | `/api/campaigns`, `/api/templates`, `/api/campaigns/{id}/messages` | campaign, steps, enrollments, templates, message_log | Marketing/Admin |
| Broadcasts | Static roadmap UI only | No Phase 1–20 backend endpoint | `bulk_send`, `bulk_send_item` | Deferred |
| User administration | Invite/edit/deactivate/roles | `/api/users` | app_user, audit_log | Admin |

All business endpoints must derive the organization from the JWT, never from a client-supplied organization ID.
