package com.rastudio.commerce.automation;

/** Roadmap Phase 13 "Initial triggers". Stored as the String enum name in automation_rule.trigger_type
 *  (VARCHAR(100) — no schema change). CART_ABANDONED can be selected when building a rule, but nothing in
 *  this codebase fires it: there is no cart entity/table anywhere in schema.sql (Segment's own CART_ABANDONED
 *  rule key is empty for the same reason — see SegmentService). The other four triggers ARE wired to real
 *  events: InventoryService fires STOCK_BELOW_THRESHOLD, OrderService fires ORDER_DELIVERED, ReturnService
 *  fires RETURN_UPDATE, and ConversionDetectionService fires CAMPAIGN_EVENT whenever a customer converts on a
 *  Phase 20 retention campaign. */
public enum TriggerType {
  STOCK_BELOW_THRESHOLD,
  ORDER_DELIVERED,
  CART_ABANDONED,
  RETURN_UPDATE,
  CAMPAIGN_EVENT
}
