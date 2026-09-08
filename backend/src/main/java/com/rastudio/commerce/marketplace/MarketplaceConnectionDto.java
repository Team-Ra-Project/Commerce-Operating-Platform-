package com.rastudio.commerce.marketplace;
import java.time.LocalDateTime;

/** API-facing projection of a marketplace connection. Deliberately excludes credentialRef — the encrypted
 *  credential material must never be serialized back to the frontend. */
public record MarketplaceConnectionDto(
    String marketplaceName,
    String authType,
    String status,
    boolean isMock,
    int productSyncPct,
    int inventorySyncPct,
    int orderSyncPct,
    LocalDateTime lastSyncAt,
    LocalDateTime connectedAt,
    int publishedProductCount
) {}