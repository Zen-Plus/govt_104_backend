package in.gov.health104.crm.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

/** A person who logs into the CRM. Password is stored only as a BCrypt hash. */
@Entity
@Table(name = "app_user")
@Getter @Setter
public class AppUser {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String username;
    private String passwordHash;
    private String fullName;
    @Enumerated(EnumType.STRING)
    private Role role;
    private String phone;
    private String email;
    private String district;
    private String extensionNo;
    private boolean active = true;
    private int failedAttempts;
    private LocalDateTime lockedUntil;
    private LocalDateTime createdAt = LocalDateTime.now();
}
