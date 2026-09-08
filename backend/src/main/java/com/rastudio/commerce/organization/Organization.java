package com.rastudio.commerce.organization;
import jakarta.persistence.*; import java.time.*;
@Entity @Table(name="organization") public class Organization {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id; @Column(nullable=false) public String name; public String industry; @Column(name="business_size") public String businessSize; @Column(nullable=false) public String currency="INR"; @Column(nullable=false) public String timezone="Asia/Kolkata"; @Column(name="data_retention_days",nullable=false) public Integer dataRetentionDays=365; @Column(name="created_at",updatable=false) public LocalDateTime createdAt; @Column(name="updated_at") public LocalDateTime updatedAt;
 @PrePersist void created(){createdAt=LocalDateTime.now();updatedAt=createdAt;} @PreUpdate void updated(){updatedAt=LocalDateTime.now();}
}
