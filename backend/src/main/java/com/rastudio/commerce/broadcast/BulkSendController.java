package com.rastudio.commerce.broadcast;

import com.rastudio.commerce.broadcast.BulkSendDtos.*;
import com.rastudio.commerce.common.ApiException;
import com.rastudio.commerce.security.TenantPrincipal;
import com.rastudio.commerce.user.UserRole;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/** Phase 22 — Bulk Sharing / Broadcast. Roles per BUTTON_ACTIONS.md section 11b: Marketing Manager/Admin can
 *  build and send; Analyst is read-only (report viewing) on top of that, exactly mirroring CampaignController's
 *  RBAC split for the same reason (this module and Retention Marketing share the same actors). */
@RestController
@RequestMapping("/api/broadcasts")
public class BulkSendController {

  private final BulkSendService service;

  public BulkSendController(BulkSendService service) {
    this.service = service;
  }

  private void requireRead(TenantPrincipal p) {
    if (p.role() != UserRole.BUSINESS_OWNER_ADMIN && p.role() != UserRole.MARKETING_MANAGER && p.role() != UserRole.ANALYST_VIEWER) {
      throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "You don't have access to bulk broadcasts");
    }
  }

  private void requireWrite(TenantPrincipal p) {
    if (p.role() != UserRole.BUSINESS_OWNER_ADMIN && p.role() != UserRole.MARKETING_MANAGER) {
      throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Only a Marketing Manager or Admin can send bulk broadcasts");
    }
  }

  @GetMapping
  public List<BulkSendResponse> list(@AuthenticationPrincipal TenantPrincipal p) {
    requireRead(p);
    return service.list(p.organizationId());
  }

  @GetMapping("/{id}")
  public BulkSendResponse get(@AuthenticationPrincipal TenantPrincipal p, @PathVariable Long id) {
    requireRead(p);
    return service.get(p.organizationId(), id);
  }

  @GetMapping("/{id}/items")
  public List<BulkSendItemResponse> items(@AuthenticationPrincipal TenantPrincipal p, @PathVariable Long id) {
    requireRead(p);
    return service.items(p.organizationId(), id);
  }

  /** "Upload Contact List" — validates the CSV and returns a preview; nothing is persisted yet. */
  @PostMapping(value = "/parse-csv", consumes = "multipart/form-data")
  public ParsedContactsResponse parseCsv(@AuthenticationPrincipal TenantPrincipal p, @RequestParam("file") MultipartFile file) {
    requireWrite(p);
    return service.parseCsv(file);
  }

  /** "Send Bulk Broadcast" — creates the bulk_send/bulk_send_item rows synchronously (so the response has a
   *  real id and list size immediately) then kicks off the actual sending on a background thread; the frontend
   *  polls GET /{id} for live progress. */
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public BulkSendResponse create(@AuthenticationPrincipal TenantPrincipal p, @RequestBody BulkSendCreateRequest request,
      HttpServletRequest httpRequest) {
    requireWrite(p);
    BulkSendResponse created = service.create(p.organizationId(), p.userId(), request, httpRequest);
    service.sendAsync(created.id());
    return created;
  }

  @PostMapping("/{id}/retry")
  public BulkSendResponse retry(@AuthenticationPrincipal TenantPrincipal p, @PathVariable Long id, HttpServletRequest httpRequest) {
    requireWrite(p);
    BulkSendResponse updated = service.retryFailed(p.organizationId(), id, p.userId(), httpRequest);
    service.sendAsync(id);
    return updated;
  }
}
