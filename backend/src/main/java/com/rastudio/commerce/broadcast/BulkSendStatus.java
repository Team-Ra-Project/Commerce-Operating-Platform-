package com.rastudio.commerce.broadcast;

/** Matches bulk_send.status. PENDING: row + items created, async sending not yet started. IN_PROGRESS:
 *  BulkSendService#sendAsync is actively working through the batch. COMPLETED: every item has a terminal
 *  status (SENT/DELIVERED/FAILED/OPTED_OUT) — including the case where the whole run failed unexpectedly and
 *  remaining items were force-marked FAILED, so a broadcast can never get stuck IN_PROGRESS forever. */
public enum BulkSendStatus { PENDING, IN_PROGRESS, COMPLETED }
