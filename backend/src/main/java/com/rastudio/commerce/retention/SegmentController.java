package com.rastudio.commerce.retention;

import com.rastudio.commerce.common.ApiException;
import com.rastudio.commerce.retention.SegmentDtos.*;
import com.rastudio.commerce.security.TenantPrincipal;
import com.rastudio.commerce.user.UserRole;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/segments")
public class SegmentController {

  private final SegmentService service;

  public SegmentController(SegmentService service) {
    this.service = service;
  }

  private void requireRead(TenantPrincipal p) {
    if (p.role() != UserRole.BUSINESS_OWNER_ADMIN && p.role() != UserRole.MARKETING_MANAGER && p.role() != UserRole.ANALYST_VIEWER) {
      throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "You don't have access to segments");
    }
  }

  private void requireWrite(TenantPrincipal p) {
    if (p.role() != UserRole.BUSINESS_OWNER_ADMIN && p.role() != UserRole.MARKETING_MANAGER) {
      throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Only a Marketing Manager or Admin can manage segments");
    }
  }

  @GetMapping
  public List<SegmentResponse> list(@AuthenticationPrincipal TenantPrincipal p) {
    requireRead(p);
    return service.list(p.organizationId());
  }

  @PostMapping
  @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
  public SegmentResponse create(@AuthenticationPrincipal TenantPrincipal p, @RequestBody SegmentRequest request) {
    requireWrite(p);
    return service.create(p.organizationId(), request);
  }

  @PutMapping("/{id}")
  public SegmentResponse update(@AuthenticationPrincipal TenantPrincipal p, @PathVariable Long id,
      @RequestBody SegmentRequest request) {
    requireWrite(p);
    return service.update(p.organizationId(), id, request);
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
  public void delete(@AuthenticationPrincipal TenantPrincipal p, @PathVariable Long id) {
    requireWrite(p);
    service.delete(p.organizationId(), id);
  }
}
