package com.rastudio.commerce.marketplace.adapter;
import com.rastudio.commerce.marketplace.MarketplaceName;
import org.springframework.stereotype.Component;

/** Sandbox adapter for SHOPIFY. See MockMarketplaceAdapter for behavior; swap for a real SHOPIFY API client here once credentials are available. */
@Component
public class ShopifyAdapter extends MockMarketplaceAdapter {
  public ShopifyAdapter() { super(MarketplaceName.SHOPIFY); }
}