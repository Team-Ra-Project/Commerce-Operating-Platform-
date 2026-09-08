package com.rastudio.commerce.broadcast;

import com.rastudio.commerce.audit.AuditLogService;
import com.rastudio.commerce.broadcast.BulkSendDtos.*;
import com.rastudio.commerce.common.ApiException;
import com.rastudio.commerce.customer.Customer;
import com.rastudio.commerce.customer.CustomerRepository;
import com.rastudio.commerce.mail.MailService;
import com.rastudio.commerce.messaging.MessageChannel;
import com.rastudio.commerce.messaging.WhatsAppProvider;
import com.rastudio.commerce.retention.SegmentService;
import com.rastudio.commerce.template.ApprovalStatus;
import com.rastudio.commerce.template.MessageTemplate;
import com.rastudio.commerce.template.MessageTemplateRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Phase 22 — Bulk Sharing / Broadcast (roadmap; BUTTON_ACTIONS.md section 11b; FULL_WORKFLOW.md module 16).
 *
 * Flow: Upload/Select List -> Choose Content -> Pick Channel(s) -> Send -> Report, backed for real by
 * bulk_send/bulk_send_item. Reuses exactly the same channel machinery Phase 20's CampaignSchedulerJob already
 * uses — WhatsAppProvider#sendTemplateMessage (never freeform text, per the roadmap's "Do not send marketing
 * WhatsApp messages without ... approved templates where required") and MailService#sendMarketingEmail — so a
 * bulk broadcast and a campaign step are sent through the identical provider boundary.
 *
 * Content storage note: bulk_send.content is a single TEXT column with no companion template-id column, so the
 * chosen WhatsApp template's name/content and/or typed Email subject/body are serialized into that one field
 * using private control-character markers (a person can't type \u0001 through a normal form field, so there's
 * no realistic collision with real message text). This makes a later "Retry Failed Sends" fully self-sufficient
 * from the persisted row — no separate durable state needed — while {@link #toResponse} strips the markers
 * back out into a clean, human-readable preview for the report page.
 */
@Service
public class BulkSendService {

  private static final Logger log = LoggerFactory.getLogger(BulkSendService.class);

  private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
  private static final int MAX_INVALID_ROWS_RETURNED = 25;

  // Control-character markers for the mini serialization inside bulk_send.content — see class javadoc.
  private static final String MARK_WA_NAME = "\u0001WA_NAME\u0001";
  private static final String MARK_WA_BODY = "\u0001WA_BODY\u0001";
  private static final String MARK_EMAIL_SUBJECT = "\u0001EMAIL_SUBJECT\u0001";
  private static final String MARK_EMAIL_BODY = "\u0001EMAIL_BODY\u0001";

  private final BulkSendRepository bulkSends;
  private final BulkSendItemRepository bulkSendItems;
  private final CustomerRepository customers;
  private final MessageTemplateRepository templates;
  private final SegmentService segmentService;
  private final WhatsAppProvider whatsAppProvider;
  private final MailService mailService;
  private final AuditLogService audit;
  private final int batchSize;
  private final long batchDelayMs;
  private final BulkSendService self; // see sendAsync() javadoc — routes internal calls back through the
                                       // Spring proxy so @Transactional on sendOneItem/failAllRemaining/
                                       // finalizeStatus actually applies (self-invocation bypasses AOP proxies).

  public BulkSendService(BulkSendRepository bulkSends, BulkSendItemRepository bulkSendItems,
      CustomerRepository customers, MessageTemplateRepository templates, SegmentService segmentService,
      WhatsAppProvider whatsAppProvider, MailService mailService, AuditLogService audit,
      @Value("${app.broadcast.batch-size:20}") int batchSize,
      @Value("${app.broadcast.batch-delay-ms:1000}") long batchDelayMs,
      @org.springframework.context.annotation.Lazy BulkSendService self) {
    this.bulkSends = bulkSends;
    this.bulkSendItems = bulkSendItems;
    this.customers = customers;
    this.templates = templates;
    this.segmentService = segmentService;
    this.whatsAppProvider = whatsAppProvider;
    this.mailService = mailService;
    this.audit = audit;
    this.batchSize = batchSize;
    this.batchDelayMs = batchDelayMs;
    this.self = self;
  }

  /* ------------------------------------------- read ------------------------------------------- */

  public List<BulkSendResponse> list(Long organizationId) {
    return bulkSends.findByOrganizationIdOrderByCreatedAtDesc(organizationId).stream().map(this::toResponse).toList();
  }

  public BulkSendResponse get(Long organizationId, Long id) {
    return toResponse(require(organizationId, id));
  }

  public List<BulkSendItemResponse> items(Long organizationId, Long id) {
    BulkSend bulkSend = require(organizationId, id);
    List<BulkSendItem> rows = bulkSendItems.findByBulkSendIdOrderByIdAsc(bulkSend.id);
    Set<Long> customerIds = new HashSet<>();
    for (BulkSendItem r : rows) if (r.customerId != null) customerIds.add(r.customerId);
    Map<Long, Customer> byId = customers.findByIdInAndOrganizationId(List.copyOf(customerIds), organizationId)
        .stream().collect(java.util.stream.Collectors.toMap(c -> c.id, c -> c));
    return rows.stream().map(r -> {
      String label;
      if (r.customerId != null) {
        Customer c = byId.get(r.customerId);
        label = c == null ? ("Customer #" + r.customerId)
            : c.fullName + (c.email != null && !c.email.isBlank() ? " (" + c.email + ")" : c.phone != null ? " (" + c.phone + ")" : "");
      } else {
        label = r.contactReference == null ? "Unknown contact" : r.contactReference;
      }
      return new BulkSendItemResponse(r.id, r.customerId, label, r.status.name(), r.failureReason);
    }).toList();
  }

  BulkSend require(Long organizationId, Long id) {
    return bulkSends.findByIdAndOrganizationId(id, organizationId)
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "BULK_SEND_NOT_FOUND", "Bulk send not found"));
  }

  /* ------------------------------------------ CSV upload ----------------------------------------- */

  /** "Upload Contact List" — validates format and dedupes, but persists nothing; the frontend holds the
   *  returned validContacts in memory and submits them back on Send Bulk Broadcast. */
  public ParsedContactsResponse parseCsv(MultipartFile file) {
    if (file == null || file.isEmpty()) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Please choose a CSV file to upload");
    }
    List<String> lines;
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
      lines = reader.lines().filter(l -> !l.isBlank()).toList();
    } catch (Exception e) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Could not read the uploaded file");
    }
    if (lines.isEmpty()) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "The uploaded file is empty");
    }

    String[] header = splitCsvLine(lines.get(0));
    int nameIdx = indexOfHeader(header, "name");
    int emailIdx = indexOfHeader(header, "email");
    int phoneIdx = indexOfHeader(header, "phone");
    if (emailIdx < 0 && phoneIdx < 0) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "CSV must include an \"email\" or \"phone\" column");
    }

    List<ContactInput> valid = new ArrayList<>();
    List<InvalidRow> invalid = new ArrayList<>();
    Set<String> seen = new HashSet<>();
    int duplicates = 0;

    for (int i = 1; i < lines.size(); i++) {
      String rawLine = lines.get(i);
      String[] cols = splitCsvLine(rawLine);
      String name = valueAt(cols, nameIdx);
      String email = valueAt(cols, emailIdx);
      String phone = valueAt(cols, phoneIdx);

      boolean emailOk = email != null && EMAIL_PATTERN.matcher(email).matches();
      boolean phoneOk = phone != null && isValidPhone(phone);
      if (!emailOk && !phoneOk) {
        invalid.add(new InvalidRow(rawLine, "No valid email or phone number found on this row"));
        continue;
      }
      String normalizedPhone = phoneOk ? normalizePhone(phone) : null;
      String dedupeKey = emailOk ? "e:" + email.toLowerCase() : "p:" + normalizedPhone;
      if (!seen.add(dedupeKey)) { duplicates++; continue; }

      valid.add(new ContactInput(name, emailOk ? email : null, normalizedPhone));
    }

    List<InvalidRow> capped = invalid.size() > MAX_INVALID_ROWS_RETURNED ? invalid.subList(0, MAX_INVALID_ROWS_RETURNED) : invalid;
    return new ParsedContactsResponse(lines.size() - 1, valid.size(), invalid.size(), duplicates, valid, capped);
  }

  private static String valueAt(String[] cols, int idx) {
    if (idx < 0 || idx >= cols.length) return null;
    String v = cols[idx].trim();
    return v.isBlank() ? null : v;
  }

  private static String[] splitCsvLine(String line) {
    List<String> out = new ArrayList<>();
    StringBuilder cur = new StringBuilder();
    boolean inQuotes = false;
    for (int i = 0; i < line.length(); i++) {
      char c = line.charAt(i);
      if (c == '"') inQuotes = !inQuotes;
      else if (c == ',' && !inQuotes) { out.add(cur.toString()); cur.setLength(0); }
      else cur.append(c);
    }
    out.add(cur.toString());
    return out.toArray(new String[0]);
  }

  private static int indexOfHeader(String[] header, String name) {
    for (int i = 0; i < header.length; i++) if (header[i].trim().equalsIgnoreCase(name)) return i;
    return -1;
  }

  private static boolean isValidPhone(String raw) {
    String digits = raw.replaceAll("[^0-9]", "");
    return digits.length() >= 7 && digits.length() <= 15;
  }

  private static String normalizePhone(String raw) {
    boolean plus = raw.trim().startsWith("+");
    String digits = raw.replaceAll("[^0-9]", "");
    return (plus ? "+" : "") + digits;
  }

  /* ------------------------------------------ create / send ---------------------------------------- */

  private record ResolvedRecipient(Long customerId, String contactReference) {}

  @Transactional
  public BulkSendResponse create(Long organizationId, Long actorUserId, BulkSendCreateRequest request, HttpServletRequest httpRequest) {
    BulkSendChannel channel = parseChannel(request.channel());
    boolean needsWhatsApp = channel != BulkSendChannel.EMAIL;
    boolean needsEmail = channel != BulkSendChannel.WHATSAPP;

    MessageTemplate whatsappTemplate = null;
    if (needsWhatsApp) {
      if (request.whatsappTemplateId() == null) {
        throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Choose an approved WhatsApp template for this broadcast");
      }
      whatsappTemplate = templates.findByIdAndOrganizationId(request.whatsappTemplateId(), organizationId)
          .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "TEMPLATE_NOT_FOUND", "WhatsApp template not found"));
      if (whatsappTemplate.channel != MessageChannel.WHATSAPP || whatsappTemplate.approvalStatus != ApprovalStatus.APPROVED) {
        throw new ApiException(HttpStatus.CONFLICT, "TEMPLATE_NOT_APPROVED",
            "Only an approved WhatsApp template can be used for a marketing broadcast");
      }
    }
    if (needsEmail && (blank(request.emailSubject()) || blank(request.emailContent()))) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Email subject and content are required for this channel");
    }

    List<ResolvedRecipient> recipients = resolveRecipients(organizationId, request);
    if (recipients.isEmpty()) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "EMPTY_RECIPIENT_LIST", "The selected list has no contacts to send to");
    }

    BulkSend bulkSend = new BulkSend();
    bulkSend.organizationId = organizationId;
    bulkSend.channel = channel;
    bulkSend.content = serializeContent(channel, whatsappTemplate, request.emailSubject(), request.emailContent());
    bulkSend.listSize = recipients.size();
    bulkSend.status = BulkSendStatus.PENDING;
    bulkSends.save(bulkSend);

    List<BulkSendItem> items = new ArrayList<>();
    for (ResolvedRecipient r : recipients) {
      BulkSendItem item = new BulkSendItem();
      item.bulkSendId = bulkSend.id;
      item.customerId = r.customerId();
      item.contactReference = r.contactReference();
      item.status = BulkSendItemStatus.PENDING;
      items.add(item);
    }
    bulkSendItems.saveAll(items);

    bulkSend.status = BulkSendStatus.IN_PROGRESS;
    bulkSends.save(bulkSend);

    audit.record(organizationId, actorUserId, "SEND_BULK_BROADCAST", "bulk_send", bulkSend.id, null,
        channel.name() + " to " + recipients.size() + " recipient(s)", httpRequest);

    return toResponse(bulkSend);
  }

  /** "Retry Failed Sends" — roadmap's explicit "retry support" bullet. Resets every FAILED item back to
   *  PENDING and re-runs the async sender; SENT/DELIVERED/OPTED_OUT items are left untouched. */
  @Transactional
  public BulkSendResponse retryFailed(Long organizationId, Long id, Long actorUserId, HttpServletRequest httpRequest) {
    BulkSend bulkSend = require(organizationId, id);
    List<BulkSendItem> failed = bulkSendItems.findByBulkSendIdAndStatus(bulkSend.id, BulkSendItemStatus.FAILED);
    if (failed.isEmpty()) {
      throw new ApiException(HttpStatus.CONFLICT, "NOTHING_TO_RETRY", "There are no failed sends to retry");
    }
    for (BulkSendItem item : failed) {
      item.status = BulkSendItemStatus.PENDING;
      item.failureReason = null;
    }
    bulkSendItems.saveAll(failed);
    bulkSend.failedCount = Math.max(0, bulkSend.failedCount - failed.size());
    bulkSend.status = BulkSendStatus.IN_PROGRESS;
    bulkSends.save(bulkSend);
    audit.record(organizationId, actorUserId, "RETRY_BULK_BROADCAST", "bulk_send", bulkSend.id, null,
        "Retrying " + failed.size() + " failed item(s)", httpRequest);
    return toResponse(bulkSend);
  }

  private List<ResolvedRecipient> resolveRecipients(Long organizationId, BulkSendCreateRequest request) {
    String source = request.recipientSource() == null ? "" : request.recipientSource().trim().toUpperCase();
    List<ResolvedRecipient> out = new ArrayList<>();

    if (source.equals("SEGMENT")) {
      if (request.segmentId() == null) {
        throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Choose a segment");
      }
      for (Long customerId : segmentService.memberCustomerIds(organizationId, request.segmentId())) {
        out.add(new ResolvedRecipient(customerId, null));
      }
    } else if (source.equals("UPLOAD")) {
      if (request.contacts() == null || request.contacts().isEmpty()) {
        throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Upload a contact list first");
      }
      Set<String> seen = new HashSet<>();
      for (ContactInput c : request.contacts()) {
        String email = c.email() == null ? null : c.email().trim();
        String phone = c.phone() == null ? null : c.phone().trim();
        boolean emailOk = email != null && !email.isBlank() && EMAIL_PATTERN.matcher(email).matches();
        boolean phoneOk = phone != null && !phone.isBlank() && isValidPhone(phone);
        if (!emailOk && !phoneOk) continue; // defensively re-validate even though parse-csv already filtered

        String normalizedPhone = phoneOk ? normalizePhone(phone) : null;
        String dedupeKey = emailOk ? "e:" + email.toLowerCase() : "p:" + normalizedPhone;
        if (!seen.add(dedupeKey)) continue;

        Customer match = emailOk ? customers.findByOrganizationIdAndEmailIgnoreCase(organizationId, email).orElse(null) : null;
        if (match == null && phoneOk) match = customers.findByOrganizationIdAndPhone(organizationId, normalizedPhone).orElse(null);

        out.add(match != null ? new ResolvedRecipient(match.id, null)
            : new ResolvedRecipient(null, emailOk ? email : normalizedPhone));
      }
    } else {
      throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "recipientSource must be SEGMENT or UPLOAD");
    }
    return out;
  }

  /* --------------------------------------- async batch sender --------------------------------------- */

  /** Runs on a separate thread (see CommerceApplication's @EnableAsync) so POST /api/broadcasts can return
   *  immediately with status IN_PROGRESS while the frontend polls GET /api/broadcasts/{id} for live
   *  sent/delivered/failed/opted-out counts — "Live progress shown" per BUTTON_ACTIONS.md. Processes in
   *  configurable batches with a short pause between them (app.broadcast.batch-size /
   *  app.broadcast.batch-delay-ms) to respect WhatsApp/ESP rate limits per the roadmap. */
  @Async
  public void sendAsync(Long bulkSendId) {
    BulkSend bulkSend = bulkSends.findById(bulkSendId).orElse(null);
    if (bulkSend == null) return;
    try {
      ParsedContent parsed = parseContent(bulkSend.content);
      List<BulkSendItem> pending = bulkSendItems.findByBulkSendIdAndStatus(bulkSendId, BulkSendItemStatus.PENDING);
      int processed = 0;
      for (BulkSendItem item : pending) {
        self.sendOneItem(bulkSend, item, parsed);
        processed++;
        if (processed % batchSize == 0 && batchDelayMs > 0) {
          try { Thread.sleep(batchDelayMs); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); break; }
        }
      }
    } catch (Exception e) {
      log.error("Bulk send {} failed unexpectedly — marking remaining items as failed", bulkSendId, e);
      self.failAllRemaining(bulkSendId, "Internal error while sending: " + e.getMessage());
    } finally {
      self.finalizeStatus(bulkSendId);
    }
  }

  @Transactional
  void sendOneItem(BulkSend bulkSend, BulkSendItem item, ParsedContent parsed) {
    Customer customer = item.customerId != null ? customers.findById(item.customerId).orElse(null) : null;
    Map<String, String> mergeFields = Map.of(
        "customer_name", customer != null && customer.fullName != null ? customer.fullName : "",
        "product_name", "", "discount_code", "", "order_number", "", "brand_name", "");

    boolean anySent = false;
    boolean anyOptedOut = false;
    List<String> failureReasons = new ArrayList<>();

    if (bulkSend.channel != BulkSendChannel.EMAIL) {
      String phone = customer != null ? customer.phone : item.contactReference;
      boolean optedIn = customer != null && Boolean.TRUE.equals(customer.whatsappOptIn) && phone != null && !phone.isBlank();
      if (!optedIn) {
        anyOptedOut = true;
      } else {
        WhatsAppProvider.ProviderSendResult result = whatsAppProvider.sendTemplateMessage(phone, parsed.whatsappTemplateName(), mergeFields);
        if (result.accepted()) anySent = true; else failureReasons.add("WhatsApp: " + result.errorDetail());
      }
    }
    if (bulkSend.channel != BulkSendChannel.WHATSAPP) {
      String email = customer != null ? customer.email : item.contactReference;
      boolean optedOut = customer != null && !Boolean.TRUE.equals(customer.emailOptIn);
      boolean hasEmail = email != null && EMAIL_PATTERN.matcher(email).matches();
      if (!hasEmail) {
        failureReasons.add("Email: no valid email address on file");
      } else if (optedOut) {
        anyOptedOut = true;
      } else {
        boolean sent = mailService.sendMarketingEmail(email, parsed.emailSubject(), parsed.emailBody());
        if (sent) anySent = true; else failureReasons.add("Email: SMTP not configured or send failed");
      }
    }

    if (anySent) {
      item.status = BulkSendItemStatus.SENT;
      item.failureReason = null;
      bulkSend.sentCount = bulkSend.sentCount + 1;
    } else if (anyOptedOut && failureReasons.isEmpty()) {
      item.status = BulkSendItemStatus.OPTED_OUT;
      bulkSend.optedOutCount = bulkSend.optedOutCount + 1;
    } else {
      item.status = BulkSendItemStatus.FAILED;
      item.failureReason = String.join("; ", failureReasons.isEmpty() ? List.of("Recipient has not opted in") : failureReasons);
      bulkSend.failedCount = bulkSend.failedCount + 1;
    }
    bulkSendItems.save(item);
    bulkSends.save(bulkSend);
  }

  @Transactional
  void failAllRemaining(Long bulkSendId, String reason) {
    List<BulkSendItem> pending = bulkSendItems.findByBulkSendIdAndStatus(bulkSendId, BulkSendItemStatus.PENDING);
    if (pending.isEmpty()) return;
    BulkSend bulkSend = bulkSends.findById(bulkSendId).orElse(null);
    for (BulkSendItem item : pending) {
      item.status = BulkSendItemStatus.FAILED;
      item.failureReason = reason;
    }
    bulkSendItems.saveAll(pending);
    if (bulkSend != null) {
      bulkSend.failedCount = bulkSend.failedCount + pending.size();
      bulkSends.save(bulkSend);
    }
  }

  @Transactional
  void finalizeStatus(Long bulkSendId) {
    bulkSends.findById(bulkSendId).ifPresent(b -> {
      b.status = BulkSendStatus.COMPLETED;
      bulkSends.save(b);
    });
  }

  /* --------------------------------------- content (de)serialization --------------------------------------- */

  private record ParsedContent(String whatsappTemplateName, String whatsappBody, String emailSubject, String emailBody) {}

  private String serializeContent(BulkSendChannel channel, MessageTemplate whatsappTemplate, String emailSubject, String emailContent) {
    StringBuilder sb = new StringBuilder();
    if (channel != BulkSendChannel.EMAIL) {
      sb.append(MARK_WA_NAME).append(whatsappTemplate.name).append(MARK_WA_BODY).append(whatsappTemplate.content);
    }
    if (channel != BulkSendChannel.WHATSAPP) {
      sb.append(MARK_EMAIL_SUBJECT).append(emailSubject).append(MARK_EMAIL_BODY).append(emailContent);
    }
    return sb.toString();
  }

  private ParsedContent parseContent(String raw) {
    String waName = extractBetween(raw, MARK_WA_NAME, MARK_WA_BODY);
    String waBody = extractBetween(raw, MARK_WA_BODY, MARK_EMAIL_SUBJECT);
    String subject = extractBetween(raw, MARK_EMAIL_SUBJECT, MARK_EMAIL_BODY);
    String body = extractFrom(raw, MARK_EMAIL_BODY);
    return new ParsedContent(waName, waBody, subject, body);
  }

  /** Renders the marker-based storage format back into a clean, human-readable preview for the API/report —
   *  never exposes the internal control-character markers to the frontend. */
  private String humanReadable(String raw) {
    ParsedContent p = parseContent(raw);
    StringBuilder sb = new StringBuilder();
    if (p.whatsappTemplateName() != null) {
      sb.append("WhatsApp — ").append(p.whatsappTemplateName()).append("\n").append(nullToEmpty(p.whatsappBody()));
    }
    if (p.emailSubject() != null) {
      if (sb.length() > 0) sb.append("\n\n---\n\n");
      sb.append("Email — ").append(p.emailSubject()).append("\n").append(nullToEmpty(p.emailBody()));
    }
    return sb.toString();
  }

  private static String extractBetween(String raw, String startMark, String endMark) {
    int start = raw.indexOf(startMark);
    if (start < 0) return null;
    start += startMark.length();
    int end = raw.indexOf(endMark, start);
    return end < 0 ? raw.substring(start) : raw.substring(start, end);
  }

  private static String extractFrom(String raw, String startMark) {
    int start = raw.indexOf(startMark);
    return start < 0 ? null : raw.substring(start + startMark.length());
  }

  private static String nullToEmpty(String s) { return s == null ? "" : s; }

  /* ------------------------------------------------ misc ------------------------------------------------ */

  private BulkSendChannel parseChannel(String raw) {
    if (blank(raw)) throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "channel is required (WHATSAPP, EMAIL, or BOTH)");
    try {
      return BulkSendChannel.valueOf(raw.trim().toUpperCase());
    } catch (IllegalArgumentException e) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Unknown channel: " + raw);
    }
  }

  private static boolean blank(String s) { return s == null || s.isBlank(); }

  private BulkSendResponse toResponse(BulkSend b) {
    return new BulkSendResponse(b.id, humanReadable(b.content), b.channel.name(), b.listSize, b.sentCount,
        b.deliveredCount, b.failedCount, b.optedOutCount, b.status.name(), b.createdAt);
  }
}
