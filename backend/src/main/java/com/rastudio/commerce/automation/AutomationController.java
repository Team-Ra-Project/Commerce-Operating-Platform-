package com.rastudio.commerce.automation;

import com.rastudio.commerce.automation.AutomationDtos.*;
import com.rastudio.commerce.common.ApiException;
import com.rastudio.commerce.security.TenantPrincipal;
import com.rastudio.commerce.user.UserRole;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** Phase 13 — Automation Engine. Endpoint-to-button mapping is in docs/BUTTON_ACTIONS.md section 8. Every
 *  action here is Ops Manager, Marketing Manager, or Admin per that doc's role column — automation touches
 *  both operational triggers (stock, returns) and marketing ones (campaigns), so both roles get full access
 *  rather than splitting the module in two. */
@RestController
@RequestMapping("/api/automation")
public class AutomationController {

  private final AutomationService service;
  public AutomationController(AutomationService service) { this.service = service; }

  /** "Create New Rule" (Select Trigger -> Add Condition -> Define Action, submitted together as one rule). */
  @PostMapping("/rules")
  @ResponseStatus(HttpStatus.CREATED)
  public RuleDto create(@AuthenticationPrincipal TenantPrincipal p, @RequestBody CreateRuleRequest request, HttpServletRequest http) {
    requireRole(p);
    return service.create(p.organizationId(), p.userId(), request, http);
  }

  @GetMapping("/rules")
  public List<RuleDto> list(@AuthenticationPrincipal TenantPrincipal p) {
    requireRole(p);
    return service.list(p.organizationId());
  }

  @GetMapping("/rules/{id}")
  public RuleDto get(@AuthenticationPrincipal TenantPrincipal p, @PathVariable Long id) {
    requireRole(p);
    return service.get(p.organizationId(), id);
  }

  /** "Edit Rule". */
  @PutMapping("/rules/{id}")
  public RuleDto update(@AuthenticationPrincipal TenantPrincipal p, @PathVariable Long id,
      @RequestBody UpdateRuleRequest request, HttpServletRequest http) {
    requireRole(p);
    return service.update(p.organizationId(), p.userId(), id, request, http);
  }

  /** "Activate Rule / Deactivate Rule (toggle)". */
  @PostMapping("/rules/{id}/toggle")
  public RuleDto toggle(@AuthenticationPrincipal TenantPrincipal p, @PathVariable Long id, HttpServletRequest http) {
    requireRole(p);
    return service.toggleActive(p.organizationId(), p.userId(), id, http);
  }

  /** "Delete Rule". */
  @DeleteMapping("/rules/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@AuthenticationPrincipal TenantPrincipal p, @PathVariable Long id, HttpServletRequest http) {
    requireRole(p);
    service.delete(p.organizationId(), p.userId(), id, http);
  }

  /** "View Run Log". */
  @GetMapping("/rules/{id}/run-log")
  public List<RunLogEntryDto> runLog(@AuthenticationPrincipal TenantPrincipal p, @PathVariable Long id) {
    requireRole(p);
    return service.runLog(p.organizationId(), id);
  }

  private void requireRole(TenantPrincipal p) {
    if (p.role() == UserRole.OPERATIONS_MANAGER || p.role() == UserRole.MARKETING_MANAGER
        || p.role() == UserRole.BUSINESS_OWNER_ADMIN) {
      return;
    }
    throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "You do not have permission to perform this action");
  }
}
