package com.rastudio.commerce.competitor;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Mirrors the existing `competitor_tracked_product` table (see database/schema.sql) — no schema changes.
 *  One row = one competitor listing linked to one of the organization's own products. `yourPrice` is a
 *  point-in-time copy of the linked product's price at last check/reprice (not a live join), so price history
 *  rows and gap calculations remain meaningful even if the underlying Product's basePrice is later edited
 *  from Product Management without going through this module's Reprice action. */
@Entity
@Table(name = "competitor_tracked_product")
public class CompetitorTrackedProduct {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "organization_id", nullable = false)
  public Long organizationId;

  @Column(name = "linked_product_id", nullable = false)
  public Long linkedProductId;

  @Column(name = "competitor_name", nullable = false)
  public String competitorName;

  @Column(name = "competitor_listing_ref")
  public String competitorListingRef;

  @Column(name = "your_price", nullable = false, precision = 12, scale = 2)
  public BigDecimal yourPrice;

  @Column(name = "competitor_price", nullable = false, precision = 12, scale = 2)
  public BigDecimal competitorPrice;

  @Column(name = "alert_threshold_pct", nullable = false)
  public Integer alertThresholdPct = 10;

  @Column(name = "created_at", updatable = false)
  public LocalDateTime createdAt;

  @PrePersist
  void onCreate() { createdAt = LocalDateTime.now(); }
}
