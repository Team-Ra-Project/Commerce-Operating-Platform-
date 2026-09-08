package com.rastudio.commerce.notification;

import com.rastudio.commerce.common.ApiException;
import com.rastudio.commerce.notification.NotificationDtos.*;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Phase 14 — Notifications & Audit (notification half; audit_log already has its own read path from Phase 3's
 * AuditLogService/AuditLogController, which Phase 12/13 write into for their administrative actions). The
 * `notification` table and its LOW_STOCK writer already existed from Phase 7 (see Notification's class
 * Javadoc) — this service adds the real create/list/unread-count/mark-read behavior the notification bell
 * needs, and gives Phase 12/13 (and any future phase) a single place to create notification rows rather than
 * constructing the entity by hand the way InventoryService currently still does for LOW_STOCK.
 */
@Service
public class NotificationService {

  private final NotificationRepository notifications;

  public NotificationService(NotificationRepository notifications) {
    this.notifications = notifications;
  }

  /** Roadmap Phase 14 event categories: LOW_STOCK, SYNC_ERROR, ORDER_UPDATE, RETURN_UPDATE, CAMPAIGN,
   *  AUTOMATION, INTEGRATION (see notification.category's comment in schema.sql). */
  @Transactional
  public Notification create(Long organizationId, String title, String category, String linkModule, Long linkEntityId) {
    Notification n = new Notification();
    n.organizationId = organizationId;
    n.title = title;
    n.category = category;
    n.linkModule = linkModule;
    n.linkEntityId = linkEntityId;
    return notifications.save(n);
  }

  /* ------------------------------------------- notification list ------------------------------------------- */

  public List<NotificationDto> list(Long organizationId, boolean unreadOnly) {
    List<Notification> rows = unreadOnly
        ? notifications.findByOrganizationIdAndIsReadFalseOrderByCreatedAtDesc(organizationId)
        : notifications.findByOrganizationIdOrderByCreatedAtDesc(organizationId);
    return rows.stream().map(this::toDto).toList();
  }

  /* -------------------------------------------- unread count -------------------------------------------- */

  public UnreadCountDto unreadCount(Long organizationId) {
    return new UnreadCountDto(notifications.countByOrganizationIdAndIsReadFalse(organizationId));
  }

  /* ----------------------------------------------- mark read ----------------------------------------------- */

  @Transactional
  public NotificationDto markRead(Long organizationId, Long id) {
    Notification n = notifications.findByIdAndOrganizationId(id, organizationId)
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOTIFICATION_NOT_FOUND", "Notification not found"));
    n.isRead = true;
    notifications.save(n);
    return toDto(n);
  }

  @Transactional
  public void markAllRead(Long organizationId) {
    List<Notification> unread = notifications.findByOrganizationIdAndIsReadFalseOrderByCreatedAtDesc(organizationId);
    unread.forEach(n -> n.isRead = true);
    notifications.saveAll(unread);
  }

  private NotificationDto toDto(Notification n) {
    return new NotificationDto(n.id, n.title, n.category, n.linkModule, n.linkEntityId,
        Boolean.TRUE.equals(n.isRead), n.createdAt);
  }
}
