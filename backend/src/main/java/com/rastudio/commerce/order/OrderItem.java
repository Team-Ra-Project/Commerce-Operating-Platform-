package com.rastudio.commerce.order;
import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "order_item")
public class OrderItem {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "order_id", nullable = false)
  public Long orderId;

  @Column(name = "product_variant_id", nullable = false)
  public Long productVariantId;

  @Column(nullable = false)
  public Integer quantity;

  @Column(name = "unit_price", nullable = false)
  public BigDecimal unitPrice;

  @Column(name = "line_total", nullable = false)
  public BigDecimal lineTotal;
}
