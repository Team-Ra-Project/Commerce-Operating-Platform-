package com.rastudio.commerce.inventory;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface InventoryItemRepository extends JpaRepository<InventoryItem, Long> {
  Optional<InventoryItem> findByProductVariantIdAndWarehouseId(Long productVariantId, Long warehouseId);

  List<InventoryItem> findByProductVariantIdIn(List<Long> productVariantIds);

  List<InventoryItem> findByWarehouseIdIn(List<Long> warehouseIds);

  /** Locks the row for the duration of the enclosing transaction so two concurrent order confirmations (or a
   *  confirmation racing a manual stock update) can never both read the same available quantity and both
   *  succeed — the roadmap's "Ensure inventory reservation is atomic. Prevent overselling." requirement. Must
   *  only be called from inside a @Transactional method. */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  Optional<InventoryItem> findWithLockByProductVariantIdAndWarehouseId(Long productVariantId, Long warehouseId);
}
