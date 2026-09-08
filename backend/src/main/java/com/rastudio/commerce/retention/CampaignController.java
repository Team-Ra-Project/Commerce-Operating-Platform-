package com.rastudio.commerce.retention;

import com.rastudio.commerce.common.ApiException;
import com.rastudio.commerce.retention.CampaignDtos.*;
import com.rastudio.commerce.security.TenantPrincipal;
import com.rastudio.commerce.user.UserRole;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/campaigns")
public class CampaignController {

  private final CampaignService service;

  public CampaignController(CampaignService service) {
    this.service = service;
  }

  private void requireRead(TenantPrincipal p) {
    if (p.role() != UserRole.BUSINESS_OWNER_ADMIN && p.role() != UserRole.MARKETING_MANAGER && p.role() != UserRole.ANALYST_VIEWER) {
      throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "You don't have access to campaigns");
    }
  }

  private void requireWrite(TenantPrincipal p) {
    if (p.role() != UserRole.BUSINESS_OWNER_ADMIN && p.role() != UserRole.MARKETING_MANAGER) {
      throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Only a Marketing Manager or Admin can manage campaigns");
    }
  }

  @GetMapping
  public List<CampaignResponse> list(@AuthenticationPrincipal TenantPrincipal p) {
    requireRead(p);
    return service.list(p.organizationId());
  }

  @GetMapping("/{id}")
  public CampaignResponse get(@AuthenticationPrincipal TenantPrincipal p, @PathVariable Long id) {
    requireRead(p);
    return service.get(p.organizationId(), id);
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public CampaignResponse create(@AuthenticationPrincipal TenantPrincipal p, @RequestBody CampaignRequest request) {
    requireWrite(p);
    return service.create(p.organizationId(), request);
  }

  @PutMapping("/{id}")
  public CampaignResponse update(@AuthenticationPrincipal TenantPrincipal p, @PathVariable Long id, @RequestBody CampaignRequest request) {
    requireWrite(p);
    return service.update(p.organizationId(), id, request);
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@AuthenticationPrincipal TenantPrincipal p, @PathVariable Long id) {
    requireWrite(p);
    service.delete(p.organizationId(), id);
  }

  @PostMapping("/{id}/activate")
  public CampaignResponse activate(@AuthenticationPrincipal TenantPrincipal p, @PathVariable Long id) {
    requireWrite(p);
    return service.activate(p.organizationId(), id);
  }

  @PostMapping("/{id}/pause")
  public CampaignResponse pause(@AuthenticationPrincipal TenantPrincipal p, @PathVariable Long id) {
    requireWrite(p);
    return service.pause(p.organizationId(), id);
  }

  @GetMapping("/{id}/messages")
  public List<MessageLogResponse> messages(@AuthenticationPrincipal TenantPrincipal p, @PathVariable Long id) {
    requireRead(p);
    return service.messageLogs(p.organizationId(), id);
  }
}
