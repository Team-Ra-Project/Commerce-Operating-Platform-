package com.rastudio.commerce.analytics;

import com.rastudio.commerce.security.TenantPrincipal;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/analytics")
public class AnalyticsController {
  private final AnalyticsService service;
  public AnalyticsController(AnalyticsService service) { this.service = service; }

  @GetMapping("/report")
  public AnalyticsService.Report report(@AuthenticationPrincipal TenantPrincipal p,
      @RequestParam(required=false) String type, @RequestParam(required=false) String range,
      @RequestParam(required=false) String marketplace, @RequestParam(required=false) String category,
      @RequestParam(required=false) Long warehouseId) {
    return service.report(p.organizationId(), type, range, marketplace, category, warehouseId);
  }

  @GetMapping(value="/report.csv", produces="text/csv")
  public void csv(@AuthenticationPrincipal TenantPrincipal p, @RequestParam(required=false) String type,
      @RequestParam(required=false) String range, @RequestParam(required=false) String marketplace,
      @RequestParam(required=false) String category, @RequestParam(required=false) Long warehouseId,
      HttpServletResponse response) throws IOException {
    AnalyticsService.Report r = service.report(p.organizationId(), type, range, marketplace, category, warehouseId);
    response.setContentType("text/csv"); response.setHeader("Content-Disposition", "attachment; filename=analytics-report.csv");
    var out = response.getWriter();
    out.println("section,name,revenue,orders,units");
    out.printf("summary,Total,%s,%d,%d%n", r.summary().revenue(), r.summary().orders(), r.summary().units());
    for (var x : r.categories()) out.printf("category,\"%s\",%s,%d,%d%n", esc(x.name()), x.revenue(), x.orders(), x.units());
    for (var x : r.marketplaces()) out.printf("marketplace,\"%s\",%s,%d,%d%n", esc(x.name()), x.revenue(), x.orders(), x.units());
    for (var x : r.products()) out.printf("product,\"%s\",%s,%d,%d%n", esc(x.name()), x.revenue(), x.orders(), x.units());
    for (var x : r.trend()) out.printf("trend,\"%s\",%s,%d,%d%n", esc(x.label()), x.revenue(), x.orders(), 0);
    if (r.inventory() != null) {
      var inv = r.inventory().summary();
      out.printf("inventory,Total stock,0,0,%d%n", inv.totalStock());
      out.printf("inventory,Reserved,0,0,%d%n", inv.reservedStock());
      out.printf("inventory,Available,0,0,%d%n", inv.availableStock());
      out.printf("inventory,Low-stock SKUs,0,0,%d%n", inv.lowStockSkus());
      for (var x : r.inventory().byWarehouse()) out.printf("inventory_warehouse,\"%s\",0,0,%d%n", esc(x.name()), x.value());
    }
  }
  private static String esc(String s) { return s == null ? "" : s.replace("\"","\"\""); }
}