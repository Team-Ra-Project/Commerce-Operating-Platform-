package com.rastudio.commerce.order;

import com.rastudio.commerce.common.ApiException;
import com.rastudio.commerce.marketplace.MarketplaceName;
import com.rastudio.commerce.order.OrderDtos.*;
import com.rastudio.commerce.security.TenantPrincipal;
import com.rastudio.commerce.user.UserRole;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** Phase 8 — Order Management. Endpoint-to-button mapping is in docs/BUTTON_ACTIONS.md section 5. */
@RestController
@RequestMapping("/api/orders")
public class OrderController {

  private final OrderService service;
  public OrderController(OrderService service) { this.service = service; }

  /** Order ingestion stands in for the real marketplace webhook receiver — see OrderService#ingest. Ops
   *  Manager/Admin can call it directly until Phase 23 wires up live webhooks. */
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public OrderDetailDto ingest(@AuthenticationPrincipal TenantPrincipal p, @RequestBody IngestOrderRequest request) {
    requireRole(p, UserRole.BUSINESS_OWNER_ADMIN, UserRole.OPERATIONS_MANAGER);
    return service.ingest(p.organizationId(), request);
  }

  /** "View Order" — open to any authenticated tenant member (Warehouse Staff need it to pack, Support/CRM
   *  need it to help customers), matching the read-access precedent already set elsewhere. */
  @GetMapping
  public List<OrderDtos.OrderSummaryDto> list(@AuthenticationPrincipal TenantPrincipal p,
      @RequestParam(required = false) String status, @RequestParam(required = false) String marketplace) {
    OrderStatus statusFilter = parseEnum(OrderStatus.class, status, "status");
    MarketplaceName marketplaceFilter = parseEnum(MarketplaceName.class, marketplace, "marketplace");
    return service.list(p.organizationId(), statusFilter, marketplaceFilter);
  }

  @GetMapping("/{id}")
  public OrderDetailDto get(@AuthenticationPrincipal TenantPrincipal p, @PathVariable Long id) {
    return service.detail(p.organizationId(), id);
  }

  /** "Confirm Order" — reviews and reserves stock. */
  @PostMapping("/{id}/confirm")
  public OrderDetailDto confirm(@AuthenticationPrincipal TenantPrincipal p, @PathVariable Long id) {
    requireRole(p, UserRole.BUSINESS_OWNER_ADMIN, UserRole.OPERATIONS_MANAGER);
    return service.confirm(p.organizationId(), id);
  }

  /** "Mark as Packed" — Warehouse Staff only, per docs/BUTTON_ACTIONS.md. */
  @PostMapping("/{id}/pack")
  public OrderDetailDto pack(@AuthenticationPrincipal TenantPrincipal p, @PathVariable Long id) {
    requireRole(p, UserRole.WAREHOUSE_STAFF);
    return service.markPacked(p.organizationId(), id);
  }

  /** "Generate Shipping Label / Book Courier". */
  @PostMapping("/{id}/ship")
  public OrderDetailDto ship(@AuthenticationPrincipal TenantPrincipal p, @PathVariable Long id,
      @RequestBody ShippingRequest request) {
    requireRole(p, UserRole.BUSINESS_OWNER_ADMIN, UserRole.OPERATIONS_MANAGER);
    return service.generateShippingLabel(p.organizationId(), id, request);
  }

  /** "Track Shipment" — refreshes/advances the sandbox courier simulation. */
  @PostMapping("/{id}/track")
  public OrderDetailDto track(@AuthenticationPrincipal TenantPrincipal p, @PathVariable Long id) {
    requireRole(p, UserRole.BUSINESS_OWNER_ADMIN, UserRole.OPERATIONS_MANAGER, UserRole.SUPPORT_CRM_AGENT);
    return service.trackShipment(p.organizationId(), id);
  }

  private <E extends Enum<E>> E parseEnum(Class<E> type, String raw, String field) {
    if (raw == null || raw.isBlank()) return null;
    try {
      return Enum.valueOf(type, raw.trim().toUpperCase());
    } catch (IllegalArgumentException e) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Invalid " + field + ": " + raw);
    }
  }

  private void requireRole(TenantPrincipal p, UserRole... allowed) {
    for (UserRole r : allowed) if (p.role() == r) return;
    throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "You do not have permission to perform this action");
  }
}
