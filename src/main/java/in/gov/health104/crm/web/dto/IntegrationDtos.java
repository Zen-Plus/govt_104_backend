package in.gov.health104.crm.web.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.Map;

/** Request/response bodies for the third-party integration configuration pages. */
public final class IntegrationDtos {
    private IntegrationDtos() {}

    /** secret: write-only. Leave blank to keep the stored value. */
    public record IntegrationRequest(
            boolean enabled,
            @Size(max = 80) String provider,
            @Size(max = 300) String baseUrl,
            @Size(max = 120) String username,
            @Size(max = 500) String secret,
            Map<String, String> settings,
            @Min(1000) @Max(120000) Integer timeoutMs,
            @Min(0) @Max(10) Integer retryCount) {}

    public record IntegrationResponse(
            String code, String displayName, boolean enabled, String provider, String baseUrl,
            String username, boolean secretSet, Map<String, String> settings, int timeoutMs, int retryCount,
            LocalDateTime lastTestAt, String lastTestStatus, String updatedBy, LocalDateTime updatedAt) {}

    public record TestResult(boolean ok, int httpStatus, String message) {}
}
