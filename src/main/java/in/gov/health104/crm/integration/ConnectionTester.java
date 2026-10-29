package in.gov.health104.crm.integration;

import in.gov.health104.crm.domain.IntegrationCode;
import in.gov.health104.crm.domain.IntegrationConfig;
import org.springframework.stereotype.Component;

/** "Test connection" button: reachability check for the configured endpoint of each integration. */
@Component
public class ConnectionTester {
    private final IntegrationService integrations;
    private final HttpJsonClient http;
    private final EmailGateway email;

    public ConnectionTester(IntegrationService integrations, HttpJsonClient http, EmailGateway email) {
        this.integrations = integrations; this.http = http; this.email = email;
    }

    public HttpJsonClient.Result test(IntegrationCode code) {
        HttpJsonClient.Result r;
        if (code == IntegrationCode.EMAIL) {
            r = email.test();
        } else {
            IntegrationConfig c = integrations.entity(code);
            r = http.get(c, integrations.secret(c), "");
            // Any HTTP answer (even 401/404) proves the host is reachable.
            if (r.status() > 0) r = new HttpJsonClient.Result(true, r.status(), "Reachable (HTTP " + r.status() + ")");
        }
        integrations.saveTestResult(code, (r.ok() ? "OK: " : "FAILED: ") + r.body());
        return r;
    }
}
