package com.rastudio.commerce.retention;

import com.rastudio.commerce.common.ApiException;
import com.rastudio.commerce.customer.Customer;
import com.rastudio.commerce.customer.CustomerRepository;
import com.rastudio.commerce.messaging.MessageChannel;
import com.rastudio.commerce.retention.CampaignDtos.*;
import com.rastudio.commerce.template.ApprovalStatus;
import com.rastudio.commerce.template.MessageTemplate;
import com.rastudio.commerce.template.MessageTemplateRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Phase 20 — Retention Marketing: build a multi-step WhatsApp/Email journey targeting a segment, activate it
 * (enrolling the segment's current members), and track performance. Actual sending happens in
 * {@link CampaignSchedulerJob}; conversion is detected by {@link ConversionDetectionService} from OrderService.
 *
 * Enrollment is a one-time snapshot taken at activation, not a continuously-refreshed audience — matches the
 * roadmap's "Enroll matching customers" as an activation-time action, not an ongoing membership sync. Pausing a
 * campaign stops new sends but does not push out already-due step timing (delay_days is measured as an
 * absolute day-offset from each customer's own enrollment date, not "days since last send"), so resuming
 * immediately catches up anything that became due while paused — a deliberate simplification, documented here
 * and in docs/CURRENT_STATUS.md.
 */
@Service
public class CampaignService {

  private final CampaignRepository campaigns;
  private final CampaignStepRepository steps;
  private final CampaignEnrollmentRepository enrollments;
  private final MessageLogRepository messageLogs;
  private final SegmentService segmentService;
  private final SegmentRepository segments;
  private final MessageTemplateRepository templates;
  private final CustomerRepository customers;

  public CampaignService(CampaignRepository campaigns, CampaignStepRepository steps,
      CampaignEnrollmentRepository enrollments, MessageLogRepository messageLogs, SegmentService segmentService,
      SegmentRepository segments, MessageTemplateRepository templates, CustomerRepository customers) {
    this.campaigns = campaigns;
    this.steps = steps;
    this.enrollments = enrollments;
    this.messageLogs = messageLogs;
    this.segmentService = segmentService;
    this.segments = segments;
    this.templates = templates;
    this.customers = customers;
  }

  @Transactional
  public List<CampaignResponse> list(Long organizationId) {
    List<Campaign> campaignList = campaigns.findByOrganizationIdOrderByCreatedAtDesc(organizationId);
    if (campaignList.isEmpty()) return List.of();
    List<Long> ids = campaignList.stream().map(c -> c.id).toList();
    Map<Long, List<CampaignStep>> stepsByCampaign = steps.findByCampaignIdInOrderByStepOrderAsc(ids).stream()
        .collect(Collectors.groupingBy(s -> s.campaignId));
    Map<Long, Segment> segmentById = segments.findByOrganizationIdOrderByNameAsc(organizationId).stream()
        .collect(Collectors.toMap(s -> s.id, s -> s));
    return campaignList.stream()
        .map(c -> toResponse(c, stepsByCampaign.getOrDefault(c.id, List.of()), segmentById.get(c.segmentId)))
        .toList();
  }

  @Transactional
  public CampaignResponse get(Long organizationId, Long id) {
    Campaign c = require(organizationId, id);
    return toResponse(c, steps.findByCampaignIdOrderByStepOrderAsc(c.id),
        segments.findById(c.segmentId).orElse(null));
  }

  @Transactional
  public CampaignResponse create(Long organizationId, CampaignRequest request) {
    if (request.name() == null || request.name().isBlank()) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Campaign name is required");
    }
    Segment segment = segmentService.require(organizationId, request.segmentId());
    CampaignGoal goal = parseGoal(request.goal());

    Campaign c = new Campaign();
    c.organizationId = organizationId;
    c.name = request.name().trim();
    c.segmentId = segment.id;
    c.goal = goal;
    c.status = CampaignStatus.DRAFT;
    campaigns.save(c);

    List<CampaignStep> saved = reconcileSteps(organizationId, c.id, List.of(), request.steps());
    return toResponse(c, saved, segment);
  }

  @Transactional
  public CampaignResponse update(Long organizationId, Long id, CampaignRequest request) {
    Campaign c = require(organizationId, id);
    if (c.status != CampaignStatus.DRAFT) {
      throw new ApiException(HttpStatus.CONFLICT, "CAMPAIGN_NOT_DRAFT", "Only a draft campaign's journey can be edited — pause won't allow structural changes");
    }
    Segment segment = segmentService.require(organizationId, request.segmentId());
    c.name = request.name() == null || request.name().isBlank() ? c.name : request.name().trim();
    c.segmentId = segment.id;
    c.goal = parseGoal(request.goal());
    campaigns.save(c);

    List<CampaignStep> existing = steps.findByCampaignIdOrderByStepOrderAsc(c.id);
    List<CampaignStep> saved = reconcileSteps(organizationId, c.id, existing, request.steps());
    return toResponse(c, saved, segment);
  }

  @Transactional
  public void delete(Long organizationId, Long id) {
    Campaign c = require(organizationId, id);
    if (c.status != CampaignStatus.DRAFT) {
      throw new ApiException(HttpStatus.CONFLICT, "CAMPAIGN_NOT_DRAFT", "Only a draft campaign can be deleted — pause and let an active one finish instead");
    }
    steps.deleteByCampaignId(c.id);
    campaigns.delete(c);
  }

  /** "Activate Campaign": DRAFT/PAUSED -> ACTIVE. First activation enrolls the segment's current members. */
  @Transactional
  public CampaignResponse activate(Long organizationId, Long id) {
    Campaign c = require(organizationId, id);
    if (c.status != CampaignStatus.DRAFT && c.status != CampaignStatus.PAUSED) {
      throw new ApiException(HttpStatus.CONFLICT, "INVALID_TRANSITION", "Only a draft or paused campaign can be activated");
    }
    List<CampaignStep> journeySteps = steps.findByCampaignIdOrderByStepOrderAsc(c.id);
    if (journeySteps.isEmpty()) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "NO_STEPS", "Add at least one journey step before activating");
    }
    boolean firstActivation = c.status == CampaignStatus.DRAFT;
    if (firstActivation) {
      Segment segment = segments.findById(c.segmentId)
          .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "SEGMENT_NOT_FOUND", "Segment not found"));
      List<Long> memberIds = segmentService.memberIds(organizationId, segment.ruleKey);
      int newlyEnrolled = 0;
      for (Long customerId : memberIds) {
        if (!enrollments.existsByCampaignIdAndCustomerId(c.id, customerId)) {
          CampaignEnrollment e = new CampaignEnrollment();
          e.campaignId = c.id;
          e.customerId = customerId;
          e.currentStep = 0;
          enrollments.save(e);
          newlyEnrolled++;
        }
      }
      c.enrolledCount = c.enrolledCount + newlyEnrolled;
      if (newlyEnrolled == 0) {
        throw new ApiException(HttpStatus.BAD_REQUEST, "EMPTY_SEGMENT",
            "This segment currently has no matching customers to enroll");
      }
    }
    c.status = CampaignStatus.ACTIVE;
    campaigns.save(c);
    return toResponse(c, journeySteps, segments.findById(c.segmentId).orElse(null));
  }

  /** "Pause Campaign": ACTIVE -> PAUSED. The scheduler skips non-ACTIVE campaigns entirely. */
  @Transactional
  public CampaignResponse pause(Long organizationId, Long id) {
    Campaign c = require(organizationId, id);
    if (c.status != CampaignStatus.ACTIVE) {
      throw new ApiException(HttpStatus.CONFLICT, "INVALID_TRANSITION", "Only an active campaign can be paused");
    }
    c.status = CampaignStatus.PAUSED;
    campaigns.save(c);
    return toResponse(c, steps.findByCampaignIdOrderByStepOrderAsc(c.id), segments.findById(c.segmentId).orElse(null));
  }

  public List<MessageLogResponse> messageLogs(Long organizationId, Long campaignId) {
    require(organizationId, campaignId);
    List<MessageLog> logs = messageLogs.findByCampaignIdOrderByCreatedAtDesc(campaignId);
    if (logs.isEmpty()) return List.of();
    List<Long> customerIds = logs.stream().map(l -> l.customerId).filter(java.util.Objects::nonNull).distinct().toList();
    Map<Long, Customer> customerById = customers.findByIdInAndOrganizationId(customerIds, organizationId).stream()
        .collect(Collectors.toMap(cu -> cu.id, cu -> cu));
    return logs.stream().map(l -> new MessageLogResponse(l.id, l.customerId,
        l.customerId != null && customerById.containsKey(l.customerId) ? customerById.get(l.customerId).fullName : "Unknown customer",
        l.channel.name(), l.status.name(), l.providerStatusDetail, l.sentAt)).toList();
  }

  /* ------------------------------------ internals ------------------------------------ */

  Campaign require(Long organizationId, Long id) {
    return campaigns.findByIdAndOrganizationId(id, organizationId)
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "CAMPAIGN_NOT_FOUND", "Campaign not found"));
  }

  private CampaignGoal parseGoal(String raw) {
    if (raw == null || raw.isBlank()) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "goal is required");
    }
    try {
      return CampaignGoal.valueOf(raw.trim().toUpperCase());
    } catch (IllegalArgumentException e) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Unknown goal: " + raw);
    }
  }

  private List<CampaignStep> reconcileSteps(Long organizationId, Long campaignId, List<CampaignStep> existing, List<CampaignStepInput> requested) {
    if (requested == null || requested.isEmpty()) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "A campaign needs at least one journey step");
    }
    Map<Long, CampaignStep> existingById = existing.stream().collect(Collectors.toMap(s -> s.id, s -> s));
    java.util.Set<Long> keepIds = requested.stream().map(CampaignStepInput::id).filter(java.util.Objects::nonNull)
        .collect(Collectors.toSet());
    for (CampaignStep s : existing) {
      if (!keepIds.contains(s.id)) steps.delete(s);
    }
    List<CampaignStep> result = new ArrayList<>();
    int order = 1;
    for (CampaignStepInput input : requested) {
      MessageChannel channel = parseChannel(input.channel());
      if (input.messageTemplateId() == null) {
        throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Each step needs a messageTemplateId");
      }
      MessageTemplate template = templates.findByIdAndOrganizationId(input.messageTemplateId(), organizationId)
          .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "TEMPLATE_NOT_FOUND", "Template " + input.messageTemplateId() + " not found"));
      if (template.channel != channel) {
        throw new ApiException(HttpStatus.BAD_REQUEST, "CHANNEL_MISMATCH",
            "Step channel " + channel + " does not match template \"" + template.name + "\"'s channel " + template.channel);
      }
      if (template.approvalStatus != ApprovalStatus.APPROVED) {
        throw new ApiException(HttpStatus.CONFLICT, "TEMPLATE_NOT_APPROVED",
            "Template \"" + template.name + "\" is not approved yet (" + template.approvalStatus + ") and can't be used in a journey");
      }
      int delayDays = input.delayDays() == null ? 0 : Math.max(0, input.delayDays());

      CampaignStep step = input.id() != null ? existingById.get(input.id()) : null;
      if (step == null) step = new CampaignStep();
      step.campaignId = campaignId;
      step.stepOrder = order++;
      step.channel = channel;
      step.messageTemplateId = template.id;
      step.delayDays = delayDays;
      result.add(steps.save(step));
    }
    return result;
  }

  private MessageChannel parseChannel(String raw) {
    if (raw == null || raw.isBlank()) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Each step needs a channel (WHATSAPP or EMAIL)");
    }
    try {
      return MessageChannel.valueOf(raw.trim().toUpperCase());
    } catch (IllegalArgumentException e) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Unknown channel: " + raw);
    }
  }

  private CampaignResponse toResponse(Campaign c, List<CampaignStep> stepList, Segment segment) {
    Map<Long, MessageTemplate> templateById = new HashMap<>();
    for (CampaignStep s : stepList) {
      templates.findById(s.messageTemplateId).ifPresent(t -> templateById.put(t.id, t));
    }
    List<CampaignStepResponse> stepDtos = stepList.stream()
        .map(s -> new CampaignStepResponse(s.id, s.stepOrder, s.channel.name(), s.messageTemplateId,
            templateById.containsKey(s.messageTemplateId) ? templateById.get(s.messageTemplateId).name : "(deleted template)",
            s.delayDays))
        .toList();
    long audienceSize = segment == null ? 0 : segmentService.memberIds(c.organizationId, segment.ruleKey).size();
    return new CampaignResponse(c.id, c.name, c.segmentId, segment == null ? null : segment.name, audienceSize,
        c.goal.name(), c.status.name(), stepDtos, c.enrolledCount, c.sentCount, c.deliveredCount, c.openedCount,
        c.clickedCount, c.convertedCount, c.revenueAttributed, c.createdAt, c.updatedAt);
  }
}
