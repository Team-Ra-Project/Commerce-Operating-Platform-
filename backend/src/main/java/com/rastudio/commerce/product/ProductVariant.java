package com.rastudio.commerce.product;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "product_variant")
public class ProductVariant {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "product_id", nullable = false)
  public Long productId;

  @Column(name = "organization_id", nullable = false)
  public Long organizationId;

  @Column(nullable = false)
  public String name;

  @Column(nullable = false)
  public String sku;

  @Column(name = "price_override", precision = 12, scale = 2)
  public BigDecimal priceOverride;

  @Column(name = "created_at", updatable = false)
  public LocalDateTime createdAt;

  @PrePersist
  void onCreate() { createdAt = LocalDateTime.now(); }
}
