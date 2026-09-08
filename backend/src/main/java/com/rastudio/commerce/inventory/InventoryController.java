package com.rastudio.commerce.inventory;

import com.rastudio.commerce.common.ApiException;
import com.rastudio.commerce.inventory.InventoryDtos.*;
import com.rastudio.commerce.security.TenantPrincipal;
import com.rastudio.commerce.user.UserRole;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** Phase 7 — Inventory Management. Endpoint-to-button mapping is in docs/BUTTON_ACTIONS.md section 4. */
@RestController
public class InventoryController {

  private final InventoryService service;
  public InventoryController(InventoryService service) { this.service = service; }

  @GetMapping("/api/inventory/warehouses")
  public List<WarehouseDto> listWarehouses(@AuthenticationPrincipal TenantPrincipal p) {
    return service.listWarehouses(p.organizationId());
  }

  @PostMapping("/api/inventory/warehouses")
  @ResponseStatus(HttpStatus.CREATED)
  public WarehouseDto createWarehouse(@AuthenticationPrincipal TenantPrincipal p, @RequestBody WarehouseRequest request) {
    requireRole(p, UserRole.BUSINESS_OWNER_ADMIN, UserRole.OPERATIONS_MANAGER);
    return service.createWarehouse(p.organizationId(), request);
  }

  /** GET /api/inventory?warehouseId= — the inventory list, with "View by Warehouse" as an optional filter.
   *  Reads are open to any authenticated tenant member (Support/Analyst/etc. may reasonably need to check
   *  stock), matching the read-access precedent already set in ProductController. */
  @GetMapping("/api/inventory")
  public InventoryListResponse list(@AuthenticationPrincipal TenantPrincipal p,
      @RequestParam(required = false) Long warehouseId) {
    return service.listInventory(p.organizationId(), warehouseId);
  }

  @GetMapping(value = "/api/inventory/export", produces = "text/csv")
  public ResponseEntity<String> export(@AuthenticationPrincipal TenantPrincipal p,
      @RequestParam(required = false) Long warehouseId) {
    requireRole(p, UserRole.BUSINESS_OWNER_ADMIN, UserRole.OPERATIONS_MANAGER, UserRole.ANALYST_VIEWER);
    String csv = service.exportCsv(p.organizationId(), warehouseId);
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType("text/csv"))
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"inventory-report.csv\"")
        .body(csv);
  }

  /** POST /api/inventory/stock-count — "Update Stock Count" (covers both stock-in and manual adjustment). */
  @PostMapping("/api/inventory/stock-count")
  public InventoryRowDto stockCount(@AuthenticationPrincipal TenantPrincipal p, @RequestBody StockCountRequest request) {
    requireRole(p, UserRole.BUSINESS_OWNER_ADMIN, UserRole.OPERATIONS_MANAGER, UserRole.WAREHOUSE_STAFF);
    return service.recordStockCount(p.organizationId(), p.userId(), request);
  }

  /** PUT /api/inventory/threshold — "Set Low-Stock Threshold". */
  @PutMapping("/api/inventory/threshold")
  public InventoryRowDto setThreshold(@AuthenticationPrincipal TenantPrincipal p, @RequestBody ThresholdRequest request) {
    requireRole(p, UserRole.BUSINESS_OWNER_ADMIN, UserRole.OPERATIONS_MANAGER);
    return service.setThreshold(p.organizationId(), request);
  }

  @GetMapping("/api/inventory/movements")
  public List<MovementDto> movements(@AuthenticationPrincipal TenantPrincipal p,
      @RequestParam Long variantId, @RequestParam Long warehouseId,
      @RequestParam(defaultValue = "30") int limit) {
    return service.movements(p.organizationId(), variantId, warehouseId, limit);
  }

  private void requireRole(TenantPrincipal p, UserRole... allowed) {
    for (UserRole r : allowed) if (p.role() == r) return;
    throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "You do not have permission to perform this action");
  }
}
