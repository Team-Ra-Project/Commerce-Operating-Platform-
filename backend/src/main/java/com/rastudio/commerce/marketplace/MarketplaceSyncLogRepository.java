package com.rastudio.commerce.marketplace;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MarketplaceSyncLogRepository extends JpaRepository<MarketplaceSyncLog, Long> {
  List<MarketplaceSyncLog> findByMarketplaceConnectionIdOrderByCreatedAtDesc(Long marketplaceConnectionId, Pageable pageable);
}