package com.rastudio.commerce.bi;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class BiService {
  private final JdbcTemplate jdbc;
  private final BiGeneratorService generator;
  public BiService(JdbcTemplate jdbc, BiGeneratorService generator) {
    this.jdbc = jdbc;
    this.generator = generator;
  }

  public record Insight(Long id, String type, String title, String summary, String detail,
                        String relatedModule, Long relatedEntityId, String status, Timestamp generatedAt) {}
  public record ForecastPoint(Long id, String periodLabel, BigDecimal forecastedValue) {}
  public record Overview(List<Insight> insights, List<Insight> recommendations,
                         List<ForecastPoint> forecast) {}

  public Overview overview(Long orgId) {
    generator.generateForOrganizationSafely(orgId);
    List<Insight> insights = jdbc.query("SELECT id, insight_type, title, summary, detail, related_module," +
        "related_entity_id, status, generated_at FROM bi_insight WHERE organization_id=? AND status<>'DISMISSED' " +
        "ORDER BY generated_at DESC", this::mapInsight, orgId);
    List<Insight> recommendations = insights.stream().filter(x -> "RECOMMENDATION".equals(x.type())).toList();
    List<ForecastPoint> forecast = jdbc.query("SELECT fp.id, fp.period_label, fp.forecasted_value FROM bi_forecast_point fp " +
        "JOIN bi_insight i ON i.id=fp.bi_insight_id WHERE i.organization_id=? AND i.status<>'DISMISSED' " +
        "ORDER BY fp.id", (rs,i) -> new ForecastPoint(rs.getLong("id"), rs.getString("period_label"),
        rs.getBigDecimal("forecasted_value")), orgId);
    return new Overview(insights, recommendations, forecast);
  }

  public boolean dismiss(Long orgId, Long id, Long userId) {
    return jdbc.update("UPDATE bi_insight SET status='DISMISSED', acknowledged_at=CURRENT_TIMESTAMP, " +
        "acknowledged_by=? WHERE id=? AND organization_id=? AND insight_type='RECOMMENDATION' AND status<>'DISMISSED'",
        userId, id, orgId) > 0;
  }

  private Insight mapInsight(java.sql.ResultSet rs, int i) throws java.sql.SQLException {
    return new Insight(rs.getLong("id"), rs.getString("insight_type"), rs.getString("title"),
        rs.getString("summary"), rs.getString("detail"), rs.getString("related_module"),
        (Long)rs.getObject("related_entity_id"), rs.getString("status"), rs.getTimestamp("generated_at"));
  }
}