package in.gov.health104.crm.integration;

import in.gov.health104.crm.domain.IntegrationCode;
import in.gov.health104.crm.domain.IntegrationConfig;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Telecom adapter for the BSNL PRI / SIP trunk via the contact-centre (dialer/ACD) middleware.
 *  - placeAlertCall : outbound IVR call reading a text-to-speech alert to an official.
 *  - transfer       : transfers/conferences a live call to a doctor, counsellor or EMR number.
 * The PBX/ACD exposes these as REST endpoints configured on the "Telecom" admin page.
 */
@Component
public class TelecomGateway {
    private final IntegrationService integrations;
    private final HttpJsonClient http;

    public TelecomGateway(IntegrationService integrations, HttpJsonClient http) {
        this.integrations = integrations; this.http = http;
    }

    public HttpJsonClient.Result placeAlertCall(String mobile, String ttsMessage) {
        IntegrationConfig c = integrations.entity(IntegrationCode.TELECOM);
        if (!c.isEnabled()) return new HttpJsonClient.Result(false, 0, "SKIPPED_DISABLED");
        Map<String, String> s = integrations.settings(c);
        return http.post(c, integrations.secret(c), "/outbound/call", Map.of(
                "cli", s.getOrDefault("outboundCli", ""), "destination", mobile, "tts", ttsMessage));
    }

    public HttpJsonClient.Result transfer(String callRef, String target, boolean conference) {
        IntegrationConfig c = integrations.entity(IntegrationCode.TELECOM);
        if (!c.isEnabled()) return new HttpJsonClient.Result(false, 0, "SKIPPED_DISABLED");
        return http.post(c, integrations.secret(c), "/calls/transfer", Map.of(
                "callRef", callRef, "target", target, "mode", conference ? "CONFERENCE" : "BLIND"));
    }
}
