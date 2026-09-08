package com.rastudio.commerce.competitor.provider;

import com.rastudio.commerce.competitor.CompetitorTrackedProduct;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.stereotype.Component;

/**
 * Sandbox price-check provider used for every tracked competitor listing until a real, credential/approval
 * backed provider is registered (see CompetitorPriceProvider). It never makes a network call or visits a
 * competitor's site; it simulates typical day-to-day price movement with a small random walk around the last
 * known competitor price, the same "real code, mock data source" stance MockMarketplaceAdapter takes for
 * marketplace sync in Phase 5.
 */
@Component
public class MockCompetitorPriceProvider implements CompetitorPriceProvider {

  @Override
  public CheckedPrice checkPrice(CompetitorTrackedProduct tracked) {
    double driftPct = ThreadLocalRandom.current().nextDouble(-0.06, 0.06); // +/- 6% typical day-to-day drift
    BigDecimal newPrice = tracked.competitorPrice
        .multiply(BigDecimal.valueOf(1 + driftPct))
        .setScale(2, RoundingMode.HALF_UP);
    if (newPrice.signum() <= 0) {
      newPrice = tracked.competitorPrice; // guard against a pathological drift on a near-zero price
    }
    return new CheckedPrice(newPrice, "Sandbox check — no live API/scrape was made (mock provider).");
  }
}
