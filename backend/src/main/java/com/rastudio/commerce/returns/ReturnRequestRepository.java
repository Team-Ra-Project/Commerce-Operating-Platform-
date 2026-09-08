package com.rastudio.commerce.returns;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReturnRequestRepository extends JpaRepository<ReturnRequest, Long> {
  List<ReturnRequest> findByOrganizationIdOrderByRequestedAtDesc(Long organizationId);

  Optional<ReturnRequest> findByIdAndOrganizationId(Long id, Long organizationId);

  Optional<ReturnRequest> findByOrderIdAndOrganizationId(Long orderId, Long organizationId);

  boolean existsByOrderIdAndStatusNotIn(Long orderId, List<ReturnStatus> statuses);
}
