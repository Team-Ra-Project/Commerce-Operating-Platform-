package com.rastudio.commerce.order;

import com.rastudio.commerce.automation.AutomationEvent;
import com.rastudio.commerce.automation.AutomationService;
import com.rastudio.commerce.automation.TriggerType;
import com.rastudio.commerce.common.ApiException;
import com.rastudio.commerce.customer.Customer;
import com.rastudio.commerce.customer.CustomerRepository;
import com.rastudio.commerce.inventory.InventoryService;
import com.rastudio.commerce.notification.NotificationService;
import com.rastudio.commerce.marketplace.MarketplaceName;
import com.rastudio.commerce.order.OrderDtos.*;
import com.rastudio.commerce.product.Product;
import com.rastudio.commerce.product.ProductRepository;
import com.rastudio.commerce.product.ProductVariant;
import com.rastudio.commerce.product.ProductVariantRepository;
import com.rastudio.commerce.retention.ConversionDetectionService;
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Phase 8 — Order Management. Status flow matches FULL_WORKFLOW.md Module 5 exactly:
 * NEW -> CONFIRMED (reserve inventory) -> PACKED (deduct inventory) -> SHIPPED (courier label) ->
 * OUT_FOR_DELIVERY -> DELIVERED, tracked step by step in order_status_history. Every inventory change this
 * module causes is delegated to InventoryService (never touches inventory_item/inventory_movement directly),
 * so the "never silently change stock" rule from Phase 7 holds for order-driven changes too.
 */
@Service
public class OrderService {

  private static final SecureRandom RANDOM = new SecureRandom();

  private final CustomerOrderRepository orders;
  private final OrderItemRepository orderItems;
  private final OrderStatusHistoryRepository history;
  private final CustomerRepository customers;
  private final ProductVariantRepository variants;
  private final ProductRepository products;
  private final InventoryService inventory;
  private final ConversionDetectionService conversionDetection;
  private final AutomationService automation;
  private final NotificationService notifications;

  public OrderService(CustomerOrderRepository orders, OrderItemRepository orderItems,
      OrderStatusHistoryRepository history, CustomerRepository customers, ProductVariantRepository variants,
      ProductRepository products, InventoryService inventory, ConversionDetectionService conversionDetection,
      AutomationService automation, NotificationService notifications) {
    this.orders = orders;
    this.orderItems = orderItems;
    this.history = history;
    this.customers = customers;
    this.variants = variants;
    this.products = products;
    this.inventory = inventory;
    this.conversionDetection = conversionDetection;
    this.automation = automation;
    this.notifications = notifications;
  }

  /* ---------------------------------- ingestion -------------------------------- */

  /**
   * Creates a new order (status NEW). This is where a real marketplace webhook receiver would hand off a
   * newly placed order once Phase 23 (Marketplace Data Sync Engine) exists; until then it is called directly
   * (Ops Manager/Admin) to bring real orders into the system for the rest of this module to operate on — the
   * same role this module's "ingestion" bullet in the roadmap describes, just without a live webhook in front
   * of it yet, exactly like Phase 5's marketplace adapters are real code with no live credentials behind them.
   */
  @Transactional
  public OrderDetailDto ingest(Long organizationId, IngestOrderRequest request) {
    if (request == null || request.items() == null || request.items().isEmpty()) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "At least one order item is required");
    }
    if (request.customerName() == null || request.customerName().isBlank()) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "customerName is required");
    }
    MarketplaceName marketplace = parseMarketplace(request.marketplaceName());

    Customer customer = resolveCustomer(organizationId, request);

    CustomerOrder order = new CustomerOrder();
    order.organizationId = organizationId;
    order.orderNumber = resolveOrderNumber(organizationId, request.orderNumber());
    order.customerId = customer.id;
    order.marketplaceName = marketplace;
    order.status = OrderStatus.NEW;
    order.paymentStatus = PaymentStatus.PENDING;
    order.shippingAddress = request.shippingAddress();
    order.taxAmount = request.taxAmount() == null ? BigDecimal.ZERO : request.taxAmount();

    BigDecimal subtotal = BigDecimal.ZERO;
    List<OrderItem> itemsToSave = new ArrayList<>();
    for (IngestOrderItemInput input : request.items()) {
      if (input.variantId() == null || input.quantity() == null || input.quantity() <= 0) {
        throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
            "Each item needs a variantId and a quantity greater than 0");
      }
      ProductVariant variant = variants.findByIdAndOrganizationId(input.variantId(), organizationId)
          .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "VARIANT_NOT_FOUND",
              "Product variant " + input.variantId() + " not found"));
      Product product = products.findByIdAndOrganizationId(variant.productId, organizationId).orElse(null);
      BigDecimal unitPrice = input.unitPrice() != null ? input.unitPrice()
          : variant.priceOverride != null ? variant.priceOverride
          : product != null ? product.basePrice : BigDecimal.ZERO;
      BigDecimal lineTotal = unitPrice.multiply(BigDecimal.valueOf(input.quantity()));
      subtotal = subtotal.add(lineTotal);

      OrderItem item = new OrderItem();
      item.productVariantId = variant.id;
      item.quantity = input.quantity();
      item.unitPrice = unitPrice;
      item.lineTotal = lineTotal;
      itemsToSave.add(item);
    }
    order.subtotal = subtotal;
    order.totalAmount = subtotal.add(order.taxAmount);
    order = orders.save(order);

    for (OrderItem item : itemsToSave) {
      item.orderId = order.id;
      orderItems.save(item);
    }
    writeHistory(order.id, OrderStatus.NEW, null);

    // Phase 20: a new order for this customer may complete an active retention-campaign journey.
    conversionDetection.recordOrderPlaced(customer.id, order.totalAmount);

    return detail(organizationId, order.id);
  }

  /* ----------------------------------- listing ---------------------------------- */

  public List<OrderSummaryDto> list(Long organizationId, OrderStatus statusFilter, MarketplaceName marketplaceFilter) {
    List<CustomerOrder> orderList = orders.findByOrganizationIdOrderByPlacedAtDesc(organizationId).stream()
        .filter(o -> statusFilter == null || o.status == statusFilter)
        .filter(o -> marketplaceFilter == null || o.marketplaceName == marketplaceFilter)
        .toList();
    if (orderList.isEmpty()) return List.of();

    List<Long> orderIds = orderList.stream().map(o -> o.id).toList();
    List<Long> customerIds = orderList.stream().map(o -> o.customerId).distinct().toList();
    Map<Long, Customer> customerById = customers.findByIdInAndOrganizationId(customerIds, organizationId).stream()
        .collect(Collectors.toMap(c -> c.id, c -> c));
    Map<Long, Long> itemCountByOrder = orderItems.findByOrderIdIn(orderIds).stream()
        .collect(Collectors.groupingBy(i -> i.orderId, Collectors.counting()));

    return orderList.stream().map(o -> new OrderSummaryDto(
        o.id, o.orderNumber,
        customerById.containsKey(o.customerId) ? customerById.get(o.customerId).fullName : "Unknown customer",
        o.marketplaceName.name(), o.status.name(), o.paymentStatus.name(), o.totalAmount,
        itemCountByOrder.getOrDefault(o.id, 0L).intValue(), o.placedAt)).toList();
  }

  public OrderDetailDto detail(Long organizationId, Long orderId) {
    CustomerOrder order = requireOrder(organizationId, orderId);
    Customer customer = customers.findByIdAndOrganizationId(order.customerId, organizationId).orElse(null);

    Map<Long, ProductVariant> variantById = new HashMap<>();
    Map<Long, Product> productByVariantId = new HashMap<>();
    List<OrderItem> items = orderItems.findByOrderIdOrderByIdAsc(order.id);
    for (OrderItem item : items) {
      ProductVariant v = variants.findByIdAndOrganizationId(item.productVariantId, organizationId).orElse(null);
      if (v != null) {
        variantById.put(item.productVariantId, v);
        products.findByIdAndOrganizationId(v.productId, organizationId).ifPresent(p -> productByVariantId.put(item.productVariantId, p));
      }
    }

    List<OrderItemDto> itemDtos = items.stream().map(i -> {
      ProductVariant v = variantById.get(i.productVariantId);
      Product p = productByVariantId.get(i.productVariantId);
      return new OrderItemDto(i.productVariantId, p == null ? "" : p.name, v == null ? "" : v.sku,
          i.quantity, i.unitPrice, i.lineTotal);
    }).toList();

    List<StatusHistoryDto> historyDtos = history.findByOrderIdOrderByChangedAtAsc(order.id).stream()
        .map(h -> new StatusHistoryDto(h.status, h.changedAt)).toList();

    return new OrderDetailDto(order.id, order.orderNumber, customer == null ? "Unknown customer" : customer.fullName,
        customer == null ? null : customer.email, customer == null ? null : customer.phone,
        order.marketplaceName.name(), order.status.name(), order.paymentStatus.name(), order.subtotal,
        order.taxAmount, order.totalAmount, order.shippingAddress, order.courierName, order.trackingNumber,
        order.placedAt, order.updatedAt, itemDtos, historyDtos);
  }

  /* ------------------------------- status transitions ---------------------------- */

  /** "Confirm Order" — reviews stock and, on success, reserves it (one warehouse per line item, the warehouse
   *  with the most available stock for that variant). Atomic: if any line item cannot be fully reserved, the
   *  whole transaction rolls back and no stock is held for any line — "Ensure inventory reservation is atomic.
   *  Prevent overselling." from the roadmap. */
  @Transactional
  public OrderDetailDto confirm(Long organizationId, Long orderId) {
    CustomerOrder order = requireOrder(organizationId, orderId);
    if (order.status != OrderStatus.NEW) {
      throw new ApiException(HttpStatus.CONFLICT, "INVALID_TRANSITION", "Only new orders can be confirmed");
    }
    List<OrderItem> items = orderItems.findByOrderIdOrderByIdAsc(order.id);
    for (OrderItem item : items) {
      Long warehouseId = inventory.bestWarehouseFor(organizationId, item.productVariantId, item.quantity)
          .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "INSUFFICIENT_STOCK",
              "Not enough available stock for item " + item.productVariantId + " to fulfil this order"));
      inventory.reserveForOrder(item.productVariantId, warehouseId, item.quantity, order.id);
    }
    order.status = OrderStatus.CONFIRMED;
    orders.save(order);
    writeHistory(order.id, OrderStatus.CONFIRMED, null);
    return detail(organizationId, order.id);
  }

  /** "Mark as Packed" — converts the reservation into an actual stock deduction, at the same warehouse each
   *  line was reserved from (see InventoryService#reservedWarehousesForOrder). */
  @Transactional
  public OrderDetailDto markPacked(Long organizationId, Long orderId) {
    CustomerOrder order = requireOrder(organizationId, orderId);
    if (order.status != OrderStatus.CONFIRMED) {
      throw new ApiException(HttpStatus.CONFLICT, "INVALID_TRANSITION", "Only confirmed orders can be marked as packed");
    }
    Map<Long, Long> warehouseByVariant = inventory.reservedWarehousesForOrder(order.id);
    List<OrderItem> items = orderItems.findByOrderIdOrderByIdAsc(order.id);
    for (OrderItem item : items) {
      Long warehouseId = warehouseByVariant.get(item.productVariantId);
      if (warehouseId == null) {
        throw new ApiException(HttpStatus.CONFLICT, "NO_RESERVATION",
            "No stock reservation found for item " + item.productVariantId + " — confirm the order again");
      }
      inventory.deductReserved(item.productVariantId, warehouseId, item.quantity, order.id);
    }
    order.status = OrderStatus.PACKED;
    orders.save(order);
    writeHistory(order.id, OrderStatus.PACKED, null);
    return detail(organizationId, order.id);
  }

  /** "Generate Shipping Label / Book Courier". No real courier API is configured anywhere in this project
   *  (same honest-sandbox stance as Phase 5's marketplace adapters), so a tracking number is generated here
   *  when the caller doesn't supply a real one from their own courier account. */
  @Transactional
  public OrderDetailDto generateShippingLabel(Long organizationId, Long orderId, ShippingRequest request) {
    CustomerOrder order = requireOrder(organizationId, orderId);
    if (order.status != OrderStatus.PACKED) {
      throw new ApiException(HttpStatus.CONFLICT, "INVALID_TRANSITION", "Only packed orders can be shipped");
    }
    if (request == null || request.courierName() == null || request.courierName().isBlank()) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "courierName is required");
    }
    order.courierName = request.courierName().trim();
    order.trackingNumber = (request.trackingNumber() == null || request.trackingNumber().isBlank())
        ? generateTrackingNumber() : request.trackingNumber().trim();
    order.status = OrderStatus.SHIPPED;
    orders.save(order);
    writeHistory(order.id, OrderStatus.SHIPPED, null);
    return detail(organizationId, order.id);
  }

  /** "Track Shipment" — no real courier tracking API is configured, so this simulates one courier-status
   *  update per call: SHIPPED -> OUT_FOR_DELIVERY on the first refresh after shipping, then -> DELIVERED on
   *  the next, matching FULL_WORKFLOW.md's "status updates automatically as courier data changes" without
   *  fabricating a live integration that doesn't exist. */
  @Transactional
  public OrderDetailDto trackShipment(Long organizationId, Long orderId) {
    CustomerOrder order = requireOrder(organizationId, orderId);
    if (order.status == OrderStatus.SHIPPED) {
      order.status = OrderStatus.OUT_FOR_DELIVERY;
    } else if (order.status == OrderStatus.OUT_FOR_DELIVERY) {
      order.status = OrderStatus.DELIVERED;
    } else {
      throw new ApiException(HttpStatus.CONFLICT, "NOT_SHIPPED", "This order has not shipped yet");
    }
    orders.save(order);
    writeHistory(order.id, order.status, null);
    if (order.status == OrderStatus.DELIVERED) {
      fireDeliveredEvent(organizationId, order);
    }
    return detail(organizationId, order.id);
  }

  private void fireDeliveredEvent(Long organizationId, CustomerOrder order) {
    Customer customer = customers.findById(order.customerId).orElse(null);
    String customerEmail = customer == null ? null : customer.email;
    String customerName = customer == null ? null : customer.fullName;
    String summary = "Order " + order.orderNumber + " was delivered"
        + (customerName != null ? " to " + customerName : "");
    automation.fireEvent(new AutomationEvent(organizationId, TriggerType.ORDER_DELIVERED, summary,
        Map.of("marketplace", order.marketplaceName.name()), customerEmail, customerName, "orders", order.id,
        customer == null ? null : customer.phone, customer != null && Boolean.TRUE.equals(customer.whatsappOptIn)));
    notifications.create(organizationId, summary, "ORDER_UPDATE", "orders", order.id);
  }

  /* -------------------------------------- internals -------------------------------------- */

  private CustomerOrder requireOrder(Long organizationId, Long orderId) {
    return orders.findByIdAndOrganizationId(orderId, organizationId)
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", "Order not found"));
  }

  private void writeHistory(Long orderId, OrderStatus status, Long changedBy) {
    OrderStatusHistory h = new OrderStatusHistory();
    h.orderId = orderId;
    h.status = status.name();
    h.changedBy = changedBy;
    history.save(h);
  }

  private MarketplaceName parseMarketplace(String raw) {
    if (raw == null || raw.isBlank()) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "marketplaceName is required");
    }
    try {
      return MarketplaceName.valueOf(raw.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException e) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Unknown marketplace: " + raw);
    }
  }

  private Customer resolveCustomer(Long organizationId, IngestOrderRequest request) {
    if (request.customerEmail() != null && !request.customerEmail().isBlank()) {
      Optional<Customer> byEmail = customers.findByOrganizationIdAndEmailIgnoreCase(organizationId, request.customerEmail().trim());
      if (byEmail.isPresent()) return byEmail.get();
    } else if (request.customerPhone() != null && !request.customerPhone().isBlank()) {
      Optional<Customer> byPhone = customers.findByOrganizationIdAndPhone(organizationId, request.customerPhone().trim());
      if (byPhone.isPresent()) return byPhone.get();
    }
    Customer c = new Customer();
    c.organizationId = organizationId;
    c.fullName = request.customerName().trim();
    c.email = request.customerEmail();
    c.phone = request.customerPhone();
    c.primaryChannel = request.marketplaceName();
    c.status = com.rastudio.commerce.customer.CustomerStatus.ACTIVE;
    return customers.save(c);
  }

  private String resolveOrderNumber(Long organizationId, String requested) {
    if (requested != null && !requested.isBlank()) {
      String trimmed = requested.trim();
      if (orders.existsByOrganizationIdAndOrderNumber(organizationId, trimmed)) {
        throw new ApiException(HttpStatus.CONFLICT, "ORDER_NUMBER_TAKEN",
            "Order number " + trimmed + " already exists for this organization");
      }
      return trimmed;
    }
    String candidate;
    do {
      candidate = "ORD-" + (100000 + RANDOM.nextInt(900000));
    } while (orders.existsByOrganizationIdAndOrderNumber(organizationId, candidate));
    return candidate;
  }

  private String generateTrackingNumber() {
    return "TRK" + (100000000 + RANDOM.nextInt(900000000));
  }
}
