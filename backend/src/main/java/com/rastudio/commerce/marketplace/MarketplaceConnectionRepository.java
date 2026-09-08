package com.rastudio.commerce.marketplace;
import java.util.List; import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MarketplaceConnectionRepository extends JpaRepository<MarketplaceConnection, Long> {
  List<MarketplaceConnection> findByOrganizationId(Long organizationId);
  Optional<MarketplaceConnection> findByOrganizationIdAndMarketplaceName(Long organizationId, MarketplaceName marketplaceName);
}