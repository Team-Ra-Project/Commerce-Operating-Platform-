package com.rastudio.commerce.broadcast;

import jakarta.persistence.*;

/**
 * Maps `bulk_send_item` — one row per recipient in a bulk_send. customerId is set when the recipient is a
 * known `customer` row (segment-sourced, or a CSV contact matched by email/phone); contactReference is set
 * instead when the recipient came from a CSV upload with no matching customer on file (schema comment:
 * "email/phone when not a known customer (CSV upload)"). Exactly one of the two is ever set.
 */
@Entity
@Table(name = "bulk_send_item")
public class BulkSendItem {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "bulk_send_id", nullable = false)
  public Long bulkSendId;

  @Column(name = "customer_id")
  public Long customerId;

  @Column(name = "contact_reference", length = 190)
  public String contactReference;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  public BulkSendItemStatus status = BulkSendItemStatus.PENDING;

  @Column(name = "failure_reason", length = 300)
  public String failureReason;
}
