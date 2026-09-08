package com.rastudio.commerce.retention;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CampaignEnrollmentRepository extends JpaRepository<CampaignEnrollment, Long> {
  List<CampaignEnrollment> findByCampaignId(Long campaignId);

  List<CampaignEnrollment> findByCampaignIdAndConvertedAtIsNull(Long campaignId);

  boolean existsByCampaignIdAndCustomerId(Long campaignId, Long customerId);

  /** Active (non-completed) enrollments for a customer, used by conversion detection when a new order lands. */
  List<CampaignEnrollment> findByCustomerIdAndConvertedAtIsNull(Long customerId);

  Optional<CampaignEnrollment> findByCampaignIdAndCustomerId(Long campaignId, Long customerId);

  long countByCampaignIdAndConvertedAtIsNotNull(Long campaignId);
}
