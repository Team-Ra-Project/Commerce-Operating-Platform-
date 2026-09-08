package com.rastudio.commerce.retention;

import com.rastudio.commerce.messaging.MessageChannel;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "message_log")
public class MessageLog {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "organization_id", nullable = false)
  public Long organizationId;

  @Column(name = "campaign_id")
  public Long campaignId;

  @Column(name = "message_template_id")
  public Long messageTemplateId;

  @Column(name = "customer_id")
  public Long customerId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  public MessageChannel channel;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  public MessageLogStatus status = MessageLogStatus.QUEUED;

  @Column(name = "provider_status_detail")
  public String providerStatusDetail;

  @Column(name = "sent_at")
  public LocalDateTime sentAt;

  @Column(name = "created_at", updatable = false)
  public LocalDateTime createdAt;

  @PrePersist
  void onCreate() { createdAt = LocalDateTime.now(); }
}
