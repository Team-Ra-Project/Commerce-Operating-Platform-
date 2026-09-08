package com.rastudio.commerce.notification;

import java.time.LocalDateTime;

public class NotificationDtos {

  public record NotificationDto(
      Long id, String title, String category, String linkModule, Long linkEntityId,
      boolean isRead, LocalDateTime createdAt) {}

  public record UnreadCountDto(long count) {}
}
