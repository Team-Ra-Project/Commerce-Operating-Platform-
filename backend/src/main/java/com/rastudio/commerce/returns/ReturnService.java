package com.rastudio.commerce.returns;

import com.rastudio.commerce.automation.AutomationEvent;
import com.rastudio.commerce.automation.AutomationService;
import com.rastudio.commerce.automation.TriggerType;
import com.rastudio.commerce.common.ApiException;
import com.rastudio.commerce.crm.CrmDtos.AddNoteRequest;
import com.rastudio.commerce.crm.CrmService;
import com.rastudio.commerce.customer.Customer;
import com.rastudio.commerce.customer.CustomerRepository;
import com.rastudio.commerce.inventory.InventoryService;
import com.rastudio.commerce.mail.MailService;
import com.rastudio.commerce.notification.NotificationService;
import com.rastudio.commerce.order.*;
import com.rastudio.commerce.returns.ReturnDtos.*;
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Phase 9 — Returns & Shipping Management. Status flow matches FULL_WORKFLOW.md Module 8 exactly:
 * REQUESTED -> APPROVED_AWAITING_ITEM -> RECEIVED_INSPECTING -> REFUNDED/REPLACED -> CLOSED (REJECTED is its
 * own terminal branch straight from REQUESTED). Every inventory change goes through InventoryService, never
 * touching inventory_item/inventory_movement directly, and every resolved case is logged to the customer's
 * CRM timeline via CrmService — "Customer CRM history must be updated" from the roadmap, satisfied for real
 * because Phase 10 (CRM) exists and this module calls into it rather than duplicating note-writing logic.
 */
@Service
public class ReturnService {

  private static final SecureRandom RANDOM = new SecureRandom();
  private static final int REASON_MAX_LENGTH = 300;

  private final ReturnRequestRepository returns;
  private final CustomerOrderRepository orders;
  private final OrderItemRepository orderItems;
  private final OrderStatusHistoryRepository orderHistory;
  private final CustomerRepository customers;
  private final InventoryService inventory;
  private final CrmService crm;
  private final MailService mail;
  private final AutomationService automation;
  private final NotificationService notifications;

  public ReturnService(ReturnRequestRepository returns, CustomerOrderRepository orders, OrderItemRepository orderItems,
      OrderStatusHistoryRepository orderHistory, CustomerRepository customers, InventoryService inventory,
      CrmService crm, MailService mail, AutomationService automation, NotificationService notifications) {
    this.returns = returns;
    this.orders = orders;
    this.orderItems = orderItems;
    this.orderHistory = orderHistory;
    this.customers = customers;
    this.inventory = inventory;
    this.crm = crm;
    this.mail = mail;
    this.automation = automation;
    this.notifications = notifications;
  }

  /* ---------------------------------- creation ---------------------------------- */

  /** Logs a return/exchange request against an order. Stands in for the real customer-facing return request
   *  (marketplace return flow or self-service portal, per FULL_WORKFLOW.md — "[Customer] Requests a return..."
   *  — neither of which exists yet); Support Agent/Ops Manager/Admin record it here today, the same
   *  "real code, no live channel behind it yet" stance Phase 8's order ingestion already takes. */
  @Transactional
  public ReturnSummaryDto create(Long organizationId, CreateReturnRequest request) {
    if (request == null || request.orderId() == null) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "orderId is required");
    }
    if (request.reason() == null || request.reason().isBlank()) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "reason is required");
    }
    CustomerOrder order = orders.findByIdAndOrganizationId(request.orderId(), organizationId)
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", "Order not found"));
    if (returns.existsByOrderIdAndStatusNotIn(order.id, List.of(ReturnStatus.REJECTED, ReturnStatus.CLOSED))) {
      throw new ApiException(HttpStatus.CONFLICT, "RETURN_ALREADY_ACTIVE", "This order already has an active return request");
    }

    ReturnRequest r = new ReturnRequest();
    r.organizationId = organizationId;
    r.orderId = order.id;
    r.reason = cap(request.reason().trim());
    r.status = ReturnStatus.REQUESTED;
    returns.save(r);
    return toSummary(r, order);
  }

  /* ----------------------------------- listing ----------------------------------- */

  public List<ReturnSummaryDto> list(Long organizationId, ReturnStatus statusFilter) {
    return returns.findByOrganizationIdOrderByRequestedAtDesc(organizationId).stream()
        .filter(r -> statusFilter == null || r.status == statusFilter)
        .map(r -> toSummary(r, orders.findByIdAndOrganizationId(r.orderId, organizationId).orElse(null)))
        .toList();
  }

  public ReturnSummaryDto get(Long organizationId, Long returnId) {
    ReturnRequest r = requireReturn(organizationId, returnId);
    return toSummary(r, orders.findByIdAndOrganizationId(r.orderId, organizationId).orElse(null));
  }

  /* ------------------------------- status transitions ---------------------------- */

  /** "Approve Return Request" — Support Agent only. */
  @Transactional
  public ReturnSummaryDto approve(Long organizationId, Long returnId) {
    ReturnRequest r = requireReturn(organizationId, returnId);
    if (r.status != ReturnStatus.REQUESTED) {
      throw new ApiException(HttpStatus.CONFLICT, "INVALID_TRANSITION", "Only requested returns can be approved");
    }
    r.status = ReturnStatus.APPROVED_AWAITING_ITEM;
    returns.save(r);
    notifyCustomer(organizationId, r, "Approved – Awaiting Item",
        "Your return has been approved. Please ship the item back to us using the instructions provided.");
    return toSummary(r, orders.findByIdAndOrganizationId(r.orderId, organizationId).orElse(null));
  }

  /** "Reject Return Request" — Support Agent only. Prompts for a rejection reason, appended to the return's
   *  note trail (see class-level Javadoc on ReturnRequest), then the request is terminal/closed immediately —
   *  FULL_WORKFLOW.md's "[Support Agent] ... approves or rejects it" treats reject itself as closing the case,
   *  unlike the refund/replace path, which needs its own separate Close Case step. */
  @Transactional
  public ReturnSummaryDto reject(Long organizationId, Long returnId, RejectRequest request) {
    ReturnRequest r = requireReturn(organizationId, returnId);
    if (r.status != ReturnStatus.REQUESTED) {
      throw new ApiException(HttpStatus.CONFLICT, "INVALID_TRANSITION", "Only requested returns can be rejected");
    }
    String reason = request == null || request.reason() == null || request.reason().isBlank()
        ? "No reason given" : request.reason().trim();
    r.status = ReturnStatus.REJECTED;
    r.resolution = "REJECTED";
    r.reason = appendNote(r.reason, "Rejected: " + reason);
    returns.save(r);
    notifyCustomer(organizationId, r, "Rejected", "Your return request was declined: " + reason);
    logToCrm(organizationId, r, "Return rejected: " + reason);
    return toSummary(r, orders.findByIdAndOrganizationId(r.orderId, organizationId).orElse(null));
  }

  /** "Log Item Received" — Warehouse Staff only. */
  @Transactional
  public ReturnSummaryDto receiveItem(Long organizationId, Long returnId, ReceiveItemRequest request) {
    ReturnRequest r = requireReturn(organizationId, returnId);
    if (r.status != ReturnStatus.APPROVED_AWAITING_ITEM) {
      throw new ApiException(HttpStatus.CONFLICT, "INVALID_TRANSITION", "Only approved returns can log a received item");
    }
    String condition = request == null || request.condition() == null || request.condition().isBlank()
        ? "UNSPECIFIED" : request.condition().trim().toUpperCase();
    String note = "Received (" + condition + ")" + (request != null && request.note() != null && !request.note().isBlank()
        ? ": " + request.note().trim() : "");
    r.status = ReturnStatus.RECEIVED_INSPECTING;
    r.reason = appendNote(r.reason, note);
    returns.save(r);
    return toSummary(r, orders.findByIdAndOrganizationId(r.orderId, organizationId).orElse(null));
  }

  /** "Approve Refund/Replacement" — Support Agent only. If restock is requested, adds real stock back
   *  (InventoryService#restockFromReturn, RETURN_IN movement). On REPLACED, attempts to create and reserve a
   *  free replacement order for the same items; if stock can't be reserved, the replacement order is still
   *  created (status NEW) so Ops can fulfil it manually rather than blocking the return resolution on a stock
   *  hiccup. Either way, the outcome is logged to the customer's CRM timeline. */
  @Transactional
  public ReturnSummaryDto resolve(Long organizationId, Long returnId, ResolveRequest request) {
    ReturnRequest r = requireReturn(organizationId, returnId);
    if (r.status != ReturnStatus.RECEIVED_INSPECTING) {
      throw new ApiException(HttpStatus.CONFLICT, "INVALID_TRANSITION", "Only inspected returns can be resolved");
    }
    if (request == null || request.resolution() == null
        || !(request.resolution().equalsIgnoreCase("REFUNDED") || request.resolution().equalsIgnoreCase("REPLACED"))) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "resolution must be REFUNDED or REPLACED");
    }
    CustomerOrder order = orders.findByIdAndOrganizationId(r.orderId, organizationId)
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", "Order not found"));
    boolean restock = Boolean.TRUE.equals(request.restock());

    if (restock) {
      if (request.warehouseId() == null) {
        throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "warehouseId is required when restock is true");
      }
      List<OrderItem> items = orderItems.findByOrderIdOrderByIdAsc(order.id);
      for (OrderItem item : items) {
        int qty = request.quantity() != null ? request.quantity() : item.quantity;
        inventory.restockFromReturn(organizationId, item.productVariantId, request.warehouseId(), qty, r.id);
      }
    }

    String resolution = request.resolution().toUpperCase();
    r.resolution = resolution;
    String outcomeNote = "Resolved: " + resolution + (restock ? " (item restocked)" : " (item not restocked)");
    r.reason = appendNote(r.reason, outcomeNote);

    String replacementOrderNumber = null;
    if (resolution.equals("REFUNDED")) {
      order.paymentStatus = PaymentStatus.REFUNDED;
      orders.save(order);
      r.status = ReturnStatus.REFUNDED;
    } else {
      replacementOrderNumber = createReplacementOrder(organizationId, order);
      order.returnId = r.id;
      orders.save(order);
      r.status = ReturnStatus.REPLACED;
    }
    returns.save(r);

    String crmMessage = "Return " + resolution.toLowerCase() + " for order " + order.orderNumber
        + (replacementOrderNumber != null ? " — replacement order " + replacementOrderNumber + " created" : "");
    logToCrm(organizationId, r, crmMessage);
    notifyCustomer(organizationId, r, resolution.equals("REFUNDED") ? "Refunded" : "Replacement on the way", crmMessage);

    return toSummary(r, order);
  }

  /** "Close Case" — Support Agent only. Only from a resolved state; REJECTED returns are already terminal
   *  (see reject()). */
  @Transactional
  public ReturnSummaryDto close(Long organizationId, Long returnId) {
    ReturnRequest r = requireReturn(organizationId, returnId);
    if (r.status != ReturnStatus.REFUNDED && r.status != ReturnStatus.REPLACED) {
      throw new ApiException(HttpStatus.CONFLICT, "INVALID_TRANSITION", "Only refunded or replaced returns can be closed");
    }
    r.status = ReturnStatus.CLOSED;
    returns.save(r);
    return toSummary(r, orders.findByIdAndOrganizationId(r.orderId, organizationId).orElse(null));
  }

  /* -------------------------------------- internals -------------------------------------- */

  private ReturnRequest requireReturn(Long organizationId, Long returnId) {
    return returns.findByIdAndOrganizationId(returnId, organizationId)
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "RETURN_NOT_FOUND", "Return request not found"));
  }

  private ReturnSummaryDto toSummary(ReturnRequest r, CustomerOrder order) {
    String customerName = "Unknown customer";
    if (order != null) {
      customerName = customers.findByIdAndOrganizationId(order.customerId, r.organizationId)
          .map(c -> c.fullName).orElse(customerName);
    }
    return new ReturnSummaryDto(r.id, r.orderId, order == null ? "" : order.orderNumber, customerName, r.reason,
        r.status.name(), r.resolution, r.requestedAt, r.updatedAt);
  }

  private String createReplacementOrder(Long organizationId, CustomerOrder original) {
    List<OrderItem> originalItems = orderItems.findByOrderIdOrderByIdAsc(original.id);

    CustomerOrder replacement = new CustomerOrder();
    replacement.organizationId = organizationId;
    replacement.orderNumber = generateReplacementOrderNumber(organizationId, original.orderNumber);
    replacement.customerId = original.customerId;
    replacement.marketplaceName = original.marketplaceName;
    replacement.status = OrderStatus.NEW;
    replacement.paymentStatus = PaymentStatus.PAID; // already paid for on the original order
    replacement.subtotal = BigDecimal.ZERO;
    replacement.taxAmount = BigDecimal.ZERO;
    replacement.totalAmount = BigDecimal.ZERO; // free replacement — no new charge to the customer
    replacement.shippingAddress = original.shippingAddress;
    replacement = orders.save(replacement);

    boolean fullyReserved = true;
    for (OrderItem original_item : originalItems) {
      OrderItem copy = new OrderItem();
      copy.orderId = replacement.id;
      copy.productVariantId = original_item.productVariantId;
      copy.quantity = original_item.quantity;
      copy.unitPrice = BigDecimal.ZERO;
      copy.lineTotal = BigDecimal.ZERO;
      orderItems.save(copy);

      var warehouseId = inventory.bestWarehouseFor(organizationId, original_item.productVariantId, original_item.quantity);
      if (warehouseId.isPresent()) {
        try {
          inventory.reserveForOrder(original_item.productVariantId, warehouseId.get(), original_item.quantity, replacement.id);
        } catch (ApiException e) {
          fullyReserved = false;
        }
      } else {
        fullyReserved = false;
      }
    }

    if (fullyReserved) {
      replacement.status = OrderStatus.CONFIRMED;
      orders.save(replacement);
    }
    OrderStatusHistory h = new OrderStatusHistory();
    h.orderId = replacement.id;
    h.status = replacement.status.name();
    orderHistory.save(h);

    return replacement.orderNumber;
  }

  private String generateReplacementOrderNumber(Long organizationId, String originalOrderNumber) {
    String candidate;
    do {
      candidate = originalOrderNumber + "-RPL" + (100 + RANDOM.nextInt(900));
    } while (orders.existsByOrganizationIdAndOrderNumber(organizationId, candidate));
    return candidate;
  }

  private void logToCrm(Long organizationId, ReturnRequest r, String message) {
    CustomerOrder order = orders.findByIdAndOrganizationId(r.orderId, organizationId).orElse(null);
    if (order == null) return;
    try {
      crm.addNote(organizationId, order.customerId, null, new AddNoteRequest(message));
    } catch (Exception ignored) {
      // Best-effort CRM logging — never let a note-write failure block the actual return resolution.
    }
  }

  private void notifyCustomer(Long organizationId, ReturnRequest r, String statusLabel, String message) {
    CustomerOrder order = orders.findByIdAndOrganizationId(r.orderId, organizationId).orElse(null);
    if (order == null) return;
    Customer customer = customers.findByIdAndOrganizationId(order.customerId, organizationId).orElse(null);
    fireReturnUpdateEvent(organizationId, r, order, customer, statusLabel);
    if (customer == null || customer.email == null || customer.email.isBlank()) return; // "where the channel supports it"
    mail.sendReturnStatusUpdate(customer.email, customer.fullName, order.orderNumber, statusLabel, message);
  }

  private void fireReturnUpdateEvent(Long organizationId, ReturnRequest r, CustomerOrder order, Customer customer,
      String statusLabel) {
    String orderNumber = order == null ? "" : order.orderNumber;
    String summary = "Return for order " + orderNumber + " is now " + statusLabel;
    automation.fireEvent(new AutomationEvent(organizationId, TriggerType.RETURN_UPDATE, summary,
        Map.of("status", statusLabel),
        customer == null ? null : customer.email, customer == null ? null : customer.fullName,
        "returns", r.id,
        customer == null ? null : customer.phone, customer != null && Boolean.TRUE.equals(customer.whatsappOptIn)));
    notifications.create(organizationId, summary, "RETURN_UPDATE", "returns", r.id);
  }

  private static String appendNote(String existing, String note) {
    String combined = (existing == null ? "" : existing) + " — " + note;
    if (combined.length() > REASON_MAX_LENGTH) {
      combined = "…" + combined.substring(combined.length() - (REASON_MAX_LENGTH - 1));
    }
    return combined;
  }

  private static String cap(String s) {
    return s.length() > REASON_MAX_LENGTH ? s.substring(0, REASON_MAX_LENGTH) : s;
  }
}
