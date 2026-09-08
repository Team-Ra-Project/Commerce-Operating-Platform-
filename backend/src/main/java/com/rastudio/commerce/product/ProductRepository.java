package com.rastudio.commerce.product;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<Product, Long> {
  List<Product> findByOrganizationIdOrderByCreatedAtDesc(Long organizationId);

  Optional<Product> findByIdAndOrganizationId(Long id, Long organizationId);

  long countByOrganizationId(Long organizationId);
}
