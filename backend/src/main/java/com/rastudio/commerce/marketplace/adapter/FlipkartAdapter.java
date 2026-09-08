package com.rastudio.commerce.marketplace.adapter;
import com.rastudio.commerce.marketplace.MarketplaceName;
import org.springframework.stereotype.Component;

/** Sandbox adapter for FLIPKART. See MockMarketplaceAdapter for behavior; swap for a real FLIPKART API client here once credentials are available. */
@Component
public class FlipkartAdapter extends MockMarketplaceAdapter {
  public FlipkartAdapter() { super(MarketplaceName.FLIPKART); }
}