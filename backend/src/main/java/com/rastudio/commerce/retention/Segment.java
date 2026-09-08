package com.rastudio.commerce.retention;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Minimal mapping of the existing `segment` table. Phase 11 (Customer Segmentation) — the dedicated segment
 * builder UI, custom rule editing, segment analytics — has not been built in this codebase yet. This entity
 * exists only because Phase 20's `campaign.segment_id` is a required, non-null foreign key: "Select Segment"
 * (Campaign builder) cannot work without a real segment to select. The same minimal-dependency pattern this
 * project already used for Phase 6 (a read-only slice of Phase 5's marketplace_connection) is used here: a
 * real, working Segment with an honestly-computed membership size, not a mock — but no rule builder, no
 * segment editing UI, no dedicated segmentation dashboard. See SegmentService for how membership is computed.
 */
@Entity
@Table(name = "segment")
public class Segment {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "organization_id", nullable = false)
  public Long organizationId;

  @Column(nullable = false)
  public String name;

  @Enumerated(EnumType.STRING)
  @Column(name = "rule_key", nullable = false)
  public SegmentRuleKey ruleKey;

  public String description;

  @Column(name = "created_at", updatable = false)
  public LocalDateTime createdAt;

  @PrePersist
  void onCreate() { createdAt = LocalDateTime.now(); }
}
