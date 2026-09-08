package com.rastudio.commerce.inventory;
import jakarta.persistence.*;
import java.time.LocalDateTime;

/** Per roadmap Phase 7: "Inventory changes must create inventory_movement records. Never silently change
 *  stock without recording the movement." Every InventoryService method that touches stock_quantity or
 *  reserved_quantity writes exactly one of these rows in the same transaction. */
@Entity
@Table(name = "inventory_movement")
public class InventoryMovement {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "inventory_item_id", nullable = false)
  public Long inventoryItemId;

  @Enumerated(EnumType.STRING)
  @Column(name = "movement_type", nullable = false)
  public MovementType movementType;

  @Column(name = "quantity_delta", nullable = false)
  public Integer quantityDelta;

  @Column(name = "reference_type")
  public String referenceType; // ORDER, RETURN, MANUAL

  @Column(name = "reference_id")
  public Long referenceId;

  @Column(name = "performed_by")
  public Long performedBy;

  @Column(name = "created_at", updatable = false)
  public LocalDateTime createdAt;

  @PrePersist
  void onCreate() { createdAt = LocalDateTime.now(); }
}
