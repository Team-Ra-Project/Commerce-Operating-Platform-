package com.rastudio.commerce.automation;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AutomationRunLogRepository extends JpaRepository<AutomationRunLog, Long> {
  List<AutomationRunLog> findByAutomationRuleIdOrderByRanAtDesc(Long automationRuleId);

  void deleteByAutomationRuleId(Long automationRuleId);
}
