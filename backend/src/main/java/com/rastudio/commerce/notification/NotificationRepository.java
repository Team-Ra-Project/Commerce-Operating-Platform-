package com.rastudio.commerce.notification;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
  // Phase 14 — Notifications & Audit. The entity itself and the LOW_STOCK writer in InventoryService
  // predate this phase (see Notification's class Javadoc); these query methods are what the real
  // notification bell (list, unread count, mark read) needed and didn't have yet.
  List<Notification> findByOrganizationIdOrderByCreatedAtDesc(Long organizationId);

  List<Notification> findByOrganizationIdAndIsReadFalseOrderByCreatedAtDesc(Long organizationId);

  long countByOrganizationIdAndIsReadFalse(Long organizationId);

  Optional<Notification> findByIdAndOrganizationId(Long id, Long organizationId);
}
