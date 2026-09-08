package com.rastudio.commerce.competitor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class CompetitorDtos {

  public record AddCompetitorProductRequest(
      Long linkedProductId, String competitorName, String competitorListingRef, BigDecimal competitorPrice) {}

  public record SetThresholdRequest(Integer alertThresholdPct) {}

  public record RepriceRequest(BigDecimal newPrice) {}

  public record PriceHistoryPointDto(BigDecimal yourPrice, BigDecimal competitorPrice, LocalDateTime recordedAt) {}

  /** trend is computed from the two most recent price_history points for this listing ("up" = competitor
   *  price rose since the previous check, "down" = fell, "flat" = first check yet or no change). gapPct is
   *  how far the competitor's price sits below the business's own price, as a percentage of the business's
   *  price — the same figure alertThresholdPct is compared against. alertTriggered is true once that gap
   *  meets or exceeds the configured threshold, for the UI to flag ("Review the alert and decide whether to
   *  adjust pricing" per FULL_WORKFLOW.md Module 10) without this module writing anything to the shared
   *  `notification` table — Phase 14's documented notification categories (low stock, sync error, order
   *  update, return update, campaign, automation, integration) don't include a competitor-pricing category,
   *  so this alert surfaces through the API/UI only, not the notification bell. */
  public record CompetitorSummaryDto(
      Long id, Long linkedProductId, String linkedProductName, String category, String competitorName,
      String competitorListingRef, BigDecimal yourPrice, BigDecimal competitorPrice, Integer alertThresholdPct,
      BigDecimal gapPct, String trend, boolean alertTriggered, LocalDateTime createdAt) {}
}
