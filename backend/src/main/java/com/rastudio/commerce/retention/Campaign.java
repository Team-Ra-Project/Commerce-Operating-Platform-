package com.rastudio.commerce.retention;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "campaign")
public class Campaign {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "organization_id", nullable = false)
  public Long organizationId;

  @Column(nullable = false)
  public String name;

  @Column(name = "segment_id", nullable = false)
  public Long segmentId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  public CampaignGoal goal;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  public CampaignStatus status = CampaignStatus.DRAFT;

  @Column(name = "enrolled_count", nullable = false)
  public Integer enrolledCount = 0;

  @Column(name = "sent_count", nullable = false)
  public Integer sentCount = 0;

  @Column(name = "delivered_count", nullable = false)
  public Integer deliveredCount = 0;

  @Column(name = "opened_count", nullable = false)
  public Integer openedCount = 0;

  @Column(name = "clicked_count", nullable = false)
  public Integer clickedCount = 0;

  @Column(name = "converted_count", nullable = false)
  public Integer convertedCount = 0;

  @Column(name = "revenue_attributed", nullable = false)
  public BigDecimal revenueAttributed = BigDecimal.ZERO;

  @Column(name = "created_at", updatable = false)
  public LocalDateTime createdAt;

  @Column(name = "updated_at")
  public LocalDateTime updatedAt;

  @PrePersist
  void onCreate() { createdAt = LocalDateTime.now(); updatedAt = createdAt; }

  @PreUpdate
  void onUpdate() { updatedAt = LocalDateTime.now(); }
}
