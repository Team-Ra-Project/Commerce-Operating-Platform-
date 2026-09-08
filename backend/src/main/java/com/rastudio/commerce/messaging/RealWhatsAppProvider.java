package com.rastudio.commerce.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * WHATSAPP_MODE=REAL. Talks to Meta's WhatsApp Cloud API directly (Graph API) using the configured phone
 * number id (for sending) and WhatsApp Business Account id (for template management). If a BSP is used
 * instead of Meta directly, app.whatsapp.api-base-url can be pointed at the BSP's Cloud-API-compatible
 * endpoint instead — the request/response shapes for Cloud API and most BSPs are the same by design.
 *
 * Per the roadmap: "No major application rewrite should be necessary" going from MOCK to REAL — this class
 * implements the exact same {@link WhatsAppProvider} contract as {@link MockWhatsAppProvider}, and Spring
 * selects between them purely based on app.whatsapp.mode.
 */
@Component
@ConditionalOnProperty(name = "app.whatsapp.mode", havingValue = "REAL")
public class RealWhatsAppProvider implements WhatsAppProvider {

  private static final Logger log = LoggerFactory.getLogger(RealWhatsAppProvider.class);

  private final RestClient restClient;
  private final String phoneNumberId;
  private final String businessAccountId;
  private final ObjectMapper mapper = new ObjectMapper();

  public RealWhatsAppProvider(
      @Value("${app.whatsapp.api-base-url:https://graph.facebook.com}") String apiBaseUrl,
      @Value("${app.whatsapp.api-version:v19.0}") String apiVersion,
      @Value("${app.whatsapp.access-token:}") String accessToken,
      @Value("${app.whatsapp.phone-number-id:}") String phoneNumberId,
      @Value("${app.whatsapp.business-account-id:}") String businessAccountId) {
    this.phoneNumberId = phoneNumberId;
    this.businessAccountId = businessAccountId;
    this.restClient = RestClient.builder()
        .baseUrl(apiBaseUrl + "/" + apiVersion)
        .defaultHeader("Authorization", "Bearer " + accessToken)
        .build();
    if (accessToken == null || accessToken.isBlank() || phoneNumberId == null || phoneNumberId.isBlank()) {
      log.warn("app.whatsapp.mode=REAL but WHATSAPP_ACCESS_TOKEN / WHATSAPP_PHONE_NUMBER_ID are not fully "
          + "configured — real sends will fail until they are set.");
    }
  }

  @Override
  public ProviderSendResult sendTemplateMessage(String toPhoneE164, String templateExternalName, Map<String, String> mergeFields) {
    try {
      List<Map<String, Object>> parameters = new ArrayList<>();
      if (mergeFields != null) {
        // Cloud API positions named merge fields by their numeric order in the template body ({{1}}, {{2}}, ...);
        // this project's own {customer_name}/{product_name}/{discount_code} syntax is translated to positional
        // values in the same order the template's own placeholders were declared (see TemplateService#mergeFieldsInOrder).
        for (String value : mergeFields.values()) {
          parameters.add(Map.of("type", "text", "text", value == null ? "" : value));
        }
      }
      Map<String, Object> body = Map.of(
          "messaging_product", "whatsapp",
          "to", toPhoneE164,
          "type", "template",
          "template", Map.of(
              "name", templateExternalName,
              "language", Map.of("code", "en_US"),
              "components", parameters.isEmpty() ? List.of() : List.of(Map.of("type", "body", "parameters", parameters))
          ));
      JsonNode response = restClient.post()
          .uri("/{phoneNumberId}/messages", phoneNumberId)
          .body(body)
          .retrieve()
          .body(JsonNode.class);
      String providerMessageId = response != null && response.has("messages")
          ? response.get("messages").get(0).get("id").asText() : null;
      return new ProviderSendResult(providerMessageId != null, providerMessageId,
          providerMessageId == null ? "Provider returned no message id" : null);
    } catch (Exception e) {
      log.error("WhatsApp sendTemplateMessage failed", e);
      return new ProviderSendResult(false, null, e.getMessage());
    }
  }

  @Override
  public ProviderSendResult sendMessage(String toPhoneE164, String body) {
    try {
      Map<String, Object> payload = Map.of(
          "messaging_product", "whatsapp",
          "to", toPhoneE164,
          "type", "text",
          "text", Map.of("body", body == null ? "" : body));
      JsonNode response = restClient.post()
          .uri("/{phoneNumberId}/messages", phoneNumberId)
          .body(payload)
          .retrieve()
          .body(JsonNode.class);
      String providerMessageId = response != null && response.has("messages")
          ? response.get("messages").get(0).get("id").asText() : null;
      return new ProviderSendResult(providerMessageId != null, providerMessageId,
          providerMessageId == null ? "Provider returned no message id" : null);
    } catch (Exception e) {
      log.error("WhatsApp sendMessage failed", e);
      return new ProviderSendResult(false, null, e.getMessage());
    }
  }

  @Override
  public TemplateSubmissionResult submitTemplate(String templateName, String content) {
    try {
      Map<String, Object> payload = Map.of(
          "name", templateName.toLowerCase().replaceAll("[^a-z0-9_]", "_"),
          "language", "en_US",
          "category", "MARKETING",
          "components", List.of(Map.of("type", "BODY", "text", content)));
      JsonNode response = restClient.post()
          .uri("/{wabaId}/message_templates", businessAccountId)
          .body(payload)
          .retrieve()
          .body(JsonNode.class);
      String id = response != null && response.has("id") ? response.get("id").asText() : null;
      String status = response != null && response.has("status") ? response.get("status").asText() : "PENDING_APPROVAL";
      return new TemplateSubmissionResult(id, status);
    } catch (Exception e) {
      log.error("WhatsApp submitTemplate failed", e);
      return new TemplateSubmissionResult(null, "REJECTED");
    }
  }

  @Override
  public String getTemplateStatus(String providerTemplateId) {
    try {
      JsonNode response = restClient.get()
          .uri("/{templateId}?fields=status", providerTemplateId)
          .retrieve()
          .body(JsonNode.class);
      return response != null && response.has("status") ? response.get("status").asText() : "PENDING_APPROVAL";
    } catch (Exception e) {
      log.error("WhatsApp getTemplateStatus failed", e);
      return "PENDING_APPROVAL";
    }
  }

  @Override
  public List<WebhookEvent> handleWebhook(String rawPayload) {
    List<WebhookEvent> events = new ArrayList<>();
    try {
      JsonNode root = mapper.readTree(rawPayload);
      for (JsonNode entry : root.path("entry")) {
        for (JsonNode change : entry.path("changes")) {
          JsonNode value = change.path("value");
          for (JsonNode status : value.path("statuses")) {
            events.add(new WebhookEvent(WebhookEventType.MESSAGE_STATUS, status.path("id").asText(null),
                null, status.path("status").asText(null), status.toString()));
          }
          JsonNode templateEvent = value.path("message_template_id");
          if (!templateEvent.isMissingNode()) {
            events.add(new WebhookEvent(WebhookEventType.TEMPLATE_STATUS, null, templateEvent.asText(null),
                value.path("event").asText(null), value.toString()));
          }
        }
      }
    } catch (Exception e) {
      log.error("Failed to parse WhatsApp webhook payload", e);
    }
    return events;
  }
}
