package com.rastudio.commerce.inventory;
import jakarta.persistence.*;

@Entity
@Table(name = "warehouse")
public class Warehouse {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "organization_id", nullable = false)
  public Long organizationId;

  @Column(nullable = false)
  public String name;

  public String location;

  @Column(name = "capacity_pct", nullable = false)
  public Integer capacityPct = 0;
}
