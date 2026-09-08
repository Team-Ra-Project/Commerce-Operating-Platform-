package com.rastudio.commerce.bi;

import com.rastudio.commerce.security.TenantPrincipal;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/bi")
public class BiController {
  private final BiService service;
  public BiController(BiService service) { this.service = service; }

  @GetMapping("/overview")
  public BiService.Overview overview(@AuthenticationPrincipal TenantPrincipal p) {
    return service.overview(p.organizationId());
  }

  @PostMapping("/recommendations/{id}/dismiss")
  public ResponseEntity<Void> dismiss(@AuthenticationPrincipal TenantPrincipal p, @PathVariable Long id) {
    return service.dismiss(p.organizationId(), id, p.userId())
        ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
  }
}