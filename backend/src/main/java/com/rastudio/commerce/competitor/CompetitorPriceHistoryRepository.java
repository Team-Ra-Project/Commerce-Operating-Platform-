package com.rastudio.commerce.competitor;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompetitorPriceHistoryRepository extends JpaRepository<CompetitorPriceHistory, Long> {
  List<CompetitorPriceHistory> findByCompetitorTrackedProductIdOrderByRecordedAtAsc(Long trackedProductId);
}
