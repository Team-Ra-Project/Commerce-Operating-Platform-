package com.rastudio.commerce.order;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerOrderRepository extends JpaRepository<CustomerOrder, Long> {
  List<CustomerOrder> findByOrganizationIdOrderByPlacedAtDesc(Long organizationId);

  Optional<CustomerOrder> findByIdAndOrganizationId(Long id, Long organizationId);

  boolean existsByOrganizationIdAndOrderNumber(Long organizationId, String orderNumber);

  long countByOrganizationId(Long organizationId);

  // Added for Phase 10 (CRM purchase history) and Phase 9 (Returns, to look up the order being returned).
  List<CustomerOrder> findByCustomerIdAndOrganizationIdOrderByPlacedAtDesc(Long customerId, Long organizationId);
}
