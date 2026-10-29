package in.gov.health104.crm.web;

import in.gov.health104.crm.domain.Patient;
import in.gov.health104.crm.repository.CallRecordRepository;
import in.gov.health104.crm.repository.CallerRepository;
import in.gov.health104.crm.repository.PatientRepository;
import in.gov.health104.crm.service.CallService;
import in.gov.health104.crm.web.dto.CallDtos.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Agent console, MO/Counsellor screens and follow-ups.
 *  POST /api/calls/start                    call lands -> Call ID
 *  POST /api/calls/{id}/caller              Caller Registration "Generate" -> CallerID
 *  POST /api/patients                       Patient Registration "Generate ID" -> Patient ID
 *  GET  /api/patients?callerId=             patients of a caller ("Select Search" dropdown)
 *  POST /api/calls/{id}/complete            call-type screen: Close / Transfer / Register Complaint
 *  POST /api/calls/{id}/transfer            conference panel: transfer / conference
 *  GET  /api/calls/{id}                     MO / Counsellor screen data (+ last advice)
 *  POST /api/calls/{id}/close               MO / Counsellor Close or Transfer to EMS
 *  GET  /api/calls/queue/{target}           transferred calls waiting for MO / Counsellor
 *  GET  /api/calls/followups                FollowUps (Pending / UpComing) for the logged-in user
 *  POST /api/calls/{id}/followup-done       mark a follow-up as done
 *  GET  /api/calls/recent                   last 50 calls (supervisor/admin)
 */
@RestController
@RequestMapping("/api")
@Validated
public class CallController {

    private static final String DESK = "hasAnyRole('ADMIN','AGENT','SUPERVISOR')";
    private static final String CLINICAL = "hasAnyRole('ADMIN','MEDICAL_OFFICER','COUNSELLOR','SUPERVISOR')";

    private final CallService service;
    private final CallerRepository callers;
    private final CallRecordRepository calls;
    private final PatientRepository patients;

    public CallController(CallService service, CallerRepository callers, CallRecordRepository calls, PatientRepository patients) {
        this.service = service; this.callers = callers; this.calls = calls; this.patients = patients;
    }

    @PostMapping("/calls/start")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(DESK)
    public StartCallResponse start(@Valid @RequestBody StartCallRequest r) { return service.start(r); }

    @PostMapping("/calls/{id}/caller")
    @PreAuthorize(DESK)
    public Map<String, Object> caller(@PathVariable Long id, @Valid @RequestBody CallerInfo r) { return service.saveCaller(id, r); }

    @PostMapping("/patients")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','AGENT','SUPERVISOR','MEDICAL_OFFICER','COUNSELLOR')")
    public Patient patient(@Valid @RequestBody PatientRequest r) { return service.savePatient(r); }

    @GetMapping("/patients")
    @PreAuthorize("hasAnyRole('ADMIN','AGENT','SUPERVISOR','MEDICAL_OFFICER','COUNSELLOR')")
    public List<Patient> patients(@RequestParam Long callerId) { return patients.findByCallerIdOrderByCreatedAtDesc(callerId); }

    @PostMapping("/calls/{id}/complete")
    @PreAuthorize(DESK)
    public CallResponse complete(@PathVariable Long id, @Valid @RequestBody CompleteCallRequest r) { return service.complete(id, r); }

    @PostMapping("/calls/{id}/transfer")
    @PreAuthorize("hasAnyRole('ADMIN','AGENT','SUPERVISOR','MEDICAL_OFFICER','COUNSELLOR')")
    public Map<String, Object> transfer(@PathVariable Long id, @Valid @RequestBody TransferRequest r) { return service.transfer(id, r); }

    @GetMapping("/calls/{id}")
    @PreAuthorize(CLINICAL)
    public Map<String, Object> view(@PathVariable Long id) { return service.view(id); }

    @PostMapping("/calls/{id}/close")
    @PreAuthorize("hasAnyRole('ADMIN','MEDICAL_OFFICER','COUNSELLOR')")
    public void close(@PathVariable Long id, @Valid @RequestBody CloseCallRequest r) { service.closeTransferred(id, r); }

    @GetMapping("/calls/caller-lookup")
    @PreAuthorize("hasAnyRole('ADMIN','AGENT','SUPERVISOR','MEDICAL_OFFICER','COUNSELLOR')")
    public Object lookup(@RequestParam @Pattern(regexp = "^[6-9]\\d{9}$") String phone) {
        return callers.findTop5ByPhoneOrderByCreatedAtDesc(phone);
    }

    @GetMapping("/calls/queue/{target}")
    @PreAuthorize(CLINICAL)
    @Transactional(readOnly = true)
    public List<Map<String, Object>> queue(@PathVariable @Pattern(regexp = "^(MEDICAL_OFFICER|COUNSELLOR)$") String target) {
        return calls.findByTransferredToAndStatus(target, "TRANSFERRED", PageRequest.of(0, 50)).map(service::toMap).getContent();
    }

    @GetMapping("/calls/followups")
    public Map<String, Object> followUps() { return service.followUps(); }

    @PostMapping("/calls/{id}/followup-done")
    public void followUpDone(@PathVariable Long id) { service.markFollowUpDone(id); }

    @GetMapping("/calls/recent")
    @PreAuthorize("hasAnyRole('ADMIN','SUPERVISOR')")
    @Transactional(readOnly = true)
    public List<Map<String, Object>> recent() {
        return calls.findAllByOrderByCreatedAtDesc(PageRequest.of(0, 50)).map(service::toMap).getContent();
    }
}
