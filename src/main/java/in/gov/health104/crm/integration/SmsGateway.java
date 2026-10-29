package in.gov.health104.crm.integration;

import in.gov.health104.crm.domain.IntegrationCode;
import in.gov.health104.crm.domain.IntegrationConfig;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * SMS gateway adapter (NIC / CDAC / any DLT-registered provider).
 * Uses settings: senderId, dltEntityId, dltTemplateId, route, unicode.
 */
@Component
public class SmsGateway {
    private final IntegrationService integrations;
    private final HttpJsonClient http;

    public SmsGateway(IntegrationService integrations, HttpJsonClient http) {
        this.integrations = integrations; this.http = http;
    }

    public HttpJsonClient.Result send(String mobile, String message) {
        IntegrationConfig c = integrations.entity(IntegrationCode.SMS);
        if (!c.isEnabled()) return new HttpJsonClient.Result(false, 0, "SKIPPED_DISABLED");
        Map<String, String> s = integrations.settings(c);
        return http.post(c, integrations.secret(c), "", Map.of(
                "senderId", s.getOrDefault("senderId", ""),
                "dltEntityId", s.getOrDefault("dltEntityId", ""),
                "dltTemplateId", s.getOrDefault("dltTemplateId", ""),
                "unicode", s.getOrDefault("unicode", "true"),
                "mobile", mobile,
                "message", message));
    }
}
