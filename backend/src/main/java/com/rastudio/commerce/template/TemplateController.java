package com.rastudio.commerce.template;

import com.rastudio.commerce.common.ApiException;
import com.rastudio.commerce.security.TenantPrincipal;
import com.rastudio.commerce.template.TemplateDtos.*;
import com.rastudio.commerce.user.UserRole;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/templates")
public class TemplateController {

  private final TemplateService service;

  public TemplateController(TemplateService service) {
    this.service = service;
  }

  private void requireRead(TenantPrincipal p) {
    if (p.role() != UserRole.BUSINESS_OWNER_ADMIN && p.role() != UserRole.MARKETING_MANAGER && p.role() != UserRole.ANALYST_VIEWER) {
      throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "You don't have access to message templates");
    }
  }

  private void requireWrite(TenantPrincipal p) {
    if (p.role() != UserRole.BUSINESS_OWNER_ADMIN && p.role() != UserRole.MARKETING_MANAGER) {
      throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Only a Marketing Manager or Admin can manage message templates");
    }
  }

  @GetMapping
  public List<TemplateResponse> list(@AuthenticationPrincipal TenantPrincipal p) {
    requireRead(p);
    return service.list(p.organizationId());
  }

  @GetMapping("/{id}")
  public TemplateResponse get(@AuthenticationPrincipal TenantPrincipal p, @PathVariable Long id) {
    requireRead(p);
    return service.get(p.organizationId(), id);
  }

  @PostMapping
  @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
  public TemplateResponse create(@AuthenticationPrincipal TenantPrincipal p, @RequestBody TemplateRequest request) {
    requireWrite(p);
    return service.create(p.organizationId(), request);
  }

  @PutMapping("/{id}")
  public TemplateResponse update(@AuthenticationPrincipal TenantPrincipal p, @PathVariable Long id, @RequestBody TemplateRequest request) {
    requireWrite(p);
    return service.update(p.organizationId(), id, request);
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
  public void delete(@AuthenticationPrincipal TenantPrincipal p, @PathVariable Long id) {
    requireWrite(p);
    service.delete(p.organizationId(), id);
  }

  @PostMapping("/{id}/submit-for-approval")
  public TemplateResponse submitForApproval(@AuthenticationPrincipal TenantPrincipal p, @PathVariable Long id) {
    requireWrite(p);
    return service.submitForApproval(p.organizationId(), id);
  }
}
