package com.rastudio.commerce.marketplace.adapter;
import com.rastudio.commerce.marketplace.MarketplaceName;
import org.springframework.stereotype.Component;

/** Sandbox adapter for MEESHO. See MockMarketplaceAdapter for behavior; swap for a real MEESHO API client here once credentials are available. */
@Component
public class MeeshoAdapter extends MockMarketplaceAdapter {
  public MeeshoAdapter() { super(MarketplaceName.MEESHO); }
}