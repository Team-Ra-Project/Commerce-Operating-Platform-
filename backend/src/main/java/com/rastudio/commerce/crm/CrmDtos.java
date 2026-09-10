package com.rastudio.commerce.crm;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
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

  public record AddCustomerRequest(
      @NotBlank(message = "fullName is required")
      @Size(max = 150, message = "fullName must be 150 characters or fewer")
      String fullName,
      @Email(message = "email must be a valid email address")
      @Size(max = 190, message = "email must be 190 characters or fewer")
      String email,
      @Size(max = 30, message = "phone must be 30 characters or fewer")
      String phone,
      @Size(max = 100, message = "city must be 100 characters or fewer")
      String city,
      @Size(max = 100, message = "country must be 100 characters or fewer")
      String country,
      @Size(max = 50, message = "primaryChannel must be 50 characters or fewer")
      String primaryChannel,
      Boolean whatsappOptIn,
      Boolean emailOptIn) {}
}
