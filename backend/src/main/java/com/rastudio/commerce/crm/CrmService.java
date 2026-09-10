package com.rastudio.commerce.crm;

import com.rastudio.commerce.common.ApiException;
import com.rastudio.commerce.crm.CrmDtos.*;
import com.rastudio.commerce.customer.Customer;
import com.rastudio.commerce.customer.CustomerRepository;
import com.rastudio.commerce.order.CustomerOrder;
import com.rastudio.commerce.order.CustomerOrderRepository;
import com.rastudio.commerce.user.AppUser;
import com.rastudio.commerce.user.AppUserRepository;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Phase 10 — CRM & Customer Management. Builds the "unified customer profile" the roadmap calls for on top of
 * the `customer` (Phase 8 already writes to this via order ingestion — "Customer must be created/updated from
 * incoming orders" holds because Phase 8's OrderService.resolveCustomer already does exactly that) and
 * `customer_note` tables. Customer Segmentation (the `segment` table, Create/Edit/Delete Segment buttons) is
 * Phase 11 and is intentionally not built here.
 */
@Service
public class CrmService {

  private final CustomerRepository customers;
  private final CustomerOrderRepository orders;
  private final CustomerNoteRepository notes;
  private final AppUserRepository users;

  public CrmService(CustomerRepository customers, CustomerOrderRepository orders, CustomerNoteRepository notes,
      AppUserRepository users) {
    this.customers = customers;
    this.orders = orders;
    this.notes = notes;
    this.users = users;
  }

  public List<CustomerSummaryDto> list(Long organizationId, String search) {
    List<Customer> customerList = customers.findByOrganizationIdOrderByCreatedAtDesc(organizationId);
    Map<Long, List<CustomerOrder>> ordersByCustomer = orders.findByOrganizationIdOrderByPlacedAtDesc(organizationId)
        .stream().collect(Collectors.groupingBy(o -> o.customerId));

    String needle = search == null ? null : search.trim().toLowerCase(Locale.ROOT);
    return customerList.stream()
        .filter(c -> needle == null || needle.isBlank()
            || (c.fullName != null && c.fullName.toLowerCase(Locale.ROOT).contains(needle))
            || (c.email != null && c.email.toLowerCase(Locale.ROOT).contains(needle))
            || (c.phone != null && c.phone.contains(needle)))
        .map(c -> toSummary(c, ordersByCustomer.getOrDefault(c.id, List.of())))
        .toList();
  }

  @Transactional
  public CustomerSummaryDto create(Long organizationId, AddCustomerRequest request) {
    String email = normalizeEmail(request.email());
    String phone = normalizePhone(request.phone());

    Customer matchingEmail = email == null ? null
        : customers.findByOrganizationIdAndEmailIgnoreCase(organizationId, email).orElse(null);
    Customer matchingPhone = phone == null ? null
        : customers.findByOrganizationIdAndPhone(organizationId, phone).orElse(null);

    if (matchingEmail != null || matchingPhone != null) {
      if (matchingEmail != null && matchingPhone != null && matchingEmail.id.equals(matchingPhone.id)) {
        throw new ApiException(HttpStatus.CONFLICT, "CUSTOMER_ALREADY_EXISTS",
            "A customer with this email and phone already exists");
      }
      throw new ApiException(HttpStatus.CONFLICT, "CUSTOMER_ALREADY_EXISTS",
          matchingEmail != null
              ? "A customer with this email already exists"
              : "A customer with this phone already exists");
    }

    Customer customer = new Customer();
    customer.organizationId = organizationId;
    customer.fullName = request.fullName().trim();
    customer.email = email;
    customer.phone = phone;
    customer.city = normalizeText(request.city());
    customer.country = normalizeText(request.country());
    customer.primaryChannel = normalizeText(request.primaryChannel()) == null
        ? "DIRECT_STORE" : normalizeText(request.primaryChannel());
    customer.status = com.rastudio.commerce.customer.CustomerStatus.ACTIVE;
    customer.whatsappOptIn = Boolean.TRUE.equals(request.whatsappOptIn());
    customer.emailOptIn = Boolean.TRUE.equals(request.emailOptIn());

    try {
      customer = customers.saveAndFlush(customer);
    } catch (DataIntegrityViolationException e) {
      // Keeps duplicate protection intact if two requests arrive concurrently.
      throw new ApiException(HttpStatus.CONFLICT, "CUSTOMER_ALREADY_EXISTS",
          "A customer with this email or phone already exists");
    }
    return toSummary(customer, List.of());
  }

  public CustomerProfileDto profile(Long organizationId, Long customerId) {
    Customer customer = requireCustomer(organizationId, customerId);
    List<CustomerOrder> customerOrders = orders.findByCustomerIdAndOrganizationIdOrderByPlacedAtDesc(customerId, organizationId);

    List<PurchaseHistoryEntryDto> history = customerOrders.stream()
        .map(o -> new PurchaseHistoryEntryDto(o.id, o.orderNumber, o.marketplaceName.name(), o.status.name(), o.totalAmount, o.placedAt))
        .toList();

    Map<Long, AppUser> userCache = new HashMap<>();
    List<NoteDto> noteDtos = notes.findByCustomerIdOrderByCreatedAtDesc(customerId).stream()
        .map(n -> {
          String authorName = "System";
          if (n.authorUserId != null) {
            AppUser author = userCache.computeIfAbsent(n.authorUserId,
                id -> users.findByIdAndOrganizationId(id, organizationId).orElse(null));
            if (author != null) authorName = author.fullName;
          }
          return new NoteDto(n.id, authorName, n.noteText, n.createdAt);
        })
        .toList();

    return new CustomerProfileDto(toSummary(customer, customerOrders), history, noteDtos);
  }

  @Transactional
  public NoteDto addNote(Long organizationId, Long customerId, Long actorUserId, AddNoteRequest request) {
    requireCustomer(organizationId, customerId);
    if (request == null || request.noteText() == null || request.noteText().isBlank()) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "noteText is required");
    }
    CustomerNote note = new CustomerNote();
    note.customerId = customerId;
    note.authorUserId = actorUserId;
    note.noteText = request.noteText().trim();
    notes.save(note);

    String authorName = users.findByIdAndOrganizationId(actorUserId, organizationId).map(u -> u.fullName).orElse("System");
    return new NoteDto(note.id, authorName, note.noteText, note.createdAt);
  }

  public String exportCsv(Long organizationId) {
    List<CustomerSummaryDto> rows = list(organizationId, null);
    StringBuilder csv = new StringBuilder("Name,Email,Phone,City,Country,Channel,Status,Orders,Total Spent,Last Order\n");
    for (CustomerSummaryDto r : rows) {
      csv.append(csvEscape(r.fullName())).append(',')
          .append(csvEscape(r.email())).append(',')
          .append(csvEscape(r.phone())).append(',')
          .append(csvEscape(r.city())).append(',')
          .append(csvEscape(r.country())).append(',')
          .append(csvEscape(r.primaryChannel())).append(',')
          .append(csvEscape(r.status())).append(',')
          .append(r.orderCount()).append(',')
          .append(r.totalSpent()).append(',')
          .append(r.lastOrderAt() == null ? "" : r.lastOrderAt()).append('\n');
    }
    return csv.toString();
  }

  /* ------------------------------------ internals ----------------------------------- */

  private Customer requireCustomer(Long organizationId, Long customerId) {
    return customers.findByIdAndOrganizationId(customerId, organizationId)
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "CUSTOMER_NOT_FOUND", "Customer not found"));
  }

  private static String normalizeEmail(String value) {
    String normalized = normalizeText(value);
    return normalized == null ? null : normalized.toLowerCase(Locale.ROOT);
  }

  private static String normalizePhone(String value) {
    String normalized = normalizeText(value);
    if (normalized == null) return null;
    return normalized.replaceAll("[\\s()\\-]", "");
  }

  private static String normalizeText(String value) {
    if (value == null || value.isBlank()) return null;
    return value.trim();
  }

  private CustomerSummaryDto toSummary(Customer c, List<CustomerOrder> customerOrders) {
    BigDecimal totalSpent = customerOrders.stream().map(o -> o.totalAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    java.time.LocalDateTime lastOrderAt = customerOrders.stream()
        .map(o -> o.placedAt).filter(Objects::nonNull).max(java.time.LocalDateTime::compareTo).orElse(null);
    return new CustomerSummaryDto(c.id, c.fullName, c.email, c.phone, c.city, c.country, c.primaryChannel,
        c.status.name(), Boolean.TRUE.equals(c.whatsappOptIn), Boolean.TRUE.equals(c.emailOptIn),
        customerOrders.size(), totalSpent, lastOrderAt, c.createdAt);
  }

  private static String csvEscape(Object value) {
    String s = value == null ? "" : value.toString();
    if (s.contains(",") || s.contains("\"") || s.contains("\n")) return "\"" + s.replace("\"", "\"\"") + "\"";
    return s;
  }
}
