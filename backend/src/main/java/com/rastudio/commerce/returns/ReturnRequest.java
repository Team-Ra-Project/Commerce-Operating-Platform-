package com.rastudio.commerce.returns;
import jakarta.persistence.*;
import java.time.LocalDateTime;

/** `reason` doubles as a short append-only note trail (rejection reason, inspection outcome) since
 *  return_request — unlike customer_order — has no dedicated history table in schema.sql. See
 *  ReturnService#appendNote for how entries are added and capped to the column's 300-char limit. */
@Entity
@Table(name = "return_request")
public class ReturnRequest {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "organization_id", nullable = false)
  public Long organizationId;

  @Column(name = "order_id", nullable = false)
  public Long orderId;

  @Column(nullable = false)
  public String reason;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  public ReturnStatus status = ReturnStatus.REQUESTED;

  public String resolution;

  @Column(name = "requested_at", updatable = false)
  public LocalDateTime requestedAt;

  @Column(name = "updated_at")
  public LocalDateTime updatedAt;

  @PrePersist
  void onCreate() { requestedAt = LocalDateTime.now(); updatedAt = requestedAt; }

  @PreUpdate
  void onUpdate() { updatedAt = LocalDateTime.now(); }
}
