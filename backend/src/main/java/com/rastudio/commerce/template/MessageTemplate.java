package com.rastudio.commerce.template;

import com.rastudio.commerce.messaging.MessageChannel;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "message_template")
public class MessageTemplate {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "organization_id", nullable = false)
  public Long organizationId;

  @Column(nullable = false)
  public String name;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  public MessageChannel channel;

  @Column(nullable = false, columnDefinition = "TEXT")
  public String content;

  @Enumerated(EnumType.STRING)
  @Column(name = "approval_status", nullable = false)
  public ApprovalStatus approvalStatus = ApprovalStatus.DRAFT;

  /** Set once Submit for Approval succeeds against a real WhatsApp provider; lets the approval poller and the
   *  inbound webhook correlate a provider-side template back to this row. Null for Email templates and for
   *  WhatsApp templates never submitted. */
  @Column(name = "provider_template_id")
  public String providerTemplateId;

  @Column(name = "created_at", updatable = false)
  public LocalDateTime createdAt;

  @Column(name = "updated_at")
  public LocalDateTime updatedAt;

  @PrePersist
  void onCreate() { createdAt = LocalDateTime.now(); updatedAt = createdAt; }

  @PreUpdate
  void onUpdate() { updatedAt = LocalDateTime.now(); }
}
