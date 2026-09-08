package com.rastudio.commerce.marketplace.adapter;
import com.rastudio.commerce.marketplace.MarketplaceName;
import org.springframework.stereotype.Component;

/** Sandbox adapter for MAGENTO. See MockMarketplaceAdapter for behavior; swap for a real MAGENTO API client here once credentials are available. */
@Component
public class MagentoAdapter extends MockMarketplaceAdapter {
  public MagentoAdapter() { super(MarketplaceName.MAGENTO); }
}