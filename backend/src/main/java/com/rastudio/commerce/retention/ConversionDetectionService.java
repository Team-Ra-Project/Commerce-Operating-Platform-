package com.rastudio.commerce.retention;

import com.rastudio.commerce.automation.AutomationEvent;
import com.rastudio.commerce.automation.AutomationService;
import com.rastudio.commerce.automation.TriggerType;
import com.rastudio.commerce.customer.Customer;
import com.rastudio.commerce.customer.CustomerRepository;
import com.rastudio.commerce.notification.NotificationService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Phase 20 — "Conversion Detection: system tracks if customer makes a purchase after receiving campaign
 * message; Stop journey after conversion." Called from OrderService right after a new order is persisted
 * (see OrderService#ingest) — this is the one place in the codebase a new order for a customer is created, so
 * it's the correct, non-invasive hook point rather than a polling job. Also fires the Phase 13 CAMPAIGN_EVENT
 * automation trigger on each conversion, so an org's own automation rules can react to it too.
 */
@Service
public class ConversionDetectionService {

  private final CampaignEnrollmentRepository enrollments;
  private final CampaignRepository campaigns;
  private final CustomerRepository customers;
  private final AutomationService automation;
  private final NotificationService notifications;

  public ConversionDetectionService(CampaignEnrollmentRepository enrollments, CampaignRepository campaigns,
      CustomerRepository customers, AutomationService automation, NotificationService notifications) {
    this.enrollments = enrollments;
    this.campaigns = campaigns;
    this.customers = customers;
    this.automation = automation;
    this.notifications = notifications;
  }

  @Transactional
  public void recordOrderPlaced(Long customerId, BigDecimal orderTotal) {
    // customer.id is globally unique (not reused across organizations), so this lookup can't cross tenants
    // even though campaign_enrollment carries no organization_id column of its own.
    List<CampaignEnrollment> active = enrollments.findByCustomerIdAndConvertedAtIsNull(customerId);
    for (CampaignEnrollment enrollment : active) {
      enrollment.convertedAt = LocalDateTime.now();
      enrollments.save(enrollment);
      campaigns.findById(enrollment.campaignId).ifPresent(c -> {
        c.convertedCount = c.convertedCount + 1;
        c.revenueAttributed = c.revenueAttributed.add(orderTotal == null ? BigDecimal.ZERO : orderTotal);
        campaigns.save(c);
        fireCampaignEvent(c, customerId);
      });
    }
  }

  private void fireCampaignEvent(Campaign campaign, Long customerId) {
    Customer customer = customers.findById(customerId).orElse(null);
    String summary = "Customer converted on campaign \"" + campaign.name + "\"";
    automation.fireEvent(new AutomationEvent(campaign.organizationId, TriggerType.CAMPAIGN_EVENT, summary,
        Map.of("campaign", campaign.name, "goal", campaign.goal.name()),
        customer == null ? null : customer.email, customer == null ? null : customer.fullName,
        "retention", campaign.id,
        customer == null ? null : customer.phone, customer != null && Boolean.TRUE.equals(customer.whatsappOptIn)));
    notifications.create(campaign.organizationId, summary, "CAMPAIGN", "retention", campaign.id);
  }
}
