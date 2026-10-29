package in.gov.health104.crm.web;

import in.gov.health104.crm.domain.EscalationLevel;
import in.gov.health104.crm.domain.GrievanceStatus;
import in.gov.health104.crm.service.EscalationService;
import in.gov.health104.crm.service.GrievanceService;
import in.gov.health104.crm.web.dto.GrievanceDtos.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Grievance Redressal REST API.
 *  GET    /api/grievances            list + filters (status, level, district, q) with paging
 *  GET    /api/grievances/{id}       detail + timeline + notification log
 *  POST   /api/grievances            add
 *  PUT    /api/grievances/{id}       edit   (send ?version= for optimistic locking)
 *  DELETE /api/grievances/{id}       soft delete (Admin / Supervisor only)
 *  POST   /api/grievances/{id}/status  resolve / close / in-progress (officers & supervisor)
 *  POST   /api/grievances/escalation/run  run the escalation engine now (Admin)
 */
@RestController
@RequestMapping("/api/grievances")
@Validated
public class GrievanceController {

    private static final String OFFICERS = "hasAnyRole('ADMIN','SUPERVISOR','FACILITY_OFFICER','DISTRICT_OFFICER','STATE_OFFICER','MINISTER_OFFICE')";
    private final GrievanceService service;
    private final EscalationService escalation;

    public GrievanceController(GrievanceService service, EscalationService escalation) {
        this.service = service; this.escalation = escalation;
    }

    @GetMapping
    public Page<GrievanceResponse> list(@RequestParam(required = false) GrievanceStatus status,
                                        @RequestParam(required = false) EscalationLevel level,
                                        @RequestParam(required = false) @Size(max = 60) String district,
                                        @RequestParam(required = false) @Size(max = 60) String q,
                                        @RequestParam(required = false) @jakarta.validation.constraints.Pattern(regexp = "^(H24|W1|D15|M1|OLDER)?$") String age,
                                        @RequestParam(defaultValue = "0") int page,
                                        @RequestParam(defaultValue = "20") int size) {
        return service.search(status, level, district, q, age, Math.max(page, 0), Math.max(size, 1));
    }

    @GetMapping("/{id}")
    public GrievanceDetail get(@PathVariable Long id) { return service.detail(id); }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','AGENT','SUPERVISOR')")
    public GrievanceResponse create(@Valid @RequestBody GrievanceRequest r) {
        return service.toResponse(service.create(r));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','AGENT','SUPERVISOR')")
    public GrievanceResponse update(@PathVariable Long id, @Valid @RequestBody GrievanceRequest r,
                                    @RequestParam(required = false) Long version) {
        return service.update(id, r, version);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('ADMIN','SUPERVISOR')")
    public void delete(@PathVariable Long id, @RequestParam(required = false) @Size(max = 300) String reason) {
        service.delete(id, reason);
    }

    @PostMapping("/{id}/status")
    @PreAuthorize(OFFICERS)
    public GrievanceResponse status(@PathVariable Long id, @Valid @RequestBody StatusUpdateRequest r) {
        return service.changeStatus(id, r);
    }

    /** SIO screen: Update Complaint / SMS / EMAIL / FORWARD TO CMHO / CLOSE. */
    @PostMapping("/{id}/supervisor-action")
    @PreAuthorize("hasAnyRole('ADMIN','SUPERVISOR','DISTRICT_OFFICER')")
    public GrievanceResponse supervisorAction(@PathVariable Long id, @Valid @RequestBody SupervisorActionRequest r) {
        return service.supervisorAction(id, r);
    }

    /** Upload a supporting document (multipart field "file"; PDF/JPG/PNG up to 5 MB). */
    @PostMapping(value = "/{id}/attachments", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(OFFICERS)
    public Map<String, Object> upload(@PathVariable Long id,
                                      @RequestParam("file") org.springframework.web.multipart.MultipartFile file) throws java.io.IOException {
        return service.attach(id, file.getOriginalFilename(), file.getBytes());
    }

    /** Download an attachment. Served as attachment with nosniff so it is never rendered inline as HTML. */
    @GetMapping("/{id}/attachments/{attachmentId}")
    public org.springframework.http.ResponseEntity<byte[]> download(@PathVariable Long id, @PathVariable Long attachmentId) {
        var a = service.attachment(id, attachmentId);
        return org.springframework.http.ResponseEntity.ok()
                .header("Content-Disposition", org.springframework.http.ContentDisposition.attachment().filename(a.getFileName()).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .contentType(org.springframework.http.MediaType.parseMediaType(a.getContentType()))
                .body(a.getData());
    }

    @PostMapping("/escalation/run")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Integer> runEscalation() { return Map.of("escalated", escalation.runOnce()); }
}
