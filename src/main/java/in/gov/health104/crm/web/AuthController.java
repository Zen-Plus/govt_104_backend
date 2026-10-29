package in.gov.health104.crm.web;

import in.gov.health104.crm.service.AuthService;
import in.gov.health104.crm.web.dto.AuthDtos.LoginRequest;
import in.gov.health104.crm.web.dto.AuthDtos.LoginResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

/** POST /api/auth/login - returns a JWT for the Authorization header. */
@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService auth;
    public AuthController(AuthService auth) { this.auth = auth; }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest r) { return auth.login(r); }
}
