package in.gov.health104.crm.web.dto;

import in.gov.health104.crm.domain.AppUser;
import jakarta.validation.constraints.*;

import java.time.LocalDateTime;

/** Admin user management (create / edit users and assign roles). */
public final class UserDtos {
    private UserDtos() {}

    /** Password rule shared by create and reset: 8-72 chars, at least one letter, one digit and one symbol. */
    public static final String PASSWORD_REGEX = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[^A-Za-z0-9]).{8,72}$";
    public static final String PASSWORD_MSG = "Password must be 8-72 characters with a letter, a digit and a symbol";

    public record CreateUserRequest(
            @NotBlank @Pattern(regexp = "^[a-zA-Z0-9._-]{3,50}$",
                    message = "3-50 characters: letters, digits, dot, underscore or hyphen") String username,
            @NotBlank @Size(max = 120) String fullName,
            @NotBlank String role,
            @Pattern(regexp = "^$|^[6-9]\\d{9}$", message = "Must be a 10-digit mobile number") String phone,
            @Email @Size(max = 120) String email,
            @Size(max = 60) String district,
            @Pattern(regexp = "^$|^\\d{2,10}$", message = "Extension must be 2-10 digits") String extensionNo,
            @NotBlank @Pattern(regexp = PASSWORD_REGEX, message = PASSWORD_MSG) String password) {}

    public record UpdateUserRequest(
            @NotBlank @Size(max = 120) String fullName,
            @NotBlank String role,
            @Pattern(regexp = "^$|^[6-9]\\d{9}$", message = "Must be a 10-digit mobile number") String phone,
            @Email @Size(max = 120) String email,
            @Size(max = 60) String district,
            @Pattern(regexp = "^$|^\\d{2,10}$", message = "Extension must be 2-10 digits") String extensionNo,
            boolean active) {}

    public record ResetPasswordRequest(
            @NotBlank @Pattern(regexp = PASSWORD_REGEX, message = PASSWORD_MSG) String password) {}

    /** What the API returns - never the password hash. */
    public record UserView(Long id, String username, String fullName, String role, String phone, String email,
                           String district, String extensionNo, boolean active, boolean locked, LocalDateTime createdAt) {
        public static UserView of(AppUser u) {
            boolean locked = u.getLockedUntil() != null && u.getLockedUntil().isAfter(LocalDateTime.now());
            return new UserView(u.getId(), u.getUsername(), u.getFullName(), u.getRole().name(), u.getPhone(), u.getEmail(),
                    u.getDistrict(), u.getExtensionNo(), u.isActive(), locked, u.getCreatedAt());
        }
    }
}
