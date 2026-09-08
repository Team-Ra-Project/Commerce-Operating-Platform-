package com.rastudio.commerce.automation;

import java.time.LocalDateTime;

public class AutomationDtos {

  public record CreateRuleRequest(String name, String triggerType, String conditionText, String actionType) {}

  public record UpdateRuleRequest(String name, String triggerType, String conditionText, String actionType) {}

  public record RuleDto(
      Long id, String name, String triggerType, String conditionText, String actionType,
      boolean isActive, long runCount, LocalDateTime createdAt, LocalDateTime updatedAt) {}

  public record RunLogEntryDto(Long id, String result, LocalDateTime ranAt) {}
}
