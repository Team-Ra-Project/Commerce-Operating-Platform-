package com.rastudio.commerce.marketplace;
import jakarta.persistence.*; import java.time.LocalDateTime;

@Entity @Table(name = "marketplace_connection", uniqueConstraints = @UniqueConstraint(columnNames = {"organization_id", "marketplace_name"}))
public class MarketplaceConnection {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
  @Column(name = "organization_id", nullable = false) public Long organizationId;
  @Enumerated(EnumType.STRING) @Column(name = "marketplace_name", nullable = false) public MarketplaceName marketplaceName;
  @Enumerated(EnumType.STRING) @Column(name = "auth_type", nullable = false) public MarketplaceAuthType authType;
  @Enumerated(EnumType.STRING) @Column(nullable = false) public MarketplaceConnectionStatus status = MarketplaceConnectionStatus.NOT_CONNECTED;
  @Column(name = "credential_ref") public String credentialRef; // opaque AES-GCM ciphertext (base64) — never the raw secret; see CredentialCipherService
  @Column(name = "is_mock", nullable = false) public Boolean isMock = true;
  @Column(name = "product_sync_pct", nullable = false) public Integer productSyncPct = 0;
  @Column(name = "inventory_sync_pct", nullable = false) public Integer inventorySyncPct = 0;
  @Column(name = "order_sync_pct", nullable = false) public Integer orderSyncPct = 0;
  @Column(name = "last_sync_at") public LocalDateTime lastSyncAt;
  @Column(name = "connected_at") public LocalDateTime connectedAt;
  @Column(name = "created_at", updatable = false) public LocalDateTime createdAt;
  @Column(name = "updated_at") public LocalDateTime updatedAt;
  @PrePersist void created() { createdAt = LocalDateTime.now(); updatedAt = createdAt; }
  @PreUpdate void updated() { updatedAt = LocalDateTime.now(); }
}