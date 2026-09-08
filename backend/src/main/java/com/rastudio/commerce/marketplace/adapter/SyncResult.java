package com.rastudio.commerce.marketplace.adapter;
public record SyncResult(boolean success, String message, int productSyncPct, int inventorySyncPct, int orderSyncPct) {}