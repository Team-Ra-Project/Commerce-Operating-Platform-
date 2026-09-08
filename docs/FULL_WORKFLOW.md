Full Workflow - Commerce Operating Platform
1. Introduction
This document maps out how the Commerce Operating Platform works, step by step, for every core module — from connecting a marketplace to shipping an order to running a WhatsApp retention campaign. It is written for both the client (to confirm the product does what they expect) and the development team (to build against a shared, unambiguous flow).
Each module below follows the same format: who is involved, a quick flow summary, and a detailed step-by-step walkthrough.
2. User Roles Overview
Business Owner / Admin — full access, manages users, roles, marketplace connections, and billing.
Operations Manager — manages products, inventory, and orders across all connected stores.
Marketing Manager — owns retention campaigns, WhatsApp/Email templates, segmentation, and bulk sharing.
Support / CRM Agent — handles customer profiles, communication history, and order-related queries.
Warehouse Staff — updates stock counts, packs and ships orders.
Analyst / Viewer — read-only access to dashboards, reports, and business intelligence.

3. User Onboarding & Authentication
Purpose: Covers how a new business signs up, verifies their identity, and how team members are invited and assigned roles.
Actors: Business Owner, Invited Team Member, System (Auth Service)
Flow: Sign Up → Verify Email → Setup Business Profile → Invite Team → Assign Roles → Login (JWT)
Steps:
[Business Owner] Signs up with company name, email, and password.
[System] Sends a verification email/OTP; owner confirms email address.
[Business Owner] Completes business profile — industry, primary marketplaces used, business size.
[Business Owner] Invites team members by email and assigns a starting role (Ops, Marketing, Support, Warehouse, Analyst).
[Invited Member] Accepts invite, sets password, logs in.
[System] Issues a JWT session token on every login and applies role-based menu/permission restrictions across the app.

4. Marketplace Integration
Purpose: How a store connects Shopify, Amazon, Flipkart, WooCommerce, Magento, Meesho, or Etsy, and keeps products, inventory, and orders in sync.
Actors: Operations Manager, System (Sync Engine), Marketplace API
Flow: Select Marketplace → Authorize (OAuth/API Key) → Test Connection → Initial Sync → Ongoing Sync
Steps:
[Ops Manager] Selects a marketplace to connect from the integrations screen.
[Ops Manager] Authorizes access — OAuth flow for Shopify/Etsy, API key + secret for Amazon SP-API, Flipkart, Meesho.
[System] Tests the connection and confirms the store is reachable and credentials are valid.
[System] Runs an initial full sync — pulls existing products, stock levels, and open orders into the platform.
[System] Runs scheduled/webhook-based sync going forward, so inventory and order changes stay current on both sides.
[Ops Manager] Can view sync status, last sync time, and error logs per store from a single integrations dashboard.

5. Product Management
Purpose: Adding, editing, and organizing products — including variants, pricing, images, and categories — and pushing them to connected marketplaces.
Actors: Ops Manager, System (Catalog Service)
Flow: Create Product → Add Variants/Images → Set Pricing → Assign Category → Publish to Marketplaces
Steps:
[Ops Manager] Creates a new product with name, description, and base category.
[Ops Manager] Adds variants (size, color, etc.), uploads product images.
[Ops Manager] Sets pricing — can be a single price or per-marketplace pricing.
[System] Validates required fields per target marketplace (e.g., Amazon requires specific attribute sets).
[Ops Manager] Selects which connected marketplaces the product should be published to.
[System] Pushes the product listing to each selected marketplace and reports success/failure per channel.

6. Inventory Management
Purpose: Tracking stock across warehouses and marketplaces, and alerting when stock runs low.
Actors: Warehouse Staff, Ops Manager, System (Inventory Engine)
Flow: Stock Received → Update Count → Auto-Sync to Channels → Threshold Check → Low-Stock Alert
Steps:
[Warehouse Staff] Records incoming stock against a product/variant and warehouse location.
[System] Updates the central stock count and immediately propagates the change to every connected marketplace.
[System] Every stock movement — sale, return, manual adjustment — is checked against the low-stock threshold set for that product.
[System] If stock falls below threshold, triggers a low-stock alert to the Ops Manager (in-app + email).
[Ops Manager] Can view stock by warehouse, reorder recommendations, and export inventory reports.

7. Order Management
Purpose: How orders flow from the moment a customer buys, through fulfillment, to delivery — across every connected marketplace, in one queue.
Actors: Customer (external), Ops Manager, Warehouse Staff, System (Order Engine)
Flow: New Order → Confirmed → Packed → Shipped → Delivered
Steps:
[System] Receives order events from all connected marketplaces in real time and lists them in one unified queue.
[Ops Manager] Reviews and confirms the order; system checks and reserves stock.
[Warehouse Staff] Packs the order and marks it ready for dispatch.
[Ops Manager] Books the courier / generates the shipping label.
[System] Tracks the shipment and updates status through to delivery, notifying the customer at each stage where the marketplace allows it.
Detailed Responsibility Table:
#
Actor
Action
System Response
1
Customer
Places an order on a connected marketplace (e.g., Amazon, Shopify).
Marketplace sends order data to the platform via webhook/API.
2
System
Order is ingested and normalized into the platform's unified order format.
Order appears on the Order Management screen with status "New".
3
Ops Manager
Reviews the order, confirms stock availability.
System reserves inventory for the order automatically.
4
Warehouse Staff
Picks and packs the order.
Status updates to "Packed".
5
Ops Manager
Generates shipping label / hands off to courier.
Status updates to "Shipped"; tracking number is stored.
6
System
Tracks shipment status via courier integration (where available).
Status updates to "Out for Delivery" then "Delivered".
7
Customer
Initiates a return, if needed.
Order enters the Returns workflow (Module 8).


8. Returns & Shipping Management
Purpose: Handling a return or exchange request end-to-end, and keeping refund/replacement status visible.
Actors: Customer, Support Agent, Warehouse Staff, System
Flow: Return Requested → Approved/Rejected → Item Received → Inspected → Refund/Replace
Steps:
[Customer] Requests a return or exchange, either on the marketplace or via the platform's customer portal (where enabled).
[Support Agent] Reviews the request against the return policy and approves or rejects it.
[Warehouse Staff] Receives the returned item and logs its condition.
[System] Updates inventory once the item passes inspection, and triggers a refund or replacement order.
[Support Agent] Closes the case and logs the outcome to the customer's CRM profile.

9. CRM — Customer Management
Purpose: Builds a single customer profile from purchase activity across all marketplaces, and groups customers into segments for marketing.
Actors: Support Agent, Marketing Manager, System
Flow: Order Placed → Profile Created/Updated → Purchase History Logged → Segmented → Available to Marketing
Steps:
[System] Automatically creates or updates a customer profile whenever an order comes in from any connected marketplace.
[System] Logs purchase history, order value, and communication touchpoints against that profile.
[Support Agent] Can view a full customer timeline and respond to queries with context.
[Marketing Manager] Builds segments using rules — e.g., "ordered in last 30 days," "cart abandoned," "high lifetime value," "inactive 90+ days."
[System] Keeps segments live/dynamic — customers move in and out automatically as their behavior changes.
Note: Building customer profiles from marketplace order data must respect each marketplace's PII rules (Amazon in particular locks contact data behind separate approval; Flipkart/Meesho/Etsy access should be verified before assuming it's usable).

10. Competitor Analysis
Purpose: Monitors competitor pricing and listings so the business can react quickly to market changes.
Actors: Ops Manager, System (Monitoring Engine)
Flow: Add Competitor Product → Scheduled Check → Price/Stock Comparison → Alert on Change → Review & Reprice
Steps:
[Ops Manager] Links a competitor's product listing to one of their own products for tracking.
[System] Checks competitor price/availability on a schedule.
[System] Compares against the business's own price and flags meaningful gaps.
[Ops Manager] Reviews the alert and decides whether to adjust pricing.
[System] Logs the pricing history over time for trend analysis.

11. Automation
Purpose: A rule engine that lets the business automate repetitive actions — inventory alerts, order notifications, and retention triggers.
Actors: Ops Manager, Marketing Manager, System (Rule Engine)
Flow: Define Trigger → Define Condition → Define Action → Rule Activated → Runs Automatically
Steps:
[User] Picks a trigger — e.g., "stock below threshold," "order delivered," "cart abandoned for 24 hours."
[User] Optionally adds a condition — e.g., "only for VIP segment" or "only for Fashion category."
[User] Defines the action — e.g., "send low-stock email to Ops," "send WhatsApp thank-you message," "add to retention segment."
[System] Activates the rule and runs it automatically whenever the trigger condition is met.
[User] Can view a run history/log for every automation rule.

12. Analytics & Reporting
Purpose: Sales, revenue, product, inventory, and platform-wise reports for day-to-day decision-making.
Actors: Analyst, Ops Manager, System
Flow: Select Report Type → Filter (Date/Channel/Category) → Generate → View/Export
Steps:
[User] Chooses a report type — sales, revenue, product performance, inventory, or platform-wise breakdown.
[User] Applies filters — date range, marketplace, category, warehouse.
[System] Generates the report from synced order/inventory data.
[User] Views it on-screen as charts/tables, or exports to CSV/PDF.

13. Business Intelligence
Purpose: Sales trend detection, demand forecasting, and recommendations to guide business decisions.
Actors: Business Owner, Analyst, System (BI Engine)
Flow: Historical Data → Trend Detection → Forecast Model → Recommendation → Owner Review
Steps:
[System] Analyzes historical sales, seasonality, and inventory turnover.
[System] Detects trends — top-growing products, declining categories, channel performance shifts.
[System] Produces a demand forecast for the next period per product/category.
[System] Surfaces plain-language recommendations — e.g., "restock X before month-end," "Category Y underperforming on Marketplace Z."
[Business Owner] Reviews recommendations on the Executive Dashboard alongside the business overview.

14. Retention Marketing (New Module)
Purpose: Gives the Marketing Manager tools to keep existing customers engaged and buying again — using segmentation, automated journeys, and outbound messaging over WhatsApp and Email.
Actors: Marketing Manager, System (Retention Engine), Customer (message recipient)
Flow: Pick Segment → Set Goal → Build Journey → Activate → Track & Attribute
Steps:
[Marketing Manager] Selects a target segment from CRM (existing segments) or defines a new rule-based one.
[Marketing Manager] Sets the retention goal for the campaign — win-back, cross-sell, loyalty reward, or replenishment reminder.
[Marketing Manager] Builds a multi-step journey, mixing WhatsApp and Email touchpoints with timing/delays between each (e.g., "Day 0: WhatsApp reminder → Day 2: Email with discount → Day 5: Final WhatsApp nudge").
[System] Validates that message templates are ready and approved before allowing activation.
[Marketing Manager] Activates the journey; system takes over enrollment and sending.
[System] Tracks delivery, engagement, and revenue attribution, and stops the journey early for a customer once they convert.
Detailed Responsibility Table:
#
Actor
Action
System Response
1
Marketing Manager
Selects or builds a customer segment (e.g., "Cart Abandoned – 24h", "VIP – Top 10% spenders", "Lapsed – no order in 90 days").
Shows live segment size and a preview of matching customers.
2
Marketing Manager
Chooses a retention goal — win-back, cross-sell, loyalty reward, or replenishment reminder.
Suggests a relevant campaign template for that goal.
3
Marketing Manager
Builds the customer journey with timed WhatsApp/Email steps.
Shows a visual timeline of the journey before activation.
4
Marketing Manager
Activates the journey.
Automatically enrolls matching customers and sends messages on schedule.
5
Marketing Manager
Monitors performance — sent, delivered, opened/read, clicked, converted to order.
Attributes any resulting order back to the campaign for ROI reporting.


15. WhatsApp & Email Campaigns (New Module)
Purpose: Covers how a single message — or a template-based campaign — actually gets built, approved, and sent over WhatsApp and Email, and how responses come back into the platform.
Note: WhatsApp requires customers to have opted in to receive business messages, and outbound marketing templates must be pre-approved by Meta/the BSP before they can be sent — this is a WhatsApp Business Platform requirement, not optional.
Actors: Marketing Manager, Customer, System (Messaging Engine), WhatsApp Business API / BSP, Email Service Provider (ESP)
Flow: Create Template → Get Approval* → Select Audience → Send/Schedule → Track Engagement
 Approval step applies to WhatsApp templates only; Email does not require external approval.
Steps:
[Marketing Manager] Creates a reusable message template with merge fields for personalization (e.g., {customer_name}, {product_name}, {discount_code}).
[System] For WhatsApp, submits the template to the connected BSP for Meta approval; Email templates skip this step.
[Marketing Manager] Picks the audience — a CRM segment, an uploaded list, or a manual selection — and the channel(s) to send on.
[System] Filters out contacts without valid consent/opt-in before sending.
[Marketing Manager] Sends immediately or schedules for a later time.
[System] Sends through the WhatsApp Cloud API and/or ESP, then tracks delivery and engagement per recipient.
[Customer] Can reply (WhatsApp) or click a link/unsubscribe (Email); both feed back into the CRM profile.
Detailed Responsibility Table:
#
Actor
Action
System Response
1
Marketing Manager
Creates a message template (WhatsApp or Email) with merge fields.
Validates merge fields and, for WhatsApp, flags that the template needs BSP/Meta approval.
2
Marketing Manager
Submits WhatsApp template for approval via the connected BSP.
Polls approval status and notifies once approved or rejected.
3
Marketing Manager
Selects the audience (segment or manual list) and channel — WhatsApp, Email, or both.
De-duplicates recipients and checks opt-in/consent status per contact.
4
Marketing Manager
Schedules or sends immediately.
Queues and sends messages via the WhatsApp Cloud API/BSP and the ESP, respecting rate limits.
5
System
Tracks delivery and engagement events (sent, delivered, read, opened, clicked, replied, bounced).
Dashboard updates in near real time with per-channel performance.
6
Customer
Can reply to a WhatsApp message or click unsubscribe on an email.
Routes WhatsApp replies to Support/CRM inbox; unsubscribes are recorded and honored on future sends.


16. Bulk Sharing / Broadcast (New Module)
Purpose: For sending a promotion, catalog, or update to a large list of customers in one go — either as a WhatsApp broadcast or an email blast — with built-in throttling and consent checks so sends stay compliant.
Actors: Marketing Manager, System (Bulk Send Engine), WhatsApp Business API / BSP, Email Service Provider (ESP)
Flow: Upload/Select List → Choose Content → Pick Channel → Batch & Send → Report
Steps:
[Marketing Manager] Uploads a contact list or selects an existing CRM segment as the recipient list.
[System] Validates the list — removes duplicates, checks for opted-out or invalid contacts.
[Marketing Manager] Selects the content to share — promotional message, product catalog link, or images/attachments.
[Marketing Manager] Chooses WhatsApp, Email, or both, and confirms the bulk send.
[System] Breaks the list into batches and sends progressively, respecting each channel's rate limits.
[Marketing Manager] Reviews the delivery report — sent, delivered, failed, opted-out — once the send completes.
Detailed Responsibility Table:
#
Actor
Action
System Response
1
Marketing Manager
Uploads a contact list (CSV) or selects an existing segment for a bulk send.
Validates the file format and checks each contact against consent/opt-in status.
2
Marketing Manager
Picks what to share — a promo message, a product catalog link, or a set of product images.
Renders a preview of exactly what the recipient will see.
3
Marketing Manager
Chooses the channel — WhatsApp broadcast, Email blast, or both — and confirms the send.
Splits the list into batches to respect WhatsApp/ESP rate limits, avoiding spam flags.
4
System
Sends the batches progressively and shows live progress (sent / delivered / failed).
Failed sends are logged with a reason (invalid number, not opted-in, rate-limited) for retry.
5
Marketing Manager
Reviews the completed bulk-send report.
Stores the report against the campaign for future reference and compliance audit.

Note: Bulk/broadcast messaging over WhatsApp is governed by Meta's commerce and messaging policies (template-only for marketing broadcasts, opt-in required, per-number rate limits) — confirm current BSP terms before build.

17. Settings & Administration
Purpose: Managing users, roles/permissions, marketplace credentials, and system-wide configuration.
Actors: Business Owner / Admin, System
Flow: Manage Users → Assign Roles → Configure Marketplaces → System Settings
Steps:
[Admin] Adds, edits, or deactivates team members.
[Admin] Assigns or changes a user's role, which updates their permissions immediately.
[Admin] Manages marketplace connections and API credentials from a central settings screen.
[Admin] Configures system-wide preferences — currency, time zone, notification defaults, data retention.

18. End-to-End Example: Order to Retention
This walks through one customer's full journey across the platform, showing how the modules above connect in practice.
Flow: Order Placed → Fulfilled → Delivered → CRM Updated → Segmented → Retention Campaign → Repeat Purchase
Steps:
A customer orders on Amazon. The order flows into the platform's unified Order Management queue (Module 7).
Ops Manager and Warehouse Staff fulfill and ship the order (Modules 7–8).
Once delivered, the customer's CRM profile is automatically updated with the new purchase (Module 9).
The automation engine checks segment rules; if the customer now qualifies for "Recent Buyer" or "High Value", they're added to that segment (Modules 9 & 11).
A retention journey targeting that segment enrolls the customer and sends a WhatsApp thank-you message, followed by an email with a reorder discount a few days later (Modules 14–15).
If the customer clicks through and orders again, the campaign is marked as converted and the revenue is attributed back to it in reporting (Modules 12 & 14).

19. Open Questions & Next Steps
Confirm which WhatsApp BSP (e.g., Gupshup, Interakt, Twilio, or direct Meta Cloud API) the platform will integrate with — affects template approval turnaround and pricing.
Confirm email service provider (e.g., SendGrid, Amazon SES, Mailgun) for the Email module.
Confirm retention journey builder scope for v1 — is a visual drag-and-drop journey builder needed, or is a simpler linear multi-step form enough for the first release?
Confirm bulk-send batch size and daily send caps per channel, based on the chosen BSP/ESP plan.
Legal/compliance sign-off needed on consent capture and storage for WhatsApp + Email marketing.

