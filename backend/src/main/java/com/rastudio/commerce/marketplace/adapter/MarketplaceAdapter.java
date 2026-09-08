package com.rastudio.commerce.marketplace.adapter;
import com.rastudio.commerce.marketplace.MarketplaceConnection; import com.rastudio.commerce.marketplace.MarketplaceName;

/**
 * One implementation per marketplace (see IMPLEMENTATION_ROADMAP.md Phase 5: "MarketplaceService -> ShopifyAdapter,
 * AmazonAdapter, ..."). MarketplaceService only depends on this interface, so a marketplace can move from the
 * sandbox MockMarketplaceAdapter to a real API-backed implementation later (once real credentials/API access are
 * available) without changing anything above the adapter layer.
 */
public interface MarketplaceAdapter {
  MarketplaceName marketplace();
  ConnectionTestResult testConnection(MarketplaceConnection connection, DecryptedCredentials credentials);
  SyncResult sync(MarketplaceConnection connection, DecryptedCredentials credentials);
}