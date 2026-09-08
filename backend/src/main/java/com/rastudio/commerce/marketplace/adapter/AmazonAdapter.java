package com.rastudio.commerce.marketplace.adapter;
import com.rastudio.commerce.marketplace.MarketplaceName;
import org.springframework.stereotype.Component;

/** Sandbox adapter for AMAZON. See MockMarketplaceAdapter for behavior; swap for a real AMAZON API client here once credentials are available. */
@Component
public class AmazonAdapter extends MockMarketplaceAdapter {
  public AmazonAdapter() { super(MarketplaceName.AMAZON); }
}