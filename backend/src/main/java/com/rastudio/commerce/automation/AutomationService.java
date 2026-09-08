package com.rastudio.commerce.automation;

import com.rastudio.commerce.audit.AuditLogService;
import com.rastudio.commerce.automation.AutomationDtos.*;
import com.rastudio.commerce.common.ApiException;
import com.rastudio.commerce.mail.MailService;
import com.rastudio.commerce.messaging.WhatsAppProvider;
import com.rastudio.commerce.notification.NotificationService;
import com.rastudio.commerce.organization.OrganizationRepository;
import com.rastudio.commerce.user.AppUser;
import com.rastudio.commerce.user.AppUserRepository;
import com.rastudio.commerce.user.UserRole;
import com.rastudio.commerce.user.UserStatus;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Phase 13 — Automation Engine. Architecture matches the roadmap exactly: Event -> Rule Engine -> Matching
 * Rules -> Conditions -> Action -> Execution Log. Other services (see TriggerType's Javadoc) call
 * {@link #fireEvent} when a real trigger happens; this class never polls for events itself except for the
 * scheduled competitor price check, which lives in CompetitorService, not here (see that class's Javadoc for
 * why competitor pricing doesn't feed this engine).
 */
@Service
public class AutomationService {

  private static final Logger log = LoggerFactory.getLogger(AutomationService.class);

  private final AutomationRuleRepository rules;
  private final AutomationRunLogRepository runLogs;
  private final MailService mail;
  private final NotificationService notifications;
  private final AppUserRepository users;
  private final OrganizationRepository organizations;
  private final AuditLogService audit;
  private final WhatsAppProvider whatsApp;

  public AutomationService(AutomationRuleRepository rules, AutomationRunLogRepository runLogs, MailService mail,
      NotificationService notifications, AppUserRepository users, OrganizationRepository organizations,
      AuditLogService audit, WhatsAppProvider whatsApp) {
    this.rules = rules;
    this.runLogs = runLogs;
    this.mail = mail;
    this.notifications = notifications;
    this.users = users;
    this.organizations = organizations;
    this.audit = audit;
    this.whatsApp = whatsApp;
  }

  /* --------------------------------------------- Create New Rule --------------------------------------------- */

  @Transactional
  public RuleDto create(Long organizationId, Long actorUserId, CreateRuleRequest request, HttpServletRequest http) {
    if (request == null || request.name() == null || request.name().isBlank()) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "name is required");
    }
    TriggerType trigger = parseTrigger(request.triggerType());
    ActionType action = parseAction(request.actionType());

    AutomationRule r = new AutomationRule();
    r.organizationId = organizationId;
    r.name = request.name().trim();
    r.triggerType = trigger;
    r.conditionText = normalizeCondition(request.conditionText());
    r.actionType = action;
    r.isActive = true;
    rules.save(r);

    audit.record(organizationId, actorUserId, "CREATE_AUTOMATION_RULE", "automation_rule", r.id,
        null, r.name + " (" + r.triggerType + " -> " + r.actionType + ")", http);
    return toDto(r);
  }

  /* ------------------------------------------------- listing ------------------------------------------------- */

  public List<RuleDto> list(Long organizationId) {
    return rules.findByOrganizationIdOrderByCreatedAtDesc(organizationId).stream().map(this::toDto).toList();
  }

  public RuleDto get(Long organizationId, Long id) {
    return toDto(requireRule(organizationId, id));
  }

  /* --------------------------------------------------- Edit Rule --------------------------------------------------- */

  @Transactional
  public RuleDto update(Long organizationId, Long actorUserId, Long id, UpdateRuleRequest request, HttpServletRequest http) {
    AutomationRule r = requireRule(organizationId, id);
    if (request == null || request.name() == null || request.name().isBlank()) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "name is required");
    }
    String previous = r.name + " (" + r.triggerType + " -> " + r.actionType + ")";
    r.name = request.name().trim();
    r.triggerType = parseTrigger(request.triggerType());
    r.conditionText = normalizeCondition(request.conditionText());
    r.actionType = parseAction(request.actionType());
    rules.save(r);

    audit.record(organizationId, actorUserId, "UPDATE_AUTOMATION_RULE", "automation_rule", r.id,
        previous, r.name + " (" + r.triggerType + " -> " + r.actionType + ")", http);
    return toDto(r);
  }

  /* --------------------------------------------- Activate/Deactivate --------------------------------------------- */

  @Transactional
  public RuleDto toggleActive(Long organizationId, Long actorUserId, Long id, HttpServletRequest http) {
    AutomationRule r = requireRule(organizationId, id);
    boolean previous = Boolean.TRUE.equals(r.isActive);
    r.isActive = !previous;
    rules.save(r);
    audit.record(organizationId, actorUserId, "TOGGLE_AUTOMATION_RULE", "automation_rule", r.id,
        previous ? "ACTIVE" : "PAUSED", r.isActive ? "ACTIVE" : "PAUSED", http);
    return toDto(r);
  }

  /* ------------------------------------------------- Delete Rule ------------------------------------------------- */

  @Transactional
  public void delete(Long organizationId, Long actorUserId, Long id, HttpServletRequest http) {
    AutomationRule r = requireRule(organizationId, id);
    runLogs.deleteByAutomationRuleId(r.id);
    rules.delete(r);
    audit.record(organizationId, actorUserId, "DELETE_AUTOMATION_RULE", "automation_rule", id, r.name, null, http);
  }

  /* ------------------------------------------------- View Run Log ------------------------------------------------- */

  public List<RunLogEntryDto> runLog(Long organizationId, Long id) {
    requireRule(organizationId, id); // 404s + org-scopes before exposing log rows
    return runLogs.findByAutomationRuleIdOrderByRanAtDesc(id).stream()
        .map(l -> new RunLogEntryDto(l.id, l.result, l.ranAt))
        .toList();
  }

  /* ====================================================================================================
     RULE ENGINE — Event -> Matching Rules -> Conditions -> Action -> Execution Log
     ==================================================================================================== */

  /** Called by other services when a real trigger happens. Never throws — a rule misfiring must never block
   *  the order/return/inventory action that raised the event, the same "must never fail the action it's
   *  describing" stance AuditLogService takes. */
  @Transactional
  public void fireEvent(AutomationEvent event) {
    try {
      List<AutomationRule> candidates = rules.findByOrganizationIdAndTriggerTypeAndIsActiveTrue(
          event.organizationId(), event.triggerType());
      for (AutomationRule rule : candidates) {
        evaluateAndRun(rule, event);
      }
    } catch (Exception e) {
      log.warn("Automation engine failed to process event {} for org {}: {}",
          event.triggerType(), event.organizationId(), e.getMessage());
    }
  }

  private void evaluateAndRun(AutomationRule rule, AutomationEvent event) {
    if (!matches(rule.conditionText, event)) {
      logRun(rule, "Skipped — condition not met");
      return;
    }
    String result;
    try {
      result = runAction(rule, event);
    } catch (Exception e) {
      result = "Failed — " + e.getMessage();
    }
    rule.runCount = (rule.runCount == null ? 0 : rule.runCount) + 1;
    rules.save(rule);
    logRun(rule, result);
  }

  /** Deliberately simple: a blank condition always matches; otherwise the condition text must appear as a
   *  case-insensitive substring of one of the event's context tag values (see AutomationEvent's Javadoc for
   *  what tags looks like per trigger). This mirrors the level of sophistication the mock UI's condition
   *  examples imply ("only for VIP segment", "only for Fashion category") without requiring a real rules DSL,
   *  which schema.sql's single condition_text VARCHAR column doesn't support anyway. */
  private boolean matches(String conditionText, AutomationEvent event) {
    if (conditionText == null || conditionText.isBlank()) return true;
    String needle = conditionText.trim().toLowerCase(Locale.ROOT);
    return event.tags().values().stream()
        .filter(v -> v != null)
        .anyMatch(v -> v.toLowerCase(Locale.ROOT).contains(needle));
  }

  private String runAction(AutomationRule rule, AutomationEvent event) {
    return switch (rule.actionType) {
      case SEND_EMAIL -> runSendEmail(rule, event);
      case SEND_WHATSAPP -> runSendWhatsApp(event);
      case CREATE_NOTIFICATION -> runCreateNotification(rule, event);
      case ADD_TO_SEGMENT -> "Skipped — segments in this platform are rule-evaluated on the fly (Phase 11), "
          + "with no stored membership table to add a customer into";
    };
  }

  private String runSendWhatsApp(AutomationEvent event) {
    if (event.customerPhone() == null || event.customerPhone().isBlank()) {
      return "Skipped — no customer phone number available for this event";
    }
    if (!event.customerWhatsAppOptIn()) {
      return "Skipped — customer has not opted in to WhatsApp";
    }
    WhatsAppProvider.ProviderSendResult result = whatsApp.sendMessage(event.customerPhone(), event.summary());
    return result.accepted() ? "Success — WhatsApp message sent to " + event.customerPhone()
        : "Failed — " + result.errorDetail();
  }

  private String runSendEmail(AutomationRule rule, AutomationEvent event) {
    String subject = "Automation alert: " + rule.name;
    if (event.customerEmail() != null && !event.customerEmail().isBlank()) {
      boolean sent = mail.sendAutomationEmail(event.customerEmail(), event.customerName(), subject, event.summary());
      return sent ? "Success — emailed " + event.customerEmail() : "Skipped — SMTP not configured";
    }
    String organizationName = organizations.findById(event.organizationId()).map(o -> o.name).orElse("your workspace");
    List<AppUser> recipients = users.findByOrganizationIdOrderByCreatedAtAsc(event.organizationId()).stream()
        .filter(u -> u.status == UserStatus.ACTIVE)
        .filter(u -> u.role == UserRole.BUSINESS_OWNER_ADMIN || u.role == UserRole.OPERATIONS_MANAGER)
        .toList();
    if (recipients.isEmpty()) return "Skipped — no Admin/Ops Manager recipient found";
    int sentCount = 0;
    for (AppUser recipient : recipients) {
      if (mail.sendAutomationEmail(recipient.email, recipient.fullName, subject, event.summary() + " (" + organizationName + ")")) {
        sentCount++;
      }
    }
    return sentCount > 0 ? "Success — emailed " + sentCount + " recipient(s)" : "Skipped — SMTP not configured";
  }

  private String runCreateNotification(AutomationRule rule, AutomationEvent event) {
    notifications.create(event.organizationId(), event.summary(), "AUTOMATION", event.linkModule(), event.linkEntityId());
    return "Success — notification created";
  }

  private void logRun(AutomationRule rule, String result) {
    AutomationRunLog log = new AutomationRunLog();
    log.automationRuleId = rule.id;
    log.result = result.length() > 300 ? result.substring(0, 300) : result;
    runLogs.save(log);
  }

  /* ------------------------------------------------------ internals ------------------------------------------------------ */

  private AutomationRule requireRule(Long organizationId, Long id) {
    return rules.findByIdAndOrganizationId(id, organizationId)
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "AUTOMATION_RULE_NOT_FOUND", "Automation rule not found"));
  }

  private TriggerType parseTrigger(String raw) {
    try {
      return TriggerType.valueOf(raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException e) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Invalid triggerType: " + raw);
    }
  }

  private ActionType parseAction(String raw) {
    try {
      return ActionType.valueOf(raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException e) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Invalid actionType: " + raw);
    }
  }

  private String normalizeCondition(String raw) {
    if (raw == null || raw.isBlank()) return null;
    String trimmed = raw.trim();
    return trimmed.length() > 300 ? trimmed.substring(0, 300) : trimmed;
  }

  private RuleDto toDto(AutomationRule r) {
    return new RuleDto(r.id, r.name, r.triggerType.name(), r.conditionText, r.actionType.name(),
        Boolean.TRUE.equals(r.isActive), r.runCount == null ? 0 : r.runCount, r.createdAt, r.updatedAt);
  }
}
