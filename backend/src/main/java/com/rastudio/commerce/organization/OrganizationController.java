package com.rastudio.commerce.organization;

import com.rastudio.commerce.audit.AuditLogService;
import com.rastudio.commerce.common.ApiException;
import com.rastudio.commerce.security.TenantPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/organization")
public class OrganizationController {
  private final OrganizationRepository organizations;
  private final SystemConfigRepository configs;
  private final AuditLogService audit;

  public OrganizationController(OrganizationRepository organizations, SystemConfigRepository configs,
      AuditLogService audit) {
    this.organizations = organizations;
    this.configs = configs;
    this.audit = audit;
  }

  @GetMapping
  public Organization current(@AuthenticationPrincipal TenantPrincipal p) {
    return organizations.findById(p.organizationId())
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ORGANIZATION_NOT_FOUND", "Organization not found"));
  }

  @GetMapping("/config")
  public SystemConfig config(@AuthenticationPrincipal TenantPrincipal p) {
    return configs.findById(p.organizationId()).orElseGet(() -> {
      var c = new SystemConfig();
      c.organizationId = p.organizationId();
      return configs.save(c);
    });
  }

  @PutMapping("/config")
  public SystemConfig update(@AuthenticationPrincipal TenantPrincipal p, @RequestBody SystemConfig input,
      HttpServletRequest http) {
    if (p.role() != com.rastudio.commerce.user.UserRole.BUSINESS_OWNER_ADMIN) {
      throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN",
          "Only an administrator can update configuration");
    }
    validateFacebookAdsUrl(input.facebookAdsUrl);

    var c = configs.findById(p.organizationId()).orElseGet(() -> {
      var x = new SystemConfig();
      x.organizationId = p.organizationId();
      return x;
    });
    String previous = "syncInterval=" + c.syncInterval + ", currency=" + c.currency
        + ", twoFactor=" + c.twoFactorEnabled + ", emailDigest=" + c.emailDigestEnabled
        + ", facebookAdsUrl=" + c.facebookAdsUrl;
    c.syncInterval = input.syncInterval;
    c.currency = input.currency;
    c.twoFactorEnabled = input.twoFactorEnabled;
    c.emailDigestEnabled = input.emailDigestEnabled;
    c.facebookAdsUrl = normalizeOptional(input.facebookAdsUrl);

    var saved = configs.save(c);
    String updated = "syncInterval=" + saved.syncInterval + ", currency=" + saved.currency
        + ", twoFactor=" + saved.twoFactorEnabled + ", emailDigest=" + saved.emailDigestEnabled
        + ", facebookAdsUrl=" + saved.facebookAdsUrl;
    audit.record(p.organizationId(), p.userId(), "UPDATE_SYSTEM_CONFIG", "system_config",
        p.organizationId(), previous, updated, http);
    return saved;
  }

  private static String normalizeOptional(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  private static void validateFacebookAdsUrl(String value) {
    String normalized = normalizeOptional(value);
    if (normalized == null) return;
    if (normalized.length() > 500) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
          "Facebook/Meta Ads URL must be 500 characters or fewer");
    }

    try {
      URI uri = URI.create(normalized);
      String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
      boolean allowedHost = host.equals("facebook.com") || host.endsWith(".facebook.com")
          || host.equals("meta.com") || host.endsWith(".meta.com");
      if (!"https".equalsIgnoreCase(uri.getScheme()) || !allowedHost || uri.getUserInfo() != null) {
        throw new IllegalArgumentException();
      }
    } catch (IllegalArgumentException e) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
          "Enter a valid HTTPS Facebook or Meta Ads URL");
    }
  }
}