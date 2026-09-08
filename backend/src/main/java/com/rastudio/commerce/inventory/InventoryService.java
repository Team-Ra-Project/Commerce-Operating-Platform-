package com.rastudio.commerce.inventory;

import com.rastudio.commerce.automation.AutomationEvent;
import com.rastudio.commerce.automation.AutomationService;
import com.rastudio.commerce.automation.TriggerType;
import com.rastudio.commerce.common.ApiException;
import com.rastudio.commerce.inventory.InventoryDtos.*;
import com.rastudio.commerce.mail.MailService;
import com.rastudio.commerce.notification.Notification;
import com.rastudio.commerce.notification.NotificationRepository;
import com.rastudio.commerce.organization.OrganizationRepository;
import com.rastudio.commerce.product.Product;
import com.rastudio.commerce.product.ProductRepository;
import com.rastudio.commerce.product.ProductVariant;
import com.rastudio.commerce.product.ProductVariantRepository;
import com.rastudio.commerce.user.AppUser;
import com.rastudio.commerce.user.AppUserRepository;
import com.rastudio.commerce.user.UserRole;
import com.rastudio.commerce.user.UserStatus;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Phase 7 — Inventory Management. Every method that changes stock_quantity or reserved_quantity writes an
 * inventory_movement row in the same transaction (roadmap: "Never silently change stock without recording the
 * movement"). Warehouse-scoped and org-scoped throughout: every InventoryItem is reached only via a variant
 * that belongs to the caller's organization, since inventory_item itself has no organization_id column.
 */
@Service
public class InventoryService {

  private static final String DEFAULT_WAREHOUSE_NAME = "Main Warehouse";

  private final WarehouseRepository warehouses;
  private final InventoryItemRepository items;
  private final InventoryMovementRepository movements;
  private final ProductVariantRepository variants;
  private final ProductRepository products;
  private final NotificationRepository notifications;
  private final AppUserRepository users;
  private final OrganizationRepository organizations;
  private final MailService mail;
  private final AutomationService automation;

  public InventoryService(WarehouseRepository warehouses, InventoryItemRepository items,
      InventoryMovementRepository movements, ProductVariantRepository variants, ProductRepository products,
      NotificationRepository notifications, AppUserRepository users, OrganizationRepository organizations,
      MailService mail, AutomationService automation) {
    this.automation = automation;
    this.warehouses = warehouses;
    this.items = items;
    this.movements = movements;
    this.variants = variants;
    this.products = products;
    this.notifications = notifications;
    this.users = users;
    this.organizations = organizations;
    this.mail = mail;
  }

  /* ------------------------------- warehouses ------------------------------- */

  @Transactional
  public List<WarehouseDto> listWarehouses(Long organizationId) {
    if (!warehouses.existsByOrganizationId(organizationId)) {
      Warehouse w = new Warehouse();
      w.organizationId = organizationId;
      w.name = DEFAULT_WAREHOUSE_NAME;
      w.location = null;
      w.capacityPct = 0;
      warehouses.save(w);
    }
    return warehouses.findByOrganizationIdOrderByNameAsc(organizationId).stream()
        .map(w -> new WarehouseDto(w.id, w.name, w.location, w.capacityPct))
        .toList();
  }

  @Transactional
  public WarehouseDto createWarehouse(Long organizationId, WarehouseRequest request) {
    if (request == null || request.name() == null || request.name().trim().isEmpty()) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Warehouse name is required");
    }
    String name = request.name().trim();
    if (warehouses.existsByOrganizationIdAndNameIgnoreCase(organizationId, name)) {
      throw new ApiException(HttpStatus.CONFLICT, "WAREHOUSE_ALREADY_EXISTS",
          "A warehouse named \"" + name + "\" already exists");
    }
    Warehouse w = new Warehouse();
    w.organizationId = organizationId;
    w.name = name;
    w.location = request.location();
    w.capacityPct = request.capacityPct() == null ? 0 : Math.max(0, Math.min(100, request.capacityPct()));
    warehouses.save(w);
    return new WarehouseDto(w.id, w.name, w.location, w.capacityPct);
  }

  private Warehouse requireWarehouse(Long organizationId, Long warehouseId) {
    return warehouses.findByIdAndOrganizationId(warehouseId, organizationId)
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "WAREHOUSE_NOT_FOUND", "Warehouse not found"));
  }

  /* -------------------------------- listing ---------------------------------- */

  @Transactional
  public InventoryListResponse listInventory(Long organizationId, Long warehouseFilter) {
    List<WarehouseDto> warehouseDtos = listWarehouses(organizationId);
    List<Warehouse> orgWarehouses = warehouseFilter == null
        ? warehouses.findByOrganizationIdOrderByNameAsc(organizationId)
        : List.of(requireWarehouse(organizationId, warehouseFilter));

    List<ProductVariant> orgVariants = variants.findByOrganizationIdOrderByIdAsc(organizationId);
    if (orgVariants.isEmpty() || orgWarehouses.isEmpty()) {
      return new InventoryListResponse(new InventorySummaryDto(0, 0, 0, warehouseDtos.size()), warehouseDtos, List.of());
    }

    List<Long> variantIds = orgVariants.stream().map(v -> v.id).toList();
    Map<Long, Product> productById = products.findByOrganizationIdOrderByCreatedAtDesc(organizationId).stream()
        .collect(Collectors.toMap(p -> p.id, p -> p));

    Map<String, InventoryItem> itemByKey = items.findByProductVariantIdIn(variantIds).stream()
        .filter(i -> orgWarehouses.stream().anyMatch(w -> w.id.equals(i.warehouseId)))
        .collect(Collectors.toMap(i -> i.productVariantId + ":" + i.warehouseId, i -> i));

    List<InventoryRowDto> rows = new ArrayList<>();
    long totalUnits = 0;
    long lowStock = 0;
    long outOfStock = 0;
    for (ProductVariant v : orgVariants) {
      Product p = productById.get(v.productId);
      if (p == null) continue;
      for (Warehouse w : orgWarehouses) {
        InventoryItem item = itemByKey.get(v.id + ":" + w.id);
        int stock = item == null ? 0 : item.stockQuantity;
        int reserved = item == null ? 0 : item.reservedQuantity;
        int threshold = item == null ? 10 : item.lowStockThreshold;
        int available = stock - reserved;
        String status = statusFor(available, threshold);
        if (status.equals("Low Stock")) lowStock++;
        if (status.equals("Out of Stock")) outOfStock++;
        totalUnits += stock;
        rows.add(new InventoryRowDto(v.id, p.id, p.name, v.sku, w.id, w.name, stock, reserved, available,
            threshold, status, v.priceOverride != null ? v.priceOverride : p.basePrice));
      }
    }
    return new InventoryListResponse(
        new InventorySummaryDto(totalUnits, lowStock, outOfStock, warehouseDtos.size()), warehouseDtos, rows);
  }

  public String exportCsv(Long organizationId, Long warehouseFilter) {
    InventoryListResponse data = listInventory(organizationId, warehouseFilter);
    StringBuilder csv = new StringBuilder("Product,SKU,Warehouse,Stock,Reserved,Available,Threshold,Status\n");
    for (InventoryRowDto r : data.rows()) {
      csv.append(csvEscape(r.productName())).append(',')
          .append(csvEscape(r.sku())).append(',')
          .append(csvEscape(r.warehouseName())).append(',')
          .append(r.stockQuantity()).append(',')
          .append(r.reservedQuantity()).append(',')
          .append(r.availableQuantity()).append(',')
          .append(r.lowStockThreshold()).append(',')
          .append(csvEscape(r.status())).append('\n');
    }
    return csv.toString();
  }

  private static String csvEscape(String value) {
    if (value == null) return "";
    if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
      return "\"" + value.replace("\"", "\"\"") + "\"";
    }
    return value;
  }

  private String statusFor(int available, int threshold) {
    if (available <= 0) return "Out of Stock";
    if (available <= threshold) return "Low Stock";
    return "In Stock";
  }

  /* ---------------------------- stock count / adjust -------------------------- */

  @Transactional
  public InventoryRowDto recordStockCount(Long organizationId, Long actorUserId, StockCountRequest request) {
    if (request == null || request.variantId() == null || request.warehouseId() == null
        || request.quantityDelta() == null || request.quantityDelta() == 0) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
          "variantId, warehouseId, and a non-zero quantityDelta are required");
    }
    ProductVariant variant = requireVariant(organizationId, request.variantId());
    Warehouse warehouse = requireWarehouse(organizationId, request.warehouseId());

    InventoryItem item = findOrCreateLocked(variant.id, warehouse.id);
    int previousAvailable = item.available();
    int newStock = item.stockQuantity + request.quantityDelta();
    if (newStock < 0) {
      throw new ApiException(HttpStatus.CONFLICT, "STOCK_CANNOT_BE_NEGATIVE",
          "This adjustment would take stock below zero (currently " + item.stockQuantity + ")");
    }
    item.stockQuantity = newStock;
    items.save(item);

    MovementType type = request.quantityDelta() > 0 ? MovementType.STOCK_IN : MovementType.MANUAL_ADJUSTMENT;
    writeMovement(item.id, type, request.quantityDelta(), "MANUAL", null, actorUserId);

    maybeAlertLowStock(organizationId, variant, warehouse, item, previousAvailable);
    return toRow(variant, warehouse, item);
  }

  @Transactional
  public InventoryRowDto setThreshold(Long organizationId, ThresholdRequest request) {
    if (request == null || request.variantId() == null || request.lowStockThreshold() == null
        || request.lowStockThreshold() < 0) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
          "variantId and a non-negative lowStockThreshold are required");
    }
    ProductVariant variant = requireVariant(organizationId, request.variantId());
    if (request.warehouseId() != null) {
      Warehouse warehouse = requireWarehouse(organizationId, request.warehouseId());
      InventoryItem item = findOrCreateLocked(variant.id, warehouse.id);
      item.lowStockThreshold = request.lowStockThreshold();
      items.save(item);
      return toRow(variant, warehouse, item);
    }
    // No specific warehouse given: apply the same threshold to every warehouse this variant already has a
    // stock row for, matching the original UI's single "threshold per product" mental model while still
    // storing the value per (variant, warehouse) exactly as schema.sql defines it.
    List<InventoryItem> existing = items.findByProductVariantIdIn(List.of(variant.id));
    if (existing.isEmpty()) {
      List<Warehouse> orgWarehouses = warehouses.findByOrganizationIdOrderByNameAsc(organizationId);
      Warehouse first = orgWarehouses.isEmpty() ? null : orgWarehouses.get(0);
      if (first == null) {
        throw new ApiException(HttpStatus.CONFLICT, "NO_WAREHOUSE",
            "This organization has no warehouse yet — add stock or a warehouse first");
      }
      InventoryItem item = findOrCreateLocked(variant.id, first.id);
      item.lowStockThreshold = request.lowStockThreshold();
      items.save(item);
      return toRow(variant, first, item);
    }
    InventoryItem last = null;
    Warehouse lastWarehouse = null;
    for (InventoryItem item : existing) {
      item.lowStockThreshold = request.lowStockThreshold();
      items.save(item);
      last = item;
      lastWarehouse = requireWarehouse(organizationId, item.warehouseId);
    }
    return toRow(variant, lastWarehouse, last);
  }

  public List<MovementDto> movements(Long organizationId, Long variantId, Long warehouseId, int limit) {
    requireVariant(organizationId, variantId);
    InventoryItem item = items.findByProductVariantIdAndWarehouseId(variantId, warehouseId).orElse(null);
    if (item == null) return List.of();
    return movements.findByInventoryItemIdOrderByCreatedAtDesc(item.id, PageRequest.of(0, Math.min(Math.max(limit, 1), 200)))
        .stream()
        .map(m -> new MovementDto(m.id, m.movementType.name(), m.quantityDelta, m.referenceType, m.referenceId, m.createdAt))
        .toList();
  }

  /* ============================================================================
     Package-visible helpers reused by OrderService (Phase 8) for reservation,
     deduction, and release — Phase 8 owns order status transitions but never
     touches inventory_item/inventory_movement rows directly; it always goes
     through these methods so the "never silently change stock" rule and the
     movement audit trail hold for order-driven stock changes too.
     ============================================================================ */

  @Transactional
  public InventoryItem reserveForOrder(Long variantId, Long warehouseId, int quantity, Long orderId) {
    InventoryItem item = findOrCreateLocked(variantId, warehouseId);
    if (item.available() < quantity) {
      throw new ApiException(HttpStatus.CONFLICT, "INSUFFICIENT_STOCK",
          "Not enough available stock (have " + item.available() + ", need " + quantity + ")");
    }
    item.reservedQuantity += quantity;
    items.save(item);
    writeMovement(item.id, MovementType.SALE_RESERVED, -quantity, "ORDER", orderId, null);
    return item;
  }

  @Transactional
  public void deductReserved(Long variantId, Long warehouseId, int quantity, Long orderId) {
    InventoryItem item = items.findWithLockByProductVariantIdAndWarehouseId(variantId, warehouseId)
        .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "NOT_RESERVED", "No reservation found to deduct"));
    item.stockQuantity = Math.max(0, item.stockQuantity - quantity);
    item.reservedQuantity = Math.max(0, item.reservedQuantity - quantity);
    items.save(item);
    writeMovement(item.id, MovementType.SALE_DEDUCTED, -quantity, "ORDER", orderId, null);
  }

  @Transactional
  public void releaseReservation(Long variantId, Long warehouseId, int quantity, Long orderId) {
    InventoryItem item = items.findWithLockByProductVariantIdAndWarehouseId(variantId, warehouseId)
        .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "NOT_RESERVED", "No reservation found to release"));
    item.reservedQuantity = Math.max(0, item.reservedQuantity - quantity);
    items.save(item);
    writeMovement(item.id, MovementType.RELEASE_RESERVATION, quantity, "ORDER", orderId, null);
  }

  public int availableAcrossWarehouses(Long variantId, List<Long> warehouseIds) {
    return items.findByProductVariantIdIn(List.of(variantId)).stream()
        .filter(i -> warehouseIds.contains(i.warehouseId))
        .mapToInt(InventoryItem::available)
        .sum();
  }

  /** Picks the single warehouse with the most available stock for this variant — Phase 8 reserves an entire
   *  order line from one warehouse rather than splitting it across several, since there is no "choose
   *  warehouse" step in the Order Management button spec. */
  public Optional<Long> bestWarehouseFor(Long organizationId, Long variantId, int neededQuantity) {
    List<Warehouse> orgWarehouses = warehouses.findByOrganizationIdOrderByNameAsc(organizationId);
    List<InventoryItem> existing = items.findByProductVariantIdIn(List.of(variantId));
    Map<Long, InventoryItem> byWarehouse = existing.stream().collect(Collectors.toMap(i -> i.warehouseId, i -> i));
    return orgWarehouses.stream()
        .map(w -> byWarehouse.get(w.id))
        .filter(Objects::nonNull)
        .filter(i -> i.available() >= neededQuantity)
        .max(Comparator.comparingInt(InventoryItem::available))
        .map(i -> i.warehouseId);
  }

  /** Which warehouse actually fulfilled each variant's reservation for a given order, recovered from the
   *  SALE_RESERVED inventory_movement rows written at confirm time (reference_type='ORDER'). Used by
   *  OrderService at "Mark as Packed" so the deduction hits the same warehouse the reservation was held at,
   *  without needing a warehouse_id column on order_item. */
  public Map<Long, Long> reservedWarehousesForOrder(Long orderId) {
    return movements.findByReferenceTypeAndReferenceIdAndMovementType("ORDER", orderId, MovementType.SALE_RESERVED)
        .stream()
        .map(m -> items.findById(m.inventoryItemId).orElse(null))
        .filter(Objects::nonNull)
        .collect(Collectors.toMap(i -> i.productVariantId, i -> i.warehouseId, (a, b) -> a));
  }

  /** Adds returned stock back on the shelf — Phase 9 ("Approve Refund/Replacement": "Triggers inventory update
   *  (if item passes inspection)"). A distinct RETURN_IN movement, never routed through recordStockCount, so
   *  the movement trail always shows whether units arrived via purchasing (STOCK_IN) or came back from a
   *  customer (RETURN_IN). */
  @Transactional
  public InventoryRowDto restockFromReturn(Long organizationId, Long variantId, Long warehouseId, int quantity, Long returnId) {
    if (quantity <= 0) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "quantity must be greater than 0");
    }
    ProductVariant variant = requireVariant(organizationId, variantId);
    Warehouse warehouse = requireWarehouse(organizationId, warehouseId);
    InventoryItem item = findOrCreateLocked(variant.id, warehouse.id);
    int previousAvailable = item.available();
    item.stockQuantity += quantity;
    items.save(item);
    writeMovement(item.id, MovementType.RETURN_IN, quantity, "RETURN", returnId, null);
    maybeAlertLowStock(organizationId, variant, warehouse, item, previousAvailable);
    return toRow(variant, warehouse, item);
  }

  /* --------------------------------- internals -------------------------------- */

  private ProductVariant requireVariant(Long organizationId, Long variantId) {
    return variants.findByIdAndOrganizationId(variantId, organizationId)
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "VARIANT_NOT_FOUND", "Product variant not found"));
  }

  private InventoryItem findOrCreateLocked(Long variantId, Long warehouseId) {
    return items.findWithLockByProductVariantIdAndWarehouseId(variantId, warehouseId).orElseGet(() -> {
      InventoryItem item = new InventoryItem();
      item.productVariantId = variantId;
      item.warehouseId = warehouseId;
      item.stockQuantity = 0;
      item.reservedQuantity = 0;
      item.lowStockThreshold = 10;
      return items.save(item);
    });
  }

  private void writeMovement(Long inventoryItemId, MovementType type, int quantityDelta, String referenceType,
      Long referenceId, Long performedBy) {
    InventoryMovement m = new InventoryMovement();
    m.inventoryItemId = inventoryItemId;
    m.movementType = type;
    m.quantityDelta = quantityDelta;
    m.referenceType = referenceType;
    m.referenceId = referenceId;
    m.performedBy = performedBy;
    movements.save(m);
  }

  private InventoryRowDto toRow(ProductVariant v, Warehouse w, InventoryItem item) {
    Product p = products.findByIdAndOrganizationId(v.productId, v.organizationId).orElse(null);
    int stock = item.stockQuantity;
    int reserved = item.reservedQuantity;
    int available = item.available();
    return new InventoryRowDto(v.id, v.productId, p == null ? "" : p.name, v.sku, w.id, w.name, stock, reserved,
        available, item.lowStockThreshold, statusFor(available, item.lowStockThreshold),
        v.priceOverride != null ? v.priceOverride : (p == null ? null : p.basePrice));
  }

  /** Fires only on the transition into low/out-of-stock (previousAvailable was above threshold, the new value
   *  is not), matching FULL_WORKFLOW.md's "If stock falls below threshold, triggers a low-stock alert" — not
   *  on every subsequent movement while stock is already low, which would flood the recipients. Writes a real
   *  `notification` row (category LOW_STOCK, picked up by Phase 4's Recent Activity feed) and best-effort
   *  emails every active Admin/Ops Manager in the organization. */
  private void maybeAlertLowStock(Long organizationId, ProductVariant variant, Warehouse warehouse,
      InventoryItem item, int previousAvailable) {
    int threshold = item.lowStockThreshold;
    int nowAvailable = item.available();
    boolean crossedNow = nowAvailable <= threshold && previousAvailable > threshold;
    if (!crossedNow) return;

    Product product = products.findByIdAndOrganizationId(variant.productId, organizationId).orElse(null);
    String productName = product == null ? variant.sku : product.name;

    Notification n = new Notification();
    n.organizationId = organizationId;
    n.title = productName + " (" + variant.sku + ") is low on stock at " + warehouse.name
        + " — " + nowAvailable + " available";
    n.category = "LOW_STOCK";
    n.linkModule = "inventory";
    n.linkEntityId = variant.id;
    notifications.save(n);

    String organizationName = organizations.findById(organizationId).map(o -> o.name).orElse("your workspace");
    List<AppUser> recipients = users.findByOrganizationIdOrderByCreatedAtAsc(organizationId).stream()
        .filter(u -> u.status == UserStatus.ACTIVE)
        .filter(u -> u.role == UserRole.BUSINESS_OWNER_ADMIN || u.role == UserRole.OPERATIONS_MANAGER)
        .toList();
    for (AppUser recipient : recipients) {
      mail.sendLowStockAlert(recipient.email, recipient.fullName, organizationName, productName, variant.sku,
          warehouse.name, nowAvailable, threshold);
    }

    String summary = productName + " (" + variant.sku + ") is low on stock at " + warehouse.name
        + " — " + nowAvailable + " available (threshold " + threshold + ")";
    automation.fireEvent(new AutomationEvent(organizationId, TriggerType.STOCK_BELOW_THRESHOLD, summary,
        Map.of("sku", variant.sku, "product", productName, "warehouse", warehouse.name),
        null, null, "inventory", variant.id));
  }
}
