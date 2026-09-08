package com.rastudio.commerce.competitor;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Mirrors the existing `competitor_price_history` table — one row per scheduled check (or manual reprice),
 *  giving the "View Price History" trend chart real data instead of a generated series. */
@Entity
@Table(name = "competitor_price_history")
public class CompetitorPriceHistory {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "competitor_tracked_product_id", nullable = false)
  public Long competitorTrackedProductId;

  @Column(name = "your_price", nullable = false, precision = 12, scale = 2)
  public BigDecimal yourPrice;

  @Column(name = "competitor_price", nullable = false, precision = 12, scale = 2)
  public BigDecimal competitorPrice;

  @Column(name = "recorded_at", updatable = false)
  public LocalDateTime recordedAt;

  @PrePersist
  void onCreate() { recordedAt = LocalDateTime.now(); }
}
