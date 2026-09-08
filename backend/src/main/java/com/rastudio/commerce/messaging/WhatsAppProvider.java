package com.rastudio.commerce.messaging;

/**
 * Phase 18 — WhatsApp Infrastructure.
 *
 * Adapter boundary for the WhatsApp Cloud API (or a BSP sitting in front of it). Exactly the roadmap's
 * function list: sendTemplateMessage, sendMessage, submitTemplate, getTemplateStatus, handleWebhook.
 *
 * Two implementations exist ({@link MockWhatsAppProvider}, {@link RealWhatsAppProvider}), selected by
 * app.whatsapp.mode (MOCK by default). Everything above this interface — template submission (Phase 19),
 * campaign sending (Phase 20) — only ever talks to {@code WhatsAppProvider}, so flipping to real Meta Cloud
 * API credentials later needs no application rewrite, per the roadmap's explicit requirement.
 */
public interface WhatsAppProvider {

  /** Sends an already-approved template message to a phone number, substituting merge-field values. */
  ProviderSendResult sendTemplateMessage(String toPhoneE164, String templateExternalName, java.util.Map<String, String> mergeFields);

  /** Sends a freeform (non-template) message — only usable inside Meta's 24-hour customer-service window. */
  ProviderSendResult sendMessage(String toPhoneE164, String body);

  /** Submits a template's content to Meta/the BSP for approval. Returns the provider's tracking id. */
  TemplateSubmissionResult submitTemplate(String templateName, String content);

  /** Polls the provider for a previously-submitted template's current approval state. */
  String getTemplateStatus(String providerTemplateId);

  /**
   * Handles an inbound delivery/read-status (or template-approval) webhook payload from the provider.
   * Returns the parsed events so the caller (WhatsAppWebhookController) can update message_log / message_template.
   */
  java.util.List<WebhookEvent> handleWebhook(String rawPayload);

  record ProviderSendResult(boolean accepted, String providerMessageId, String errorDetail) {}

  record TemplateSubmissionResult(String providerTemplateId, String initialStatus) {}

  /** A single normalized event extracted from a provider webhook payload. */
  record WebhookEvent(WebhookEventType type, String providerMessageId, String providerTemplateId, String status, String detail) {}

  enum WebhookEventType { MESSAGE_STATUS, TEMPLATE_STATUS }
}
