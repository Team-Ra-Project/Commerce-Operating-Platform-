package com.rastudio.commerce.order;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public class OrderDtos {

  public record OrderItemDto(Long variantId, String productName, String sku, int quantity,
      BigDecimal unitPrice, BigDecimal lineTotal) {}

  public record StatusHistoryDto(String status, LocalDateTime changedAt) {}

  public record OrderSummaryDto(
      Long id, String orderNumber, String customerName, String marketplaceName, String status,
      String paymentStatus, BigDecimal totalAmount, int itemCount, LocalDateTime placedAt) {}

  public record OrderDetailDto(
      Long id, String orderNumber, String customerName, String customerEmail, String customerPhone,
      String marketplaceName, String status, String paymentStatus, BigDecimal subtotal, BigDecimal taxAmount,
      BigDecimal totalAmount, String shippingAddress, String courierName, String trackingNumber,
      LocalDateTime placedAt, LocalDateTime updatedAt, List<OrderItemDto> items, List<StatusHistoryDto> history) {}

  /** Body for order ingestion — stands in for the real marketplace webhook receiver Phase 23
   *  (Marketplace Data Sync Engine) will eventually build; see OrderService#ingest. */
  public record IngestOrderItemInput(Long variantId, Integer quantity, java.math.BigDecimal unitPrice) {}

  public record IngestOrderRequest(
      String orderNumber, String marketplaceName, String customerName, String customerEmail, String customerPhone,
      String shippingAddress, BigDecimal taxAmount, List<IngestOrderItemInput> items) {}

  /** Body for "Generate Shipping Label / Book Courier". trackingNumber is optional — if omitted, a sandbox
   *  tracking number is generated, the same "mock adapter" pattern Phase 5 uses when no real courier API
   *  credentials exist. */
  public record ShippingRequest(String courierName, String trackingNumber) {}
}
