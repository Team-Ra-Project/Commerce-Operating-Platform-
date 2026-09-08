package com.rastudio.commerce.product;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "product")
public class Product {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "organization_id", nullable = false)
  public Long organizationId;

  @Column(name = "category_id")
  public Long categoryId;

  @Column(nullable = false)
  public String name;

  @Column(columnDefinition = "TEXT")
  public String description;

  @Column(name = "base_price", nullable = false, precision = 12, scale = 2)
  public BigDecimal basePrice = BigDecimal.ZERO;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  public ProductStatus status = ProductStatus.DRAFT;

  @Column(name = "image_path")
  public String imagePath;

  @Column(name = "created_by")
  public Long createdBy;

  @Column(name = "created_at", updatable = false)
  public LocalDateTime createdAt;

  @Column(name = "updated_at")
  public LocalDateTime updatedAt;

  @PrePersist
  void onCreate() { createdAt = LocalDateTime.now(); updatedAt = createdAt; }

  @PreUpdate
  void onUpdate() { updatedAt = LocalDateTime.now(); }
}
