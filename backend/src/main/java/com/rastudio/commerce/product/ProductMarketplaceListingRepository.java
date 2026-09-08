package com.rastudio.commerce.product;
import com.rastudio.commerce.marketplace.MarketplaceName;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductMarketplaceListingRepository extends JpaRepository<ProductMarketplaceListing, Long> {
  List<ProductMarketplaceListing> findByProductIdOrderByMarketplaceNameAsc(Long productId);

  List<ProductMarketplaceListing> findByProductIdInOrderByMarketplaceNameAsc(List<Long> productIds);

  Optional<ProductMarketplaceListing> findByProductIdAndMarketplaceName(Long productId, MarketplaceName marketplaceName);

  void deleteByProductId(Long productId);
}
