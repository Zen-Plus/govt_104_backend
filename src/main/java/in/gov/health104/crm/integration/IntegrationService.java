package in.gov.health104.crm.integration;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.gov.health104.crm.config.ApiException;
import in.gov.health104.crm.config.CryptoService;
import in.gov.health104.crm.domain.IntegrationCode;
import in.gov.health104.crm.domain.IntegrationConfig;
import in.gov.health104.crm.repository.IntegrationConfigRepository;
import in.gov.health104.crm.security.CurrentUser;
import in.gov.health104.crm.service.AuditService;
import in.gov.health104.crm.web.dto.IntegrationDtos.IntegrationRequest;
import in.gov.health104.crm.web.dto.IntegrationDtos.IntegrationResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Reads/writes third-party connection settings.
 *  - Secrets are encrypted before saving and are never returned (response only says "secretSet").
 *  - Sending a blank secret on update keeps the existing one.
 *  - Base URLs must be http(s) to avoid file:/ or other scheme abuse.
 */
@Service
public class IntegrationService {

    private final IntegrationConfigRepository repo;
    private final CryptoService crypto;
    private final AuditService audit;
    private final ObjectMapper mapper;

    public IntegrationService(IntegrationConfigRepository repo, CryptoService crypto,
                              AuditService audit, ObjectMapper mapper) {
        this.repo = repo; this.crypto = crypto; this.audit = audit; this.mapper = mapper;
    }

    public List<IntegrationResponse> list() {
        return repo.findAll().stream().sorted(Comparator.comparing(IntegrationConfig::getId)).map(this::toResponse).toList();
    }

    public IntegrationResponse get(IntegrationCode code) { return toResponse(entity(code)); }

    public IntegrationConfig entity(IntegrationCode code) {
        return repo.findByCode(code).orElseThrow(() -> ApiException.notFound("Integration " + code));
    }

    @Transactional
    public IntegrationResponse update(IntegrationCode code, IntegrationRequest r) {
        IntegrationConfig c = entity(code);
        if (r.baseUrl() != null && !r.baseUrl().isBlank()) {
            try {
                String scheme = URI.create(r.baseUrl().trim()).getScheme();
                if (!"https".equalsIgnoreCase(scheme) && !"http".equalsIgnoreCase(scheme)) throw new IllegalArgumentException();
            } catch (IllegalArgumentException e) {
                throw ApiException.bad("Base URL must start with http:// or https://");
            }
        }
        c.setEnabled(r.enabled());
        c.setProvider(r.provider());
        c.setBaseUrl(r.baseUrl() == null ? null : r.baseUrl().trim());
        c.setUsername(r.username());
        if (r.secret() != null && !r.secret().isBlank()) c.setSecretEnc(crypto.encrypt(r.secret()));
        c.setTimeoutMs(r.timeoutMs() == null ? 10000 : r.timeoutMs());
        c.setRetryCount(r.retryCount() == null ? 3 : r.retryCount());
        try {
            c.setSettingsJson(mapper.writeValueAsString(r.settings() == null ? Map.of() : r.settings()));
        } catch (Exception e) { throw ApiException.bad("Invalid settings"); }
        c.setUpdatedBy(CurrentUser.username());
        c.setUpdatedAt(LocalDateTime.now());
        repo.save(c);
        audit.record("INTEGRATION_UPDATE", "Integration", code,
                Map.of("enabled", r.enabled(), "secretChanged", r.secret() != null && !r.secret().isBlank()));
        return toResponse(c);
    }

    @Transactional
    public void saveTestResult(IntegrationCode code, String status) {
        IntegrationConfig c = entity(code);
        c.setLastTestAt(LocalDateTime.now());
        c.setLastTestStatus(status.length() > 200 ? status.substring(0, 200) : status);
        repo.save(c);
    }

    public String secret(IntegrationConfig c) { return crypto.decrypt(c.getSecretEnc()); }

    public Map<String, String> settings(IntegrationConfig c) {
        try {
            if (c.getSettingsJson() == null) return new HashMap<>();
            return mapper.readValue(c.getSettingsJson(), new TypeReference<Map<String, String>>() {});
        } catch (Exception e) { return new HashMap<>(); }
    }

    private IntegrationResponse toResponse(IntegrationConfig c) {
        return new IntegrationResponse(c.getCode().name(), c.getDisplayName(), c.isEnabled(), c.getProvider(),
                c.getBaseUrl(), c.getUsername(), c.getSecretEnc() != null, settings(c), c.getTimeoutMs(),
                c.getRetryCount(), c.getLastTestAt(), c.getLastTestStatus(), c.getUpdatedBy(), c.getUpdatedAt());
    }
}
