package com.rastudio.commerce.audit;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;

/**
 * Writes one audit_log row per administrative action (Phase 3 requirement:
 * "Also implement audit logging for administrative actions"). Deliberately
 * fire-and-forget from the caller's point of view — record() never throws,
 * so a logging hiccup can never fail the administrative action itself.
 */
@Service
public class AuditLogService {
    private final AuditLogRepository repo;
    public AuditLogService(AuditLogRepository repo) { this.repo = repo; }

    public void record(Long organizationId, Long actorUserId, String action, String entityType,
                        Long entityId, String previousValue, String newValue, HttpServletRequest request) {
        try {
            var log = new AuditLog();
            log.organizationId = organizationId;
            log.userId = actorUserId;
            log.action = action;
            log.entityType = entityType;
            log.entityId = entityId;
            log.previousValue = trim(previousValue);
            log.newValue = trim(newValue);
            log.ipAddress = clientIp(request);
            repo.save(log);
        } catch (Exception ignored) {
            // Auditing must never block or fail the action it's describing.
        }
    }

    private static String trim(String s) {
        if (s == null) return null;
        return s.length() > 500 ? s.substring(0, 500) : s;
    }

    private static String clientIp(HttpServletRequest request) {
        if (request == null) return null;
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) return forwarded.split(",")[0].trim();
        return request.getRemoteAddr();
    }
}