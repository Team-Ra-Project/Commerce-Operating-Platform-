package com.rastudio.commerce.order;
import com.rastudio.commerce.marketplace.MarketplaceName;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "customer_order", uniqueConstraints = @UniqueConstraint(columnNames = {"organization_id", "order_number"}))
public class CustomerOrder {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "organization_id", nullable = false)
  public Long organizationId;

  @Column(name = "order_number", nullable = false)
  public String orderNumber;

  @Column(name = "customer_id", nullable = false)
  public Long customerId;

  @Enumerated(EnumType.STRING)
  @Column(name = "marketplace_name", nullable = false)
  public MarketplaceName marketplaceName;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  public OrderStatus status = OrderStatus.NEW;

  @Enumerated(EnumType.STRING)
  @Column(name = "payment_status", nullable = false)
  public PaymentStatus paymentStatus = PaymentStatus.PENDING;

  @Column(nullable = false)
  public BigDecimal subtotal = BigDecimal.ZERO;

  @Column(name = "tax_amount", nullable = false)
  public BigDecimal taxAmount = BigDecimal.ZERO;

  @Column(name = "total_amount", nullable = false)
  public BigDecimal totalAmount = BigDecimal.ZERO;

  @Column(name = "shipping_address")
  public String shippingAddress;

  @Column(name = "courier_name")
  public String courierName;

  @Column(name = "tracking_number")
  public String trackingNumber;

  @Column(name = "return_id")
  public Long returnId;

  @Column(name = "placed_at")
  public LocalDateTime placedAt;

  @Column(name = "updated_at")
  public LocalDateTime updatedAt;

  @PrePersist
  void onCreate() { placedAt = LocalDateTime.now(); updatedAt = placedAt; }

  @PreUpdate
  void onUpdate() { updatedAt = LocalDateTime.now(); }
}
