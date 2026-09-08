package com.rastudio.commerce.automation;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** Mirrors the existing `automation_run_log` table. One row per trigger event a rule was evaluated against
 *  (fired successfully, failed, or was skipped because its condition didn't match) — "All executions must be
 *  logged" per roadmap Phase 13. */
@Entity
@Table(name = "automation_run_log")
public class AutomationRunLog {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "automation_rule_id", nullable = false)
  public Long automationRuleId;

  @Column(nullable = false, length = 300)
  public String result;

  @Column(name = "ran_at", updatable = false)
  public LocalDateTime ranAt;

  @PrePersist
  void onCreate() { ranAt = LocalDateTime.now(); }
}
