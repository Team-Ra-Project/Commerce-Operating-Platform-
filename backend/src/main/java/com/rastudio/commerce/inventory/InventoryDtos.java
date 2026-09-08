package com.rastudio.commerce.inventory;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public class InventoryDtos {

  public record WarehouseDto(Long id, String name, String location, int capacityPct) {}

  public record WarehouseRequest(String name, String location, Integer capacityPct) {}

  /** One row per (variant, warehouse) pair the org has products/warehouses for. Rows with no matching
   *  inventory_item yet are synthesized with zero stock (see InventoryService#listInventory) — a variant that
   *  has never been stocked at a given warehouse still shows up as 0/0, the same way a not-yet-authorized
   *  marketplace shows up as NOT_CONNECTED in Phase 5, rather than being missing entirely. */
  public record InventoryRowDto(
      Long variantId,
      Long productId,
      String productName,
      String sku,
      Long warehouseId,
      String warehouseName,
      int stockQuantity,
      int reservedQuantity,
      int availableQuantity,
      int lowStockThreshold,
      String status,
      BigDecimal price) {}

  public record MovementDto(
      Long id,
      String movementType,
      int quantityDelta,
      String referenceType,
      Long referenceId,
      LocalDateTime createdAt) {}

  /** Body for "Update Stock Count". quantityDelta > 0 is received stock (STOCK_IN); quantityDelta < 0 is a
   *  manual downward correction (MANUAL_ADJUSTMENT) — the same single button the Button & Action doc describes
   *  ("Record incoming stock or manual adjustment") maps to one endpoint, split into the correct movement type
   *  by the sign of the delta. */
  public record StockCountRequest(Long variantId, Long warehouseId, Integer quantityDelta, String note) {}

  public record ThresholdRequest(Long variantId, Long warehouseId, Integer lowStockThreshold) {}

  public record InventorySummaryDto(long totalUnits, long lowStockCount, long outOfStockCount, int warehouseCount) {}

  public record InventoryListResponse(InventorySummaryDto summary, List<WarehouseDto> warehouses, List<InventoryRowDto> rows) {}
}
