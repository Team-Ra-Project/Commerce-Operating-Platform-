package com.rastudio.commerce.template;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MessageTemplateRepository extends JpaRepository<MessageTemplate, Long> {
  List<MessageTemplate> findByOrganizationIdOrderByUpdatedAtDesc(Long organizationId);

  Optional<MessageTemplate> findByIdAndOrganizationId(Long id, Long organizationId);

  List<MessageTemplate> findByIdInAndOrganizationId(List<Long> ids, Long organizationId);

  /** Used by TemplateApprovalPollingJob (Phase 19) to find real-mode WhatsApp templates still awaiting a
   *  Meta/BSP decision. */
  List<MessageTemplate> findByApprovalStatusAndProviderTemplateIdIsNotNull(ApprovalStatus approvalStatus);

  Optional<MessageTemplate> findByProviderTemplateId(String providerTemplateId);
}
