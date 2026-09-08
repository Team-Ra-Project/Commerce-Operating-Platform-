package com.rastudio.commerce.marketplace;
import jakarta.persistence.*; import java.time.LocalDateTime;

@Entity @Table(name = "marketplace_sync_log")
public class MarketplaceSyncLog {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
  @Column(name = "marketplace_connection_id", nullable = false) public Long marketplaceConnectionId;
  @Column(nullable = false) public String event;
  @Column(name = "is_error", nullable = false) public Boolean isError = false;
  @Column(name = "created_at", updatable = false) public LocalDateTime createdAt;
  @PrePersist void created() { createdAt = LocalDateTime.now(); }
}