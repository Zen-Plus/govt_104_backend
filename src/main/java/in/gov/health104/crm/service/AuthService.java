package in.gov.health104.crm.service;

import in.gov.health104.crm.config.ApiException;
import in.gov.health104.crm.domain.AppUser;
import in.gov.health104.crm.repository.AppUserRepository;
import in.gov.health104.crm.security.JwtService;
import in.gov.health104.crm.web.dto.AuthDtos.LoginRequest;
import in.gov.health104.crm.web.dto.AuthDtos.LoginResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Login with brute-force protection: after N failed attempts the account is locked for M minutes.
 * The same generic message is returned for "unknown user" and "wrong password" (no user enumeration).
 */
@Service
public class AuthService {

    private static final String GENERIC = "Invalid username or password";
    private final AppUserRepository users;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final AuditService audit;
    @Value("${app.security.login-max-attempts}") private int maxAttempts;
    @Value("${app.security.login-lock-minutes}") private int lockMinutes;

    public AuthService(AppUserRepository users, PasswordEncoder encoder, JwtService jwt, AuditService audit) {
        this.users = users; this.encoder = encoder; this.jwt = jwt; this.audit = audit;
    }

    @Transactional(noRollbackFor = ApiException.class)
    public LoginResponse login(LoginRequest r) {
        AppUser u = users.findByUsername(r.username().trim()).orElse(null);
        if (u == null || !u.isActive()) {
            audit.record(r.username(), "LOGIN_FAILED", "User", null, Map.of("reason", "unknown/inactive"));
            throw new ApiException(HttpStatus.UNAUTHORIZED, GENERIC);
        }
        if (u.getLockedUntil() != null && u.getLockedUntil().isAfter(LocalDateTime.now())) {
            throw new ApiException(HttpStatus.LOCKED, "Account locked due to failed attempts. Try again later.");
        }
        if (!encoder.matches(r.password(), u.getPasswordHash())) {
            u.setFailedAttempts(u.getFailedAttempts() + 1);
            if (u.getFailedAttempts() >= maxAttempts) {
                u.setLockedUntil(LocalDateTime.now().plusMinutes(lockMinutes));
                u.setFailedAttempts(0);
            }
            users.save(u);
            audit.record(u.getUsername(), "LOGIN_FAILED", "User", u.getId(), Map.of("reason", "bad password"));
            throw new ApiException(HttpStatus.UNAUTHORIZED, GENERIC);
        }
        u.setFailedAttempts(0);
        u.setLockedUntil(null);
        if (r.extensionNo() != null && !r.extensionNo().isBlank()) u.setExtensionNo(r.extensionNo().trim());
        users.save(u);
        audit.record(u.getUsername(), "LOGIN_SUCCESS", "User", u.getId(), null);
        return new LoginResponse(jwt.issue(u.getUsername(), u.getRole().name()), jwt.getExpiryMinutes(),
                u.getUsername(), u.getFullName(), u.getRole().name(), u.getDistrict(), u.getExtensionNo());
    }
}
