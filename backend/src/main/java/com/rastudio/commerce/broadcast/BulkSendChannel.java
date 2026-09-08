package com.rastudio.commerce.broadcast;

/** Matches the `channel` ENUM('WHATSAPP','EMAIL','BOTH') on bulk_send. Deliberately its own enum rather than
 *  reusing messaging.MessageChannel (WHATSAPP/EMAIL only) — BOTH is a real, distinct option here because one
 *  bulk_send row can target both channels for the same recipient list, unlike a single campaign_step. */
public enum BulkSendChannel { WHATSAPP, EMAIL, BOTH }
