package com.rastudio.commerce.notification;
import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Minimal mapping of the existing `notification` table (see database/schema.sql). Phase 14 —
 * Notifications & Audit — owns this table in full (the notification center UI, read/unread state, every
 * category). This entity exists only because Phase 7 (Inventory Management) is required to raise a real
 * low-stock alert ("triggers a low-stock alert to the Ops Manager (in-app + email)" — FULL_WORKFLOW.md,
 * Module 6); it only ever writes category = 'LOW_STOCK' rows here. DashboardService (Phase 4) already reads
 * this same table for its Recent Activity feed, so alerts written here surface there automatically.
 */
@Entity
@Table(name = "notification")
public class Notification {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "organization_id", nullable = false)
  public Long organizationId;

  @Column(nullable = false)
  public String title;

  @Column(nullable = false)
  public String category;

  @Column(name = "link_module")
  public String linkModule;

  @Column(name = "link_entity_id")
  public Long linkEntityId;

  @Column(name = "is_read", nullable = false)
  public Boolean isRead = false;

  @Column(name = "created_at", updatable = false)
  public LocalDateTime createdAt;

  @PrePersist
  void onCreate() { createdAt = LocalDateTime.now(); }
}
