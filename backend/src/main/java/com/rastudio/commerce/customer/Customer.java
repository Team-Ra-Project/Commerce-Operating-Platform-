package com.rastudio.commerce.customer;
import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Minimal mapping of the existing `customer` table (see database/schema.sql). Phase 10 — CRM & Customer
 * Management — owns this table in full (notes, segments, opt-ins, the CRM UI). This entity exists only because
 * Phase 8 (Order Management) cannot insert a customer_order row without a real customer_id: order ingestion
 * finds-or-creates a bare customer record (name/email/phone/channel) from the order payload, exactly the way
 * Phase 5's product publishing read (but did not own) product_marketplace_listing, and the way Phase 7 reads
 * (but does not own) product_variant. Nothing here builds customer notes, segmentation, or a CRM screen.
 */
@Entity
@Table(name = "customer")
public class Customer {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "organization_id", nullable = false)
  public Long organizationId;

  @Column(name = "full_name", nullable = false)
  public String fullName;

  public String email;
  public String phone;
  public String city;
  public String country;

  @Column(name = "primary_channel")
  public String primaryChannel;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  public CustomerStatus status = CustomerStatus.ACTIVE;

  @Column(name = "whatsapp_opt_in", nullable = false)
  public Boolean whatsappOptIn = false;

  @Column(name = "email_opt_in", nullable = false)
  public Boolean emailOptIn = false;

  @Column(name = "created_at", updatable = false)
  public LocalDateTime createdAt;

  @Column(name = "updated_at")
  public LocalDateTime updatedAt;

  @PrePersist
  void onCreate() { createdAt = LocalDateTime.now(); updatedAt = createdAt; }

  @PreUpdate
  void onUpdate() { updatedAt = LocalDateTime.now(); }
}
