package com.rastudio.commerce.competitor.provider;

import com.rastudio.commerce.competitor.CompetitorTrackedProduct;

/**
 * Roadmap Phase 12: "If automatic external scraping/API access is unavailable: Implement provider/adapter
 * architecture and mock provider. Do not implement unauthorized scraping where marketplace rules prohibit
 * it." No competitor-site credentials or scraping approval exist anywhere in this project, so real scraping
 * is out of scope here — this interface exists so a future real provider (an approved price-comparison API,
 * for instance) can be dropped in as a Spring bean without CompetitorService or the scheduled job changing at
 * all, the same seam MarketplaceAdapter gives marketplace sync in Phase 5.
 */
public interface CompetitorPriceProvider {
  /** Returns a freshly observed competitor price for the given tracked listing. */
  CheckedPrice checkPrice(CompetitorTrackedProduct tracked);
}
