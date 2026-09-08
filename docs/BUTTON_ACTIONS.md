Button & Action Documentation — Commerce Operating Platform
Derived from the SRS/PRD and Full Workflow Document already shared. Organized by module. "Role Required" reflects the RBAC roles defined in the workflow doc (Business Owner/Admin, Ops Manager, Marketing Manager, Support/CRM Agent, Warehouse Staff, Analyst/Viewer).

1. Executive Dashboard
Button/Action
Where It Appears
Purpose
What Happens on Click
Next Step Triggered
Role Required
Date Range Filter
Top of dashboard
Change the time window for all dashboard widgets
Opens a date picker; on selection, all widgets (revenue, orders, inventory) refresh for that range
Dashboard data reloads in place
All roles (view)
Marketplace/Store Filter
Top of dashboard
View data for one store or all connected stores combined
Opens dropdown of connected marketplaces; selecting one filters all widgets
Dashboard data reloads in place
All roles (view)
Refresh Dashboard
Top-right corner
Pull the latest synced data on demand
Triggers a re-fetch of dashboard metrics from the database (not a live marketplace re-sync)
Widgets update with latest cached values
All roles (view)
View All Orders (drill-down link on Order Summary widget)
Order Summary card
Jump from the summary count into the full order queue
Navigates to Order Management, pre-filtered to match the widget's current date/store filter
Opens Order Management (Module 7)
Ops Manager, Admin
View All Products / Inventory Status (drill-down link)
Inventory Status card
Jump from stock summary into full inventory
Navigates to Inventory Management, pre-filtered to low-stock or as shown
Opens Inventory Management (Module 6)
Ops Manager, Admin
View Full Revenue Report (drill-down link)
Revenue Summary card
See detailed revenue breakdown
Navigates to Analytics & Reporting with revenue report pre-selected
Opens Analytics & Reporting (Module 12)
Ops Manager, Analyst, Admin
Recent Activity Item
Recent Activities feed
Investigate a specific event (new order, low stock, sync error)
Clicking the activity line navigates to the relevant record (order, product, integration log)
Opens the relevant module/record
All roles (view)


2. Marketplace Integration
Button/Action
Where It Appears
Purpose
What Happens on Click
Next Step Triggered
Role Required
Connect Marketplace
Integrations screen
Start connecting a new store (Shopify, Amazon, Flipkart, WooCommerce, Magento, Meesho, Etsy)
Opens the marketplace selection list
Proceeds to Authorize step
Ops Manager, Admin
Authorize / Connect
Marketplace setup wizard
Grant the platform access to the marketplace store
For OAuth marketplaces (Shopify, Etsy): redirects to marketplace login/consent screen. For API-key marketplaces (Amazon, Flipkart, Meesho): submits entered API key/secret
System tests the connection
Ops Manager, Admin
Test Connection
Marketplace setup wizard / Integrations list
Confirm credentials are valid and the store is reachable
Sends a test API call to the marketplace; shows success/failure status
On success → triggers Initial Sync; on failure → shows error and lets user re-enter credentials
Ops Manager, Admin
Sync Now
Integrations list (per connected store)
Manually force a re-sync outside the scheduled/webhook cycle
Triggers an on-demand pull of products, inventory, and orders from that marketplace
Updates product/inventory/order data; sync status and timestamp update
Ops Manager, Admin
View Sync Log / Error Log
Integrations list (per store)
Diagnose why a sync failed or check sync history
Opens a log panel showing recent sync attempts, timestamps, and error messages
None (informational)
Ops Manager, Admin
Disconnect Store
Integrations list (per store)
Remove a marketplace connection
Prompts for confirmation, then revokes stored credentials and stops future syncing
Store moves to "disconnected" state; existing synced data remains but no longer updates
Admin


3. Product Management
Button/Action
Where It Appears
Purpose
What Happens on Click
Next Step Triggered
Role Required
Add New Product
Products list (top-right)
Start creating a new product
Opens the product creation form
Proceeds to product detail entry
Ops Manager, Admin
Edit Product
Product row / product detail page
Modify an existing product's details
Opens the product form pre-filled with current data
Saves changes on submit
Ops Manager, Admin
Delete Product
Product row / product detail page
Remove a product from the catalog
Prompts for confirmation, then removes the product from the platform catalog
Product is delisted; does not automatically delist from marketplaces unless explicitly confirmed
Ops Manager, Admin
Add Variant
Product detail page
Add size/color/etc. variants to a product
Opens a variant sub-form (name, SKU, price override, stock)
New variant row appears under the product
Ops Manager, Admin
Upload Image(s)
Product detail page
Attach product photos
Opens file picker; uploads and attaches image(s) to the product
Image thumbnail(s) appear on the product
Ops Manager, Admin
Set Pricing
Product detail page
Define product price — single or per-marketplace
Opens pricing fields; can toggle "same price everywhere" vs. per-channel pricing
Price saved against the product/variant
Ops Manager, Admin
Assign Category
Product detail page
Organize product under a category
Opens category dropdown/selector
Product tagged with selected category
Ops Manager, Admin
Select Marketplaces to Publish
Product detail page
Choose which connected stores should list this product
Checkbox list of connected marketplaces
Enables the Publish action for selected channels
Ops Manager, Admin
Publish to Marketplaces
Product detail page
Push the product listing live on selected channels
System validates required fields per marketplace, then pushes the listing
Reports success/failure per marketplace; product status updates to "Published" or shows per-channel errors
Ops Manager, Admin
Save as Draft
Product detail page
Save incomplete product without publishing
Saves the product record without pushing to any marketplace
Product remains in "Draft" status
Ops Manager, Admin


4. Inventory Management
Button/Action
Where It Appears
Purpose
What Happens on Click
Next Step Triggered
Role Required
Update Stock Count
Inventory list / product-variant row
Record incoming stock or manual adjustment
Opens a quantity input tied to a product/variant and warehouse location
Central stock count updates and auto-syncs to all connected marketplaces
Warehouse Staff, Ops Manager
Set Low-Stock Threshold
Product/variant inventory settings
Define at what quantity a low-stock alert should fire
Opens a numeric input field per product/variant
Threshold saved; used by the alert-check process going forward
Ops Manager, Admin
View by Warehouse
Inventory list (filter/toggle)
See stock broken down by warehouse location
Filters the inventory table by selected warehouse
Table re-renders with warehouse-specific counts
Ops Manager, Warehouse Staff, Admin
Export Inventory Report
Inventory list (top-right)
Download current stock data
Generates and downloads a CSV/PDF of current inventory
File download starts
Ops Manager, Analyst, Admin
Low-Stock Alert (notification click)
Notification bell / alert feed
Jump to a product that has crossed its low-stock threshold
Navigates to that product's inventory detail
Opens Product/Inventory detail page
Ops Manager, Admin


5. Order Management
Button/Action
Where It Appears
Purpose
What Happens on Click
Next Step Triggered
Role Required
View Order
Order queue row
Open full order details
Navigates to the order detail page (items, customer, shipping address, status history)
None (informational)
Ops Manager, Support Agent, Admin
Confirm Order
Order detail page (status = "New")
Accept the order and move it into fulfillment
System checks and reserves inventory for the ordered items
Order status changes to "Confirmed"; appears in Warehouse Staff's pack queue
Ops Manager, Admin
Mark as Packed
Order detail page (status = "Confirmed")
Indicate the order has been picked and packed
Updates order status
Order status changes to "Packed"; ready for shipping label
Warehouse Staff
Generate Shipping Label / Book Courier
Order detail page (status = "Packed")
Create the shipment and get a tracking number
Calls the courier integration (or opens manual entry if not integrated) to generate a label
Order status changes to "Shipped"; tracking number stored and (where supported) pushed back to the marketplace
Ops Manager, Admin
Track Shipment
Order detail page (status = "Shipped"/"Out for Delivery")
Check current delivery status
Opens/refreshes tracking info from the courier integration
Status updates automatically as courier data changes
Ops Manager, Support Agent, Admin

5a. Returns Management
Button/Action
Where It Appears
Purpose
What Happens on Click
Next Step Triggered
Role Required
Approve Return Request
Returns queue
Accept a customer's return/exchange request
Marks the return as approved; generates return instructions
Return status changes to "Approved – Awaiting Item"; customer is notified where the channel supports it
Support Agent
Reject Return Request
Returns queue
Deny a return request that doesn't meet policy
Prompts for a rejection reason, then closes the request
Return status changes to "Rejected"; customer notified
Support Agent
Log Item Received
Return detail page
Record that the returned item has arrived at the warehouse
Opens a condition/inspection form
Return status changes to "Received – Inspecting"
Warehouse Staff
Approve Refund/Replacement
Return detail page (post-inspection)
Finalize the return outcome
Triggers inventory update (if item passes inspection) and initiates refund or replacement order
Refund is issued or a new replacement order is created; CRM profile logs the outcome
Support Agent
Close Case
Return detail page
Mark the return as fully resolved
Closes the return record
Case removed from active Returns queue
Support Agent


6. CRM — Customer Management
Button/Action
Where It Appears
Purpose
What Happens on Click
Next Step Triggered
Role Required
View Customer Profile
Customer list row
Open a customer's full record
Navigates to the customer profile (purchase history, communications, segment membership)
None (informational)
Support Agent, Marketing Manager, Admin
Add Note / Log Communication
Customer profile page
Record a support interaction or note
Opens a text entry field, saves to the customer's timeline
Note appears in the customer's activity history
Support Agent
Create Segment
Segments screen
Define a new customer group for marketing/analysis
Opens the segment rule builder (e.g., "ordered in last 30 days," "cart abandoned," "inactive 90+ days")
New segment saved and evaluated live against the customer database
Marketing Manager, Admin
Edit Segment Rules
Segment detail page
Modify an existing segment's criteria
Opens the rule builder pre-filled with current rules
Segment membership recalculates automatically
Marketing Manager, Admin
Delete Segment
Segment list
Remove a segment no longer in use
Prompts for confirmation, then deletes the segment
Any active campaigns tied to it are flagged
Marketing Manager, Admin
Export Customer List
Customer list / segment detail
Download customer data for external use
Generates and downloads a CSV of the filtered list
File download starts
Marketing Manager, Admin


7. Competitor Analysis
Button/Action
Where It Appears
Purpose
What Happens on Click
Next Step Triggered
Role Required
Add Competitor Product
Competitor Analysis screen
Start tracking a competitor's listing
Opens a form to enter/link the competitor's product URL or listing ID
Opens the "Link to Own Product" step
Ops Manager, Admin
Link to Own Product
Add Competitor Product form
Map the competitor listing to one of the business's own products
Opens a product search/select field
Tracking pair is saved; scheduled price checks begin
Ops Manager, Admin
View Price History
Competitor product row
See how competitor pricing has changed over time
Opens a price-history chart/table for that tracked pair
None (informational)
Ops Manager, Analyst, Admin
Set Alert Threshold
Competitor product settings
Define how large a price gap should trigger an alert
Opens a numeric/percentage input
Threshold saved; used by the scheduled comparison check
Ops Manager, Admin
Reprice Product (manual action, triggered from an alert)
Alert detail / product page
Act on a price-gap alert by adjusting the business's own price
Opens the product's pricing field, pre-filled with current price for editing
Updated price saves to the product (and re-publishes to marketplaces per Module 3's publish flow)
Ops Manager, Admin


8. Automation
Button/Action
Where It Appears
Purpose
What Happens on Click
Next Step Triggered
Role Required
Create New Rule
Automation screen
Start building a new automation
Opens the rule builder
Proceeds to Trigger selection
Ops Manager, Marketing Manager, Admin
Select Trigger
Rule builder
Choose the event that starts the rule (e.g., "stock below threshold," "order delivered," "cart abandoned for 24 hours")
Opens a trigger-type dropdown
Enables the Condition step
Ops Manager, Marketing Manager, Admin
Add Condition (optional)
Rule builder
Narrow when the rule applies (e.g., "only for VIP segment")
Opens condition fields relevant to the chosen trigger
Enables the Action step
Ops Manager, Marketing Manager, Admin
Define Action
Rule builder
Set what happens when the rule fires (e.g., "send low-stock email," "send WhatsApp thank-you," "add to retention segment")
Opens an action-type selector
Enables rule activation
Ops Manager, Marketing Manager, Admin
Activate Rule / Deactivate Rule (toggle)
Automation rule list
Turn a rule on or off
Flips the rule's active status
Rule starts/stops running automatically on matching events
Ops Manager, Marketing Manager, Admin
View Run Log
Automation rule row
Check when and how often a rule has fired
Opens a history panel of past rule executions
None (informational)
Ops Manager, Marketing Manager, Admin
Edit Rule / Delete Rule
Automation rule row
Modify or remove an existing rule
Opens the rule builder pre-filled (Edit) or prompts confirmation then removes it (Delete)
Rule updates or is removed from the active list
Ops Manager, Marketing Manager, Admin


9. Analytics & Reporting
Button/Action
Where It Appears
Purpose
What Happens on Click
Next Step Triggered
Role Required
Select Report Type
Reports screen
Choose which report to view (sales, revenue, product, inventory, platform-wise)
Opens a report-type dropdown/list
Loads the default view for that report type
Analyst, Ops Manager, Admin
Apply Filters
Reports screen
Narrow the report by date range, marketplace, category, or warehouse
Opens filter controls; on apply, re-runs the report query
Report table/chart updates
Analyst, Ops Manager, Admin
Generate Report
Reports screen
Run the report against current filters
Queries synced order/inventory data and renders results
Report displays as chart/table on screen
Analyst, Ops Manager, Admin
Export (CSV/PDF)
Reports screen (top-right)
Download the current report
Generates a file in the chosen format
File download starts
Analyst, Ops Manager, Admin


10. Business Intelligence
Button/Action
Where It Appears
Purpose
What Happens on Click
Next Step Triggered
Role Required
View Trend Details
BI dashboard / trend card
Drill into a detected sales trend
Opens a detailed trend view (chart + affected products/categories)
None (informational)
Business Owner, Analyst
View Forecast
BI dashboard / forecast card
See demand forecast for a product/category
Opens the forecast chart for the selected period
None (informational)
Business Owner, Analyst
View Recommendation
Executive Dashboard / BI screen
Read a system-generated recommendation (e.g., "restock X before month-end")
Expands the recommendation with supporting data
None (informational)
Business Owner, Analyst
Acknowledge/Dismiss Recommendation
Recommendation card
Mark a recommendation as seen/actioned so it stops resurfacing
Updates the recommendation's status
Recommendation moves out of the active list
Business Owner, Admin


11. Retention Marketing
Button/Action
Where It Appears
Purpose
What Happens on Click
Next Step Triggered
Role Required
Create New Campaign/Journey
Retention Marketing screen
Start building a retention journey
Opens the campaign builder
Proceeds to segment selection
Marketing Manager, Admin
Select Segment
Campaign builder
Choose the target audience for the journey
Opens segment picker (existing CRM segments or new rule-based one)
Shows live segment size/preview; enables Goal selection
Marketing Manager, Admin
Set Retention Goal
Campaign builder
Define campaign intent (win-back, cross-sell, loyalty reward, replenishment reminder)
Opens goal dropdown; system suggests a matching template
Enables journey building
Marketing Manager, Admin
Add Journey Step
Campaign builder
Add a timed WhatsApp or Email touchpoint to the journey
Opens a step form (channel, template, delay/timing)
New step appears on the journey timeline
Marketing Manager, Admin
Preview Journey
Campaign builder
Review the full journey timeline before going live
Renders a visual timeline of all steps and timing
None (informational)
Marketing Manager, Admin
Activate Journey
Campaign builder (final step)
Launch the campaign
System validates that all templates are approved, then enrolls matching customers
Journey starts running; customers enrolled and messages sent on schedule
Marketing Manager, Admin
Pause/Stop Journey
Active campaign list
Halt a running campaign
Stops further enrollment and sending for that journey
Journey status changes to "Paused"/"Stopped"
Marketing Manager, Admin
View Campaign Performance
Active/completed campaign row
Check results
Opens a performance dashboard: sent, delivered, opened/read, clicked, converted, revenue attributed
None (informational)
Marketing Manager, Admin, Analyst

11a. WhatsApp & Email Campaigns
Button/Action
Where It Appears
Purpose
What Happens on Click
Next Step Triggered
Role Required
Create Template
Templates screen
Build a reusable WhatsApp or Email message with merge fields (e.g., {customer_name}, {discount_code})
Opens the template editor
For WhatsApp: enables "Submit for Approval." For Email: template is immediately usable
Marketing Manager, Admin
Submit for Approval (WhatsApp only)
Template editor
Send a WhatsApp template to the connected BSP for Meta approval
Submits the template via the BSP integration
Template status shows "Pending Approval"; system polls and updates to Approved/Rejected
Marketing Manager, Admin
Select Audience
Send/campaign screen
Choose who receives the message
Opens picker: CRM segment, uploaded list, or manual selection
System filters out contacts without valid opt-in/consent
Marketing Manager, Admin
Send Now
Send/campaign screen
Send the message immediately
Queues the message for immediate delivery via WhatsApp Cloud API and/or ESP
Delivery begins; engagement tracking starts
Marketing Manager, Admin
Schedule Send
Send/campaign screen
Send the message at a future date/time
Opens a date/time picker; queues the send for later
Message sends automatically at the scheduled time
Marketing Manager, Admin
View Delivery Report
Sent campaign row
Check message-level delivery/engagement results
Opens a report: sent, delivered, read, opened, clicked, replied, bounced
None (informational)
Marketing Manager, Admin, Analyst

11b. Bulk Sharing / Broadcast
Button/Action
Where It Appears
Purpose
What Happens on Click
Next Step Triggered
Role Required
Upload Contact List
Bulk Sharing screen
Provide recipients for a bulk send via CSV
Opens file picker; system validates format and checks consent/opt-in status per contact
Enables content selection
Marketing Manager, Admin
Select Segment (for bulk send)
Bulk Sharing screen
Use an existing CRM segment instead of an uploaded list
Opens segment picker
Enables content selection
Marketing Manager, Admin
Choose Content to Share
Bulk Sharing screen
Pick what recipients will receive — promo message, catalog link, or product images
Opens content selector; renders a preview of what the recipient will see
Enables channel selection
Marketing Manager, Admin
Select Channel(s)
Bulk Sharing screen
Choose WhatsApp, Email, or both for the broadcast
Checkbox selection
Enables the Send action
Marketing Manager, Admin
Send Bulk Broadcast
Bulk Sharing screen (final step)
Launch the broadcast
System splits the list into batches and sends progressively, respecting rate limits
Live progress shown (sent/delivered/failed); failed sends logged with reason for retry
Marketing Manager, Admin
View Bulk Send Report
Completed broadcast row
Review final results
Opens a report: sent, delivered, failed, opted-out
Report stored against the campaign for compliance audit
Marketing Manager, Admin, Analyst


12. Settings & Administration
Button/Action
Where It Appears
Purpose
What Happens on Click
Next Step Triggered
Role Required
Add User
User Management screen
Invite a new team member
Opens invite form (email, starting role)
Invitation email sent; user appears as "Pending" until accepted
Admin
Edit User
User list row
Change a user's details
Opens the user's profile for editing
Changes saved
Admin
Deactivate User
User list row
Revoke a team member's access
Prompts confirmation, then disables the account
User can no longer log in; existing records they created remain
Admin
Assign Role
User detail page
Set or change a user's RBAC role (Ops, Marketing, Support, Warehouse, Analyst)
Opens role dropdown
Permissions update immediately across the platform
Admin
Edit Marketplace Credentials
Settings → Marketplace Settings
Update API keys/OAuth tokens for a connected store
Opens credential fields for that marketplace
Triggers a "Test Connection" check on save
Admin
Save System Configuration
Settings screen
Update system-wide preferences (currency, time zone, notification defaults, data retention)
Saves the entered configuration
Settings apply platform-wide
Admin


Notes for developers/designers:
Actions marked with a role of "Admin" alone are typically restricted to Business Owner/Admin; where a workflow document actor was "Ops Manager" or "Marketing Manager," Admin is assumed to also retain access by default RBAC convention (override this if the client wants stricter separation).
A few actions above (Disconnect Store, Reprice Product, Acknowledge Recommendation) are reasonable UI conveniences implied by the workflow steps but not spelled out verbatim in the SRS — flag these with the client before final sign-off if you want to keep the doc strictly spec-only.
"What Happens on Click" descriptions assume the same system behavior already defined in the workflow document; no new backend logic is introduced here.

