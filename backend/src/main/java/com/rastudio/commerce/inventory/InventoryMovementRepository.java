package com.rastudio.commerce.inventory;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InventoryMovementRepository extends JpaRepository<InventoryMovement, Long> {
  List<InventoryMovement> findByInventoryItemIdOrderByCreatedAtDesc(Long inventoryItemId, Pageable pageable);

  List<InventoryMovement> findByReferenceTypeAndReferenceIdAndMovementType(
      String referenceType, Long referenceId, MovementType movementType);
}
