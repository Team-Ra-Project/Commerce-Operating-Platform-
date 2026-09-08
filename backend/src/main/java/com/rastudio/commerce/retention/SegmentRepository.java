package com.rastudio.commerce.retention;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SegmentRepository extends JpaRepository<Segment, Long> {
  List<Segment> findByOrganizationIdOrderByNameAsc(Long organizationId);

  Optional<Segment> findByIdAndOrganizationId(Long id, Long organizationId);

  boolean existsByOrganizationId(Long organizationId);
}
