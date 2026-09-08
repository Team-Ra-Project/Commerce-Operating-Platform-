package com.rastudio.commerce.marketplace.adapter;
import com.rastudio.commerce.marketplace.MarketplaceName;
import org.springframework.stereotype.Component;

/** Sandbox adapter for ETSY. See MockMarketplaceAdapter for behavior; swap for a real ETSY API client here once credentials are available. */
@Component
public class EtsyAdapter extends MockMarketplaceAdapter {
  public EtsyAdapter() { super(MarketplaceName.ETSY); }
}