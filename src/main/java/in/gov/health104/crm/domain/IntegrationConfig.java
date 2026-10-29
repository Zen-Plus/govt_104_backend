package in.gov.health104.crm.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

/**
 * Connection settings for one third-party system.
 * {@code secretEnc} holds the password/API key encrypted with AES-GCM (see CryptoService)
 * and is NEVER sent back to the browser. {@code settingsJson} holds non-secret provider settings.
 */
@Entity
@Table(name = "integration_config")
@Getter @Setter
public class IntegrationConfig {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Enumerated(EnumType.STRING)
    private IntegrationCode code;
    private String displayName;
    private boolean enabled;
    private String provider;
    private String baseUrl;
    private String username;
    private String secretEnc;
    private String settingsJson;
    private int timeoutMs = 10000;
    private int retryCount = 3;
    private LocalDateTime lastTestAt;
    private String lastTestStatus;
    private String updatedBy;
    private LocalDateTime updatedAt = LocalDateTime.now();
}
