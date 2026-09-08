package com.rastudio.commerce.dashboard;

import com.rastudio.commerce.security.TenantPrincipal;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Phase 4 — Executive Dashboard.
 * Available to every authenticated role (view-only), per
 * docs/BUTTON_ACTIONS.md — "Date Range Filter / Marketplace Filter /
 * Refresh Dashboard ... All roles (view)". No extra role check here beyond
 * the global `.anyRequest().authenticated()` already set in SecurityConfig.
 */
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

  private final DashboardService dashboardService;

  public DashboardController(DashboardService dashboardService) {
    this.dashboardService = dashboardService;
  }

  /**
   * range: "7" | "30" | "90" (defaults to 30 if missing/invalid)
   * marketplace: "All" or one of MarketplaceName (case-insensitive); defaults to All if missing/invalid
   */
  @GetMapping("/summary")
  public DashboardService.DashboardSummary summary(
      @AuthenticationPrincipal TenantPrincipal principal,
      @RequestParam(name = "range", required = false, defaultValue = "30") String range,
      @RequestParam(name = "marketplace", required = false, defaultValue = "All") String marketplace) {
    return dashboardService.summary(principal.organizationId(), range, marketplace);
  }

  @GetMapping("/recent-activity")
  public List<DashboardService.RecentActivityItem> recentActivity(
      @AuthenticationPrincipal TenantPrincipal principal,
      @RequestParam(name = "limit", required = false, defaultValue = "10") int limit) {
    int safeLimit = Math.max(1, Math.min(limit, 50));
    return dashboardService.recentActivity(principal.organizationId(), safeLimit);
  }
}