package com.rastudio.commerce.broadcast;

/** Matches bulk_send_item.status. PENDING: created, not yet attempted. SENT: the channel provider (WhatsApp
 *  Cloud API / SMTP) accepted the message for delivery. DELIVERED: reserved for a future inbound
 *  delivery-confirmation webhook — this project has no provider-message-id column on bulk_send_item to
 *  correlate such a webhook back to a specific item (the same documented limitation WhatsAppWebhookController
 *  already has for message_log), so no code path sets DELIVERED today; it exists in the schema/enum for when
 *  that webhook correlation is built. FAILED: validation failed (bad email/phone) or the provider rejected the
 *  send — failure_reason explains why, and Retry Failed Sends resets these back to PENDING. OPTED_OUT: skipped
 *  without an attempt because the recipient has not consented on that channel. */
public enum BulkSendItemStatus { PENDING, SENT, DELIVERED, FAILED, OPTED_OUT }
