# COMMERCE OPERATING PLATFORM
## Full-Stack Implementation Roadmap

Version: 1.0
Project Type: Multi-tenant Commerce Operations SaaS
Frontend Status: COMPLETE — UI currently uses dummy/mock data
Database Status: COMPLETE — schema.sql is the source of truth
Backend Status: TO BE IMPLEMENTED
Primary Goal: Convert the existing frontend demo into a fully functional production-ready full-stack application.

====================================================================
1. PROJECT OBJECTIVE
====================================================================

The existing frontend is already implemented and contains the complete
UI/UX for the Commerce Operating Platform.

The objective is NOT to rebuild the frontend.

The objective is to:

1. Connect the existing frontend to a real backend.
2. Connect the backend to the existing MySQL database.
3. Replace dummy/mock/static data with real database data.
4. Implement all workflows defined in:
   - Full Workflow - Commerce Operating Platform
   - Button & Action Documentation — Commerce Operating Platform
5. Implement authentication and RBAC.
6. Implement all required CRUD operations.
7. Implement business logic and status transitions.
8. Implement integrations using real APIs where credentials are available.
9. Implement mock/sandbox adapters where external credentials are not yet available.
10. Implement email functionality through SMTP/ESP.
11. Prepare WhatsApp integration so real credentials can be added later.
12. Implement automation, analytics, BI, retention and bulk messaging.
13. Ensure all modules share the same tenant-scoped data model.
14. Preserve the existing frontend design unless a functional change is
    absolutely necessary.

====================================================================
2. SOURCE OF TRUTH
====================================================================

The following documents/files are authoritative:

1. Full Workflow - Commerce Operating Platform
2. Button & Action Documentation — Commerce Operating Platform
3. database/schema.sql
4. Existing frontend source code

Priority order:

1. schema.sql
2. Full Workflow document
3. Button & Action document
4. Existing frontend implementation

Do NOT invent workflows that conflict with these sources.

Do NOT redesign the application architecture without a clear technical reason.

Do NOT replace the existing frontend.

====================================================================
3. CURRENT PROJECT STATE
====================================================================

FRONTEND:
COMPLETE

The frontend already contains:
- Dashboard
- Marketplace Integration
- Product Management
- Inventory
- Orders
- Returns
- CRM
- Competitor Analysis
- Automation
- Analytics
- Business Intelligence
- Retention Marketing
- WhatsApp & Email Campaigns
- Bulk Sharing / Broadcast
- Settings / Administration

The frontend currently uses dummy/mock data.

DATABASE:
COMPLETE

Database:
commerce_operating_platform

Database schema:
database/schema.sql

The schema contains the core entities required for the application.

BACKEND:
NOT COMPLETE

The backend must now be implemented incrementally according to the phases
defined in this document.

====================================================================
4. CORE ARCHITECTURE
====================================================================

Recommended architecture:

Frontend
    |
    | HTTP/REST API
    v
Backend
    |
    +---- Authentication / Authorization
    |
    +---- Business Services
    |
    +---- Integration Services
    |
    +---- Automation Engine
    |
    +---- Messaging Engine
    |
    +---- Analytics / BI
    |
    v
MySQL Database

External services:

Backend
    |
    +---- Shopify API
    +---- Amazon SP-API
    +---- Flipkart API
    +---- WooCommerce API
    +---- Magento API
    +---- Meesho API
    +---- Etsy API
    +---- Courier APIs
    +---- SMTP / Email Provider
    +---- WhatsApp Cloud API / BSP

====================================================================
5. NON-NEGOTIABLE DEVELOPMENT RULES
====================================================================

RULE 1 — DO NOT REBUILD THE FRONTEND

The existing frontend is considered complete.

Only modify frontend code when required to:
- connect an API
- replace dummy data
- submit real forms
- display backend responses
- handle loading states
- handle errors
- handle authentication
- enforce role-based UI
- support real file uploads
- support real downloads

Do not redesign existing UI.

------------------------------------------------------------

RULE 2 — DATABASE IS THE SOURCE OF TRUTH

Use:

database/schema.sql

Do not create a second unrelated database model.

Do not create duplicate tables for the same business entity.

Do not unnecessarily rename existing tables or columns.

If a required field is genuinely missing:
1. identify the problem
2. explain why it is required
3. make the minimum necessary schema change
4. update migration/documentation

------------------------------------------------------------

RULE 3 — MULTI-TENANCY IS MANDATORY

Every authenticated request must operate within the user's
organization_id.

Never trust organization_id supplied by the frontend.

organization_id must come from the authenticated JWT/user context.

Every business query must be tenant-scoped.

Example:

CORRECT:

SELECT * FROM customer
WHERE organization_id = authenticatedOrganizationId;

INCORRECT:

SELECT * FROM customer;

------------------------------------------------------------

RULE 4 — NEVER USE DUMMY DATA IN PRODUCTION FLOWS

Remove hardcoded/mock data from functional screens.

All functional data must come from:

Frontend
→ Backend API
→ Database

Temporary mock adapters are allowed only for external integrations
where credentials/API access are unavailable.

------------------------------------------------------------

RULE 5 — DO NOT BREAK EXISTING MODULES

When implementing one phase:

- modify only required files
- do not rewrite unrelated modules
- do not replace working components
- do not rename APIs unnecessarily
- do not change database structure unnecessarily

------------------------------------------------------------

RULE 6 — TEST AFTER EACH PHASE

After implementing a phase:

1. compile backend
2. start backend
3. verify database connection
4. test APIs
5. test frontend integration
6. test authorization
7. test error cases
8. verify existing modules still work
9. update CURRENT_STATUS.md

------------------------------------------------------------

RULE 7 — UPDATE DEVELOPMENT STATUS

Maintain:

docs/CURRENT_STATUS.md

After every phase record:

- completed features
- modified files
- database changes
- APIs created
- known issues
- next phase
- testing status

====================================================================
6. STANDARD BACKEND STRUCTURE
====================================================================

Use a clean modular backend architecture.

Recommended:

backend/
    src/
        main/
            java/
                .../
                    config/
                    security/
                    auth/
                    organization/
                    user/
                    marketplace/
                    product/
                    inventory/
                    order/
                    return/
                    customer/
                    competitor/
                    automation/
                    analytics/
                    bi/
                    messaging/
                    campaign/
                    bulk/
                    notification/
                    audit/
                    settings/
                    common/

Each module should preferably contain:

controller/
service/
repository/
entity/
dto/

Do not create unnecessary layers if they provide no value.

====================================================================
7. API DESIGN PRINCIPLES
====================================================================

Use REST APIs.

Example:

GET    /api/customers
GET    /api/customers/{id}
POST   /api/customers
PUT    /api/customers/{id}
DELETE /api/customers/{id}

Use consistent HTTP status codes.

200 = successful request
201 = created
400 = validation error
401 = unauthenticated
403 = unauthorized
404 = not found
409 = conflict
422 = business validation failure
500 = server error

Never expose:
- password_hash
- secret credentials
- API secrets
- JWT secrets
- internal security information

====================================================================
8. ERROR HANDLING
====================================================================

Implement centralized backend error handling.

Return consistent JSON responses.

Example:

{
  "success": false,
  "message": "Customer not found",
  "errorCode": "CUSTOMER_NOT_FOUND"
}

Validation errors should identify the invalid field.

Never expose stack traces to the frontend.

Log technical errors on the server.

====================================================================
9. AUTHENTICATION & SECURITY
====================================================================

Authentication flow:

Sign Up
→ Email Verification
→ Business Profile
→ Login
→ JWT
→ Authorized API requests

Implement:

- password hashing
- JWT authentication
- refresh tokens
- logout/revocation
- email verification
- protected routes
- role-based access control
- inactive/deactivated account blocking

Roles:

BUSINESS_OWNER_ADMIN
OPERATIONS_MANAGER
MARKETING_MANAGER
SUPPORT_CRM_AGENT
WAREHOUSE_STAFF
ANALYST_VIEWER

====================================================================
10. ROLE ACCESS PRINCIPLE
====================================================================

Business Owner/Admin:
Full access.

Operations Manager:
Marketplace
Products
Inventory
Orders
Relevant analytics
Automation

Marketing Manager:
CRM
Segments
Retention
Messaging
Bulk Sharing
Marketing automation

Support/CRM Agent:
Customers
Customer notes
Orders
Returns
Communication history

Warehouse Staff:
Inventory
Packing
Order fulfillment
Returns receiving

Analyst/Viewer:
Read-only dashboards
Analytics
Reports
BI

Backend authorization must enforce these permissions.

Do not rely only on hiding frontend buttons.

====================================================================
11. DEVELOPMENT PHASES
====================================================================


####################################################################
PHASE 0 — PROJECT AUDIT & BACKEND FOUNDATION
####################################################################

Goal:

Understand the existing frontend, database and project structure before
implementing business logic.

Tasks:

1. Inspect the complete frontend.
2. Identify all pages.
3. Identify all buttons/actions.
4. Identify current dummy-data sources.
5. Map frontend actions to required APIs.
6. Inspect schema.sql.
7. Verify database connectivity.
8. Set up backend project.
9. Configure environment variables.
10. Configure CORS.
11. Configure global error handling.
12. Configure logging.
13. Create API base structure.
14. Create health-check endpoint.

Expected result:

GET /api/health

returns:

{
  "status": "UP"
}

Create:

docs/API_MAP.md

Map:

Frontend Page
→ Button
→ API
→ Database Table
→ Required Role

Do NOT implement full business modules yet.

####################################################################
PHASE 1 — DATABASE CONNECTION & ORGANIZATION FOUNDATION
####################################################################

Goal:

Connect backend to MySQL and establish multi-tenant foundation.

Implement:

- organization
- app_user
- system_config

Tasks:

1. Database connection.
2. ORM/JPA configuration.
3. Entity mapping.
4. Repository layer.
5. Organization service.
6. Organization configuration.
7. Tenant context.
8. Authenticated organization resolution.

Test:

Create organization.

Retrieve organization.

Verify data isolation.

####################################################################
PHASE 2 — AUTHENTICATION
####################################################################

Goal:

Implement complete authentication.

Implement:

- registration
- login
- logout
- JWT access token
- refresh token
- token revocation
- password hashing
- email verification
- account status validation

Endpoints should include approximately:

POST /api/auth/register
POST /api/auth/verify-email
POST /api/auth/login
POST /api/auth/refresh
POST /api/auth/logout
GET  /api/auth/me

Connect existing frontend authentication screens.

Acceptance criteria:

A new owner can register.

Owner can verify email.

Owner can login.

JWT is issued.

Protected API rejects unauthenticated requests.

Deactivated user cannot login.

####################################################################
PHASE 3 — USERS, ROLES & ADMINISTRATION
####################################################################

Goal:

Implement Settings → User Management.

Tables:

app_user
audit_log
system_config

Implement:

- add/invite user
- accept invitation
- edit user
- deactivate user
- assign role
- role enforcement
- system configuration

Connect:

Add User
Edit User
Deactivate User
Assign Role
Save System Configuration

Also implement audit logging for administrative actions.

####################################################################
PHASE 4 — EXECUTIVE DASHBOARD
####################################################################

Goal:

Replace dashboard dummy data with real database metrics.

Implement:

- date range filter
- marketplace filter
- revenue summary
- order summary
- inventory summary
- recent activity
- low-stock summary
- dashboard refresh
- drill-down links

Dashboard must calculate data from real tables.

Do NOT use hardcoded numbers.

Implement APIs such as:

GET /api/dashboard/summary
GET /api/dashboard/recent-activity

Connect all existing dashboard widgets.

####################################################################
PHASE 5 — MARKETPLACE INTEGRATION FOUNDATION
####################################################################

Goal:

Implement marketplace connection management.

Tables:

marketplace_connection
marketplace_sync_log

Implement:

- connect marketplace
- credentials storage reference
- test connection
- connection status
- disconnect
- sync log
- sync status
- last sync

Supported:

SHOPIFY
AMAZON
FLIPKART
WOOCOMMERCE
MAGENTO
MEESHO
ETSY

IMPORTANT:

Do not pretend external APIs are connected if credentials are unavailable.

Create an adapter architecture:

MarketplaceService
    |
    +-- ShopifyAdapter
    +-- AmazonAdapter
    +-- FlipkartAdapter
    +-- WooCommerceAdapter
    +-- MagentoAdapter
    +-- MeeshoAdapter
    +-- EtsyAdapter

Use MOCK/SANDBOX adapters where real credentials are unavailable.

The rest of the platform must work against the adapter interface.

####################################################################
PHASE 6 — PRODUCT MANAGEMENT
####################################################################

Goal:

Replace product dummy data with real CRUD.

Tables:

category
product
product_variant
product_marketplace_listing

Implement:

- product list
- product detail
- create product
- edit product
- delete/delist
- variants
- images
- pricing
- category
- marketplace selection
- draft
- publish

Buttons:

Add New Product
Edit Product
Delete Product
Add Variant
Upload Image(s)
Set Pricing
Assign Category
Select Marketplaces
Publish to Marketplaces
Save as Draft

Implement marketplace validation before publishing.

####################################################################
PHASE 7 — INVENTORY MANAGEMENT
####################################################################

Goal:

Implement real inventory management.

Tables:

warehouse
inventory_item
inventory_movement

Implement:

- warehouses
- stock quantity
- reserved quantity
- stock-in
- manual adjustment
- sale reservation
- sale deduction
- return stock
- release reservation
- low-stock threshold
- warehouse filtering
- inventory reports
- low-stock notifications

Buttons:

Update Stock Count
Set Low-Stock Threshold
View by Warehouse
Export Inventory Report
Low-Stock Alert

Inventory changes must create inventory_movement records.

Never silently change stock without recording the movement.

####################################################################
PHASE 8 — ORDER MANAGEMENT
####################################################################

Goal:

Implement unified order management.

Tables:

customer_order
order_item
order_status_history

Implement:

- order ingestion
- order list
- order detail
- status changes
- inventory reservation
- packing
- shipping
- tracking
- status history

Flow:

NEW
→ CONFIRMED
→ PACKED
→ SHIPPED
→ OUT_FOR_DELIVERY
→ DELIVERED

Implement:

View Order
Confirm Order
Mark as Packed
Generate Shipping Label / Book Courier
Track Shipment

Ensure inventory reservation is atomic.

Prevent overselling.

####################################################################
PHASE 9 — RETURNS & SHIPPING
####################################################################

Goal:

Implement return workflow.

Table:

return_request

Flow:

REQUESTED
→ APPROVED_AWAITING_ITEM
→ RECEIVED_INSPECTING
→ REFUNDED / REPLACED
→ CLOSED

Implement:

Approve Return Request
Reject Return Request
Log Item Received
Approve Refund/Replacement
Close Case

Inventory must be updated when returned inventory is accepted.

Customer CRM history must be updated.

####################################################################
PHASE 10 — CRM & CUSTOMER MANAGEMENT
####################################################################

Goal:

Build the unified customer profile.

Table:

customer
customer_note

Customer must be created/updated from incoming orders.

Implement:

- customer list
- customer profile
- purchase history
- order history
- notes
- communication history
- channel information
- opt-in status
- customer status

Buttons:

View Customer Profile
Add Note / Log Communication
Export Customer List

Customer data must remain organization-scoped.

####################################################################
PHASE 11 — CUSTOMER SEGMENTATION
####################################################################

Goal:

Implement dynamic customer segments.

Table:

segment

Implement:

Create Segment
Edit Segment Rules
Delete Segment
Preview Segment
Calculate Segment Size

Initial supported rules:

CART_ABANDONED
VIP
LOYAL
LAPSED
RECENT_BUYER
NEW

Segment membership must be dynamically calculated.

Do not permanently rely on manually stored membership if the workflow
requires live/dynamic evaluation.

Expose:

GET segment size
GET matching customers
POST create segment
PUT update segment
DELETE segment

####################################################################
PHASE 12 — COMPETITOR ANALYSIS
####################################################################

Tables:

competitor_tracked_product
competitor_price_history

Implement:

- add competitor
- link own product
- scheduled price checks
- price history
- alert threshold
- price gap detection
- reprice workflow

Buttons:

Add Competitor Product
Link to Own Product
View Price History
Set Alert Threshold
Reprice Product

If automatic external scraping/API access is unavailable:

Implement provider/adapter architecture and mock provider.

Do not implement unauthorized scraping where marketplace rules prohibit it.

####################################################################
PHASE 13 — AUTOMATION ENGINE
####################################################################

Tables:

automation_rule
automation_run_log

Implement:

Create New Rule
Select Trigger
Add Condition
Define Action
Activate/Deactivate
View Run Log
Edit Rule
Delete Rule

Initial triggers:

- stock below threshold
- order delivered
- cart abandoned
- return update
- campaign event

Initial actions:

- send email
- send WhatsApp
- create notification
- add to segment

Architecture:

Event
→ Rule Engine
→ Matching Rules
→ Conditions
→ Action
→ Execution Log

All executions must be logged.

####################################################################
PHASE 14 — NOTIFICATIONS & AUDIT
####################################################################

Tables:

notification
audit_log

Implement:

- notification creation
- unread count
- mark read
- notification list
- notification navigation
- audit events

Events:

low stock
sync error
order update
return update
campaign
automation
integration

Connect notification bell.

####################################################################
PHASE 15 — ANALYTICS & REPORTING
####################################################################

Goal:

Implement real reports.

Reports:

- sales
- revenue
- product performance
- inventory
- marketplace/platform performance

Filters:

- date
- marketplace
- category
- warehouse

Implement:

Select Report Type
Apply Filters
Generate Report
Export CSV
Export PDF

All reports must query real database data.

####################################################################
PHASE 16 — BUSINESS INTELLIGENCE
####################################################################

Tables:

bi_insight
bi_forecast_point

Implement calculation-based BI first.

Do not require a complex AI model for v1.

Implement:

- trend detection
- product performance trends
- category trends
- marketplace trends
- demand forecast
- recommendations

Buttons:

View Trend Details
View Forecast
View Recommendation
Acknowledge/Dismiss Recommendation

Examples:

"Product X sales increased 24%."

"Inventory for Product Y may run low within 10 days."

"Marketplace Z has declining performance."

The database stores the generated results.

Analytics engine calculates them.

####################################################################
PHASE 17 — EMAIL INFRASTRUCTURE
####################################################################

Goal:

Create reusable email infrastructure.

Support:

SMTP

Configuration through environment variables.

Example:

MAIL_HOST
MAIL_PORT
MAIL_USERNAME
MAIL_PASSWORD
MAIL_FROM

Never store SMTP passwords in the database as plain text.

Implement reusable:

EmailService

Capabilities:

- send verification email
- send invitation
- send low-stock alert
- send order notifications
- send invoice/transactional emails when required
- send marketing email

Track sending result.

####################################################################
PHASE 18 — WHATSAPP INFRASTRUCTURE
####################################################################

Goal:

Prepare WhatsApp integration.

Support architecture for:

Meta WhatsApp Cloud API
or selected BSP

Implement adapter:

WhatsAppProvider

Functions:

sendTemplateMessage()
sendMessage()
submitTemplate()
getTemplateStatus()
handleWebhook()

IMPORTANT:

The application must work even when WhatsApp credentials are not configured.

Use:

WHATSAPP_MODE=MOCK

for development/testing.

When real credentials are supplied:

WHATSAPP_MODE=REAL

No major application rewrite should be necessary.

####################################################################
PHASE 19 — MESSAGE TEMPLATES
####################################################################

Table:

message_template

Implement:

Create Template
Edit Template
Delete Template
Submit for Approval
View Approval Status

WhatsApp:

DRAFT
→ PENDING_APPROVAL
→ APPROVED / REJECTED

Email:

DRAFT
→ APPROVED/READY

Validate merge fields.

Supported examples:

{customer_name}
{product_name}
{discount_code}

####################################################################
PHASE 20 — RETENTION MARKETING
####################################################################

Tables:

campaign
campaign_step
campaign_enrollment
message_log

Implement:

Create New Campaign/Journey
Select Segment
Set Retention Goal
Add Journey Step
Preview Journey
Activate Journey
Pause/Stop Journey
View Campaign Performance

Goals:

WIN_BACK
CROSS_SELL
LOYALTY_REWARD
REPLENISHMENT_REMINDER

Journey example:

Day 0:
WhatsApp

Day 2:
Email

Day 5:
WhatsApp

Implement enrollment.

Implement step scheduling.

Implement conversion detection.

Stop journey after conversion.

Track:

sent
delivered
read/opened
clicked
converted
revenue attributed

####################################################################
PHASE 21 — WHATSAPP & EMAIL CAMPAIGNS
####################################################################

Implement:

Select Audience
Send Now
Schedule Send
Delivery Tracking
Engagement Tracking

WhatsApp:

- opt-in validation
- approved template validation
- provider sending
- webhook handling

Email:

- SMTP/ESP sending
- unsubscribe handling
- bounce handling where provider supports it

Message events must update message_log.

####################################################################
PHASE 22 — BULK SHARING / BROADCAST
####################################################################

Tables:

bulk_send
bulk_send_item

Implement:

Upload Contact List
Select Segment
Choose Content
Select Channels
Send Bulk Broadcast
View Bulk Send Report

Support:

CSV upload
deduplication
email validation
phone validation
WhatsApp opt-in check
email opt-out check
batch processing
rate limiting
failure logging
retry support

Channels:

WHATSAPP
EMAIL
BOTH

Do not send marketing WhatsApp messages without valid consent and
approved templates where required.

####################################################################
PHASE 23 — MARKETPLACE DATA SYNC ENGINE
####################################################################

Goal:

Implement real synchronization infrastructure.

Sync:

Products
Inventory
Orders

Implement:

initial sync
scheduled sync
manual sync
webhook handling
sync logs
error handling
retry

Architecture:

SyncScheduler
SyncService
MarketplaceAdapter
SyncLogService

Prevent duplicate order ingestion.

Use external marketplace order IDs/idempotency.

####################################################################
PHASE 24 — ORDER & INVENTORY EVENT SYSTEM
####################################################################

Goal:

Connect modules together.

Important event flows:

ORDER_CREATED
→ CRM update
→ inventory reservation
→ notification

ORDER_CONFIRMED
→ warehouse queue

ORDER_SHIPPED
→ tracking

ORDER_DELIVERED
→ CRM update
→ segmentation
→ automation
→ retention eligibility

RETURN_RECEIVED
→ inventory update

LOW_STOCK
→ notification
→ automation

CAMPAIGN_CONVERTED
→ campaign metrics
→ revenue attribution

This phase is critical because it turns independent modules into
one connected platform.

####################################################################
PHASE 25 — END-TO-END ORDER → RETENTION WORKFLOW
####################################################################

Implement the complete workflow:

Amazon/Shopify/etc.
        ↓
Order received
        ↓
Order normalized
        ↓
Customer created/updated
        ↓
Inventory reserved
        ↓
Order confirmed
        ↓
Packed
        ↓
Shipped
        ↓
Delivered
        ↓
CRM updated
        ↓
Segment recalculated
        ↓
Retention campaign eligibility
        ↓
Customer enrolled
        ↓
WhatsApp/Email journey
        ↓
Customer clicks
        ↓
Repeat order
        ↓
Conversion detected
        ↓
Campaign attributed revenue
        ↓
Analytics updated

Test this workflow end-to-end.

####################################################################
PHASE 26 — FRONTEND DUMMY DATA REMOVAL
####################################################################

After backend modules are functional:

Audit every frontend page.

Remove:

- hardcoded products
- fake customers
- fake orders
- fake dashboard statistics
- fake campaigns
- fake reports
- fake notifications
- fake integration status

Replace all with API calls.

Implement:

loading states
empty states
error states
success messages
pagination where required
filters
search
sorting

####################################################################
PHASE 27 — RBAC FRONTEND INTEGRATION
####################################################################

Connect backend permissions to frontend.

Examples:

Warehouse Staff:
Cannot see/administer marketing.

Marketing Manager:
Cannot modify warehouse stock unless explicitly allowed.

Analyst:
Read-only.

Admin:
Full access.

IMPORTANT:

Frontend restrictions are UX only.

Backend authorization remains the actual security boundary.

####################################################################
PHASE 28 — SECURITY HARDENING
####################################################################

Review:

- authentication
- authorization
- tenant isolation
- password security
- JWT handling
- refresh tokens
- CORS
- SQL injection protection
- input validation
- file upload security
- API rate limiting
- secret management
- logging
- audit logs

Never expose credentials.

Never trust frontend organization_id.

Never trust frontend role.

####################################################################
PHASE 29 — TESTING & QA
####################################################################

Test every module.

Testing levels:

1. Database tests
2. Repository tests
3. Service tests
4. API tests
5. Authentication tests
6. RBAC tests
7. Multi-tenant isolation tests
8. Frontend integration tests
9. End-to-end workflow tests

Minimum critical tests:

- registration
- login
- JWT
- role restrictions
- tenant isolation
- product CRUD
- inventory update
- order confirmation
- inventory reservation
- order shipping
- return
- CRM update
- segment evaluation
- campaign enrollment
- email sending
- WhatsApp mock sending
- bulk send
- dashboard metrics

####################################################################
PHASE 30 — PRODUCTION READINESS
####################################################################

Implement:

- environment-based configuration
- production database configuration
- secure secrets
- database migration strategy
- logging
- error monitoring
- backups
- scheduled jobs
- API documentation
- deployment configuration

Create:

README.md

Include:

1. Installation
2. Database setup
3. Environment variables
4. Backend startup
5. Frontend startup
6. API documentation
7. External integrations
8. Mock mode
9. Production mode
10. Troubleshooting

====================================================================
12. EXTERNAL INTEGRATION STRATEGY
====================================================================

The application must use an adapter architecture.

Never hard-code marketplace-specific logic throughout the application.

Example:

interface MarketplaceAdapter {

    testConnection()

    syncProducts()

    syncInventory()

    syncOrders()

    publishProduct()

    updateInventory()

    updateOrderStatus()

}

Implement separate adapters.

If credentials are unavailable:

Use MockMarketplaceAdapter.

This allows the complete platform to be developed and tested before
real API credentials are received.

When credentials become available, configure the real adapter.

====================================================================
13. MOCK MODE
====================================================================

Development must support mock mode.

Examples:

MARKETPLACE_MODE=MOCK
WHATSAPP_MODE=MOCK
EMAIL_MODE=SMTP

Mock mode should simulate:

successful connection
product sync
inventory sync
order sync
message sending
delivery status
template approval

BUT:

Mock mode must still use the real backend and database.

Do not return fake data directly from frontend JavaScript.

The flow must remain:

Frontend
→ Backend
→ Mock Adapter
→ Backend
→ Database
→ Frontend

====================================================================
14. DATABASE RULES
====================================================================

Use the existing schema.sql.

Important entities include:

organization
app_user
refresh_token

marketplace_connection
marketplace_sync_log

category
product
product_variant
product_marketplace_listing

warehouse
inventory_item
inventory_movement

customer
customer_note
segment

customer_order
order_item
order_status_history

return_request

competitor_tracked_product
competitor_price_history

automation_rule
automation_run_log

bi_insight
bi_forecast_point

message_template
campaign
campaign_step
campaign_enrollment
message_log

bulk_send
bulk_send_item

notification
audit_log
system_config

Do not duplicate these entities.

====================================================================
15. CRITICAL BUSINESS RULES
====================================================================

RULE:

One organization → one shared data model.

RULE:

One customer should represent the same customer across supported
channels where matching can be safely established.

RULE:

Orders reference customers.

RULE:

Orders reference product variants through order_items.

RULE:

Inventory belongs to product variants and warehouses.

RULE:

Inventory movements must be recorded.

RULE:

Order confirmation must reserve inventory.

RULE:

Order cancellation/relevant status changes must release reservations
when applicable.

RULE:

Delivery updates CRM.

RULE:

Returns affect inventory when approved/received according to business
rules.

RULE:

Marketing messages require consent.

RULE:

WhatsApp marketing requires appropriate approved templates.

RULE:

Campaign conversion must be attributable to the campaign.

RULE:

Every organization must be isolated from every other organization.

RULE:

Deactivated users cannot access the platform.

====================================================================
16. AI DEVELOPMENT RULES
====================================================================

The AI coding agent MUST work phase-by-phase.

When the user says:

"Implement Phase X"

ONLY implement Phase X.

Do not automatically implement future phases.

Do not rewrite previously completed phases.

Before coding:

1. Read this roadmap.
2. Read docs/CURRENT_STATUS.md.
3. Read relevant workflow section.
4. Read relevant button/action section.
5. Inspect existing implementation.
6. Inspect relevant database tables.

Then implement.

After coding:

1. Compile.
2. Fix errors.
3. Run tests.
4. Verify APIs.
5. Verify frontend integration.
6. Update CURRENT_STATUS.md.
7. Report completed work.
8. Report remaining issues.
9. STOP.

====================================================================
17. PHASE COMPLETION FORMAT
====================================================================

After every phase update:

docs/CURRENT_STATUS.md

Use:

# Current Status

## Completed Phase
Phase X — Name

## Completed
- item
- item
- item

## Backend APIs
- endpoint
- endpoint

## Database Tables Used
- table
- table

## Frontend Connected
- page
- page

## Tests
- passed
- passed

## Known Issues
- issue

## Next Phase
Phase X+1 — Name

====================================================================
18. ACCEPTANCE CRITERIA
====================================================================

A phase is NOT considered complete merely because code was generated.

A phase is complete only when:

[ ] Backend compiles
[ ] Database connection works
[ ] APIs work
[ ] Authentication works where applicable
[ ] RBAC works where applicable
[ ] Tenant isolation works
[ ] Frontend is connected
[ ] Dummy data is removed for that feature
[ ] Error handling works
[ ] Loading/empty states work
[ ] Database records are correctly created/updated
[ ] Existing features still work
[ ] Phase tests pass
[ ] CURRENT_STATUS.md updated

====================================================================
19. FINAL DEFINITION OF DONE
====================================================================

The Commerce Operating Platform is complete when:

[ ] Authentication works
[ ] Multi-tenancy works
[ ] RBAC works
[ ] Dashboard uses real data
[ ] Marketplace connections work
[ ] Product management works
[ ] Inventory works
[ ] Order management works
[ ] Returns work
[ ] CRM works
[ ] Segmentation works
[ ] Competitor analysis works
[ ] Automation works
[ ] Notifications work
[ ] Analytics works
[ ] Business Intelligence works
[ ] Email works
[ ] WhatsApp integration architecture works
[ ] Retention journeys work
[ ] Campaign tracking works
[ ] Bulk sharing works
[ ] Marketplace sync works
[ ] Event-driven workflows work
[ ] Frontend no longer depends on dummy data
[ ] All critical buttons perform real actions
[ ] All role permissions are enforced
[ ] Tenant isolation is verified
[ ] Audit logging works
[ ] Error handling is implemented
[ ] Production configuration is documented
[ ] README is complete

====================================================================
20. IMPORTANT INSTRUCTION TO THE AI
====================================================================

THIS IS AN INCREMENTAL IMPLEMENTATION PROJECT.

DO NOT TRY TO BUILD THE ENTIRE APPLICATION IN ONE RESPONSE OR ONE
CODING SESSION.

The user will explicitly tell you which phase to implement.

When a phase is provided:

1. Read this roadmap.
2. Read the existing project.
3. Read schema.sql.
4. Read the relevant workflow.
5. Read the relevant button/action requirements.
6. Read CURRENT_STATUS.md.
7. Implement ONLY the requested phase.
8. Reuse existing code where possible.
9. Do not rebuild the frontend.
10. Do not create duplicate functionality.
11. Do not use frontend mock data for completed functionality.
12. Test the implementation.
13. Fix compilation/runtime errors.
14. Update CURRENT_STATUS.md.
15. Provide a concise implementation report.
16. STOP.

Never skip directly to later phases.

Never assume external credentials exist.

Use mock adapters for unavailable external services.

The final application must preserve the existing UI/UX while replacing
dummy functionality with real backend-driven functionality.

====================================================================
END OF ROADMAP
====================================================================