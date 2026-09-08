package com.rastudio.commerce.broadcast;

import java.time.LocalDateTime;
import java.util.List;

public class BulkSendDtos {

  /** One recipient row — either parsed from an uploaded CSV or submitted directly by the frontend after the
   *  parse-csv preview step. name is optional and only used for display / the {customer_name} merge field
   *  when no matching `customer` record is found. */
  public record ContactInput(String name, String email, String phone) {}

  public record InvalidRow(String rawLine, String reason) {}

  /** Response of "Upload Contact List" (POST /api/broadcasts/parse-csv) — validates format per
   *  BUTTON_ACTIONS.md, but does not persist anything; the frontend holds validContacts in memory and submits
   *  them back on Send Bulk Broadcast. */
  public record ParsedContactsResponse(
      int totalRows,
      int validCount,
      int invalidCount,
      int duplicateCount,
      List<ContactInput> validContacts,
      List<InvalidRow> invalidRows) {}

  public record BulkSendCreateRequest(
      String channel,               // WHATSAPP | EMAIL | BOTH
      String recipientSource,       // SEGMENT | UPLOAD
      Long segmentId,                // required when recipientSource = SEGMENT
      List<ContactInput> contacts,   // required when recipientSource = UPLOAD
      Long whatsappTemplateId,       // required when channel includes WhatsApp — must be an APPROVED WhatsApp template
      String emailSubject,           // required when channel includes Email
      String emailContent) {}        // required when channel includes Email

  public record BulkSendResponse(
      Long id,
      String content,
      String channel,
      int listSize,
      int sentCount,
      int deliveredCount,
      int failedCount,
      int optedOutCount,
      String status,
      LocalDateTime createdAt) {}

  public record BulkSendItemResponse(
      Long id,
      Long customerId,
      String recipient,   // resolved display label — customer name/email/phone, or the raw contact_reference
      String status,
      String failureReason) {}
}
