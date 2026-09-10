-- ============================================================================
-- Commerce Operating Platform (RA Studio) — MySQL 8 Schema
-- ============================================================================
-- Database:        commerce_operating_platform
-- Engine:          MySQL 8.0+ (InnoDB, utf8mb4)
-- Source of truth: Full Workflow Document + Button & Action Documentation
--                  for the Commerce Operating Platform (18 modules).
--
-- ID STRATEGY
--   BIGINT AUTO_INCREMENT surrogate keys throughout. Chosen over UUIDs because
--   (a) every table is already tenant-scoped via organization_id, so global
--   uniqueness of the PK itself is not a security requirement; (b) sequential
--   integer PKs keep InnoDB clustered-index inserts and FK joins cheap at the
--   order/inventory-movement volumes this platform is built for; (c) it keeps
--   URLs and support conversations readable ("order 78001" vs a UUID).
--
-- MULTI-TENANCY
--   Every business table carries organization_id (directly, or transitively
--   through a parent FK — e.g. product_variant -> product -> organization).
--   The backend must filter every query by the authenticated user's
--   organization_id; the schema enforces this at the data-model level with
--   composite unique constraints and FKs, but tenant isolation is ultimately
--   an application-layer responsibility (see backend service layer).
--
-- MONEY
--   All monetary columns use DECIMAL — never FLOAT/DOUBLE. Unit prices/line
--   items use DECIMAL(12,2); aggregated figures that can run larger
--   (campaign revenue attribution) use DECIMAL(14,2).
--
-- DELETE BEHAVIOR
--   ON DELETE CASCADE is used only where the child record has no standalone
--   business meaning without its parent (e.g. order_item without its order,
--   sync logs without their connection). Records with independent audit/
--   business value (orders referencing a customer, returns referencing an
--   order) use the default RESTRICT so history can't be silently destroyed.
--
-- MAJOR RELATIONSHIPS (see README.md for the full entity list)
--   organization 1—N app_user, product, warehouse, customer, customer_order,
--     marketplace_connection, segment, automation_rule, campaign, etc.
--   product 1—N product_variant 1—N inventory_item (per warehouse)
--   customer_order 1—N order_item, 1—N order_status_history, 0/1 return_request
--   segment 1—N campaign 1—N campaign_step / campaign_enrollment
-- ============================================================================

CREATE DATABASE IF NOT EXISTS commerce_operating_platform
  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE commerce_operating_platform;

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------------------------------------------------------
-- 1. ORGANIZATIONS & USERS (multi-tenant root)
-- ----------------------------------------------------------------------------
CREATE TABLE organization (
  id                BIGINT AUTO_INCREMENT PRIMARY KEY,
  name              VARCHAR(150) NOT NULL,
  industry          VARCHAR(100),
  business_size     VARCHAR(50),
  currency          VARCHAR(10)  NOT NULL DEFAULT 'INR',
  timezone          VARCHAR(50)  NOT NULL DEFAULT 'Asia/Kolkata',
  data_retention_days INT        NOT NULL DEFAULT 365,
  created_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB;

CREATE TABLE app_user (
  id                BIGINT AUTO_INCREMENT PRIMARY KEY,
  organization_id   BIGINT NOT NULL,
  full_name         VARCHAR(150) NOT NULL,
  email             VARCHAR(190) NOT NULL,
  password_hash     VARCHAR(255) NOT NULL,
  role              ENUM('BUSINESS_OWNER_ADMIN','OPERATIONS_MANAGER','MARKETING_MANAGER',
                          'SUPPORT_CRM_AGENT','WAREHOUSE_STAFF','ANALYST_VIEWER') NOT NULL,
  status            ENUM('PENDING','ACTIVE','DEACTIVATED') NOT NULL DEFAULT 'PENDING',
  email_verified_at DATETIME NULL,
  invited_by        BIGINT NULL,
  last_login_at     DATETIME NULL,
  created_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_user_org FOREIGN KEY (organization_id) REFERENCES organization(id) ON DELETE CASCADE,
  CONSTRAINT uq_user_email_org UNIQUE (organization_id, email),
  INDEX idx_user_org (organization_id)
) ENGINE=InnoDB;

CREATE TABLE refresh_token (
  id            BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id       BIGINT NOT NULL,
  token_hash    VARCHAR(255) NOT NULL,
  expires_at    DATETIME NOT NULL,
  revoked       TINYINT(1) NOT NULL DEFAULT 0,
  created_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_rt_user FOREIGN KEY (user_id) REFERENCES app_user(id) ON DELETE CASCADE,
  INDEX idx_rt_user (user_id)
) ENGINE=InnoDB;

-- ----------------------------------------------------------------------------
-- 2. MARKETPLACE INTEGRATION
-- ----------------------------------------------------------------------------
CREATE TABLE marketplace_connection (
  id                BIGINT AUTO_INCREMENT PRIMARY KEY,
  organization_id   BIGINT NOT NULL,
  marketplace_name  ENUM('SHOPIFY','AMAZON','FLIPKART','WOOCOMMERCE','MAGENTO','MEESHO','ETSY') NOT NULL,
  auth_type         ENUM('OAUTH','API_KEY') NOT NULL,
  status            ENUM('NOT_CONNECTED','CONNECTED','SYNCING','ACTION_NEEDED','DISCONNECTED') NOT NULL DEFAULT 'NOT_CONNECTED',
  credential_ref    VARCHAR(255) NULL COMMENT 'Opaque reference/alias to secret storage; never the raw secret',
  is_mock           TINYINT(1) NOT NULL DEFAULT 1 COMMENT '1 = local/sandbox adapter, 0 = real credentials configured',
  product_sync_pct  INT NOT NULL DEFAULT 0,
  inventory_sync_pct INT NOT NULL DEFAULT 0,
  order_sync_pct    INT NOT NULL DEFAULT 0,
  last_sync_at      DATETIME NULL,
  connected_at      DATETIME NULL,
  created_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_mc_org FOREIGN KEY (organization_id) REFERENCES organization(id) ON DELETE CASCADE,
  CONSTRAINT uq_mc_org_marketplace UNIQUE (organization_id, marketplace_name)
) ENGINE=InnoDB;

CREATE TABLE marketplace_sync_log (
  id                    BIGINT AUTO_INCREMENT PRIMARY KEY,
  marketplace_connection_id BIGINT NOT NULL,
  event                 VARCHAR(255) NOT NULL,
  is_error              TINYINT(1) NOT NULL DEFAULT 0,
  created_at            DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_msl_conn FOREIGN KEY (marketplace_connection_id) REFERENCES marketplace_connection(id) ON DELETE CASCADE,
  INDEX idx_msl_conn (marketplace_connection_id)
) ENGINE=InnoDB;

-- ----------------------------------------------------------------------------
-- 3. CATALOG: CATEGORIES, PRODUCTS, VARIANTS, PUBLISHING
-- ----------------------------------------------------------------------------
CREATE TABLE category (
  id                BIGINT AUTO_INCREMENT PRIMARY KEY,
  organization_id   BIGINT NOT NULL,
  name              VARCHAR(120) NOT NULL,
  CONSTRAINT fk_cat_org FOREIGN KEY (organization_id) REFERENCES organization(id) ON DELETE CASCADE,
  CONSTRAINT uq_cat_org_name UNIQUE (organization_id, name)
) ENGINE=InnoDB;

CREATE TABLE product (
  id                BIGINT AUTO_INCREMENT PRIMARY KEY,
  organization_id   BIGINT NOT NULL,
  category_id       BIGINT NULL,
  name              VARCHAR(200) NOT NULL,
  description       TEXT NULL,
  base_price        DECIMAL(12,2) NOT NULL DEFAULT 0.00,
  status            ENUM('DRAFT','PUBLISHED') NOT NULL DEFAULT 'DRAFT',
  image_path        VARCHAR(500) NULL,
  created_by        BIGINT NULL,
  created_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_prod_org FOREIGN KEY (organization_id) REFERENCES organization(id) ON DELETE CASCADE,
  CONSTRAINT fk_prod_cat FOREIGN KEY (category_id) REFERENCES category(id) ON DELETE SET NULL,
  INDEX idx_prod_org (organization_id),
  INDEX idx_prod_cat (category_id)
) ENGINE=InnoDB;

CREATE TABLE product_variant (
  id                BIGINT AUTO_INCREMENT PRIMARY KEY,
  product_id        BIGINT NOT NULL,
  organization_id   BIGINT NOT NULL COMMENT 'Denormalized from product.organization_id so SKU uniqueness can be scoped per-tenant',
  name              VARCHAR(150) NOT NULL COMMENT 'e.g. Black / M',
  sku               VARCHAR(80) NOT NULL,
  price_override    DECIMAL(12,2) NULL,
  created_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_variant_product FOREIGN KEY (product_id) REFERENCES product(id) ON DELETE CASCADE,
  CONSTRAINT fk_variant_org FOREIGN KEY (organization_id) REFERENCES organization(id) ON DELETE CASCADE,
  CONSTRAINT uq_variant_org_sku UNIQUE (organization_id, sku),
  INDEX idx_variant_product (product_id)
) ENGINE=InnoDB;

CREATE TABLE product_marketplace_listing (
  id                BIGINT AUTO_INCREMENT PRIMARY KEY,
  product_id        BIGINT NOT NULL,
  marketplace_name  ENUM('SHOPIFY','AMAZON','FLIPKART','WOOCOMMERCE','MAGENTO','MEESHO','ETSY') NOT NULL,
  status            ENUM('PENDING','PUBLISHED','FAILED') NOT NULL DEFAULT 'PENDING',
  external_listing_id VARCHAR(150) NULL,
  last_error        VARCHAR(500) NULL,
  published_at      DATETIME NULL,
  CONSTRAINT fk_pml_product FOREIGN KEY (product_id) REFERENCES product(id) ON DELETE CASCADE,
  CONSTRAINT uq_pml_product_marketplace UNIQUE (product_id, marketplace_name)
) ENGINE=InnoDB;

-- ----------------------------------------------------------------------------
-- 4. INVENTORY: WAREHOUSES, STOCK, MOVEMENTS
-- ----------------------------------------------------------------------------
CREATE TABLE warehouse (
  id                BIGINT AUTO_INCREMENT PRIMARY KEY,
  organization_id   BIGINT NOT NULL,
  name              VARCHAR(150) NOT NULL,
  location          VARCHAR(200) NULL,
  capacity_pct      INT NOT NULL DEFAULT 0,
  CONSTRAINT fk_wh_org FOREIGN KEY (organization_id) REFERENCES organization(id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE inventory_item (
  id                BIGINT AUTO_INCREMENT PRIMARY KEY,
  product_variant_id BIGINT NOT NULL,
  warehouse_id      BIGINT NOT NULL,
  stock_quantity    INT NOT NULL DEFAULT 0,
  reserved_quantity INT NOT NULL DEFAULT 0,
  low_stock_threshold INT NOT NULL DEFAULT 10,
  updated_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_inv_variant FOREIGN KEY (product_variant_id) REFERENCES product_variant(id) ON DELETE CASCADE,
  CONSTRAINT fk_inv_warehouse FOREIGN KEY (warehouse_id) REFERENCES warehouse(id) ON DELETE CASCADE,
  CONSTRAINT uq_inv_variant_warehouse UNIQUE (product_variant_id, warehouse_id),
  INDEX idx_inv_variant (product_variant_id),
  CHECK (stock_quantity >= 0),
  CHECK (reserved_quantity >= 0)
) ENGINE=InnoDB;

CREATE TABLE inventory_movement (
  id                BIGINT AUTO_INCREMENT PRIMARY KEY,
  inventory_item_id BIGINT NOT NULL,
  movement_type     ENUM('STOCK_IN','SALE_RESERVED','SALE_DEDUCTED','RETURN_IN','MANUAL_ADJUSTMENT','RELEASE_RESERVATION') NOT NULL,
  quantity_delta    INT NOT NULL COMMENT 'positive = increase, negative = decrease',
  reference_type    VARCHAR(50) NULL COMMENT 'ORDER, RETURN, MANUAL',
  reference_id      BIGINT NULL,
  performed_by      BIGINT NULL,
  created_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_mov_item FOREIGN KEY (inventory_item_id) REFERENCES inventory_item(id) ON DELETE CASCADE,
  INDEX idx_mov_item (inventory_item_id)
) ENGINE=InnoDB;

-- ----------------------------------------------------------------------------
-- 5. CUSTOMERS, NOTES, SEGMENTS
-- ----------------------------------------------------------------------------
CREATE TABLE customer (
  id                BIGINT AUTO_INCREMENT PRIMARY KEY,
  organization_id   BIGINT NOT NULL,
  full_name         VARCHAR(150) NOT NULL,
  email             VARCHAR(190) NULL,
  phone             VARCHAR(30) NULL,
  city              VARCHAR(100) NULL,
  country           VARCHAR(100) NULL,
  primary_channel   VARCHAR(50) NULL,
  status            ENUM('ACTIVE','INACTIVE') NOT NULL DEFAULT 'ACTIVE',
  whatsapp_opt_in   TINYINT(1) NOT NULL DEFAULT 0,
  email_opt_in      TINYINT(1) NOT NULL DEFAULT 0,
  created_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_cust_org FOREIGN KEY (organization_id) REFERENCES organization(id) ON DELETE CASCADE,
  INDEX idx_cust_org (organization_id)
) ENGINE=InnoDB;

CREATE TABLE customer_note (
  id                BIGINT AUTO_INCREMENT PRIMARY KEY,
  customer_id       BIGINT NOT NULL,
  author_user_id    BIGINT NULL,
  note_text         TEXT NOT NULL,
  created_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_note_customer FOREIGN KEY (customer_id) REFERENCES customer(id) ON DELETE CASCADE,
  INDEX idx_note_customer (customer_id)
) ENGINE=InnoDB;

CREATE TABLE segment (
  id                BIGINT AUTO_INCREMENT PRIMARY KEY,
  organization_id   BIGINT NOT NULL,
  name              VARCHAR(150) NOT NULL,
  rule_key          ENUM('CART_ABANDONED','VIP','LOYAL','LAPSED','RECENT_BUYER','NEW') NOT NULL,
  description       VARCHAR(300) NULL,
  created_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_seg_org FOREIGN KEY (organization_id) REFERENCES organization(id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- ----------------------------------------------------------------------------
-- 6. ORDERS, ITEMS, STATUS HISTORY
-- ----------------------------------------------------------------------------
CREATE TABLE customer_order (
  id                BIGINT AUTO_INCREMENT PRIMARY KEY,
  organization_id   BIGINT NOT NULL,
  order_number      VARCHAR(40) NOT NULL,
  customer_id       BIGINT NOT NULL,
  marketplace_name  ENUM('SHOPIFY','AMAZON','FLIPKART','WOOCOMMERCE','MAGENTO','MEESHO','ETSY') NOT NULL,
  status            ENUM('NEW','CONFIRMED','PACKED','SHIPPED','OUT_FOR_DELIVERY','DELIVERED','CANCELLED') NOT NULL DEFAULT 'NEW',
  payment_status    ENUM('PAID','PENDING','REFUNDED','FAILED') NOT NULL DEFAULT 'PENDING',
  subtotal          DECIMAL(12,2) NOT NULL DEFAULT 0.00,
  tax_amount        DECIMAL(12,2) NOT NULL DEFAULT 0.00,
  total_amount      DECIMAL(12,2) NOT NULL DEFAULT 0.00,
  shipping_address  VARCHAR(500) NULL,
  courier_name      VARCHAR(100) NULL,
  tracking_number   VARCHAR(100) NULL,
  return_id         BIGINT NULL,
  placed_at         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_order_org FOREIGN KEY (organization_id) REFERENCES organization(id) ON DELETE CASCADE,
  CONSTRAINT fk_order_customer FOREIGN KEY (customer_id) REFERENCES customer(id),
  CONSTRAINT uq_order_number UNIQUE (organization_id, order_number),
  INDEX idx_order_org (organization_id),
  INDEX idx_order_status (status),
  INDEX idx_order_customer (customer_id)
) ENGINE=InnoDB;

CREATE TABLE order_item (
  id                BIGINT AUTO_INCREMENT PRIMARY KEY,
  order_id          BIGINT NOT NULL,
  product_variant_id BIGINT NOT NULL,
  quantity          INT NOT NULL,
  unit_price        DECIMAL(12,2) NOT NULL,
  line_total        DECIMAL(12,2) NOT NULL,
  CONSTRAINT fk_item_order FOREIGN KEY (order_id) REFERENCES customer_order(id) ON DELETE CASCADE,
  CONSTRAINT fk_item_variant FOREIGN KEY (product_variant_id) REFERENCES product_variant(id),
  INDEX idx_item_order (order_id),
  CHECK (quantity > 0)
) ENGINE=InnoDB;

CREATE TABLE order_status_history (
  id                BIGINT AUTO_INCREMENT PRIMARY KEY,
  order_id          BIGINT NOT NULL,
  status            VARCHAR(30) NOT NULL,
  changed_by        BIGINT NULL,
  changed_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_hist_order FOREIGN KEY (order_id) REFERENCES customer_order(id) ON DELETE CASCADE,
  INDEX idx_hist_order (order_id)
) ENGINE=InnoDB;

-- ----------------------------------------------------------------------------
-- 7. RETURNS
-- ----------------------------------------------------------------------------
CREATE TABLE return_request (
  id                BIGINT AUTO_INCREMENT PRIMARY KEY,
  organization_id   BIGINT NOT NULL,
  order_id          BIGINT NOT NULL,
  reason            VARCHAR(300) NOT NULL,
  status            ENUM('REQUESTED','APPROVED_AWAITING_ITEM','REJECTED','RECEIVED_INSPECTING','REFUNDED','REPLACED','CLOSED') NOT NULL DEFAULT 'REQUESTED',
  resolution        VARCHAR(30) NULL,
  requested_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_ret_org FOREIGN KEY (organization_id) REFERENCES organization(id) ON DELETE CASCADE,
  CONSTRAINT fk_ret_order FOREIGN KEY (order_id) REFERENCES customer_order(id) ON DELETE RESTRICT,
  INDEX idx_ret_order (order_id)
) ENGINE=InnoDB;

ALTER TABLE customer_order
  ADD CONSTRAINT fk_order_return FOREIGN KEY (return_id) REFERENCES return_request(id) ON DELETE SET NULL;

-- ----------------------------------------------------------------------------
-- 8. COMPETITOR ANALYSIS
-- ----------------------------------------------------------------------------
CREATE TABLE competitor_tracked_product (
  id                BIGINT AUTO_INCREMENT PRIMARY KEY,
  organization_id   BIGINT NOT NULL,
  linked_product_id BIGINT NOT NULL,
  competitor_name   VARCHAR(150) NOT NULL,
  competitor_listing_ref VARCHAR(300) NULL,
  your_price        DECIMAL(12,2) NOT NULL,
  competitor_price  DECIMAL(12,2) NOT NULL,
  alert_threshold_pct INT NOT NULL DEFAULT 10,
  created_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_comp_org FOREIGN KEY (organization_id) REFERENCES organization(id) ON DELETE CASCADE,
  CONSTRAINT fk_comp_product FOREIGN KEY (linked_product_id) REFERENCES product(id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE competitor_price_history (
  id                BIGINT AUTO_INCREMENT PRIMARY KEY,
  competitor_tracked_product_id BIGINT NOT NULL,
  your_price        DECIMAL(12,2) NOT NULL,
  competitor_price  DECIMAL(12,2) NOT NULL,
  recorded_at       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_cph_tracked FOREIGN KEY (competitor_tracked_product_id) REFERENCES competitor_tracked_product(id) ON DELETE CASCADE,
  INDEX idx_cph_tracked (competitor_tracked_product_id)
) ENGINE=InnoDB;

-- ----------------------------------------------------------------------------
-- 9. AUTOMATION ENGINE
-- ----------------------------------------------------------------------------
CREATE TABLE automation_rule (
  id                BIGINT AUTO_INCREMENT PRIMARY KEY,
  organization_id   BIGINT NOT NULL,
  name              VARCHAR(200) NOT NULL,
  trigger_type      VARCHAR(100) NOT NULL,
  condition_text    VARCHAR(300) NULL,
  action_type       VARCHAR(150) NOT NULL,
  is_active         TINYINT(1) NOT NULL DEFAULT 1,
  run_count         BIGINT NOT NULL DEFAULT 0,
  created_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_auto_org FOREIGN KEY (organization_id) REFERENCES organization(id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE automation_run_log (
  id                BIGINT AUTO_INCREMENT PRIMARY KEY,
  automation_rule_id BIGINT NOT NULL,
  result            VARCHAR(300) NOT NULL,
  ran_at            DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_arl_rule FOREIGN KEY (automation_rule_id) REFERENCES automation_rule(id) ON DELETE CASCADE,
  INDEX idx_arl_rule (automation_rule_id)
) ENGINE=InnoDB;

-- ----------------------------------------------------------------------------
-- 9b. BUSINESS INTELLIGENCE — generated insights, forecasts, recommendations
--     Stores the OUTPUT of calculation-based analytics (or a future ML/AI
--     service) — the database is a store of results, not the analytics engine
--     itself. See workflow doc Module 13.
-- ----------------------------------------------------------------------------
CREATE TABLE bi_insight (
  id                BIGINT AUTO_INCREMENT PRIMARY KEY,
  organization_id   BIGINT NOT NULL,
  insight_type      ENUM('TREND','FORECAST','RECOMMENDATION') NOT NULL,
  title             VARCHAR(255) NOT NULL,
  summary           VARCHAR(500) NULL,
  detail            TEXT NULL COMMENT 'Longer explanation shown on drill-down (View Trend Details / View Forecast)',
  related_module    VARCHAR(50) NULL COMMENT 'e.g. inventory, products, marketplace — for navigation on click-through',
  related_entity_id BIGINT NULL,
  status            ENUM('ACTIVE','ACKNOWLEDGED','DISMISSED') NOT NULL DEFAULT 'ACTIVE',
  generated_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  acknowledged_at   DATETIME NULL,
  acknowledged_by   BIGINT NULL,
  CONSTRAINT fk_bi_org FOREIGN KEY (organization_id) REFERENCES organization(id) ON DELETE CASCADE,
  INDEX idx_bi_org_status (organization_id, status)
) ENGINE=InnoDB;

CREATE TABLE bi_forecast_point (
  id                BIGINT AUTO_INCREMENT PRIMARY KEY,
  bi_insight_id     BIGINT NOT NULL,
  period_label      VARCHAR(30) NOT NULL COMMENT 'e.g. "Wk 1", "Feb 2027"',
  forecasted_value  DECIMAL(14,2) NOT NULL,
  CONSTRAINT fk_bfp_insight FOREIGN KEY (bi_insight_id) REFERENCES bi_insight(id) ON DELETE CASCADE,
  INDEX idx_bfp_insight (bi_insight_id)
) ENGINE=InnoDB;

-- ----------------------------------------------------------------------------
-- 10. MESSAGING: TEMPLATES, CAMPAIGNS, ENROLLMENTS, MESSAGE LOG
-- ----------------------------------------------------------------------------
CREATE TABLE message_template (
  id                BIGINT AUTO_INCREMENT PRIMARY KEY,
  organization_id   BIGINT NOT NULL,
  name              VARCHAR(200) NOT NULL,
  channel           ENUM('WHATSAPP','EMAIL') NOT NULL,
  content           TEXT NOT NULL,
  approval_status   ENUM('DRAFT','PENDING_APPROVAL','APPROVED','REJECTED') NOT NULL DEFAULT 'DRAFT',
  -- Nullable; only ever set for WhatsApp templates once Submit for Approval succeeds. Lets the approval
  -- poller (TemplateApprovalPollingJob, Phase 19) and the real inbound webhook (WhatsAppWebhookController,
  -- Phase 18) both correlate a provider-side template back to this row without any other schema change.
  provider_template_id VARCHAR(200) NULL,
  created_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_tpl_org FOREIGN KEY (organization_id) REFERENCES organization(id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE campaign (
  id                BIGINT AUTO_INCREMENT PRIMARY KEY,
  organization_id   BIGINT NOT NULL,
  name              VARCHAR(200) NOT NULL,
  segment_id        BIGINT NOT NULL,
  goal              ENUM('WIN_BACK','CROSS_SELL','LOYALTY_REWARD','REPLENISHMENT_REMINDER') NOT NULL,
  status            ENUM('DRAFT','ACTIVE','PAUSED','COMPLETED') NOT NULL DEFAULT 'DRAFT',
  enrolled_count    INT NOT NULL DEFAULT 0,
  sent_count        INT NOT NULL DEFAULT 0,
  delivered_count   INT NOT NULL DEFAULT 0,
  opened_count      INT NOT NULL DEFAULT 0,
  clicked_count     INT NOT NULL DEFAULT 0,
  converted_count   INT NOT NULL DEFAULT 0,
  revenue_attributed DECIMAL(14,2) NOT NULL DEFAULT 0.00,
  created_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_camp_org FOREIGN KEY (organization_id) REFERENCES organization(id) ON DELETE CASCADE,
  CONSTRAINT fk_camp_segment FOREIGN KEY (segment_id) REFERENCES segment(id)
) ENGINE=InnoDB;

CREATE TABLE campaign_step (
  id                BIGINT AUTO_INCREMENT PRIMARY KEY,
  campaign_id       BIGINT NOT NULL,
  step_order        INT NOT NULL,
  channel           ENUM('WHATSAPP','EMAIL') NOT NULL,
  message_template_id BIGINT NOT NULL,
  delay_days        INT NOT NULL DEFAULT 0,
  CONSTRAINT fk_step_campaign FOREIGN KEY (campaign_id) REFERENCES campaign(id) ON DELETE CASCADE,
  CONSTRAINT fk_step_template FOREIGN KEY (message_template_id) REFERENCES message_template(id),
  INDEX idx_step_campaign (campaign_id)
) ENGINE=InnoDB;

CREATE TABLE campaign_enrollment (
  id                BIGINT AUTO_INCREMENT PRIMARY KEY,
  campaign_id       BIGINT NOT NULL,
  customer_id       BIGINT NOT NULL,
  current_step      INT NOT NULL DEFAULT 0,
  enrolled_at       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  converted_at      DATETIME NULL,
  CONSTRAINT fk_enroll_campaign FOREIGN KEY (campaign_id) REFERENCES campaign(id) ON DELETE CASCADE,
  CONSTRAINT fk_enroll_customer FOREIGN KEY (customer_id) REFERENCES customer(id) ON DELETE CASCADE,
  CONSTRAINT uq_enroll_campaign_customer UNIQUE (campaign_id, customer_id)
) ENGINE=InnoDB;

CREATE TABLE message_log (
  id                BIGINT AUTO_INCREMENT PRIMARY KEY,
  organization_id   BIGINT NOT NULL,
  campaign_id       BIGINT NULL,
  message_template_id BIGINT NULL,
  customer_id       BIGINT NULL,
  channel           ENUM('WHATSAPP','EMAIL') NOT NULL,
  status            ENUM('QUEUED','SENT','DELIVERED','READ','FAILED','BOUNCED') NOT NULL DEFAULT 'QUEUED',
  provider_status_detail VARCHAR(300) NULL,
  sent_at           DATETIME NULL,
  created_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_mlog_org FOREIGN KEY (organization_id) REFERENCES organization(id) ON DELETE CASCADE,
  CONSTRAINT fk_mlog_campaign FOREIGN KEY (campaign_id) REFERENCES campaign(id) ON DELETE SET NULL,
  CONSTRAINT fk_mlog_customer FOREIGN KEY (customer_id) REFERENCES customer(id) ON DELETE SET NULL,
  INDEX idx_mlog_campaign (campaign_id)
) ENGINE=InnoDB;

-- ----------------------------------------------------------------------------
-- 11. BULK SHARING / BROADCAST
-- ----------------------------------------------------------------------------
CREATE TABLE bulk_send (
  id                BIGINT AUTO_INCREMENT PRIMARY KEY,
  organization_id   BIGINT NOT NULL,
  content           TEXT NOT NULL,
  channel           ENUM('WHATSAPP','EMAIL','BOTH') NOT NULL,
  list_size         INT NOT NULL DEFAULT 0,
  sent_count        INT NOT NULL DEFAULT 0,
  delivered_count   INT NOT NULL DEFAULT 0,
  failed_count      INT NOT NULL DEFAULT 0,
  opted_out_count   INT NOT NULL DEFAULT 0,
  status            ENUM('PENDING','IN_PROGRESS','COMPLETED') NOT NULL DEFAULT 'PENDING',
  created_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_bulk_org FOREIGN KEY (organization_id) REFERENCES organization(id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE bulk_send_item (
  id                BIGINT AUTO_INCREMENT PRIMARY KEY,
  bulk_send_id      BIGINT NOT NULL,
  customer_id       BIGINT NULL,
  contact_reference VARCHAR(190) NULL COMMENT 'email/phone when not a known customer (CSV upload)',
  status            ENUM('PENDING','SENT','DELIVERED','FAILED','OPTED_OUT') NOT NULL DEFAULT 'PENDING',
  failure_reason    VARCHAR(300) NULL,
  CONSTRAINT fk_bsi_bulk FOREIGN KEY (bulk_send_id) REFERENCES bulk_send(id) ON DELETE CASCADE,
  INDEX idx_bsi_bulk (bulk_send_id)
) ENGINE=InnoDB;

-- ----------------------------------------------------------------------------
-- 12. NOTIFICATIONS & AUDIT LOG
-- ----------------------------------------------------------------------------
CREATE TABLE notification (
  id                BIGINT AUTO_INCREMENT PRIMARY KEY,
  organization_id   BIGINT NOT NULL,
  title             VARCHAR(255) NOT NULL,
  category          VARCHAR(50) NOT NULL COMMENT 'LOW_STOCK, SYNC_ERROR, ORDER_UPDATE, RETURN_UPDATE, CAMPAIGN, AUTOMATION, INTEGRATION',
  link_module       VARCHAR(50) NULL,
  link_entity_id    BIGINT NULL,
  is_read           TINYINT(1) NOT NULL DEFAULT 0,
  created_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_notif_org FOREIGN KEY (organization_id) REFERENCES organization(id) ON DELETE CASCADE,
  INDEX idx_notif_org (organization_id)
) ENGINE=InnoDB;

CREATE TABLE audit_log (
  id                BIGINT AUTO_INCREMENT PRIMARY KEY,
  organization_id   BIGINT NOT NULL,
  user_id           BIGINT NULL,
  action            VARCHAR(100) NOT NULL,
  entity_type       VARCHAR(100) NOT NULL,
  entity_id         BIGINT NULL,
  previous_value    VARCHAR(500) NULL,
  new_value         VARCHAR(500) NULL,
  ip_address        VARCHAR(45) NULL COMMENT 'IPv4 or IPv6; populated when the action originates from an HTTP request',
  created_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_audit_org FOREIGN KEY (organization_id) REFERENCES organization(id) ON DELETE CASCADE,
  INDEX idx_audit_org (organization_id),
  INDEX idx_audit_entity (entity_type, entity_id)
) ENGINE=InnoDB;

-- ----------------------------------------------------------------------------
-- 13. SYSTEM CONFIGURATION
-- ----------------------------------------------------------------------------
CREATE TABLE system_config (
  organization_id   BIGINT PRIMARY KEY,
  sync_interval     VARCHAR(50) NOT NULL DEFAULT 'Every 15 minutes',
  currency          VARCHAR(10) NOT NULL DEFAULT 'INR',
  two_factor_enabled TINYINT(1) NOT NULL DEFAULT 1,
  email_digest_enabled TINYINT(1) NOT NULL DEFAULT 1,
  facebook_ads_url  VARCHAR(500) NULL,
  updated_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_cfg_org FOREIGN KEY (organization_id) REFERENCES organization(id) ON DELETE CASCADE
) ENGINE=InnoDB;

SET FOREIGN_KEY_CHECKS = 1;
