package com.rastudio.commerce.retention;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CampaignRepository extends JpaRepository<Campaign, Long> {
  List<Campaign> findByOrganizationIdOrderByCreatedAtDesc(Long organizationId);

  Optional<Campaign> findByIdAndOrganizationId(Long id, Long organizationId);

  List<Campaign> findByStatus(CampaignStatus status);

  boolean existsByOrganizationIdAndSegmentId(Long organizationId, Long segmentId);
}
