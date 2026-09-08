package com.rastudio.commerce.inventory;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "inventory_item", uniqueConstraints = @UniqueConstraint(columnNames = {"product_variant_id", "warehouse_id"}))
public class InventoryItem {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "product_variant_id", nullable = false)
  public Long productVariantId;

  @Column(name = "warehouse_id", nullable = false)
  public Long warehouseId;

  @Column(name = "stock_quantity", nullable = false)
  public Integer stockQuantity = 0;

  @Column(name = "reserved_quantity", nullable = false)
  public Integer reservedQuantity = 0;

  @Column(name = "low_stock_threshold", nullable = false)
  public Integer lowStockThreshold = 10;

  @Column(name = "updated_at")
  public LocalDateTime updatedAt;

  @Transient
  public int available() { return stockQuantity - reservedQuantity; }

  @PrePersist
  @PreUpdate
  void touch() { updatedAt = LocalDateTime.now(); }
}
