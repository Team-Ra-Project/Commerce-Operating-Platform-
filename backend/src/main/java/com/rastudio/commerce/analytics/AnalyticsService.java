package com.rastudio.commerce.analytics;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class AnalyticsService {
  private final JdbcTemplate jdbc;

  public AnalyticsService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

  public record Summary(BigDecimal revenue, long orders, long units, BigDecimal averageOrderValue) {}
  public record Breakdown(String name, BigDecimal revenue, long orders, long units) {}
  public record Trend(String label, BigDecimal revenue, long orders) {}
  public record Report(Summary summary, List<Breakdown> categories, List<Breakdown> marketplaces,
                       List<Trend> trend) {}

  public Report report(Long orgId, String type, String range, String marketplace, String category, Long warehouseId) {
    int days = "7".equals(range) ? 7 : "90".equals(range) ? 90 : 30;
    LocalDateTime from = LocalDateTime.now().minusDays(days);
    List<Object> params = new ArrayList<>(List.of(orgId, from));
    String joins = " FROM customer_order co JOIN order_item oi ON oi.order_id=co.id " +
        "JOIN product_variant pv ON pv.id=oi.product_variant_id JOIN product p ON p.id=pv.product_id " +
        "LEFT JOIN category c ON c.id=p.category_id ";
    String where = " WHERE co.organization_id=? AND co.placed_at>=? AND co.status<>'CANCELLED'";
    where = filters(where, params, marketplace, category, warehouseId);
    Map<String,Object> s = jdbc.queryForMap("SELECT COALESCE(SUM(oi.line_total),0) revenue, " +
        "COUNT(DISTINCT co.id) orders, COALESCE(SUM(oi.quantity),0) units" + joins + where, params.toArray());
    BigDecimal revenue = decimal(s.get("revenue"));
    long orders = number(s.get("orders")), units = number(s.get("units"));
    Summary summary = new Summary(revenue, orders, units,
        orders == 0 ? BigDecimal.ZERO : revenue.divide(BigDecimal.valueOf(orders), 2, java.math.RoundingMode.HALF_UP));
    return new Report(summary, breakdown(orgId, from, marketplace, category, warehouseId, "c.name"),
        breakdown(orgId, from, marketplace, category, warehouseId, "co.marketplace_name"),
        trend(orgId, from, marketplace, category, warehouseId));
  }

  private List<Breakdown> breakdown(Long orgId, LocalDateTime from, String marketplace, String category,
                                    Long warehouseId, String group) {
    List<Object> p = new ArrayList<>(List.of(orgId, from));
    String joins = " FROM customer_order co JOIN order_item oi ON oi.order_id=co.id " +
        "JOIN product_variant pv ON pv.id=oi.product_variant_id JOIN product pr ON pr.id=pv.product_id " +
        "LEFT JOIN category c ON c.id=pr.category_id ";
    String w = filters(" WHERE co.organization_id=? AND co.placed_at>=? AND co.status<>'CANCELLED'", p,
        marketplace, category, warehouseId);
    return jdbc.query("SELECT COALESCE(" + group + ",'Uncategorized') name, COALESCE(SUM(oi.line_total),0) revenue," +
        "COUNT(DISTINCT co.id) orders, COALESCE(SUM(oi.quantity),0) units" + joins + w +
        " GROUP BY " + group + " ORDER BY revenue DESC", (rs,i) -> new Breakdown(rs.getString("name"),
        rs.getBigDecimal("revenue"), rs.getLong("orders"), rs.getLong("units")), p.toArray());
  }

  private List<Trend> trend(Long orgId, LocalDateTime from, String marketplace, String category, Long warehouseId) {
    List<Object> p = new ArrayList<>(List.of(orgId, from));
    String joins = " FROM customer_order co JOIN order_item oi ON oi.order_id=co.id " +
        "JOIN product_variant pv ON pv.id=oi.product_variant_id JOIN product pr ON pr.id=pv.product_id " +
        "LEFT JOIN category c ON c.id=pr.category_id ";
    String w = filters(" WHERE co.organization_id=? AND co.placed_at>=? AND co.status<>'CANCELLED'", p,
        marketplace, category, warehouseId);
    return jdbc.query("SELECT DATE_FORMAT(co.placed_at,'%Y-%m-%d') label, COALESCE(SUM(oi.line_total),0) revenue," +
        "COUNT(DISTINCT co.id) orders" + joins + w + " GROUP BY label ORDER BY label", (rs,i) ->
        new Trend(rs.getString("label"), rs.getBigDecimal("revenue"), rs.getLong("orders")), p.toArray());
  }

  private String filters(String where, List<Object> p, String marketplace, String category, Long warehouseId) {
    if (marketplace != null && !marketplace.isBlank() && !"All".equalsIgnoreCase(marketplace)) {
      where += " AND co.marketplace_name=?"; p.add(marketplace.toUpperCase());
    }
    if (category != null && !category.isBlank() && !"All".equalsIgnoreCase(category)) {
      where += " AND c.name=?"; p.add(category);
    }
    if (warehouseId != null) {
      where += " AND EXISTS (SELECT 1 FROM inventory_item ii WHERE ii.product_variant_id=pv.id AND ii.warehouse_id=?)";
      p.add(warehouseId);
    }
    return where;
  }
  private static long number(Object o) { return o == null ? 0 : ((Number)o).longValue(); }
  private static BigDecimal decimal(Object o) { return o == null ? BigDecimal.ZERO : (BigDecimal)o; }
}