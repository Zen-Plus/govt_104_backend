package in.gov.health104.crm.integration;

import in.gov.health104.crm.domain.IntegrationCode;
import in.gov.health104.crm.domain.IntegrationConfig;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Adapters for MoHFW national programmes (RBSK, AB-PMJAY) and the State Ambulance service.
 * Endpoint paths come from each integration's settings so they can be changed without a release.
 */
@Component
public class ExternalGateways {
    private final IntegrationService integrations;
    private final HttpJsonClient http;

    public ExternalGateways(IntegrationService integrations, HttpJsonClient http) {
        this.integrations = integrations; this.http = http;
    }

    /** Push a child-health referral (from a 104 call) to the RBSK system. */
    public HttpJsonClient.Result pushRbskReferral(Map<String, ?> referral) {
        return call(IntegrationCode.MOHFW_RBSK, "pushEndpoint", referral);
    }

    /** Check AB-PMJAY eligibility by beneficiary / ration card id. */
    public HttpJsonClient.Result pmjayLookup(String beneficiaryId) {
        return call(IntegrationCode.MOHFW_PMJAY, "beneficiaryLookupEndpoint", Map.of("beneficiaryId", beneficiaryId));
    }

    /** Forward a PMJAY-related grievance to the NHA grievance system. */
    public HttpJsonClient.Result pmjayGrievance(Map<String, ?> grievance) {
        return call(IntegrationCode.MOHFW_PMJAY, "grievanceEndpoint", grievance);
    }

    /** Request an ambulance dispatch from the State EMS (108/102) with patient location. */
    public HttpJsonClient.Result dispatchAmbulance(Map<String, ?> request) {
        return call(IntegrationCode.AMBULANCE, "dispatchEndpoint", request);
    }

    private HttpJsonClient.Result call(IntegrationCode code, String pathKey, Map<String, ?> body) {
        IntegrationConfig c = integrations.entity(code);
        if (!c.isEnabled()) return new HttpJsonClient.Result(false, 0, "SKIPPED_DISABLED");
        String path = integrations.settings(c).getOrDefault(pathKey, "");
        return http.post(c, integrations.secret(c), path, body);
    }
}
