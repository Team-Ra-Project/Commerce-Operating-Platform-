package com.rastudio.commerce.competitor;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompetitorTrackedProductRepository extends JpaRepository<CompetitorTrackedProduct, Long> {
  List<CompetitorTrackedProduct> findByOrganizationIdOrderByCreatedAtDesc(Long organizationId);

  Optional<CompetitorTrackedProduct> findByIdAndOrganizationId(Long id, Long organizationId);

  // findAll() from JpaRepository is used as-is by the scheduled price-check job, which runs
  // across every organization at once.
}
