package in.gov.health104.crm.web;

import in.gov.health104.crm.domain.IntegrationCode;
import in.gov.health104.crm.integration.ConnectionTester;
import in.gov.health104.crm.integration.IntegrationService;
import in.gov.health104.crm.web.dto.IntegrationDtos.*;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Third-party connectivity configuration (Admin only).
 *  GET  /api/integrations                list all
 *  GET  /api/integrations/{code}         one (TELECOM, SMS, EMAIL, GIS, MOHFW_RBSK, MOHFW_PMJAY, AMBULANCE)
 *  PUT  /api/integrations/{code}         save
 *  POST /api/integrations/{code}/test    test connection
 */
@RestController
@RequestMapping("/api/integrations")
@PreAuthorize("hasRole('ADMIN')")
public class IntegrationController {

    private final IntegrationService service;
    private final ConnectionTester tester;

    public IntegrationController(IntegrationService service, ConnectionTester tester) {
        this.service = service; this.tester = tester;
    }

    @GetMapping
    public List<IntegrationResponse> list() { return service.list(); }

    @GetMapping("/{code}")
    public IntegrationResponse get(@PathVariable IntegrationCode code) { return service.get(code); }

    @PutMapping("/{code}")
    public IntegrationResponse update(@PathVariable IntegrationCode code, @Valid @RequestBody IntegrationRequest r) {
        return service.update(code, r);
    }

    @PostMapping("/{code}/test")
    public TestResult test(@PathVariable IntegrationCode code) {
        var r = tester.test(code);
        return new TestResult(r.ok(), r.status(), r.body());
    }
}
