package com.rastudio.commerce.bi;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Phase 16 — Business Intelligence, calculation engine.
 *
 * schema.sql's comment on bi_insight says it plainly: "the database is a store of results, not the analytics
 * engine itself" — something has to be that engine, or the BI page is permanently empty. This class is it.
 * The roadmap for Phase 16 asks for exactly this, in this order, with no ML model required for v1:
 *   trend detection / product performance trends / category trends / marketplace trends / demand forecast /
 *   recommendations — computed from real order, inventory and marketplace data, written into bi_insight and
 *   bi_forecast_point, which BiService then serves read-only. Runs on a schedule (like
 *   {@code CampaignSchedulerJob}) and also eagerly on first read via {@link BiService#overview}, so a fresh
 *   organization sees real insights immediately instead of waiting for the next scheduled tick.
 *
 * To avoid spamming the feed (or fighting a user's Dismiss), a given insight (same org + type + title) is not
 * regenerated while an equivalent row already exists from the last {@link #REGENERATE_AFTER_HOURS} hours,
 * dismissed or not.
 */
@Component
public class BiGeneratorService {

  private static final Logger log = LoggerFactory.getLogger(BiGeneratorService.class);

  private static final BigDecimal MIN_TREND_REVENUE = new BigDecimal("500");
  private static final BigDecimal TREND_THRESHOLD_PCT = new BigDecimal("15");
  private static final int LOW_STOCK_FORECAST_DAYS = 14;
  private static final int MAX_RECOMMENDATIONS_PER_ORG = 5;
  private static final int MAX_TREND_INSIGHTS_PER_GROUP = 2;
  private static final int REGENERATE_AFTER_HOURS = 24;

  private final JdbcTemplate jdbc;

  public BiGeneratorService(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Scheduled(fixedDelayString = "${app.bi.scheduler-interval-ms:1800000}", initialDelay = 30000)
  public void generateAllScheduled() {
    List<Long> orgIds = jdbc.queryForList("SELECT DISTINCT organization_id FROM customer_order", Long.class);
    for (Long orgId : orgIds) {
      generateForOrganizationSafely(orgId);
    }
  }

  /** Called by {@link BiService#overview} before reading, so a brand-new organization's first visit to the
   *  BI page is never a cold empty page — it computes real insights from whatever real data already exists. */
  public void generateForOrganizationSafely(Long orgId) {
    try {
      generateForOrganization(orgId);
    } catch (Exception e) {
      log.warn("BI generation failed for org {}: {}", orgId, e.getMessage());
    }
  }

  @Transactional
  public void generateForOrganization(Long orgId) {
    generateProductTrends(orgId);
    generateCategoryTrends(orgId);
    generateMarketplaceTrends(orgId);
    generateDemandForecast(orgId);
    generateLowStockRecommendations(orgId);
  }

  /* --------------------------------------------- trend detection --------------------------------------------- */

  private record TrendRow(Long id, String name, BigDecimal recent, BigDecimal prior) {}

  private void generateProductTrends(Long orgId) {
    List<Map<String, Object>> rows = jdbc.queryForList(
        "SELECT p.id id, p.name name, " + recentPriorSelect() +
        " FROM customer_order co JOIN order_item oi ON oi.order_id=co.id" +
        " JOIN product_variant pv ON pv.id=oi.product_variant_id JOIN product p ON p.id=pv.product_id" +
        " WHERE co.organization_id=? AND co.status<>'CANCELLED' AND co.placed_at >= NOW() - INTERVAL 60 DAY" +
        " GROUP BY p.id, p.name", orgId);
    applyTrend(orgId, parseTrendRows(rows), "products", "Product");
  }

  private void generateCategoryTrends(Long orgId) {
    List<Map<String, Object>> rows = jdbc.queryForList(
        "SELECT c.id id, COALESCE(c.name,'Uncategorized') name, " + recentPriorSelect() +
        " FROM customer_order co JOIN order_item oi ON oi.order_id=co.id" +
        " JOIN product_variant pv ON pv.id=oi.product_variant_id JOIN product p ON p.id=pv.product_id" +
        " LEFT JOIN category c ON c.id=p.category_id" +
        " WHERE co.organization_id=? AND co.status<>'CANCELLED' AND co.placed_at >= NOW() - INTERVAL 60 DAY" +
        " GROUP BY c.id, name", orgId);
    applyTrend(orgId, parseTrendRows(rows), "products", "Category");
  }

  private void generateMarketplaceTrends(Long orgId) {
    List<Map<String, Object>> rows = jdbc.queryForList(
        "SELECT NULL id, co.marketplace_name name, " + recentPriorSelect() +
        " FROM customer_order co JOIN order_item oi ON oi.order_id=co.id" +
        " WHERE co.organization_id=? AND co.status<>'CANCELLED' AND co.placed_at >= NOW() - INTERVAL 60 DAY" +
        " GROUP BY co.marketplace_name", orgId);
    applyTrend(orgId, parseTrendRows(rows), "marketplace", "Marketplace");
  }

  private String recentPriorSelect() {
    return "SUM(CASE WHEN co.placed_at >= NOW() - INTERVAL 30 DAY THEN oi.line_total ELSE 0 END) recent," +
        " SUM(CASE WHEN co.placed_at >= NOW() - INTERVAL 60 DAY AND co.placed_at < NOW() - INTERVAL 30 DAY" +
        " THEN oi.line_total ELSE 0 END) prior";
  }

  private List<TrendRow> parseTrendRows(List<Map<String, Object>> rows) {
    List<TrendRow> parsed = new ArrayList<>();
    for (Map<String, Object> r : rows) {
      Object idObj = r.get("id");
      Long id = idObj == null ? null : ((Number) idObj).longValue();
      parsed.add(new TrendRow(id, String.valueOf(r.get("name")), toBigDecimal(r.get("recent")), toBigDecimal(r.get("prior"))));
    }
    return parsed;
  }

  private void applyTrend(Long orgId, List<TrendRow> rows, String relatedModule, String subjectLabel) {
    List<TrendRow> movers = rows.stream()
        .filter(r -> r.prior().compareTo(BigDecimal.ZERO) > 0)
        .filter(r -> r.recent().max(r.prior()).compareTo(MIN_TREND_REVENUE) >= 0)
        .filter(r -> percentChange(r.prior(), r.recent()).abs().compareTo(TREND_THRESHOLD_PCT) >= 0)
        .sorted(Comparator.comparing((TrendRow r) -> percentChange(r.prior(), r.recent()).abs()).reversed())
        .limit(MAX_TREND_INSIGHTS_PER_GROUP)
        .toList();

    for (TrendRow r : movers) {
      BigDecimal pct = percentChange(r.prior(), r.recent());
      boolean up = pct.signum() >= 0;
      String title = subjectLabel + " " + r.name() + (up ? " sales increased " : " sales decreased ")
          + pct.abs().toPlainString() + "% in the last 30 days";
      String summary = "Revenue moved from " + r.prior() + " to " + r.recent() + " versus the prior 30 days.";
      String detail = "Prior 30-day revenue: " + r.prior() + ". Current 30-day revenue: " + r.recent()
          + ". Change: " + pct + "%.";
      insertIfNew(orgId, "TREND", title, summary, detail, relatedModule, r.id());
    }
  }

  private static BigDecimal percentChange(BigDecimal prior, BigDecimal recent) {
    return recent.subtract(prior).divide(prior, 4, RoundingMode.HALF_UP)
        .multiply(BigDecimal.valueOf(100)).setScale(1, RoundingMode.HALF_UP);
  }

  /* --------------------------------------------- demand forecast --------------------------------------------- */

  private static final int FORECAST_WEEKS = 6; // matches the frontend's "next 6 weeks" copy on the BI page

  private void generateDemandForecast(Long orgId) {
    String title = "Revenue forecast for the next " + FORECAST_WEEKS + " weeks";
    if (alreadyGeneratedRecently(orgId, "FORECAST", title)) return;

    List<Map<String, Object>> rows = jdbc.queryForList(
        "SELECT YEARWEEK(co.placed_at,3) yw, SUM(oi.line_total) revenue" +
        " FROM customer_order co JOIN order_item oi ON oi.order_id=co.id" +
        " WHERE co.organization_id=? AND co.status<>'CANCELLED' AND co.placed_at >= NOW() - INTERVAL 8 WEEK" +
        " GROUP BY yw ORDER BY yw", orgId);
    if (rows.size() < 3) return; // not enough history for a meaningful trend line

    int n = rows.size();
    double[] y = new double[n];
    for (int i = 0; i < n; i++) y[i] = toBigDecimal(rows.get(i).get("revenue")).doubleValue();

    double sumX = 0, sumY = 0, sumXY = 0, sumXX = 0;
    for (int i = 0; i < n; i++) {
      sumX += i; sumY += y[i]; sumXY += i * y[i]; sumXX += (double) i * i;
    }
    double denom = n * sumXX - sumX * sumX;
    double slope = denom == 0 ? 0 : (n * sumXY - sumX * sumY) / denom;
    double intercept = (sumY - slope * sumX) / n;

    BigDecimal nextWeek = money(Math.max(0, intercept + slope * n));
    String summary = "Based on the last " + n + " weeks of orders, next week's revenue is projected at about " + nextWeek + ".";
    String detail = "Straight-line trend fitted over the last " + n + " weekly revenue totals (calculation-based v1 forecast, no external model).";
    Long insightId = insertInsight(orgId, "FORECAST", title, summary, detail, "analytics", null);
    for (int i = 0; i < 4; i++) {
      BigDecimal projected = money(Math.max(0, intercept + slope * (n + i)));
      insertForecastPoint(insightId, "Wk " + (i + 1), projected);
    }
  }

  /* --------------------------------------------- recommendations --------------------------------------------- */

  private record LowStockCandidate(Long productId, String label, double daysLeft) {}

  private void generateLowStockRecommendations(Long orgId) {
    List<Map<String, Object>> rows = jdbc.queryForList(
        "SELECT pv.product_id product_id, p.name product_name, pv.name variant_name," +
        " COALESCE(SUM(inv.stock_quantity - inv.reserved_quantity),0) available," +
        " (SELECT COALESCE(SUM(oi2.quantity),0) FROM order_item oi2 JOIN customer_order co2 ON co2.id=oi2.order_id" +
        "   WHERE oi2.product_variant_id=pv.id AND co2.organization_id=? AND co2.status<>'CANCELLED'" +
        "   AND co2.placed_at >= NOW() - INTERVAL 30 DAY) units_sold_30d" +
        " FROM product_variant pv JOIN product p ON p.id=pv.product_id" +
        " LEFT JOIN inventory_item inv ON inv.product_variant_id=pv.id" +
        " WHERE pv.organization_id=?" +
        " GROUP BY pv.id, pv.product_id, p.name, pv.name", orgId, orgId);

    List<LowStockCandidate> candidates = new ArrayList<>();
    for (Map<String, Object> r : rows) {
      long unitsSold = ((Number) r.get("units_sold_30d")).longValue();
      if (unitsSold <= 0) continue;
      double velocity = unitsSold / 30.0;
      long available = ((Number) r.get("available")).longValue();
      double daysLeft = available <= 0 ? 0 : available / velocity;
      if (daysLeft <= LOW_STOCK_FORECAST_DAYS) {
        String label = r.get("product_name") + " (" + r.get("variant_name") + ")";
        candidates.add(new LowStockCandidate(((Number) r.get("product_id")).longValue(), label, daysLeft));
      }
    }
    candidates.sort(Comparator.comparingDouble(LowStockCandidate::daysLeft));

    for (LowStockCandidate c : candidates.stream().limit(MAX_RECOMMENDATIONS_PER_ORG).toList()) {
      int days = (int) Math.round(c.daysLeft());
      String title = days <= 0
          ? "Inventory for " + c.label() + " is out of stock but still selling"
          : "Inventory for " + c.label() + " may run low within " + days + " day" + (days == 1 ? "" : "s");
      String summary = days <= 0
          ? "Current stock is exhausted while demand continues in the last 30 days."
          : "At the current 30-day sales pace, stock is projected to run out in about " + days + " days.";
      String detail = "Based on units sold in the last 30 days versus units currently available across all warehouses.";
      insertIfNew(orgId, "RECOMMENDATION", title, summary, detail, "inventory", c.productId());
    }
  }

  /* --------------------------------------------------- storage --------------------------------------------------- */

  private boolean alreadyGeneratedRecently(Long orgId, String type, String title) {
    Integer count = jdbc.queryForObject(
        "SELECT COUNT(*) FROM bi_insight WHERE organization_id=? AND insight_type=? AND title=?" +
        " AND generated_at >= NOW() - INTERVAL ? HOUR", Integer.class, orgId, type, title, REGENERATE_AFTER_HOURS);
    return count != null && count > 0;
  }

  private void insertIfNew(Long orgId, String type, String title, String summary, String detail,
      String relatedModule, Long relatedEntityId) {
    if (alreadyGeneratedRecently(orgId, type, title)) return;
    insertInsight(orgId, type, title, summary, detail, relatedModule, relatedEntityId);
  }

  private Long insertInsight(Long orgId, String type, String title, String summary, String detail,
      String relatedModule, Long relatedEntityId) {
    KeyHolder kh = new GeneratedKeyHolder();
    jdbc.update(con -> {
      PreparedStatement ps = con.prepareStatement(
          "INSERT INTO bi_insight (organization_id, insight_type, title, summary, detail, related_module," +
          " related_entity_id, status, generated_at) VALUES (?,?,?,?,?,?,?,'ACTIVE',CURRENT_TIMESTAMP)",
          Statement.RETURN_GENERATED_KEYS);
      ps.setLong(1, orgId);
      ps.setString(2, type);
      ps.setString(3, title.length() > 255 ? title.substring(0, 255) : title);
      ps.setString(4, summary != null && summary.length() > 500 ? summary.substring(0, 500) : summary);
      ps.setString(5, detail);
      ps.setString(6, relatedModule);
      if (relatedEntityId != null) ps.setLong(7, relatedEntityId); else ps.setNull(7, Types.BIGINT);
      return ps;
    }, kh);
    return kh.getKey().longValue();
  }

  private void insertForecastPoint(Long insightId, String periodLabel, BigDecimal value) {
    jdbc.update("INSERT INTO bi_forecast_point (bi_insight_id, period_label, forecasted_value) VALUES (?,?,?)",
        insightId, periodLabel, value);
  }

  private static BigDecimal money(double v) {
    return BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP);
  }

  private static BigDecimal toBigDecimal(Object o) {
    if (o == null) return BigDecimal.ZERO;
    if (o instanceof BigDecimal bd) return bd;
    return BigDecimal.valueOf(((Number) o).doubleValue());
  }
}
