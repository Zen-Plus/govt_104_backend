package in.gov.health104.crm.service;

import in.gov.health104.crm.config.ApiException;
import in.gov.health104.crm.domain.*;
import in.gov.health104.crm.integration.TelecomGateway;
import in.gov.health104.crm.repository.*;
import in.gov.health104.crm.security.CurrentUser;
import in.gov.health104.crm.web.dto.CallDtos.*;
import in.gov.health104.crm.web.dto.GrievanceDtos.GrievanceRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Call handling exactly as in the agent screens of the UX deck:
 *
 *  START      call lands -> CallRecord(OPEN) with Call ID / phone / date; repeat caller lookup
 *  CALLER     "Generate"    -> saves Caller Registration (name, district, block, village, address) -> CallerID
 *  PATIENT    "Generate ID" -> Patient (name, age, age unit, gender) -> Patient ID
 *  COMPLETE   call type screen:
 *      MEDICAL_ADVICE      -> Close, Transfer (to Medical Officer), Transfer to EMS
 *      COUNSELLING         -> Close (with optional follow-up), Transfer (to Counsellor)
 *      COMPLAINTS          -> Register Complaint (creates grievance, call stays open) then Close
 *      GENERAL_INFORMATION -> sub type DKBSSY / RCH / HOSPITAL / BLOOD_ON_CALL / GOVT_SCHEMES -> Close
 *      INVALID             -> Close with close type
 *      TRANSFER            -> sub type 102 / 108 / 112 / 1099 -> transfer via telecom
 *  MO / COUNSELLOR screens then load the transferred call, record advice, CDI, follow-up and Close
 *  (or Transfer to EMS from the MO screen).
 */
@Service
public class CallService {

    private static final Map<String, CallPathway> PATHWAY = Map.of(
            "MEDICAL_ADVICE", CallPathway.HEALTH_ADVICE, "COUNSELLING", CallPathway.COUNSELLING,
            "COMPLAINTS", CallPathway.GRIEVANCE, "GENERAL_INFORMATION", CallPathway.INFORMATION,
            "INVALID", CallPathway.INFORMATION, "TRANSFER", CallPathway.TRANSFER);

    private final CallerRepository callers;
    private final CallRecordRepository calls;
    private final PatientRepository patients;
    private final AppUserRepository users;
    private final GrievanceService grievances;
    private final TelecomGateway telecom;
    private final AuditService audit;

    public CallService(CallerRepository callers, CallRecordRepository calls, PatientRepository patients,
                       AppUserRepository users, GrievanceService grievances, TelecomGateway telecom, AuditService audit) {
        this.callers = callers; this.calls = calls; this.patients = patients; this.users = users;
        this.grievances = grievances; this.telecom = telecom; this.audit = audit;
    }

    /** Step 1: call lands on the agent (in production the dialer calls this with the ANI). */
    @Transactional
    public StartCallResponse start(StartCallRequest r) {
        CallRecord call = new CallRecord();
        call.setCallRef(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"))
                + String.format("%05d", ThreadLocalRandom.current().nextInt(100000)));
        call.setPhone(r.phone());
        call.setStatus("OPEN");
        currentUserId().ifPresent(call::setAgentId);
        calls.save(call);
        audit.record("CALL_START", "Call", call.getCallRef(), null);
        return new StartCallResponse(call.getId(), call.getCallRef(), call.getPhone(), call.getCreatedAt(),
                r.phone().length() == 10 ? callers.findTop5ByPhoneOrderByCreatedAtDesc(r.phone()) : List.of());
    }

    /** Step 2: caller registration "Generate" button. Re-uses the caller if an id is sent. */
    @Transactional
    public Map<String, Object> saveCaller(Long callId, CallerInfo i) {
        CallRecord call = open(callId);
        Caller c = i.id() != null ? callers.findById(i.id()).orElse(new Caller()) : new Caller();
        c.setName(i.name().trim()); c.setAge(i.age()); c.setSex(i.sex());
        c.setPhone(i.phone() == null || i.phone().isBlank() ? call.getPhone() : i.phone());
        c.setEmail(i.email()); c.setDistrict(i.district()); c.setBlock(i.block());
        c.setVillage(i.village()); c.setAddress(i.address());
        callers.save(c);
        call.setCaller(c);
        calls.save(call);
        return Map.of("callerId", c.getId(), "patients", patients.findByCallerIdOrderByCreatedAtDesc(c.getId()));
    }

    /** Step 3: patient registration "Generate ID". */
    @Transactional
    public Patient savePatient(PatientRequest r) {
        Patient p = new Patient();
        p.setPatientCode(String.valueOf(patients.nextSequence()));
        p.setCallerId(r.callerId());
        p.setName(r.name().trim());
        p.setAge(r.age());
        p.setAgeUnit(r.ageUnit() == null || r.ageUnit().isBlank() ? "YEARS" : r.ageUnit());
        p.setGender(r.gender());
        return patients.save(p);
    }

    /** Step 4: the call-type screen's action button. */
    @Transactional
    public CallResponse complete(Long callId, CompleteCallRequest r) {
        CallRecord call = open(callId);
        boolean needsCaller = !"INVALID".equals(r.callType()) && !"TRANSFER".equals(r.callType());
        if (needsCaller && call.getCaller() == null)
            throw ApiException.bad("Please complete Caller Registration (Generate) first");
        if ("CLOSE".equals(r.action()) && (r.closeType() == null || r.closeType().isBlank()))
            throw ApiException.bad("Select a Close Type");
        if (("MEDICAL_ADVICE".equals(r.callType()) || "COUNSELLING".equals(r.callType()))
                && (r.cdiSatisfied() == null || r.cdiSatisfied().isBlank()))
            throw ApiException.bad("CDI satisfaction is required");

        call.setPathway(PATHWAY.get(r.callType()));
        call.setCallType(r.callType());
        call.setSubCallType(r.subCallType());
        call.setPatientId(r.patientId());
        call.setCloseType(r.closeType());
        call.setCdiSatisfied(r.cdiSatisfied());
        call.setCdiReason(r.cdiReason());
        call.setSpecialityGroup(r.specialityGroup());
        call.setQueryText(r.queryText());
        call.setAdviceGiven(r.adviceGiven());
        call.setFollowUpAt(r.followUpAt());
        Object existingGrievance = call.getDetails() == null ? null : call.getDetails().get("grievanceNo");
        Map<String, Object> details = new HashMap<>(r.details() == null ? Map.of() : r.details());
        if (existingGrievance != null) details.put("grievanceNo", existingGrievance);
        call.setDetails(details);

        String grievanceNo = null;
        switch (r.action()) {
            case "COMPLAINT" -> {
                if (r.grievance() == null) throw ApiException.bad("Complaint details are required");
                GrievanceRequest g = r.grievance();
                GrievanceRequest linked = new GrievanceRequest(g.patientName(), g.patientAge(), g.patientSex(), g.contactPhone(),
                        g.contactEmail(), g.district(), g.block(), g.village(), g.category(), g.complaintAgainst(), g.authorityId(),
                        g.description(), g.priority(), g.epidemic(), call.getCaller().getId(), call.getId(),
                        g.patientAgeUnit(), g.commonComplaint(), g.pandemic(), g.passTo());
                // As on the Complaint screen: "Register Complaint" creates the grievance and shows its ID;
                // the agent then chooses a Close Type and presses Close (a second CLOSE request).
                if (call.getDetails() != null && call.getDetails().get("grievanceNo") != null)
                    throw ApiException.bad("Complaint already registered for this call");
                grievanceNo = grievances.create(linked).getGrievanceNo();
                Map<String, Object> d = new HashMap<>(r.details() == null ? Map.of() : r.details());
                d.put("grievanceNo", grievanceNo);
                call.setDetails(d);
            }
            case "TRANSFER" -> {
                String target = r.transferTo();
                if (target == null || target.isBlank()) throw ApiException.bad("Select where to transfer the call");
                call.setTransferredTo(target);
                call.setTransferMode(r.transferMode() == null ? "BLIND" : r.transferMode());
                boolean internal = "MEDICAL_OFFICER".equals(target) || "COUNSELLOR".equals(target);
                // Internal transfers wait in the MO/Counsellor queue; external (EMS/102/108/112/1099) close here.
                if (internal) call.setStatus("TRANSFERRED"); else closeNow(call);
                String ref = call.getCallRef(); boolean conf = "CONFERENCE".equals(call.getTransferMode());
                GrievanceService.afterCommit(() -> telecom.transfer(ref, target, conf));
            }
            default -> closeNow(call);
        }
        calls.save(call);
        audit.record("CALL_" + r.action(), "Call", call.getCallRef(), Map.of("callType", r.callType()));
        return new CallResponse(call.getId(), call.getCallRef(), call.getCaller() == null ? null : call.getCaller().getId(),
                grievanceNo, call.getStatus());
    }

    /** Medical Officer / Counsellor screen: full call context incl. last advice given to this caller. */
    @Transactional(readOnly = true)
    public Map<String, Object> view(Long id) {
        CallRecord c = calls.findById(id).orElseThrow(() -> ApiException.notFound("Call"));
        Map<String, Object> m = toMap(c);
        if (c.getPatientId() != null) patients.findById(c.getPatientId()).ifPresent(p -> m.put("patient", p));
        if (c.getCaller() != null) {
            m.put("caller", c.getCaller());
            m.put("lastAdvice", calls.findTop5ByCaller_IdAndIdNotOrderByCreatedAtDesc(c.getCaller().getId(), c.getId())
                    .stream().filter(x -> x.getAdviceGiven() != null).findFirst().map(this::toMap).orElse(null));
        }
        return m;
    }

    /** MO / Counsellor closes the transferred call, or MO transfers to EMS. */
    @Transactional
    public void closeTransferred(Long id, CloseCallRequest r) {
        CallRecord call = calls.findById(id).orElseThrow(() -> ApiException.notFound("Call"));
        if (!"TRANSFERRED".equals(call.getStatus())) throw ApiException.bad("This call is already closed");
        if (r.cdiSatisfied() == null || r.cdiSatisfied().isBlank()) throw ApiException.bad("CDI satisfaction is required");
        call.setAdviceGiven(r.adviceGiven());
        call.setCloseType(r.closeType());
        call.setCdiSatisfied(r.cdiSatisfied());
        call.setCdiReason(r.cdiReason());
        call.setFollowUpAt(r.followUpAt());
        if (r.details() != null) {
            Map<String, Object> merged = new HashMap<>(call.getDetails() == null ? Map.of() : call.getDetails());
            merged.putAll(r.details());
            call.setDetails(merged);
        }
        currentUserId().ifPresent(call::setHandledById);
        if ("EMS".equals(r.transferTo())) {
            call.setTransferredTo("EMS");
            String ref = call.getCallRef();
            GrievanceService.afterCommit(() -> telecom.transfer(ref, "EMS", false));
        }
        closeNow(call);
        calls.save(call);
        audit.record("CALL_CLOSE", "Call", call.getCallRef(), null);
    }

    /** Live transfer / conference from the Conf panel. */
    public Map<String, Object> transfer(Long id, TransferRequest r) {
        CallRecord call = calls.findById(id).orElseThrow(() -> ApiException.notFound("Call"));
        var res = telecom.transfer(call.getCallRef(), r.target(), "CONFERENCE".equals(r.mode()));
        audit.record("CALL_TRANSFER", "Call", call.getCallRef(), Map.of("target", r.target(), "mode", r.mode()));
        return Map.of("ok", res.ok(), "message", res.body());
    }

    /** FollowUps (Pending / UpComing) shown in the agent top bar. */
    @Transactional(readOnly = true)
    public Map<String, Object> followUps() {
        Long me = currentUserId().orElse(-1L);
        LocalDateTime now = LocalDateTime.now();
        List<Map<String, Object>> all = calls.openFollowUps(me)
                .stream().map(this::toMap).toList();
        long pending = all.stream().filter(m -> ((LocalDateTime) m.get("followUpAt")).isBefore(now)).count();
        return Map.of("pending", pending, "upcoming", all.size() - pending, "items", all);
    }

    @Transactional
    public void markFollowUpDone(Long id) {
        CallRecord c = calls.findById(id).orElseThrow(() -> ApiException.notFound("Call"));
        c.setFollowUpDone(true);
        calls.save(c);
    }

    public Map<String, Object> toMap(CallRecord c) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", c.getId()); m.put("callRef", c.getCallRef()); m.put("phone", c.getPhone());
        m.put("callType", c.getCallType()); m.put("subCallType", c.getSubCallType());
        m.put("queryText", c.getQueryText()); m.put("adviceGiven", c.getAdviceGiven());
        m.put("status", c.getStatus()); m.put("transferredTo", c.getTransferredTo());
        m.put("closeType", c.getCloseType()); m.put("cdiSatisfied", c.getCdiSatisfied());
        m.put("specialityGroup", c.getSpecialityGroup()); m.put("followUpAt", c.getFollowUpAt());
        m.put("details", c.getDetails() == null ? Map.of() : c.getDetails());
        m.put("createdAt", c.getCreatedAt());
        if (c.getCaller() != null) {
            m.put("callerName", c.getCaller().getName()); m.put("callerAge", c.getCaller().getAge());
            m.put("callerSex", c.getCaller().getSex()); m.put("callerPhone", c.getCaller().getPhone());
            m.put("district", c.getCaller().getDistrict());
        }
        return m;
    }

    private CallRecord open(Long id) {
        CallRecord c = calls.findById(id).orElseThrow(() -> ApiException.notFound("Call"));
        if (!"OPEN".equals(c.getStatus())) throw ApiException.bad("This call is already " + c.getStatus().toLowerCase());
        return c;
    }

    private static void closeNow(CallRecord c) {
        c.setStatus("CLOSED");
        c.setClosedAt(LocalDateTime.now());
    }

    private Optional<Long> currentUserId() {
        return users.findByUsername(CurrentUser.username()).map(AppUser::getId);
    }
}
