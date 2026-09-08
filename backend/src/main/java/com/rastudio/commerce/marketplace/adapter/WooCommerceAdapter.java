package com.rastudio.commerce.marketplace.adapter;
import com.rastudio.commerce.marketplace.MarketplaceName;
import org.springframework.stereotype.Component;

/** Sandbox adapter for WOOCOMMERCE. See MockMarketplaceAdapter for behavior; swap for a real WOOCOMMERCE API client here once credentials are available. */
@Component
public class WooCommerceAdapter extends MockMarketplaceAdapter {
  public WooCommerceAdapter() { super(MarketplaceName.WOOCOMMERCE); }
}