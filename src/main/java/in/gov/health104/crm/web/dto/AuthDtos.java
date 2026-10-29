package in.gov.health104.crm.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class AuthDtos {
    private AuthDtos() {}
    public record LoginRequest(@NotBlank @Size(max = 50) String username,
                               @NotBlank @Size(max = 100) String password,
                               @Size(max = 10) String extensionNo) {}
    public record LoginResponse(String token, long expiresInMinutes, String username, String fullName,
                                String role, String district, String extensionNo) {}
}
