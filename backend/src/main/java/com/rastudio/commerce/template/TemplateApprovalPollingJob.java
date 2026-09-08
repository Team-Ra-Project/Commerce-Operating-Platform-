package com.rastudio.commerce.template;

import com.rastudio.commerce.messaging.WhatsAppProvider;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Phase 19 — BUTTON_ACTIONS.md, "Submit for Approval (WhatsApp only)": "Template status shows 'Pending
 * Approval'; system polls and updates to Approved/Rejected." This is that poll.
 *
 * In MOCK mode (the default) this job runs but never finds anything: MockWhatsAppProvider resolves every
 * submission to APPROVED/REJECTED immediately, so no template is ever left at PENDING_APPROVAL with a
 * provider_template_id set. In REAL mode, a template genuinely can sit in Meta's review queue for hours to
 * days, so this job periodically calls WhatsAppProvider#getTemplateStatus for each one still pending and
 * applies whatever status Meta/the BSP returns — the same transition TemplateService#submitForApproval already
 * applies for an immediate answer, just resolved later instead of synchronously.
 */
@Component
public class TemplateApprovalPollingJob {

  private static final Logger log = LoggerFactory.getLogger(TemplateApprovalPollingJob.class);

  private final MessageTemplateRepository templates;
  private final WhatsAppProvider whatsAppProvider;

  public TemplateApprovalPollingJob(MessageTemplateRepository templates, WhatsAppProvider whatsAppProvider) {
    this.templates = templates;
    this.whatsAppProvider = whatsAppProvider;
  }

  @Scheduled(fixedDelayString = "${app.template.approval-poll-interval-ms:300000}", initialDelay = 45000)
  @Transactional
  public void pollPendingApprovals() {
    List<MessageTemplate> pending = templates.findByApprovalStatusAndProviderTemplateIdIsNotNull(ApprovalStatus.PENDING_APPROVAL);
    for (MessageTemplate t : pending) {
      try {
        applyStatus(t, whatsAppProvider.getTemplateStatus(t.providerTemplateId));
      } catch (Exception e) {
        log.warn("Template approval poll failed for template {}: {}", t.id, e.getMessage());
      }
    }
  }

  private void applyStatus(MessageTemplate t, String rawStatus) {
    ApprovalStatus resolved = switch (rawStatus == null ? "" : rawStatus.toUpperCase()) {
      case "APPROVED" -> ApprovalStatus.APPROVED;
      case "REJECTED" -> ApprovalStatus.REJECTED;
      default -> ApprovalStatus.PENDING_APPROVAL; // still pending — nothing to change yet
    };
    if (resolved != ApprovalStatus.PENDING_APPROVAL && resolved != t.approvalStatus) {
      t.approvalStatus = resolved;
      templates.save(t);
      log.info("Template {} approval resolved to {}", t.id, resolved);
    }
  }
}
