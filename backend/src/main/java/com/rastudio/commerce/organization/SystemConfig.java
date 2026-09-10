package com.rastudio.commerce.organization;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "system_config")
public class SystemConfig {
  @Id
  @Column(name = "organization_id")
  public Long organizationId;

  @Column(name = "sync_interval", nullable = false)
  public String syncInterval = "Every 15 minutes";

  @Column(nullable = false)
  public String currency = "INR";

  @Column(name = "two_factor_enabled", nullable = false)
  public boolean twoFactorEnabled = true;

  @Column(name = "email_digest_enabled", nullable = false)
  public boolean emailDigestEnabled = true;

  @Column(name = "facebook_ads_url", length = 500)
  public String facebookAdsUrl;

  @Column(name = "updated_at")
  public LocalDateTime updatedAt;

  @PrePersist
  @PreUpdate
  void changed() {
    updatedAt = LocalDateTime.now();
  }
}
