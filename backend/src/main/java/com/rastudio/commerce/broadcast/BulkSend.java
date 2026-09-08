package com.rastudio.commerce.broadcast;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Phase 22 — Bulk Sharing / Broadcast. Maps the existing `bulk_send` table (database/schema.sql) — one row per
 * "Send Bulk Broadcast" click. content is a single free-text field (the schema has no separate template-id
 * column), so BulkSendService composes it up front from the chosen WhatsApp template and/or typed Email
 * content — see BulkSendService#buildContentSummary — and stores that composed text here for the report page
 * and audit trail; per-recipient sending still goes through the real WhatsApp/Email provider/template
 * machinery, not this stored copy.
 */
@Entity
@Table(name = "bulk_send")
public class BulkSend {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "organization_id", nullable = false)
  public Long organizationId;

  @Column(nullable = false, columnDefinition = "TEXT")
  public String content;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  public BulkSendChannel channel;

  @Column(name = "list_size", nullable = false)
  public Integer listSize = 0;

  @Column(name = "sent_count", nullable = false)
  public Integer sentCount = 0;

  @Column(name = "delivered_count", nullable = false)
  public Integer deliveredCount = 0;

  @Column(name = "failed_count", nullable = false)
  public Integer failedCount = 0;

  @Column(name = "opted_out_count", nullable = false)
  public Integer optedOutCount = 0;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  public BulkSendStatus status = BulkSendStatus.PENDING;

  @Column(name = "created_at", updatable = false)
  public LocalDateTime createdAt;

  @PrePersist
  void onCreate() { createdAt = LocalDateTime.now(); }
}
