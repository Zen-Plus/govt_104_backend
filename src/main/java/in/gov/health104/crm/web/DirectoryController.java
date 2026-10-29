package in.gov.health104.crm.web;

import in.gov.health104.crm.config.ApiException;
import in.gov.health104.crm.domain.Facility;
import in.gov.health104.crm.domain.PublicAuthority;
import in.gov.health104.crm.integration.ExternalGateways;
import in.gov.health104.crm.repository.FacilityRepository;
import in.gov.health104.crm.repository.PublicAuthorityRepository;
import in.gov.health104.crm.service.AuditService;
import in.gov.health104.crm.web.dto.CallDtos.AmbulanceRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Directory & external-service lookups used from the agent console:
 *  GET  /api/directory/facilities          search hospitals / blood banks / diagnostic centres
 *  GET  /api/directory/facilities/nearest  GIS: nearest facilities to a lat/lng (Haversine)
 *  GET  /api/directory/authorities         public authorities (for assigning grievances)
 *  POST /api/directory/authorities         add/edit authority (Admin)
 *  POST /api/directory/ambulance           request State ambulance dispatch
 *  GET  /api/directory/pmjay/{id}          AB-PMJAY beneficiary lookup
 */
@RestController
@RequestMapping("/api/directory")
@Validated
public class DirectoryController {

    private final FacilityRepository facilities;
    private final PublicAuthorityRepository authorities;
    private final ExternalGateways external;
    private final AuditService audit;

    public DirectoryController(FacilityRepository facilities, PublicAuthorityRepository authorities,
                               ExternalGateways external, AuditService audit) {
        this.facilities = facilities; this.authorities = authorities; this.external = external; this.audit = audit;
    }

    @GetMapping("/facilities")
    public List<Facility> facilities(@RequestParam(required = false) @Size(max = 30) String type,
                                     @RequestParam(required = false) @Size(max = 60) String district,
                                     @RequestParam(required = false) @Size(max = 60) String q) {
        return facilities.search(blank(type), blank(district), blank(q));
    }

    @GetMapping("/facilities/nearest")
    public List<Map<String, Object>> nearest(@RequestParam double lat, @RequestParam double lng,
                                             @RequestParam(required = false) String type,
                                             @RequestParam(defaultValue = "10") int limit) {
        return facilities.search(blank(type), null, null).stream()
                .filter(f -> f.getLatitude() != null && f.getLongitude() != null)
                .map(f -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("facility", f);
                    m.put("distanceKm", Math.round(haversine(lat, lng, f.getLatitude(), f.getLongitude()) * 10) / 10.0);
                    return m;
                })
                .sorted(Comparator.comparingDouble(m -> (Double) m.get("distanceKm")))
                .limit(Math.min(limit, 50)).toList();
    }

    @PostMapping("/facilities")
    @PreAuthorize("hasRole('ADMIN')")
    public Facility saveFacility(@RequestBody Facility f) {
        if (f.getName() == null || f.getName().isBlank()) throw ApiException.bad("Name is required");
        audit.record("FACILITY_SAVE", "Facility", f.getId(), null);
        return facilities.save(f);
    }

    @GetMapping("/authorities")
    public List<PublicAuthority> authorities() { return authorities.findByActiveTrueOrderByName(); }

    @PostMapping("/authorities")
    @PreAuthorize("hasRole('ADMIN')")
    public PublicAuthority saveAuthority(@RequestBody PublicAuthority a) {
        if (a.getName() == null || a.getName().isBlank() || a.getLevel() == null || a.getOfficerName() == null)
            throw ApiException.bad("Name, level and officer name are required");
        audit.record("AUTHORITY_SAVE", "PublicAuthority", a.getId(), null);
        return authorities.save(a);
    }

    @PostMapping("/ambulance")
    @PreAuthorize("hasAnyRole('ADMIN','AGENT','MEDICAL_OFFICER','SUPERVISOR')")
    public Map<String, Object> ambulance(@Valid @RequestBody AmbulanceRequest r) {
        Map<String, Object> body = new HashMap<>();
        body.put("patientName", r.patientName()); body.put("phone", r.phone()); body.put("location", r.location());
        body.put("latitude", r.latitude()); body.put("longitude", r.longitude()); body.put("condition", r.condition());
        var res = external.dispatchAmbulance(body);
        audit.record("AMBULANCE_REQUEST", "Ambulance", null, Map.of("ok", res.ok()));
        return Map.of("ok", res.ok(), "message", res.body());
    }

    @GetMapping("/pmjay/{beneficiaryId}")
    @PreAuthorize("hasAnyRole('ADMIN','AGENT','SUPERVISOR')")
    public Map<String, Object> pmjay(@PathVariable @Size(max = 30) String beneficiaryId) {
        var res = external.pmjayLookup(beneficiaryId);
        return Map.of("ok", res.ok(), "message", res.body());
    }

    private static String blank(String s) { return s == null || s.isBlank() ? null : s; }

    /** Great-circle distance in km. */
    static double haversine(double lat1, double lon1, double lat2, double lon2) {
        double r = 6371, dLat = Math.toRadians(lat2 - lat1), dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * r * Math.asin(Math.sqrt(a));
    }
}
