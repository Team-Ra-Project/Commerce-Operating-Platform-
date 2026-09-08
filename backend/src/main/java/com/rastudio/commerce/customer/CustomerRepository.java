package com.rastudio.commerce.customer;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerRepository extends JpaRepository<Customer, Long> {
  Optional<Customer> findByOrganizationIdAndEmailIgnoreCase(Long organizationId, String email);

  Optional<Customer> findByOrganizationIdAndPhone(Long organizationId, String phone);

  Optional<Customer> findByIdAndOrganizationId(Long id, Long organizationId);

  List<Customer> findByIdInAndOrganizationId(List<Long> ids, Long organizationId);

  // Added for Phase 10 (CRM customer list).
  List<Customer> findByOrganizationIdOrderByCreatedAtDesc(Long organizationId);
}
