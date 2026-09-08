package com.rastudio.commerce.crm;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "customer_note")
public class CustomerNote {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "customer_id", nullable = false)
  public Long customerId;

  @Column(name = "author_user_id")
  public Long authorUserId;

  @Column(name = "note_text", nullable = false, columnDefinition = "TEXT")
  public String noteText;

  @Column(name = "created_at", updatable = false)
  public LocalDateTime createdAt;

  @PrePersist
  void onCreate() { createdAt = LocalDateTime.now(); }
}
