package com.rastudio.commerce.inventory;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WarehouseRepository extends JpaRepository<Warehouse, Long> {
  List<Warehouse> findByOrganizationIdOrderByNameAsc(Long organizationId);

  Optional<Warehouse> findByIdAndOrganizationId(Long id, Long organizationId);

  boolean existsByOrganizationId(Long organizationId);

  boolean existsByOrganizationIdAndNameIgnoreCase(Long organizationId, String name);
}
