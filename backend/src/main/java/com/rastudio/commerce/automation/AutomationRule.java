package com.rastudio.commerce.automation;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** Mirrors the existing `automation_rule` table (see database/schema.sql) — no schema changes. conditionText
 *  is free text (e.g. "VIP", "Fashion") matched as a case-insensitive substring against the firing event's
 *  context tags in AutomationService#matches — a deliberately simple heuristic, not a full condition DSL,
 *  since schema.sql only gives this rule a single VARCHAR(300) column to hold it in. */
@Entity
@Table(name = "automation_rule")
public class AutomationRule {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "organization_id", nullable = false)
  public Long organizationId;

  @Column(nullable = false)
  public String name;

  @Enumerated(EnumType.STRING)
  @Column(name = "trigger_type", nullable = false)
  public TriggerType triggerType;

  @Column(name = "condition_text")
  public String conditionText;

  @Enumerated(EnumType.STRING)
  @Column(name = "action_type", nullable = false)
  public ActionType actionType;

  @Column(name = "is_active", nullable = false)
  public Boolean isActive = true;

  @Column(name = "run_count", nullable = false)
  public Long runCount = 0L;

  @Column(name = "created_at", updatable = false)
  public LocalDateTime createdAt;

  @Column(name = "updated_at")
  public LocalDateTime updatedAt;

  @PrePersist
  void onCreate() { createdAt = LocalDateTime.now(); updatedAt = createdAt; }

  @PreUpdate
  void onUpdate() { updatedAt = LocalDateTime.now(); }
}
