package com.rastudio.commerce.retention;

import com.rastudio.commerce.customer.Customer;
import com.rastudio.commerce.customer.CustomerRepository;
import com.rastudio.commerce.mail.MailService;
import com.rastudio.commerce.messaging.MessageChannel;
import com.rastudio.commerce.messaging.WhatsAppProvider;
import com.rastudio.commerce.template.MessageTemplate;
import com.rastudio.commerce.template.MessageTemplateRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Phase 20 — fires every ACTIVE campaign's due journey steps. Runs every minute; in a real, non-mock
 * deployment this cadence is more than adequate for day-granularity delays (delay_days), and MOCK mode makes
 * near-instant local testing possible (a "Day 0" step becomes due the moment a customer is enrolled).
 *
 * Per-customer step timing: delay_days is an absolute day-offset from that customer's own enrolled_at (not
 * "days since the previous step"), so "Day 0 / Day 2 / Day 5" in a journey builder maps directly to each
 * step's delay_days value — see CampaignService's class javadoc for why, including how pausing interacts
 * with this.
 */
@Component
public class CampaignSchedulerJob {

  private static final Logger log = LoggerFactory.getLogger(CampaignSchedulerJob.class);

  private final CampaignRepository campaigns;
  private final CampaignStepRepository steps;
  private final CampaignEnrollmentRepository enrollments;
  private final MessageLogRepository messageLogs;
  private final MessageTemplateRepository templates;
  private final CustomerRepository customers;
  private final WhatsAppProvider whatsAppProvider;
  private final MailService mailService;
  private final com.rastudio.commerce.template.TemplateService templateService;

  public CampaignSchedulerJob(CampaignRepository campaigns, CampaignStepRepository steps,
      CampaignEnrollmentRepository enrollments, MessageLogRepository messageLogs,
      MessageTemplateRepository templates, CustomerRepository customers, WhatsAppProvider whatsAppProvider,
      MailService mailService, com.rastudio.commerce.template.TemplateService templateService) {
    this.campaigns = campaigns;
    this.steps = steps;
    this.enrollments = enrollments;
    this.messageLogs = messageLogs;
    this.templates = templates;
    this.customers = customers;
    this.whatsAppProvider = whatsAppProvider;
    this.mailService = mailService;
    this.templateService = templateService;
  }

  @Scheduled(fixedDelayString = "${app.retention.scheduler-interval-ms:60000}", initialDelay = 15000)
  @Transactional
  public void processDueSteps() {
    List<Campaign> active = campaigns.findByStatus(CampaignStatus.ACTIVE);
    for (Campaign campaign : active) {
      try {
        processCampaign(campaign);
      } catch (Exception e) {
        log.error("Campaign scheduler failed for campaign {}", campaign.id, e);
      }
    }
  }

  private void processCampaign(Campaign campaign) {
    List<CampaignStep> journeySteps = steps.findByCampaignIdOrderByStepOrderAsc(campaign.id);
    if (journeySteps.isEmpty()) return;
    List<CampaignEnrollment> active = enrollments.findByCampaignIdAndConvertedAtIsNull(campaign.id);
    LocalDateTime now = LocalDateTime.now();
    boolean anySent = false;
    boolean allFinishedOrConverted = true;

    for (CampaignEnrollment enrollment : active) {
      if (enrollment.currentStep >= journeySteps.size()) continue; // already finished all steps
      CampaignStep step = journeySteps.get(enrollment.currentStep);
      LocalDateTime dueAt = enrollment.enrolledAt.plusDays(step.delayDays);
      if (now.isBefore(dueAt)) {
        allFinishedOrConverted = false;
        continue;
      }
      sendStep(campaign, enrollment, step);
      anySent = true;
      if (enrollment.currentStep < journeySteps.size()) allFinishedOrConverted = false;
    }
    // Also count already-converted or already-finished enrollments toward "is this campaign done".
    List<CampaignEnrollment> all = enrollments.findByCampaignId(campaign.id);
    boolean everyoneDone = !all.isEmpty() && all.stream()
        .allMatch(e -> e.convertedAt != null || e.currentStep >= journeySteps.size());
    if (everyoneDone) {
      campaign.status = CampaignStatus.COMPLETED;
    }
    if (anySent || everyoneDone) {
      campaigns.save(campaign);
    }
  }

  private void sendStep(Campaign campaign, CampaignEnrollment enrollment, CampaignStep step) {
    Customer customer = customers.findById(enrollment.customerId).orElse(null);
    MessageTemplate template = templates.findById(step.messageTemplateId).orElse(null);

    MessageLog logEntry = new MessageLog();
    logEntry.organizationId = campaign.organizationId;
    logEntry.campaignId = campaign.id;
    logEntry.messageTemplateId = step.messageTemplateId;
    logEntry.customerId = enrollment.customerId;
    logEntry.channel = step.channel;

    if (customer == null || template == null) {
      logEntry.status = MessageLogStatus.FAILED;
      logEntry.providerStatusDetail = "Customer or template no longer exists";
      messageLogs.save(logEntry);
      enrollment.currentStep = enrollment.currentStep + 1;
      enrollments.save(enrollment);
      return;
    }

    Map<String, String> mergeFields = Map.of(
        "customer_name", customer.fullName == null ? "" : customer.fullName,
        "product_name", "", // no single "the product" is well-defined for a multi-item order history; left blank
        "discount_code", "", // no coupon/discount-code system exists in this schema
        "order_number", "",
        "brand_name", "");

    if (step.channel == MessageChannel.WHATSAPP) {
      if (!Boolean.TRUE.equals(customer.whatsappOptIn) || customer.phone == null || customer.phone.isBlank()) {
        logEntry.status = MessageLogStatus.FAILED;
        logEntry.providerStatusDetail = "Customer has not opted in to WhatsApp (or has no phone number on file)";
      } else {
        WhatsAppProvider.ProviderSendResult result = whatsAppProvider.sendTemplateMessage(customer.phone, template.name, mergeFields);
        logEntry.status = result.accepted() ? MessageLogStatus.SENT : MessageLogStatus.FAILED;
        logEntry.providerStatusDetail = result.accepted() ? result.providerMessageId() : result.errorDetail();
        if (result.accepted()) { campaign.sentCount = campaign.sentCount + 1; }
      }
    } else {
      if (!Boolean.TRUE.equals(customer.emailOptIn) || customer.email == null || customer.email.isBlank()) {
        logEntry.status = MessageLogStatus.FAILED;
        logEntry.providerStatusDetail = "Customer has not opted in to Email (or has no email on file)";
      } else {
        String rendered = templateService.render(template.content, mergeFields);
        boolean sent = mailService.sendMarketingEmail(customer.email, campaign.name, rendered);
        logEntry.status = sent ? MessageLogStatus.SENT : MessageLogStatus.FAILED;
        logEntry.providerStatusDetail = sent ? null : "SMTP not configured or send failed";
        if (sent) { campaign.sentCount = campaign.sentCount + 1; }
      }
    }
    logEntry.sentAt = LocalDateTime.now();
    messageLogs.save(logEntry);
    enrollment.currentStep = enrollment.currentStep + 1;
    enrollments.save(enrollment);
  }
}
