package com.rastudio.commerce.retention;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CampaignStepRepository extends JpaRepository<CampaignStep, Long> {
  List<CampaignStep> findByCampaignIdOrderByStepOrderAsc(Long campaignId);

  List<CampaignStep> findByCampaignIdInOrderByStepOrderAsc(List<Long> campaignIds);

  boolean existsByMessageTemplateId(Long messageTemplateId);

  void deleteByCampaignId(Long campaignId);

  long countByCampaignId(Long campaignId);
}
