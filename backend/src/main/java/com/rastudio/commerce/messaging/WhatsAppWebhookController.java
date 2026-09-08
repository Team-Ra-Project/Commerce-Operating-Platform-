package com.rastudio.commerce.messaging;

import com.rastudio.commerce.template.ApprovalStatus;
import com.rastudio.commerce.template.MessageTemplate;
import com.rastudio.commerce.template.MessageTemplateRepository;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/**
 * Phase 18 — WhatsApp Infrastructure: handleWebhook().
 *
 * Public endpoint (see SecurityConfig — Meta itself calls this with no user session), guarded by the standard
 * GET hub.challenge handshake plus an optional shared verify token.
 *
 * Note on correlation: message_log has no provider-message-id column to persist against, so an inbound
 * MESSAGE_STATUS event is logged but not written back onto a specific message_log row — delivery/read status
 * for real sends instead relies on the synchronous result WhatsAppProvider#sendTemplateMessage already returns
 * at send time (CampaignSchedulerJob applies that immediately). message_template DOES carry a
 * provider_template_id (added for this purpose), so an inbound TEMPLATE_STATUS event IS applied here directly
 * — the same real-time path BUTTON_ACTIONS.md describes, running alongside TemplateApprovalPollingJob's
 * periodic poll rather than instead of it, since Meta may deliver the webhook, the poll may catch it first, or
 * (in a sandbox with no public URL to receive webhooks at all) only the poll ever runs.
 */
@RestController
@RequestMapping("/api/webhooks/whatsapp")
public class WhatsAppWebhookController {

  private static final Logger log = LoggerFactory.getLogger(WhatsAppWebhookController.class);

  private final WhatsAppProvider provider;
  private final MessageTemplateRepository templates;
  private final String verifyToken;

  public WhatsAppWebhookController(WhatsAppProvider provider, MessageTemplateRepository templates,
      @Value("${app.whatsapp.webhook-verify-token:}") String verifyToken) {
    this.provider = provider;
    this.templates = templates;
    this.verifyToken = verifyToken;
  }

  /** Meta's one-time webhook subscription handshake. */
  @GetMapping
  public ResponseEntity<String> verify(
      @RequestParam(name = "hub.mode", required = false) String mode,
      @RequestParam(name = "hub.verify_token", required = false) String token,
      @RequestParam(name = "hub.challenge", required = false) String challenge) {
    boolean tokenOk = verifyToken == null || verifyToken.isBlank() || verifyToken.equals(token);
    if ("subscribe".equals(mode) && tokenOk && challenge != null) {
      return ResponseEntity.ok(challenge);
    }
    return ResponseEntity.status(403).build();
  }

  /** Real inbound delivery-status / template-approval events. */
  @PostMapping
  @Transactional
  public Map<String, Object> receive(@RequestBody String rawPayload) {
    List<WhatsAppProvider.WebhookEvent> events = provider.handleWebhook(rawPayload);
    int applied = 0;
    for (WhatsAppProvider.WebhookEvent event : events) {
      log.info("WhatsApp webhook event received: {}", event);
      if (event.type() == WhatsAppProvider.WebhookEventType.TEMPLATE_STATUS && event.providerTemplateId() != null) {
        if (applyTemplateStatus(event.providerTemplateId(), event.status())) applied++;
      }
    }
    return Map.of("received", events.size(), "applied", applied);
  }

  private boolean applyTemplateStatus(String providerTemplateId, String rawStatus) {
    ApprovalStatus resolved = switch (rawStatus == null ? "" : rawStatus.toUpperCase()) {
      case "APPROVED" -> ApprovalStatus.APPROVED;
      case "REJECTED" -> ApprovalStatus.REJECTED;
      default -> null; // PENDING or an event we don't need to act on
    };
    if (resolved == null) return false;
    return templates.findByProviderTemplateId(providerTemplateId).map(t -> {
      t.approvalStatus = resolved;
      templates.save(t);
      return true;
    }).orElse(false);
  }
}
