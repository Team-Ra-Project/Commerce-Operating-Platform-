package com.rastudio.commerce.broadcast;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BulkSendItemRepository extends JpaRepository<BulkSendItem, Long> {
  List<BulkSendItem> findByBulkSendIdOrderByIdAsc(Long bulkSendId);

  List<BulkSendItem> findByBulkSendIdAndStatus(Long bulkSendId, BulkSendItemStatus status);

  long countByBulkSendIdAndStatus(Long bulkSendId, BulkSendItemStatus status);
}
