package com.rastudio.commerce.order;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "order_status_history")
public class OrderStatusHistory {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "order_id", nullable = false)
  public Long orderId;

  @Column(nullable = false)
  public String status;

  @Column(name = "changed_by")
  public Long changedBy;

  @Column(name = "changed_at", updatable = false)
  public LocalDateTime changedAt;

  @PrePersist
  void onCreate() { changedAt = LocalDateTime.now(); }
}
