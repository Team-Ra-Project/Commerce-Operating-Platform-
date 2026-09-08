package com.rastudio.commerce.automation;

import java.util.Map;

/**
 * Fired by other phases into AutomationService#fireEvent when a real trigger happens (see TriggerType for
 * which triggers are actually wired up: InventoryService for STOCK_BELOW_THRESHOLD, OrderService for
 * ORDER_DELIVERED, ReturnService for RETURN_UPDATE). tags is a small set of free-text labels describing the
 * event (e.g. {"segment": "VIP"} or {"category": "Fashion"}) that a rule's conditionText is matched against —
 * see AutomationService#matches. customerEmail/customerName/customerPhone/customerWhatsAppOptIn are supplied
 * when the event concerns a specific customer (ORDER_DELIVERED, RETURN_UPDATE) so SEND_EMAIL/SEND_WHATSAPP can
 * reach that customer directly; they're left null/false for organization-facing events like
 * STOCK_BELOW_THRESHOLD, where SEND_EMAIL instead reaches the org's own Admins/Ops Managers and SEND_WHATSAPP
 * has no customer to message.
 */
public record AutomationEvent(
    Long organizationId,
    TriggerType triggerType,
    String summary,
    Map<String, String> tags,
    String customerEmail,
    String customerName,
    String linkModule,
    Long linkEntityId,
    String customerPhone,
    boolean customerWhatsAppOptIn) {

  public AutomationEvent {
    if (tags == null) tags = Map.of();
  }

  /** Convenience constructor for events with no specific customer phone (most existing call sites). */
  public AutomationEvent(Long organizationId, TriggerType triggerType, String summary, Map<String, String> tags,
      String customerEmail, String customerName, String linkModule, Long linkEntityId) {
    this(organizationId, triggerType, summary, tags, customerEmail, customerName, linkModule, linkEntityId,
        null, false);
  }
}
