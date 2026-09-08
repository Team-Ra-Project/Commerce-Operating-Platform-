package com.rastudio.commerce.automation;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AutomationRuleRepository extends JpaRepository<AutomationRule, Long> {
  List<AutomationRule> findByOrganizationIdOrderByCreatedAtDesc(Long organizationId);

  Optional<AutomationRule> findByIdAndOrganizationId(Long id, Long organizationId);

  /** Used by the rule engine (AutomationService#fireEvent) to find this organization's active rules for a
   *  firing trigger type. */
  List<AutomationRule> findByOrganizationIdAndTriggerTypeAndIsActiveTrue(Long organizationId, TriggerType triggerType);
}
