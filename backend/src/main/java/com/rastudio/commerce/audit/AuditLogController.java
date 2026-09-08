package com.rastudio.commerce.audit;
import com.rastudio.commerce.common.ApiException;
import com.rastudio.commerce.security.TenantPrincipal;
import com.rastudio.commerce.user.UserRole;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** Read-only view of administrative activity for the current organization — Admin only. */
@RestController
@RequestMapping("/api/audit-logs")
public class AuditLogController {
    private final AuditLogRepository repo;
    public AuditLogController(AuditLogRepository repo) { this.repo = repo; }

    @GetMapping
    public List<AuditLog> list(@AuthenticationPrincipal TenantPrincipal principal) {
        if (principal.role() != UserRole.BUSINESS_OWNER_ADMIN) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Only an administrator can view audit logs");
        }
        return repo.findByOrganizationIdOrderByCreatedAtDesc(principal.organizationId());
    }
}