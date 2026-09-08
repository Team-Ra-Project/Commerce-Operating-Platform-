package com.rastudio.commerce.user;

import com.rastudio.commerce.audit.AuditLogService;
import com.rastudio.commerce.auth.AuthService;
import com.rastudio.commerce.common.ApiException;
import com.rastudio.commerce.mail.MailService;
import com.rastudio.commerce.organization.Organization;
import com.rastudio.commerce.organization.OrganizationRepository;
import com.rastudio.commerce.security.JwtService;
import com.rastudio.commerce.security.TenantPrincipal;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Phase 3 — Users, Roles & Administration.
 * Every write here is Admin-only (BUSINESS_OWNER_ADMIN) and tenant-scoped to
 * the caller's organizationId, matching the pattern already established in
 * OrganizationController. Every write is also audit-logged.
 */
@Service
public class UserService {

    private final AppUserRepository users;
    private final OrganizationRepository organizations;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final MailService mailService;
    private final AuditLogService audit;
    private final AuthService authService;
    private final String publicAppUrl;
    private final boolean exposeInvitationLink;

    public UserService(AppUserRepository users, OrganizationRepository organizations, PasswordEncoder encoder,
                        JwtService jwt, MailService mailService, AuditLogService audit, AuthService authService,
                        @Value("${app.public-app-url}") String publicAppUrl,
                        @Value("${app.expose-invitation-link}") boolean exposeInvitationLink) {
        this.users = users;
        this.organizations = organizations;
        this.encoder = encoder;
        this.jwt = jwt;
        this.mailService = mailService;
        this.audit = audit;
        this.authService = authService;
        this.publicAppUrl = publicAppUrl;
        this.exposeInvitationLink = exposeInvitationLink;
    }

    public List<UserSummary> list(TenantPrincipal actor) {
        return users.findByOrganizationIdOrderByCreatedAtAsc(actor.organizationId()).stream().map(this::summary).toList();
    }

    /** "Add User" — invites a new team member, or resends the invitation if one is already pending for that email. */
    @Transactional
    public InviteResult invite(TenantPrincipal actor, InviteRequest req, HttpServletRequest http) {
        requireAdmin(actor);
        String email = req.email().trim().toLowerCase();
        AppUser target = users.findByOrganizationIdAndEmailIgnoreCase(actor.organizationId(), email).orElse(null);

        boolean resend = false;
        if (target != null) {
            if (target.status == UserStatus.ACTIVE) {
                throw new ApiException(HttpStatus.CONFLICT, "USER_ALREADY_ACTIVE", "This person is already an active member of your team");
            }
            if (target.status == UserStatus.DEACTIVATED) {
                throw new ApiException(HttpStatus.CONFLICT, "USER_DEACTIVATED", "This email belongs to a deactivated account. Reactivating an account isn't supported yet — use a different email or contact support.");
            }
            // status == PENDING: treat as "resend invitation", refreshing name/role if changed
            target.fullName = req.fullName();
            target.role = req.role();
            resend = true;
        } else {
            target = new AppUser();
            target.organizationId = actor.organizationId();
            target.fullName = req.fullName();
            target.email = email;
            target.role = req.role();
            target.status = UserStatus.PENDING;
            target.invitedBy = actor.userId();
            // app_user.password_hash is NOT NULL in schema; this is an unusable
            // placeholder overwritten for real in acceptInvitation() below.
            target.passwordHash = encoder.encode(randomPlaceholder());
        }
        target = users.save(target);

        AppUser inviter = users.findById(actor.userId()).orElse(null);
        Organization org = organizations.findById(actor.organizationId()).orElse(null);
        String orgName = org != null ? org.name : "your workspace";
        String inviterName = inviter != null ? inviter.fullName : "A team admin";
        String roleLabel = RoleLabels.LABELS.getOrDefault(target.role, target.role.name());

        String token = jwt.invitation(target);
        String acceptUrl = publicAppUrl + "/accept-invitation.html?token=" + token;
        boolean emailSent = mailService.sendInvitation(target.email, target.fullName, orgName, inviterName, roleLabel, acceptUrl);

        audit.record(actor.organizationId(), actor.userId(), resend ? "RESEND_INVITATION" : "INVITE_USER",
                "app_user", target.id, null, target.role.name(), http);

        return new InviteResult(summary(target), emailSent, exposeInvitationLink ? acceptUrl : null);
    }

    /** "Edit User" + "Assign Role" — both land here since the existing UI edits them together; each changed field is audited under its own action. */
    @Transactional
    public UserSummary update(TenantPrincipal actor, Long id, UpdateUserRequest req, HttpServletRequest http) {
        requireAdmin(actor);
        AppUser target = users.findByIdAndOrganizationId(id, actor.organizationId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User not found"));

        if (req.fullName() != null && !req.fullName().isBlank() && !req.fullName().equals(target.fullName)) {
            String previous = target.fullName;
            target.fullName = req.fullName();
            audit.record(actor.organizationId(), actor.userId(), "UPDATE_USER", "app_user", target.id, previous, target.fullName, http);
        }
        if (req.email() != null && !req.email().isBlank()) {
            String newEmail = req.email().trim().toLowerCase();
            if (!newEmail.equalsIgnoreCase(target.email)) {
                users.findByOrganizationIdAndEmailIgnoreCase(actor.organizationId(), newEmail)
                        .filter(other -> !other.id.equals(target.id))
                        .ifPresent(other -> { throw new ApiException(HttpStatus.CONFLICT, "EMAIL_EXISTS", "Another team member already uses this email"); });
                String previous = target.email;
                target.email = newEmail;
                audit.record(actor.organizationId(), actor.userId(), "UPDATE_USER", "app_user", target.id, previous, target.email, http);
            }
        }
        if (req.role() != null && req.role() != target.role) {
            if (target.id.equals(actor.userId()) && target.role == UserRole.BUSINESS_OWNER_ADMIN && req.role() != UserRole.BUSINESS_OWNER_ADMIN) {
                requireAnotherActiveAdmin(actor.organizationId(), target.id, "You're the only administrator — assign another admin before changing your own role.");
            }
            String previous = target.role.name();
            target.role = req.role();
            audit.record(actor.organizationId(), actor.userId(), "ASSIGN_ROLE", "app_user", target.id, previous, target.role.name(), http);
        }

        return summary(users.save(target));
    }

    /** "Deactivate User". */
    @Transactional
    public UserSummary deactivate(TenantPrincipal actor, Long id, HttpServletRequest http) {
        requireAdmin(actor);
        AppUser target = users.findByIdAndOrganizationId(id, actor.organizationId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User not found"));
        if (target.id.equals(actor.userId())) {
            throw new ApiException(HttpStatus.CONFLICT, "CANNOT_DEACTIVATE_SELF", "You can't deactivate your own account");
        }
        if (target.status == UserStatus.DEACTIVATED) return summary(target); // idempotent
        if (target.role == UserRole.BUSINESS_OWNER_ADMIN) {
            requireAnotherActiveAdmin(actor.organizationId(), target.id, "You can't deactivate the only administrator — assign another admin first.");
        }
        String previousStatus = target.status.name();
        target.status = UserStatus.DEACTIVATED;
        target = users.save(target);
        audit.record(actor.organizationId(), actor.userId(), "DEACTIVATE_USER", "app_user", target.id, previousStatus, target.status.name(), http);
        return summary(target);
    }

    /** Public preview shown on accept-invitation.html before the invited person sets a password. */
    public InvitationPreview invitationPreview(String token) {
        AppUser user = resolveInviteToken(token);
        Organization org = organizations.findById(user.organizationId).orElse(null);
        return new InvitationPreview(user.email, user.fullName, user.role.name(), org != null ? org.name : "your workspace");
    }

    /** Public — sets the invited person's password, activates the account, and logs them straight in. */
    @Transactional
    public AuthService.AuthResponse acceptInvitation(AcceptInvitationRequest req, HttpServletRequest http) {
        AppUser user = resolveInviteToken(req.token());
        user.passwordHash = encoder.encode(req.password());
        user.status = UserStatus.ACTIVE;
        user.emailVerifiedAt = LocalDateTime.now();
        user = users.save(user);
        audit.record(user.organizationId, user.id, "ACCEPT_INVITATION", "app_user", user.id, "PENDING", "ACTIVE", http);
        return authService.issueSession(user);
    }

    private AppUser resolveInviteToken(String token) {
        AppUser user;
        try {
            Claims claims = jwt.claims(token);
            if (!"invite".equals(claims.get("type", String.class))) throw new IllegalArgumentException();
            user = users.findById(Long.valueOf(claims.getSubject()))
                    .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "INVALID_INVITATION_TOKEN", "This invitation link is invalid or has expired"));
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_INVITATION_TOKEN", "This invitation link is invalid or has expired");
        }
        if (user.status != UserStatus.PENDING) {
            throw new ApiException(HttpStatus.CONFLICT, "INVITATION_ALREADY_USED", "This invitation has already been accepted or is no longer valid");
        }
        return user;
    }

    private void requireAdmin(TenantPrincipal actor) {
        if (actor.role() != UserRole.BUSINESS_OWNER_ADMIN) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Only an administrator can manage users");
        }
    }

    private void requireAnotherActiveAdmin(Long organizationId, Long excludingUserId, String message) {
        boolean anotherAdminExists = users.findByOrganizationIdOrderByCreatedAtAsc(organizationId).stream()
                .anyMatch(u -> !u.id.equals(excludingUserId) && u.role == UserRole.BUSINESS_OWNER_ADMIN && u.status == UserStatus.ACTIVE);
        if (!anotherAdminExists) throw new ApiException(HttpStatus.CONFLICT, "LAST_ADMIN", message);
    }

    private static String randomPlaceholder() {
        byte[] b = new byte[24];
        new SecureRandom().nextBytes(b);
        return Base64.getEncoder().encodeToString(b);
    }

    private UserSummary summary(AppUser u) {
        return new UserSummary(u.id, u.fullName, u.email, u.role.name(), u.status.name(), u.invitedBy, u.lastLoginAt, u.createdAt);
    }

    public record InviteRequest(@NotBlank String fullName, @Email @NotBlank String email, @NotNull UserRole role) {}
    public record UpdateUserRequest(String fullName, @Email String email, UserRole role) {}
    public record AcceptInvitationRequest(@NotBlank String token, @Size(min = 8) String password) {}
    public record UserSummary(Long id, String fullName, String email, String role, String status, Long invitedBy, LocalDateTime lastLoginAt, LocalDateTime createdAt) {}
    public record InvitationPreview(String email, String fullName, String role, String organizationName) {}
    public record InviteResult(UserSummary user, boolean emailSent, String invitationLink) {}
}