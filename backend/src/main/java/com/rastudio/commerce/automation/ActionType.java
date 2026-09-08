package com.rastudio.commerce.automation;

/** Roadmap Phase 13 "Initial actions". Stored as the String enum name in automation_rule.action_type
 *  (VARCHAR(150) — no schema change).
 *  - SEND_EMAIL sends for real through MailService (generic automation template), to the event's customer
 *    email when the event carries one, otherwise to the organization's Admins/Ops Managers — the same
 *    recipient targeting InventoryService's low-stock alert already uses.
 *  - SEND_WHATSAPP sends for real through WhatsAppProvider#sendMessage (Phase 18 infrastructure) when the
 *    triggering event carries an opted-in customer phone number; otherwise it's logged as skipped rather than
 *    silently failing.
 *  - CREATE_NOTIFICATION writes a real `notification` row (category AUTOMATION — one of Phase 14's
 *    documented notification categories) via NotificationService, so it shows up in the real bell.
 *  - ADD_TO_SEGMENT is intentionally a documented no-op: segments in this platform (Phase 11) are evaluated
 *    live from rule keys (VIP, LOYAL, ...), not a stored membership table, so there is nothing to "add" a
 *    customer into — the action still runs and logs why, rather than pretending to succeed. */
public enum ActionType {
  SEND_EMAIL,
  SEND_WHATSAPP,
  CREATE_NOTIFICATION,
  ADD_TO_SEGMENT
}
