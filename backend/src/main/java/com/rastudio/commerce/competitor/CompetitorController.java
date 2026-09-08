package com.rastudio.commerce.competitor;

import com.rastudio.commerce.common.ApiException;
import com.rastudio.commerce.competitor.CompetitorDtos.*;
import com.rastudio.commerce.security.TenantPrincipal;
import com.rastudio.commerce.user.UserRole;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** Phase 12 — Competitor Analysis. Endpoint-to-button mapping is in docs/BUTTON_ACTIONS.md section 7. */
@RestController
@RequestMapping("/api/competitors")
public class CompetitorController {

  private final CompetitorService service;
  public CompetitorController(CompetitorService service) { this.service = service; }

  /** "Add Competitor Product" + "Link to Own Product" — one combined submit per BUTTON_ACTIONS.md (the form
   *  collects both the competitor listing and the linked product before saving). Ops Manager/Admin only. */
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public CompetitorSummaryDto add(@AuthenticationPrincipal TenantPrincipal p, @RequestBody AddCompetitorProductRequest request) {
    requireRole(p, UserRole.OPERATIONS_MANAGER, UserRole.BUSINESS_OWNER_ADMIN);
    return service.addCompetitorProduct(p.organizationId(), request);
  }

  /** Competitor Analysis screen's tracked-listing table — Ops Manager, Analyst, Admin per BUTTON_ACTIONS.md. */
  @GetMapping
  public List<CompetitorSummaryDto> list(@AuthenticationPrincipal TenantPrincipal p) {
    requireRole(p, UserRole.OPERATIONS_MANAGER, UserRole.ANALYST_VIEWER, UserRole.BUSINESS_OWNER_ADMIN);
    return service.list(p.organizationId());
  }

  @GetMapping("/{id}")
  public CompetitorSummaryDto get(@AuthenticationPrincipal TenantPrincipal p, @PathVariable Long id) {
    requireRole(p, UserRole.OPERATIONS_MANAGER, UserRole.ANALYST_VIEWER, UserRole.BUSINESS_OWNER_ADMIN);
    return service.get(p.organizationId(), id);
  }

  /** "View Price History" — Ops Manager, Analyst, Admin. */
  @GetMapping("/{id}/history")
  public List<PriceHistoryPointDto> history(@AuthenticationPrincipal TenantPrincipal p, @PathVariable Long id) {
    requireRole(p, UserRole.OPERATIONS_MANAGER, UserRole.ANALYST_VIEWER, UserRole.BUSINESS_OWNER_ADMIN);
    return service.priceHistory(p.organizationId(), id);
  }

  /** "Set Alert Threshold" — Ops Manager, Admin only. */
  @PutMapping("/{id}/threshold")
  public CompetitorSummaryDto setThreshold(@AuthenticationPrincipal TenantPrincipal p, @PathVariable Long id,
      @RequestBody SetThresholdRequest request, HttpServletRequest http) {
    requireRole(p, UserRole.OPERATIONS_MANAGER, UserRole.BUSINESS_OWNER_ADMIN);
    return service.setAlertThreshold(p.organizationId(), p.userId(), id, request, http);
  }

  /** "Reprice Product" — Ops Manager, Admin only. */
  @PostMapping("/{id}/reprice")
  public CompetitorSummaryDto reprice(@AuthenticationPrincipal TenantPrincipal p, @PathVariable Long id,
      @RequestBody RepriceRequest request, HttpServletRequest http) {
    requireRole(p, UserRole.OPERATIONS_MANAGER, UserRole.BUSINESS_OWNER_ADMIN);
    return service.reprice(p.organizationId(), p.userId(), id, request, http);
  }

  private void requireRole(TenantPrincipal p, UserRole... allowed) {
    for (UserRole r : allowed) if (p.role() == r) return;
    throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "You do not have permission to perform this action");
  }
}
