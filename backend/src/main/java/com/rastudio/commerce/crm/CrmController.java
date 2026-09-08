package com.rastudio.commerce.crm;

import com.rastudio.commerce.common.ApiException;
import com.rastudio.commerce.crm.CrmDtos.*;
import com.rastudio.commerce.security.TenantPrincipal;
import com.rastudio.commerce.user.UserRole;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** Phase 10 — CRM & Customer Management. Endpoint-to-button mapping is in docs/BUTTON_ACTIONS.md section 6
 *  (segment endpoints — Create/Edit/Delete Segment — belong to Phase 11 and are not part of this controller). */
@RestController
@RequestMapping("/api/customers")
public class CrmController {

  private final CrmService service;
  public CrmController(CrmService service) { this.service = service; }

  /** "View Customer Profile" (list form). Per BUTTON_ACTIONS.md: Support Agent, Marketing Manager, Admin —
   *  customer PII is more sensitive than product/order data, so (unlike Product/Order) this is not left open
   *  to every authenticated role. */
  @GetMapping
  public List<CustomerSummaryDto> list(@AuthenticationPrincipal TenantPrincipal p, @RequestParam(required = false) String search) {
    requireRole(p, UserRole.BUSINESS_OWNER_ADMIN, UserRole.SUPPORT_CRM_AGENT, UserRole.MARKETING_MANAGER);
    return service.list(p.organizationId(), search);
  }

  @GetMapping("/{id}")
  public CustomerProfileDto profile(@AuthenticationPrincipal TenantPrincipal p, @PathVariable Long id) {
    requireRole(p, UserRole.BUSINESS_OWNER_ADMIN, UserRole.SUPPORT_CRM_AGENT, UserRole.MARKETING_MANAGER);
    return service.profile(p.organizationId(), id);
  }

  /** "Add Note / Log Communication" — Support Agent only, per docs/BUTTON_ACTIONS.md. */
  @PostMapping("/{id}/notes")
  @ResponseStatus(HttpStatus.CREATED)
  public NoteDto addNote(@AuthenticationPrincipal TenantPrincipal p, @PathVariable Long id, @RequestBody AddNoteRequest request) {
    requireRole(p, UserRole.SUPPORT_CRM_AGENT, UserRole.BUSINESS_OWNER_ADMIN);
    return service.addNote(p.organizationId(), id, p.userId(), request);
  }

  /** "Export Customer List" — Marketing Manager, Admin. */
  @GetMapping(value = "/export", produces = "text/csv")
  public ResponseEntity<String> export(@AuthenticationPrincipal TenantPrincipal p) {
    requireRole(p, UserRole.MARKETING_MANAGER, UserRole.BUSINESS_OWNER_ADMIN);
    String csv = service.exportCsv(p.organizationId());
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType("text/csv"))
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"customers-export.csv\"")
        .body(csv);
  }

  private void requireRole(TenantPrincipal p, UserRole... allowed) {
    for (UserRole r : allowed) if (p.role() == r) return;
    throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "You do not have permission to perform this action");
  }
}
