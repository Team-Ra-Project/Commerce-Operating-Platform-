package com.rastudio.commerce.marketplace;
import java.time.LocalDateTime;
public record SyncLogDto(Long id, String event, boolean isError, LocalDateTime createdAt) {}