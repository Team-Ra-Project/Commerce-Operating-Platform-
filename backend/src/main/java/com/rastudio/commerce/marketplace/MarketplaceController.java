package com.rastudio.commerce.marketplace;
import com.rastudio.commerce.common.ApiException;
import com.rastudio.commerce.security.TenantPrincipal;
import com.rastudio.commerce.user.UserRole;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** Phase 5 — Marketplace Integration Foundation. Endpoint-to-button mapping is in docs/BUTTON_ACTIONS.md section 2. */
@RestController
@RequestMapping("/api/marketplaces")
public class MarketplaceController {
  private final MarketplaceService service;
  public MarketplaceController(MarketplaceService service) { this.service = service; }

  @GetMapping
  public List<MarketplaceConnectionDto> list(@AuthenticationPrincipal TenantPrincipal principal) {
    return service.list(principal.organizationId());
  }

  @GetMapping("/{name}")
  public MarketplaceConnectionDto get(@AuthenticationPrincipal TenantPrincipal principal, @PathVariable MarketplaceName name) {
    return service.get(principal.organizationId(), name);
  }

  @PostMapping("/{name}/authorize")
  public MarketplaceConnectionDto authorize(@AuthenticationPrincipal TenantPrincipal principal, @PathVariable MarketplaceName name,
      @RequestBody(required = false) AuthorizeRequest request, HttpServletRequest http) {
    requireRole(principal, UserRole.BUSINESS_OWNER_ADMIN, UserRole.OPERATIONS_MANAGER);
    return service.authorize(principal, name, request, http);
  }

  @PostMapping("/{name}/test-connection")
  public MarketplaceConnectionDto testConnection(@AuthenticationPrincipal TenantPrincipal principal, @PathVariable MarketplaceName name) {
    requireRole(principal, UserRole.BUSINESS_OWNER_ADMIN, UserRole.OPERATIONS_MANAGER);
    return service.testConnection(principal.organizationId(), name);
  }

  @PostMapping("/{name}/sync")
  public MarketplaceConnectionDto sync(@AuthenticationPrincipal TenantPrincipal principal, @PathVariable MarketplaceName name) {
    requireRole(principal, UserRole.BUSINESS_OWNER_ADMIN, UserRole.OPERATIONS_MANAGER);
    return service.syncNow(principal.organizationId(), name);
  }

  @DeleteMapping("/{name}")
  public MarketplaceConnectionDto disconnect(@AuthenticationPrincipal TenantPrincipal principal, @PathVariable MarketplaceName name,
      HttpServletRequest http) {
    requireRole(principal, UserRole.BUSINESS_OWNER_ADMIN);
    return service.disconnect(principal, name, http);
  }

  @GetMapping("/{name}/logs")
  public List<SyncLogDto> logs(@AuthenticationPrincipal TenantPrincipal principal, @PathVariable MarketplaceName name,
      @RequestParam(defaultValue = "50") int limit) {
    requireRole(principal, UserRole.BUSINESS_OWNER_ADMIN, UserRole.OPERATIONS_MANAGER);
    return service.logs(principal.organizationId(), name, Math.min(Math.max(limit, 1), 200));
  }

  private void requireRole(TenantPrincipal principal, UserRole... allowed) {
    for (UserRole r : allowed) if (principal.role() == r) return;
    throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "You do not have permission to perform this action");
  }
}