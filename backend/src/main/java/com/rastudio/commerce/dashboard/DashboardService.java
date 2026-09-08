package com.rastudio.commerce.dashboard;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import com.rastudio.commerce.marketplace.MarketplaceName;

/**
 * Phase 4 — Executive Dashboard.
 *
 * Reads real numbers straight out of the schema.sql tables (customer_order,
 * order_item, product, product_marketplace_listing, inventory_item,
 * marketplace_connection, notification) via JdbcTemplate rather than JPA
 * entities. Product/Inventory/Order/CRM/Marketplace each get their own
 * entity model in their own phase (5–10) — this phase only needs to READ
 * aggregates from tables that already exist, so introducing a full JPA
 * entity graph for those modules here would both overreach this phase's
 * scope and risk conflicting with how those later phases model the same
 * tables. Every query is explicitly organization_id-scoped using the
 * authenticated caller's tenant id (never a value supplied by the client).
 */
@Service
public class DashboardService {

  private final JdbcTemplate jdbc;

  public DashboardService(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  /* --------------------------------------------------------------------
     Public records (response shapes) — nested here the same way
     AuthService nests its request/response records.
     -------------------------------------------------------------------- */
  public record TrendPoint(String label, BigDecimal value) {}
  public record MarketplacePerformance(String marketplace, Integer orderSyncPct) {}
  public record TopProduct(Long productId, String name, long quantitySold) {}
  public record MetricDelta(BigDecimal value, Double deltaPct, String deltaDirection) {}
  public record CountDelta(long value, Double deltaPct, String deltaDirection) {}
  public record RecentActivityItem(Long id, String title, String category, String linkModule, Long linkEntityId, LocalDateTime createdAt) {}

  public record DashboardSummary(
      MetricDelta totalRevenue,
      CountDelta totalOrders,
      CountDelta activeCustomers,
      long productsListed,
      long lowStockCount,
      Double avgMarketplaceSyncPct,
      List<MarketplacePerformance> marketplacePerformance,
      List<TrendPoint> revenueTrend,
      List<TrendPoint> salesTrendWeek,
      List<TopProduct> topProducts) {}

  /* --------------------------------------------------------------------
     GET /api/dashboard/summary
     -------------------------------------------------------------------- */
  public DashboardSummary summary(Long organizationId, String rangeParam, String marketplaceParam) {
    int rangeDays = parseRange(rangeParam);
    MarketplaceName marketplace = parseMarketplace(marketplaceParam);

    LocalDateTime windowStart = LocalDateTime.now().minusDays(rangeDays);
    LocalDateTime windowEnd = LocalDateTime.now();
    LocalDateTime previousStart = windowStart.minusDays(rangeDays);

    RevenueAndOrders current = revenueAndOrders(organizationId, windowStart, windowEnd, marketplace);
    RevenueAndOrders previous = revenueAndOrders(organizationId, previousStart, windowStart, marketplace);
    long currentCustomers = activeCustomers(organizationId, windowStart, windowEnd, marketplace);
    long previousCustomers = activeCustomers(organizationId, previousStart, windowStart, marketplace);

    MetricDelta revenue = new MetricDelta(current.revenue(), percentChange(previous.revenue(), current.revenue()),
        direction(previous.revenue(), current.revenue()));
    CountDelta orders = new CountDelta(current.orderCount(), percentChange(previous.orderCount(), current.orderCount()),
        direction(previous.orderCount(), current.orderCount()));
    CountDelta customers = new CountDelta(currentCustomers, percentChange(previousCustomers, currentCustomers),
        direction(previousCustomers, currentCustomers));

    return new DashboardSummary(
        revenue,
        orders,
        customers,
        productsListed(organizationId, marketplace),
        lowStockCount(organizationId),
        avgMarketplaceSyncPct(organizationId, marketplace),
        marketplacePerformance(organizationId, marketplace),
        revenueTrendLast12Months(organizationId),
        salesTrendLast7Days(organizationId),
        topProducts(organizationId, windowStart, windowEnd, marketplace));
  }

  /* --------------------------------------------------------------------
     GET /api/dashboard/recent-activity
     -------------------------------------------------------------------- */
  public List<RecentActivityItem> recentActivity(Long organizationId, int limit) {
    String sql = "SELECT id, title, category, link_module, link_entity_id, created_at " +
        "FROM notification WHERE organization_id = ? ORDER BY created_at DESC LIMIT ?";
    return jdbc.query(sql, (rs, i) -> new RecentActivityItem(
        rs.getLong("id"),
        rs.getString("title"),
        rs.getString("category"),
        rs.getString("link_module"),
        (Long) rs.getObject("link_entity_id"),
        rs.getTimestamp("created_at").toLocalDateTime()
    ), organizationId, limit);
  }

  /* ======================================================================
     Internal query helpers
     ====================================================================== */

  private record RevenueAndOrders(BigDecimal revenue, long orderCount) {}

  private RevenueAndOrders revenueAndOrders(Long orgId, LocalDateTime from, LocalDateTime to, MarketplaceName marketplace) {
    StringBuilder sql = new StringBuilder(
        "SELECT COALESCE(SUM(CASE WHEN status <> 'CANCELLED' THEN total_amount ELSE 0 END), 0) AS revenue, " +
        "COUNT(*) AS order_count FROM customer_order WHERE organization_id = ? AND placed_at >= ? AND placed_at < ?");
    List<Object> params = new ArrayList<>(List.of(orgId, from, to));
    appendMarketplaceFilter(sql, params, marketplace);
    Map<String, Object> row = jdbc.queryForMap(sql.toString(), params.toArray());
    return new RevenueAndOrders((BigDecimal) row.get("revenue"), ((Number) row.get("order_count")).longValue());
  }

  private long activeCustomers(Long orgId, LocalDateTime from, LocalDateTime to, MarketplaceName marketplace) {
    StringBuilder sql = new StringBuilder(
        "SELECT COUNT(DISTINCT customer_id) FROM customer_order WHERE organization_id = ? AND placed_at >= ? AND placed_at < ?");
    List<Object> params = new ArrayList<>(List.of(orgId, from, to));
    appendMarketplaceFilter(sql, params, marketplace);
    Long count = jdbc.queryForObject(sql.toString(), Long.class, params.toArray());
    return count == null ? 0L : count;
  }

  private long productsListed(Long orgId, MarketplaceName marketplace) {
    if (marketplace == null) {
      Long count = jdbc.queryForObject(
          "SELECT COUNT(*) FROM product WHERE organization_id = ? AND status = 'PUBLISHED'", Long.class, orgId);
      return count == null ? 0L : count;
    }
    Long count = jdbc.queryForObject(
        "SELECT COUNT(DISTINCT l.product_id) FROM product_marketplace_listing l " +
        "JOIN product p ON p.id = l.product_id " +
        "WHERE p.organization_id = ? AND l.marketplace_name = ? AND l.status = 'PUBLISHED'",
        Long.class, orgId, marketplace.name());
    return count == null ? 0L : count;
  }

  private long lowStockCount(Long orgId) {
    Long count = jdbc.queryForObject(
        "SELECT COUNT(*) FROM inventory_item ii " +
        "JOIN product_variant pv ON pv.id = ii.product_variant_id " +
        "WHERE pv.organization_id = ? AND ii.stock_quantity <= ii.low_stock_threshold",
        Long.class, orgId);
    return count == null ? 0L : count;
  }

  private List<MarketplacePerformance> marketplacePerformance(Long orgId, MarketplaceName marketplace) {
    StringBuilder sql = new StringBuilder(
        "SELECT marketplace_name, order_sync_pct FROM marketplace_connection " +
        "WHERE organization_id = ? AND status <> 'DISCONNECTED'");
    List<Object> params = new ArrayList<>(List.of((Object) orgId));
    if (marketplace != null) {
      sql.append(" AND marketplace_name = ?");
      params.add(marketplace.name());
    }
    return jdbc.query(sql.toString(), (rs, i) ->
        new MarketplacePerformance(rs.getString("marketplace_name"), rs.getInt("order_sync_pct")), params.toArray());
  }

  private Double avgMarketplaceSyncPct(Long orgId, MarketplaceName marketplace) {
    List<MarketplacePerformance> rows = marketplacePerformance(orgId, marketplace);
    if (rows.isEmpty()) return null;
    double avg = rows.stream().mapToInt(MarketplacePerformance::orderSyncPct).average().orElse(0);
    return Math.round(avg * 10.0) / 10.0;
  }

  private List<TrendPoint> revenueTrendLast12Months(Long orgId) {
    LocalDate startMonth = YearMonth.now().minusMonths(11).atDay(1);
    List<Map<String, Object>> rows = jdbc.queryForList(
        "SELECT DATE_FORMAT(placed_at, '%Y-%m') AS ym, " +
        "COALESCE(SUM(CASE WHEN status <> 'CANCELLED' THEN total_amount ELSE 0 END), 0) AS revenue " +
        "FROM customer_order WHERE organization_id = ? AND placed_at >= ? GROUP BY ym",
        orgId, startMonth.atStartOfDay());
    Map<String, BigDecimal> byMonth = new LinkedHashMap<>();
    for (Map<String, Object> row : rows) byMonth.put((String) row.get("ym"), (BigDecimal) row.get("revenue"));

    List<TrendPoint> points = new ArrayList<>();
    DateTimeFormatter keyFmt = DateTimeFormatter.ofPattern("yyyy-MM");
    DateTimeFormatter labelFmt = DateTimeFormatter.ofPattern("MMM");
    for (int i = 11; i >= 0; i--) {
      YearMonth ym = YearMonth.now().minusMonths(i);
      BigDecimal value = byMonth.getOrDefault(ym.format(keyFmt), BigDecimal.ZERO);
      points.add(new TrendPoint(ym.atDay(1).format(labelFmt), value));
    }
    return points;
  }

  private List<TrendPoint> salesTrendLast7Days(Long orgId) {
    LocalDate start = LocalDate.now().minusDays(6);
    List<Map<String, Object>> rows = jdbc.queryForList(
        "SELECT DATE(co.placed_at) AS d, COALESCE(SUM(oi.quantity), 0) AS units " +
        "FROM order_item oi JOIN customer_order co ON co.id = oi.order_id " +
        "WHERE co.organization_id = ? AND co.placed_at >= ? GROUP BY d",
        orgId, start.atStartOfDay());
    Map<LocalDate, Long> byDay = new LinkedHashMap<>();
    for (Map<String, Object> row : rows) {
      LocalDate d = ((java.sql.Date) row.get("d")).toLocalDate();
      byDay.put(d, ((Number) row.get("units")).longValue());
    }
    List<TrendPoint> points = new ArrayList<>();
    DateTimeFormatter labelFmt = DateTimeFormatter.ofPattern("EEE");
    for (int i = 6; i >= 0; i--) {
      LocalDate d = LocalDate.now().minusDays(i);
      points.add(new TrendPoint(d.format(labelFmt), BigDecimal.valueOf(byDay.getOrDefault(d, 0L))));
    }
    return points;
  }

  private List<TopProduct> topProducts(Long orgId, LocalDateTime from, LocalDateTime to, MarketplaceName marketplace) {
    StringBuilder sql = new StringBuilder(
        "SELECT p.id AS product_id, p.name AS name, SUM(oi.quantity) AS qty " +
        "FROM order_item oi " +
        "JOIN customer_order co ON co.id = oi.order_id " +
        "JOIN product_variant pv ON pv.id = oi.product_variant_id " +
        "JOIN product p ON p.id = pv.product_id " +
        "WHERE co.organization_id = ? AND co.placed_at >= ? AND co.placed_at < ?");
    List<Object> params = new ArrayList<>(List.of(orgId, from, to));
    appendMarketplaceFilter(sql, params, marketplace, "co.marketplace_name");
    sql.append(" GROUP BY p.id, p.name ORDER BY qty DESC LIMIT 5");
    return jdbc.query(sql.toString(), (rs, i) ->
        new TopProduct(rs.getLong("product_id"), rs.getString("name"), rs.getLong("qty")), params.toArray());
  }

  /* ======================================================================
     Small shared helpers
     ====================================================================== */

  private void appendMarketplaceFilter(StringBuilder sql, List<Object> params, MarketplaceName marketplace) {
    appendMarketplaceFilter(sql, params, marketplace, "marketplace_name");
  }

  private void appendMarketplaceFilter(StringBuilder sql, List<Object> params, MarketplaceName marketplace, String column) {
    if (marketplace != null) {
      sql.append(" AND ").append(column).append(" = ?");
      params.add(marketplace.name());
    }
  }

  private int parseRange(String rangeParam) {
    if ("7".equals(rangeParam)) return 7;
    if ("90".equals(rangeParam)) return 90;
    return 30;
  }

  private MarketplaceName parseMarketplace(String marketplaceParam) {
    if (marketplaceParam == null || marketplaceParam.isBlank() || "All".equalsIgnoreCase(marketplaceParam)) return null;
    try {
      return MarketplaceName.valueOf(marketplaceParam.trim().toUpperCase());
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  private Double percentChange(BigDecimal previous, BigDecimal current) {
    if (previous == null || previous.compareTo(BigDecimal.ZERO) == 0) {
      return current != null && current.compareTo(BigDecimal.ZERO) > 0 ? 100.0 : 0.0;
    }
    return current.subtract(previous).divide(previous, 4, RoundingMode.HALF_UP)
        .multiply(BigDecimal.valueOf(100)).setScale(1, RoundingMode.HALF_UP).doubleValue();
  }

  private Double percentChange(long previous, long current) {
    return percentChange(BigDecimal.valueOf(previous), BigDecimal.valueOf(current));
  }

  private String direction(BigDecimal previous, BigDecimal current) {
    return current.compareTo(previous) >= 0 ? "up" : "down";
  }

  private String direction(long previous, long current) {
    return current >= previous ? "up" : "down";
  }
}