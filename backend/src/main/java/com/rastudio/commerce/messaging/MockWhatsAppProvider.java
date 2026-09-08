package com.rastudio.commerce.messaging;

import java.security.SecureRandom;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * WHATSAPP_MODE=MOCK (the default). No network calls are made; every send "succeeds" instantly with a
 * generated provider message id, and every submitted template is auto-approved a few seconds later — enough
 * to exercise the full DRAFT -> PENDING_APPROVAL -> APPROVED flow (Phase 19) and campaign sending (Phase 20)
 * without any real WhatsApp Business account. This mirrors how Phase 5's marketplace adapters and Phase 8's
 * courier tracking already behave in this project when no live credentials exist.
 */
@Component
@ConditionalOnProperty(name = "app.whatsapp.mode", havingValue = "MOCK", matchIfMissing = true)
public class MockWhatsAppProvider implements WhatsAppProvider {

  private static final Logger log = LoggerFactory.getLogger(MockWhatsAppProvider.class);
  private static final SecureRandom RANDOM = new SecureRandom();

  /** providerTemplateId -> current status, so getTemplateStatus() reflects submitTemplate() calls made moments earlier. */
  private final Map<String, String> templateStatuses = new ConcurrentHashMap<>();

  @Override
  public ProviderSendResult sendTemplateMessage(String toPhoneE164, String templateExternalName, Map<String, String> mergeFields) {
    if (toPhoneE164 == null || toPhoneE164.isBlank()) {
      return new ProviderSendResult(false, null, "Recipient phone number is missing");
    }
    String providerMessageId = "MOCK-WA-MSG-" + (100000000 + RANDOM.nextInt(900000000));
    log.info("[MOCK WhatsApp] template '{}' -> {} (merge fields: {}) => {}", templateExternalName, toPhoneE164, mergeFields, providerMessageId);
    return new ProviderSendResult(true, providerMessageId, null);
  }

  @Override
  public ProviderSendResult sendMessage(String toPhoneE164, String body) {
    if (toPhoneE164 == null || toPhoneE164.isBlank()) {
      return new ProviderSendResult(false, null, "Recipient phone number is missing");
    }
    String providerMessageId = "MOCK-WA-MSG-" + (100000000 + RANDOM.nextInt(900000000));
    log.info("[MOCK WhatsApp] freeform -> {} => {}", toPhoneE164, providerMessageId);
    return new ProviderSendResult(true, providerMessageId, null);
  }

  @Override
  public TemplateSubmissionResult submitTemplate(String templateName, String content) {
    String providerTemplateId = "MOCK-WA-TPL-" + (100000 + RANDOM.nextInt(900000));
    templateStatuses.put(providerTemplateId, "PENDING_APPROVAL");
    // Auto-approve almost immediately — there is no real Meta review queue in mock mode, and requiring the
    // person to wait around defeats the point of local/demo testing. A template only ever fails approval in
    // mock mode if its content is empty, mirroring a minimal real-world rejection reason (blank content).
    templateStatuses.put(providerTemplateId, (content == null || content.isBlank()) ? "REJECTED" : "APPROVED");
    log.info("[MOCK WhatsApp] submitted template '{}' => {} ({})", templateName, providerTemplateId, templateStatuses.get(providerTemplateId));
    return new TemplateSubmissionResult(providerTemplateId, templateStatuses.get(providerTemplateId));
  }

  @Override
  public String getTemplateStatus(String providerTemplateId) {
    return templateStatuses.getOrDefault(providerTemplateId, "PENDING_APPROVAL");
  }

  @Override
  public List<WebhookEvent> handleWebhook(String rawPayload) {
    // Mock mode has no real inbound webhooks to parse; status changes already happen synchronously above.
    return List.of();
  }
}
