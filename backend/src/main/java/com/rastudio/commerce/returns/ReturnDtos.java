package com.rastudio.commerce.returns;
import java.time.LocalDateTime;

public class ReturnDtos {

  public record ReturnSummaryDto(
      Long id, Long orderId, String orderNumber, String customerName, String reason, String status,
      String resolution, LocalDateTime requestedAt, LocalDateTime updatedAt) {}

  public record CreateReturnRequest(Long orderId, String reason) {}

  public record RejectRequest(String reason) {}

  /** Body for "Log Item Received". condition is a short human-readable label (e.g. "PASSED", "FAILED",
   *  "DAMAGED_IN_TRANSIT") — not a fixed enum in schema.sql, so it's carried as free text into the return's
   *  note trail for the Support Agent to read before making the final call in ReceiveItemRequest. */
  public record ReceiveItemRequest(String condition, String note) {}

  /** Body for "Approve Refund/Replacement". restock=true triggers a real inventory update (RETURN_IN
   *  movement); warehouseId is required in that case since, unlike order fulfillment, a human is physically
   *  checking a specific item back into a specific warehouse rather than the system picking one. */
  public record ResolveRequest(String resolution, Boolean restock, Long warehouseId, Integer quantity, String note) {}
}
