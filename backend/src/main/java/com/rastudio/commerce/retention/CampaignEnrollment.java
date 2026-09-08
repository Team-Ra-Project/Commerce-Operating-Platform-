package com.rastudio.commerce.retention;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "campaign_enrollment", uniqueConstraints = @UniqueConstraint(columnNames = {"campaign_id", "customer_id"}))
public class CampaignEnrollment {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "campaign_id", nullable = false)
  public Long campaignId;

  @Column(name = "customer_id", nullable = false)
  public Long customerId;

  @Column(name = "current_step", nullable = false)
  public Integer currentStep = 0;

  @Column(name = "enrolled_at")
  public LocalDateTime enrolledAt;

  @Column(name = "converted_at")
  public LocalDateTime convertedAt;

  @PrePersist
  void onCreate() { enrolledAt = LocalDateTime.now(); }
}
