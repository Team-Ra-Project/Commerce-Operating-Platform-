package com.rastudio.commerce.retention;

import com.rastudio.commerce.common.ApiException;
import com.rastudio.commerce.customer.Customer;
import com.rastudio.commerce.customer.CustomerRepository;
import com.rastudio.commerce.order.CustomerOrder;
import com.rastudio.commerce.order.CustomerOrderRepository;
import com.rastudio.commerce.retention.SegmentDtos.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SegmentService {

  private static final BigDecimal VIP_LIFETIME_SPEND_THRESHOLD = new BigDecimal("20000");
  private static final int LOYAL_MIN_ORDERS = 3;
  private static final int RECENT_BUYER_WINDOW_DAYS = 30;
  private static final int LAPSED_MIN_DAYS_SINCE_LAST_ORDER = 90;

  private record DefaultSegment(String name, SegmentRuleKey ruleKey, String description) {}

  private static final List<DefaultSegment> DEFAULT_SEGMENTS = List.of(
      new DefaultSegment("New Customers", SegmentRuleKey.NEW, "Customers with no repeat purchase, including manually added customers"),
      new DefaultSegment("Recent Buyers", SegmentRuleKey.RECENT_BUYER, "Ordered within the last " + RECENT_BUYER_WINDOW_DAYS + " days"),
      new DefaultSegment("Loyal Customers", SegmentRuleKey.LOYAL, "Placed " + LOYAL_MIN_ORDERS + " or more orders"),
      new DefaultSegment("VIP Customers", SegmentRuleKey.VIP, "Lifetime spend of " + VIP_LIFETIME_SPEND_THRESHOLD + " or more"),
      new DefaultSegment("Lapsed Customers", SegmentRuleKey.LAPSED, "No order in the last " + LAPSED_MIN_DAYS_SINCE_LAST_ORDER + "+ days"),
      new DefaultSegment("Cart Abandoners", SegmentRuleKey.CART_ABANDONED,
          "Not computable yet — this project's schema has no abandoned-cart/session tracking table, so this segment is always empty until that exists"));

  private final SegmentRepository segments;
  private final CampaignRepository campaigns;
  private final CustomerOrderRepository orders;
  private final CustomerRepository customers;

  public SegmentService(SegmentRepository segments, CampaignRepository campaigns, CustomerOrderRepository orders,
      CustomerRepository customers) {
    this.segments = segments;
    this.campaigns = campaigns;
    this.orders = orders;
    this.customers = customers;
  }

  @Transactional
  public List<SegmentResponse> list(Long organizationId) {
    if (!segments.existsByOrganizationId(organizationId)) {
      for (DefaultSegment d : DEFAULT_SEGMENTS) {
        Segment s = new Segment();
        s.organizationId = organizationId;
        s.name = d.name();
        s.ruleKey = d.ruleKey();
        s.description = d.description();
        segments.save(s);
      }
    }
    return segments.findByOrganizationIdOrderByNameAsc(organizationId).stream()
        .map(s -> toResponse(s, memberIds(organizationId, s.ruleKey).size()))
        .toList();
  }

  @Transactional
  public SegmentResponse create(Long organizationId, SegmentRequest request) {
    if (request.name() == null || request.name().isBlank()) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Segment name is required");
    }
    SegmentRuleKey ruleKey = parseRuleKey(request.ruleKey());
    Segment s = new Segment();
    s.organizationId = organizationId;
    s.name = request.name().trim();
    s.ruleKey = ruleKey;
    s.description = request.description();
    segments.save(s);
    return toResponse(s, memberIds(organizationId, ruleKey).size());
  }

  @Transactional
  public SegmentResponse update(Long organizationId, Long id, SegmentRequest request) {
    if (request.name() == null || request.name().isBlank()) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Segment name is required");
    }
    Segment segment = require(organizationId, id);
    segment.name = request.name().trim();
    segment.ruleKey = parseRuleKey(request.ruleKey());
    segment.description = request.description();
    segments.save(segment);
    return toResponse(segment, memberIds(organizationId, segment.ruleKey).size());
  }

  @Transactional
  public void delete(Long organizationId, Long id) {
    Segment s = require(organizationId, id);
    if (campaigns.existsByOrganizationIdAndSegmentId(organizationId, s.id)) {
      throw new ApiException(HttpStatus.CONFLICT, "SEGMENT_IN_USE", "This segment is used by a campaign — remove or reassign that campaign first");
    }
    segments.delete(s);
  }

  Segment require(Long organizationId, Long id) {
    return segments.findByIdAndOrganizationId(id, organizationId)
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "SEGMENT_NOT_FOUND", "Segment not found"));
  }

  /** Public entry point for other modules (Phase 22 bulk broadcast) that need a segment's actual member
   *  customer ids, not just the count {@link #list} exposes. Reuses the exact same live rule evaluation. */
  @Transactional
  public List<Long> memberCustomerIds(Long organizationId, Long segmentId) {
    return memberIds(organizationId, require(organizationId, segmentId).ruleKey);
  }

  private SegmentRuleKey parseRuleKey(String raw) {
    if (raw == null || raw.isBlank()) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "ruleKey is required");
    }
    try {
      return SegmentRuleKey.valueOf(raw.trim().toUpperCase());
    } catch (IllegalArgumentException e) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Unknown ruleKey: " + raw);
    }
  }

  /**
   * Computes which customers currently belong to a rule-key-based segment from the real customer and order
   * tables — evaluated fresh every time (list, activate-campaign, enroll), not cached, so it reflects current
   * CRM records and order history. CART_ABANDONED always returns empty (see class javadoc).
   */
  List<Long> memberIds(Long organizationId, SegmentRuleKey ruleKey) {
    if (ruleKey == SegmentRuleKey.CART_ABANDONED) return List.of();

    List<CustomerOrder> allOrders = orders.findByOrganizationIdOrderByPlacedAtDesc(organizationId);
    Map<Long, List<CustomerOrder>> byCustomer = allOrders.stream().collect(Collectors.groupingBy(o -> o.customerId));
    LocalDateTime now = LocalDateTime.now();

    /*
     * Use the customer table as the audience source, not only customer_order.
     * A customer added manually in CRM may not have a marketplace order row yet,
     * but is still a valid "New Customer" audience member for retention.
     */
    return customers.findByOrganizationIdOrderByCreatedAtDesc(organizationId).stream()
        .filter(customer -> matchesRule(byCustomer.getOrDefault(customer.id, List.of()), ruleKey, now))
        .map(customer -> customer.id)
        .toList();
  }

  private boolean matchesRule(List<CustomerOrder> customerOrders, SegmentRuleKey ruleKey, LocalDateTime now) {
    int orderCount = customerOrders.size();
    LocalDateTime lastOrderAt = customerOrders.stream().map(o -> o.placedAt).max(Comparator.naturalOrder()).orElse(null);
    BigDecimal lifetimeSpend = customerOrders.stream().map(o -> o.totalAmount).reduce(BigDecimal.ZERO, BigDecimal::add);

    return switch (ruleKey) {
      // Includes manually-added customers with no order yet, plus first-order customers.
      case NEW -> orderCount <= 1;
      case RECENT_BUYER -> lastOrderAt != null && lastOrderAt.isAfter(now.minusDays(RECENT_BUYER_WINDOW_DAYS));
      case LOYAL -> orderCount >= LOYAL_MIN_ORDERS;
      case VIP -> lifetimeSpend.compareTo(VIP_LIFETIME_SPEND_THRESHOLD) >= 0;
      case LAPSED -> lastOrderAt != null && lastOrderAt.isBefore(now.minusDays(LAPSED_MIN_DAYS_SINCE_LAST_ORDER));
      case CART_ABANDONED -> false;
    };
  }

  private SegmentResponse toResponse(Segment s, long memberCount) {
    return new SegmentResponse(s.id, s.name, s.ruleKey.name(), s.description, memberCount);
  }
}
