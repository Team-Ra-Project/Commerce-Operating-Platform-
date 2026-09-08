package com.rastudio.commerce.retention;

import com.rastudio.commerce.messaging.MessageChannel;
import jakarta.persistence.*;

@Entity
@Table(name = "campaign_step")
public class CampaignStep {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "campaign_id", nullable = false)
  public Long campaignId;

  @Column(name = "step_order", nullable = false)
  public Integer stepOrder;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  public MessageChannel channel;

  @Column(name = "message_template_id", nullable = false)
  public Long messageTemplateId;

  @Column(name = "delay_days", nullable = false)
  public Integer delayDays = 0;
}
