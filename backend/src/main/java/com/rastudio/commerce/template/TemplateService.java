package com.rastudio.commerce.template;

import com.rastudio.commerce.common.ApiException;
import com.rastudio.commerce.messaging.MessageChannel;
import com.rastudio.commerce.messaging.WhatsAppProvider;
import com.rastudio.commerce.retention.CampaignStepRepository;
import com.rastudio.commerce.template.TemplateDtos.*;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Phase 19 — Message Templates.
 *
 * WhatsApp: DRAFT -> PENDING_APPROVAL -> APPROVED/REJECTED (via Submit for Approval, Phase 18's
 * WhatsAppProvider#submitTemplate). Email: DRAFT -> APPROVED immediately on creation — "For Email: template is
 * immediately usable" (BUTTON_ACTIONS.md), no external review step exists for email.
 */
@Service
public class TemplateService {

  /** The roadmap gives {customer_name}/{product_name}/{discount_code} as examples; a few closely-related
   *  fields that campaign sending (Phase 20) and order-driven messaging can actually supply are included too,
   *  so real templates aren't limited to exactly three fields while still rejecting typos/unknown tokens. */
  public static final Set<String> ALLOWED_MERGE_FIELDS = Set.of(
      "customer_name", "product_name", "discount_code", "order_number", "brand_name");

  private static final Pattern MERGE_FIELD_PATTERN = Pattern.compile("\\{([a-zA-Z0-9_]+)\\}");

  private final MessageTemplateRepository templates;
  private final CampaignStepRepository campaignSteps;
  private final WhatsAppProvider whatsAppProvider;

  public TemplateService(MessageTemplateRepository templates, CampaignStepRepository campaignSteps,
      WhatsAppProvider whatsAppProvider) {
    this.templates = templates;
    this.campaignSteps = campaignSteps;
    this.whatsAppProvider = whatsAppProvider;
  }

  public List<TemplateResponse> list(Long organizationId) {
    return templates.findByOrganizationIdOrderByUpdatedAtDesc(organizationId).stream().map(this::toResponse).toList();
  }

  public TemplateResponse get(Long organizationId, Long id) {
    return toResponse(require(organizationId, id));
  }

  @Transactional
  public TemplateResponse create(Long organizationId, TemplateRequest request) {
    MessageChannel channel = parseChannel(request.channel());
    String content = validateContent(request);
    Set<String> mergeFields = extractAndValidateMergeFields(content);

    MessageTemplate t = new MessageTemplate();
    t.organizationId = organizationId;
    t.name = request.name().trim();
    t.channel = channel;
    t.content = content;
    // "For Email: template is immediately usable" — Email skips the approval workflow entirely.
    t.approvalStatus = channel == MessageChannel.EMAIL ? ApprovalStatus.APPROVED : ApprovalStatus.DRAFT;
    templates.save(t);
    return toResponse(t, mergeFields);
  }

  @Transactional
  public TemplateResponse update(Long organizationId, Long id, TemplateRequest request) {
    MessageTemplate t = require(organizationId, id);
    if (t.approvalStatus == ApprovalStatus.PENDING_APPROVAL) {
      throw new ApiException(HttpStatus.CONFLICT, "TEMPLATE_PENDING",
          "This template is awaiting WhatsApp approval and can't be edited until that resolves");
    }
    MessageChannel channel = parseChannel(request.channel());
    String content = validateContent(request);
    Set<String> mergeFields = extractAndValidateMergeFields(content);

    t.name = request.name().trim();
    t.channel = channel;
    t.content = content;
    // Editing an approved WhatsApp template invalidates that approval (Meta approves exact content, not the
    // template row); a rejected one simply goes back to DRAFT so it can be fixed and resubmitted. Email stays
    // APPROVED since there's no external review to invalidate.
    if (channel == MessageChannel.WHATSAPP && t.approvalStatus != ApprovalStatus.DRAFT) {
      t.approvalStatus = ApprovalStatus.DRAFT;
      t.providerTemplateId = null;
    }
    templates.save(t);
    return toResponse(t, mergeFields);
  }

  @Transactional
  public void delete(Long organizationId, Long id) {
    MessageTemplate t = require(organizationId, id);
    if (campaignSteps.existsByMessageTemplateId(t.id)) {
      throw new ApiException(HttpStatus.CONFLICT, "TEMPLATE_IN_USE",
          "This template is used by a journey step in a campaign — remove it from that journey first");
    }
    templates.delete(t);
  }

  /** "Submit for Approval (WhatsApp only)" — sends the template's content to the BSP/Meta and applies whatever
   *  status it hands back immediately (PENDING_APPROVAL, or, in mock mode, an instantly-resolved
   *  APPROVED/REJECTED — see MockWhatsAppProvider for why an immediate resolution is used there). */
  @Transactional
  public TemplateResponse submitForApproval(Long organizationId, Long id) {
    MessageTemplate t = require(organizationId, id);
    if (t.channel != MessageChannel.WHATSAPP) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "NOT_WHATSAPP", "Only WhatsApp templates need approval submission");
    }
    if (t.approvalStatus != ApprovalStatus.DRAFT && t.approvalStatus != ApprovalStatus.REJECTED) {
      throw new ApiException(HttpStatus.CONFLICT, "ALREADY_SUBMITTED", "This template has already been submitted");
    }
    WhatsAppProvider.TemplateSubmissionResult result = whatsAppProvider.submitTemplate(t.name, t.content);
    t.providerTemplateId = result.providerTemplateId();
    t.approvalStatus = switch (result.initialStatus() == null ? "PENDING_APPROVAL" : result.initialStatus().toUpperCase()) {
      case "APPROVED" -> ApprovalStatus.APPROVED;
      case "REJECTED" -> ApprovalStatus.REJECTED;
      default -> ApprovalStatus.PENDING_APPROVAL;
    };
    templates.save(t);
    return toResponse(t);
  }

  /** Renders a template's content with real values — used both for a "preview" and as the actual message
   *  body/components handed to the channel provider when a campaign step (Phase 20) fires. */
  public String render(String content, Map<String, String> values) {
    Matcher m = MERGE_FIELD_PATTERN.matcher(content);
    StringBuilder out = new StringBuilder();
    while (m.find()) {
      String key = m.group(1);
      String value = values == null ? null : values.get(key);
      m.appendReplacement(out, Matcher.quoteReplacement(value != null ? value : ""));
    }
    m.appendTail(out);
    return out.toString();
  }

  /* ------------------------------------ internals ------------------------------------ */

  MessageTemplate require(Long organizationId, Long id) {
    return templates.findByIdAndOrganizationId(id, organizationId)
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "TEMPLATE_NOT_FOUND", "Template not found"));
  }

  private MessageChannel parseChannel(String raw) {
    if (raw == null || raw.isBlank()) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "channel is required (WHATSAPP or EMAIL)");
    }
    try {
      return MessageChannel.valueOf(raw.trim().toUpperCase());
    } catch (IllegalArgumentException e) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Unknown channel: " + raw);
    }
  }

  private String validateContent(TemplateRequest request) {
    if (request.name() == null || request.name().isBlank()) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Template name is required");
    }
    if (request.content() == null || request.content().isBlank()) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Template content is required");
    }
    return request.content().trim();
  }

  private Set<String> extractAndValidateMergeFields(String content) {
    Set<String> found = new LinkedHashSet<>();
    Matcher m = MERGE_FIELD_PATTERN.matcher(content);
    Set<String> unknown = new LinkedHashSet<>();
    while (m.find()) {
      String key = m.group(1);
      found.add(key);
      if (!ALLOWED_MERGE_FIELDS.contains(key)) unknown.add(key);
    }
    if (!unknown.isEmpty()) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "UNKNOWN_MERGE_FIELD",
          "Unsupported merge field(s): " + String.join(", ", unknown) + ". Supported: " + String.join(", ", ALLOWED_MERGE_FIELDS));
    }
    return found;
  }

  private TemplateResponse toResponse(MessageTemplate t) {
    return toResponse(t, extractFieldsIgnoringValidity(t.content));
  }

  private TemplateResponse toResponse(MessageTemplate t, Set<String> mergeFields) {
    return new TemplateResponse(t.id, t.name, t.channel.name(), t.content, t.approvalStatus.name(),
        List.copyOf(mergeFields), t.createdAt, t.updatedAt);
  }

  private Set<String> extractFieldsIgnoringValidity(String content) {
    Set<String> found = new LinkedHashSet<>();
    Matcher m = MERGE_FIELD_PATTERN.matcher(content == null ? "" : content);
    while (m.find()) found.add(m.group(1));
    return found;
  }
}
