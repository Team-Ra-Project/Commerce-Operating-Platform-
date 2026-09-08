package com.rastudio.commerce.product;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductVariantRepository extends JpaRepository<ProductVariant, Long> {
  List<ProductVariant> findByProductIdOrderByIdAsc(Long productId);

  List<ProductVariant> findByProductIdInOrderByIdAsc(List<Long> productIds);

  Optional<ProductVariant> findByIdAndProductIdAndOrganizationId(Long id, Long productId, Long organizationId);

  // Added for Phase 7 (Inventory) and Phase 8 (Orders), which both need to resolve/validate a bare variant id
  // against the caller's organization without going through a specific product id first.
  Optional<ProductVariant> findByIdAndOrganizationId(Long id, Long organizationId);

  List<ProductVariant> findByOrganizationIdOrderByIdAsc(Long organizationId);

  boolean existsByOrganizationIdAndSkuIgnoreCase(Long organizationId, String sku);

  boolean existsByOrganizationIdAndSkuIgnoreCaseAndIdNot(Long organizationId, String sku, Long id);

  long countByProductId(Long productId);
}
