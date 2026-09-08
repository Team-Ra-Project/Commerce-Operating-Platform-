package com.rastudio.commerce.crm;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public class CrmDtos {

  public record CustomerSummaryDto(
      Long id, String fullName, String email, String phone, String city, String country,
      String primaryChannel, String status, boolean whatsappOptIn, boolean emailOptIn,
      int orderCount, BigDecimal totalSpent, LocalDateTime lastOrderAt, LocalDateTime createdAt) {}

  public record PurchaseHistoryEntryDto(
      Long orderId, String orderNumber, String marketplaceName, String status, BigDecimal totalAmount, LocalDateTime placedAt) {}

  public record NoteDto(Long id, String authorName, String noteText, LocalDateTime createdAt) {}

  public record CustomerProfileDto(
      CustomerSummaryDto customer, List<PurchaseHistoryEntryDto> purchaseHistory, List<NoteDto> notes) {}

  public record AddNoteRequest(String noteText) {}
}
