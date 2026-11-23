package in.gov.health104.crm.web;

import in.gov.health104.crm.config.ApiException;
import in.gov.health104.crm.domain.AppUser;
import in.gov.health104.crm.domain.Role;
import in.gov.health104.crm.repository.AppUserRepository;
import in.gov.health104.crm.security.CurrentUser;
import in.gov.health104.crm.service.AuditService;
import in.gov.health104.crm.web.dto.UserDtos.CreateUserRequest;
import in.gov.health104.crm.web.dto.UserDtos.ResetPasswordRequest;
import in.gov.health104.crm.web.dto.UserDtos.UpdateUserRequest;
import in.gov.health104.crm.web.dto.UserDtos.UserView;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * User & role management (Admin only).
 *  GET  /api/admin/users                     list all users
 *  POST /api/admin/users                     create a user with any role (Medical Officer, Counsellor, ...)
 *  PUT  /api/admin/users/{id}                edit details / role / active flag
 *  POST /api/admin/users/{id}/reset-password set a new password and clear any login lock
 */
@RestController
@RequestMapping("/api/admin/users")
@PreAuthorize("hasRole('ADMIN')")
public class UserAdminController {

    /** Roles that take calls and are routed by phone extension. */
    private static final Set<Role> EXTENSION_ROLES = Set.of(Role.AGENT, Role.MEDICAL_OFFICER, Role.COUNSELLOR);
    /** Roles scoped to a single district. */
    private static final Set<Role> DISTRICT_ROLES = Set.of(Role.FACILITY_OFFICER, Role.DISTRICT_OFFICER);

    private final AppUserRepository users;
    private final PasswordEncoder encoder;
    private final AuditService audit;

    public UserAdminController(AppUserRepository users, PasswordEncoder encoder, AuditService audit) {
        this.users = users; this.encoder = encoder; this.audit = audit;
    }

    @GetMapping
    public List<UserView> list() {
        return users.findAllByOrderByRoleAscUsernameAsc().stream().map(UserView::of).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public UserView create(@Valid @RequestBody CreateUserRequest r) {
        String username = r.username().trim().toLowerCase();
        if (users.existsByUsernameIgnoreCase(username)) throw ApiException.bad("Username '" + username + "' is already taken");
        Role role = parseRole(r.role());
        AppUser u = new AppUser();
        u.setUsername(username);
        u.setPasswordHash(encoder.encode(r.password()));
        apply(u, r.fullName(), role, r.phone(), r.email(), r.district(), r.extensionNo());
        users.save(u);
        audit.record("USER_CREATE", "User", u.getId(), details(u));
        return UserView.of(u);
    }

    @PutMapping("/{id}")
    @Transactional
    public UserView update(@PathVariable Long id, @Valid @RequestBody UpdateUserRequest r) {
        AppUser u = users.findById(id).orElseThrow(() -> ApiException.notFound("User"));
        Role role = parseRole(r.role());
        boolean self = u.getUsername().equals(CurrentUser.username());
        if (self && (role != Role.ADMIN || !r.active()))
            throw ApiException.bad("You cannot remove your own Admin role or deactivate yourself");
        String oldRole = u.getRole().name();
        apply(u, r.fullName(), role, r.phone(), r.email(), r.district(), r.extensionNo());
        u.setActive(r.active());
        users.save(u);
        Map<String, Object> d = details(u);
        d.put("previousRole", oldRole);
        d.put("active", r.active());
        audit.record("USER_UPDATE", "User", u.getId(), d);
        return UserView.of(u);
    }

    @PostMapping("/{id}/reset-password")
    @Transactional
    public Map<String, Object> resetPassword(@PathVariable Long id, @Valid @RequestBody ResetPasswordRequest r) {
        AppUser u = users.findById(id).orElseThrow(() -> ApiException.notFound("User"));
        u.setPasswordHash(encoder.encode(r.password()));
        u.setFailedAttempts(0);
        u.setLockedUntil(null);
        users.save(u);
        audit.record("USER_PASSWORD_RESET", "User", u.getId(), Map.of("username", u.getUsername()));
        return Map.of("ok", true);
    }

    // ---------------------------------------------------------------------

    private void apply(AppUser u, String fullName, Role role, String phone, String email, String district, String ext) {
        String dist = blankToNull(district);
        String extension = blankToNull(ext);
        if (DISTRICT_ROLES.contains(role) && dist == null) throw ApiException.bad("District is required for " + role);
        if (!EXTENSION_ROLES.contains(role)) extension = null; // only call-taking roles have an extension
        if (extension != null && users.existsByExtensionNoAndActiveTrueAndIdNot(extension, u.getId() == null ? -1L : u.getId()))
            throw ApiException.bad("Extension " + extension + " is already assigned to another active user");
        u.setFullName(fullName.trim());
        u.setRole(role);
        u.setPhone(blankToNull(phone));
        u.setEmail(blankToNull(email));
        u.setDistrict(dist);
        u.setExtensionNo(extension);
    }

    private static Role parseRole(String s) {
        try { return Role.valueOf(s); } catch (IllegalArgumentException e) { throw ApiException.bad("Unknown role"); }
    }

    private static String blankToNull(String s) { return s == null || s.isBlank() ? null : s.trim(); }

    private static Map<String, Object> details(AppUser u) {
        Map<String, Object> d = new HashMap<>();
        d.put("username", u.getUsername());
        d.put("role", u.getRole().name());
        if (u.getDistrict() != null) d.put("district", u.getDistrict());
        if (u.getExtensionNo() != null) d.put("extension", u.getExtensionNo());
        return d;
    }
}
