package com.rastudio.commerce.user;

import com.rastudio.commerce.security.TenantPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
public class UserController {
    private final UserService service;
    public UserController(UserService service) { this.service = service; }

    @GetMapping
    public List<UserService.UserSummary> list(@AuthenticationPrincipal TenantPrincipal principal) {
        return service.list(principal);
    }

    @PostMapping("/invite")
    public UserService.InviteResult invite(@AuthenticationPrincipal TenantPrincipal principal,
                                            @Valid @RequestBody UserService.InviteRequest request,
                                            HttpServletRequest http) {
        return service.invite(principal, request, http);
    }

    @PutMapping("/{id}")
    public UserService.UserSummary update(@AuthenticationPrincipal TenantPrincipal principal,
                                           @PathVariable Long id,
                                           @RequestBody UserService.UpdateUserRequest request,
                                           HttpServletRequest http) {
        return service.update(principal, id, request, http);
    }

    @PostMapping("/{id}/deactivate")
    public UserService.UserSummary deactivate(@AuthenticationPrincipal TenantPrincipal principal,
                                               @PathVariable Long id,
                                               HttpServletRequest http) {
        return service.deactivate(principal, id, http);
    }

    // ---- Public — no session; the invited person doesn't have an account yet ----

    @GetMapping("/invitation/{token}")
    public UserService.InvitationPreview invitationPreview(@PathVariable String token) {
        return service.invitationPreview(token);
    }

    @PostMapping("/accept-invitation")
    public com.rastudio.commerce.auth.AuthService.AuthResponse acceptInvitation(
            @Valid @RequestBody UserService.AcceptInvitationRequest request, HttpServletRequest http) {
        return service.acceptInvitation(request, http);
    }
}