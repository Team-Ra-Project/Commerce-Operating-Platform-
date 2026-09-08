package com.rastudio.commerce.returns;

import com.rastudio.commerce.common.ApiException;
import com.rastudio.commerce.returns.ReturnDtos.*;
import com.rastudio.commerce.security.TenantPrincipal;
import com.rastudio.commerce.user.UserRole;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** Phase 9 — Returns & Shipping Management. Endpoint-to-button mapping is in docs/BUTTON_ACTIONS.md section 5a. */
@RestController
@RequestMapping("/api/returns")
public class ReturnController {

  private final ReturnService service;
  public ReturnController(ReturnService service) { this.service = service; }

  /** Stands in for the real customer-facing return request channel — see ReturnService#create. */
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public ReturnDtos.ReturnSummaryDto create(@AuthenticationPrincipal TenantPrincipal p, @RequestBody CreateReturnRequest request) {
    requireRole(p, UserRole.BUSINESS_OWNER_ADMIN, UserRole.OPERATIONS_MANAGER, UserRole.SUPPORT_CRM_AGENT);
    return service.create(p.organizationId(), request);
  }

  /** "Returns queue" — open read for any authenticated tenant member, matching the read-access precedent set
   *  in Product/Order; only the state-changing actions below are role-restricted. */
  @GetMapping
  public List<ReturnDtos.ReturnSummaryDto> list(@AuthenticationPrincipal TenantPrincipal p, @RequestParam(required = false) String status) {
    ReturnStatus statusFilter = parseStatus(status);
    return service.list(p.organizationId(), statusFilter);
  }

  @GetMapping("/{id}")
  public ReturnDtos.ReturnSummaryDto get(@AuthenticationPrincipal TenantPrincipal p, @PathVariable Long id) {
    return service.get(p.organizationId(), id);
  }

  /** "Approve Return Request" — Support Agent only. */
  @PostMapping("/{id}/approve")
  public ReturnDtos.ReturnSummaryDto approve(@AuthenticationPrincipal TenantPrincipal p, @PathVariable Long id) {
    requireRole(p, UserRole.SUPPORT_CRM_AGENT, UserRole.BUSINESS_OWNER_ADMIN);
    return service.approve(p.organizationId(), id);
  }

  /** "Reject Return Request" — Support Agent only. */
  @PostMapping("/{id}/reject")
  public ReturnDtos.ReturnSummaryDto reject(@AuthenticationPrincipal TenantPrincipal p, @PathVariable Long id, @RequestBody RejectRequest request) {
    requireRole(p, UserRole.SUPPORT_CRM_AGENT, UserRole.BUSINESS_OWNER_ADMIN);
    return service.reject(p.organizationId(), id, request);
  }

  /** "Log Item Received" — Warehouse Staff only. */
  @PostMapping("/{id}/receive")
  public ReturnDtos.ReturnSummaryDto receive(@AuthenticationPrincipal TenantPrincipal p, @PathVariable Long id, @RequestBody ReceiveItemRequest request) {
    requireRole(p, UserRole.WAREHOUSE_STAFF);
    return service.receiveItem(p.organizationId(), id, request);
  }

  /** "Approve Refund/Replacement" — Support Agent only. */
  @PostMapping("/{id}/resolve")
  public ReturnDtos.ReturnSummaryDto resolve(@AuthenticationPrincipal TenantPrincipal p, @PathVariable Long id, @RequestBody ResolveRequest request) {
    requireRole(p, UserRole.SUPPORT_CRM_AGENT, UserRole.BUSINESS_OWNER_ADMIN);
    return service.resolve(p.organizationId(), id, request);
  }

  /** "Close Case" — Support Agent only. */
  @PostMapping("/{id}/close")
  public ReturnDtos.ReturnSummaryDto close(@AuthenticationPrincipal TenantPrincipal p, @PathVariable Long id) {
    requireRole(p, UserRole.SUPPORT_CRM_AGENT, UserRole.BUSINESS_OWNER_ADMIN);
    return service.close(p.organizationId(), id);
  }

  private ReturnStatus parseStatus(String raw) {
    if (raw == null || raw.isBlank()) return null;
    try {
      return ReturnStatus.valueOf(raw.trim().toUpperCase());
    } catch (IllegalArgumentException e) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Invalid status: " + raw);
    }
  }

  private void requireRole(TenantPrincipal p, UserRole... allowed) {
    for (UserRole r : allowed) if (p.role() == r) return;
    throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "You do not have permission to perform this action");
  }
}
