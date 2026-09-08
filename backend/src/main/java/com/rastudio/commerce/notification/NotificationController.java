package com.rastudio.commerce.notification;

import com.rastudio.commerce.notification.NotificationDtos.*;
import com.rastudio.commerce.security.TenantPrincipal;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** Phase 14 — Notifications & Audit. Backs the notification bell (list, unread count, mark read); every
 *  endpoint is open to any authenticated tenant member — a user only ever sees their own organization's
 *  notifications, and there's no more sensitive data here than the alert titles already visible elsewhere in
 *  the product (low-stock alerts, order/return updates), matching the open-read precedent set by
 *  Product/Order/Returns list endpoints. */
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

  private final NotificationService service;
  public NotificationController(NotificationService service) { this.service = service; }

  /** "Notification list" — the bell dropdown's contents. */
  @GetMapping
  public List<NotificationDto> list(@AuthenticationPrincipal TenantPrincipal p,
      @RequestParam(name = "unreadOnly", required = false, defaultValue = "false") boolean unreadOnly) {
    return service.list(p.organizationId(), unreadOnly);
  }

  /** "Unread count" — the red dot badge on the bell icon. */
  @GetMapping("/unread-count")
  public UnreadCountDto unreadCount(@AuthenticationPrincipal TenantPrincipal p) {
    return service.unreadCount(p.organizationId());
  }

  /** "Mark read" — fired when a notification is opened ("notification navigation" per the roadmap). */
  @PostMapping("/{id}/read")
  public NotificationDto markRead(@AuthenticationPrincipal TenantPrincipal p, @PathVariable Long id) {
    return service.markRead(p.organizationId(), id);
  }

  /** Natural counterpart to "mark read" for the bell dropdown's "Mark all as read" affordance. */
  @PostMapping("/read-all")
  public void markAllRead(@AuthenticationPrincipal TenantPrincipal p) {
    service.markAllRead(p.organizationId());
  }
}
