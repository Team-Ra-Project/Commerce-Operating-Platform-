package com.rastudio.commerce.audit;
import jakarta.persistence.*; import java.time.*;
@Entity @Table(name="audit_log") public class AuditLog {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
 @Column(name="organization_id",nullable=false) public Long organizationId;
 @Column(name="user_id") public Long userId;
 @Column(nullable=false) public String action;
 @Column(name="entity_type",nullable=false) public String entityType;
 @Column(name="entity_id") public Long entityId;
 @Column(name="previous_value",length=500) public String previousValue;
 @Column(name="new_value",length=500) public String newValue;
 @Column(name="ip_address",length=45) public String ipAddress;
 @Column(name="created_at",updatable=false) public LocalDateTime createdAt;
 @PrePersist void created(){createdAt=LocalDateTime.now();}
}