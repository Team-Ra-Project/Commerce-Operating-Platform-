package com.rastudio.commerce.retention;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MessageLogRepository extends JpaRepository<MessageLog, Long> {
  List<MessageLog> findByOrganizationIdOrderByCreatedAtDesc(Long organizationId);

  List<MessageLog> findByCampaignIdOrderByCreatedAtDesc(Long campaignId);

  long countByCampaignIdAndStatus(Long campaignId, MessageLogStatus status);
}
