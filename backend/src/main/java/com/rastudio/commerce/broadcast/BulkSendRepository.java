package com.rastudio.commerce.broadcast;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BulkSendRepository extends JpaRepository<BulkSend, Long> {
  List<BulkSend> findByOrganizationIdOrderByCreatedAtDesc(Long organizationId);

  Optional<BulkSend> findByIdAndOrganizationId(Long id, Long organizationId);
}
