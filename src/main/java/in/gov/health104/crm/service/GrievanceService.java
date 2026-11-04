package in.gov.health104.crm.service;

import in.gov.health104.crm.config.ApiException;
import in.gov.health104.crm.domain.*;
import in.gov.health104.crm.mongo.NotificationLogRepository;
import in.gov.health104.crm.repository.*;
import in.gov.health104.crm.security.CurrentUser;
import in.gov.health104.crm.web.dto.GrievanceDtos.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.Year;
import java.util.Map;

/**
 * Grievance Redressal (Pathway 1) business logic: create, edit, soft-delete, status change,
 * plus the helpers used by the escalation engine.
 */
@Service
public class GrievanceService {

    private final GrievanceRepository repo;
    private final GrievanceHistoryRepository history;
    private final PublicAuthorityRepository authorities;
    private final EscalationRuleRepository rules;
    private final NotificationService notify;
    private final NotificationLogRepository notificationLogs;
    private final AuditService audit;
    private final GrievanceAttachmentRepository attachments;

    /** Allowed attachment types and max size (5 MB) for the SIO screen "Attached Docs". */
    private static final java.util.Set<String> ALLOWED_TYPES = java.util.Set.of("application/pdf", "image/jpeg", "image/png");
    private static final long MAX_ATTACHMENT_BYTES = 5L * 1024 * 1024;

    public GrievanceService(GrievanceRepository repo, GrievanceHistoryRepository history,
                            PublicAuthorityRepository authorities, EscalationRuleRepository rules,
                            NotificationService notify, NotificationLogRepository notificationLogs, AuditService audit,
                            GrievanceAttachmentRepository attachments) {
        this.repo = repo; this.history = history; this.authorities = authorities; this.rules = rules;
        this.notify = notify; this.notificationLogs = notificationLogs; this.audit = audit; this.attachments = attachments;
    }

    /**
     * @param age supervisor tabs: H24 (last 24 hours), W1 (1 week), D15 (15 days), M1 (1 month), OLDER (more than 1 month)
     */
    public Page<GrievanceResponse> search(GrievanceStatus status, EscalationLevel level, String district,
                                          String q, String age, int page, int size) {
        String query = (q == null || q.isBlank()) ? null : q.trim();
        String pattern = query == null ? null : "%" + query + "%";
        String dist = (district == null || district.isBlank()) ? null : district;
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime from = LocalDateTime.of(1970, 1, 1, 0, 0), to = now.plusDays(1);
        if (age != null) switch (age) {
            case "H24" -> from = now.minusHours(24);
            case "W1" -> from = now.minusWeeks(1);
            case "D15" -> from = now.minusDays(15);
            case "M1" -> from = now.minusMonths(1);
            case "OLDER" -> to = now.minusMonths(1);
            default -> { }
        }
        return repo.search(status, level, dist, query, pattern, from, to, PageRequest.of(page, Math.min(size, 100))).map(this::toResponse);
    }

    /** SIO screen actions by Supervisor / CMHO. */
    @Transactional
    public GrievanceResponse supervisorAction(Long id, SupervisorActionRequest r) {
        Grievance g = find(id);
        String action = r.action();
        if (r.supervisorRemarks() != null && !r.supervisorRemarks().isBlank()) g.setSupervisorRemarks(r.supervisorRemarks().trim());
        if (r.cmhoRemarks() != null && !r.cmhoRemarks().isBlank()) g.setCmhoRemarks(r.cmhoRemarks().trim());
        String from = g.getStatus().name();
        switch (action) {
            case "FORWARD_CMHO" -> {
                // Hand over to the district (CMHO) level immediately, without waiting for the SLA.
                g.setCurrentLevel(EscalationLevel.DISTRICT);
                g.setStatus(GrievanceStatus.IN_PROGRESS);
                g.setPassTo("DISTRICT_OFFICER");
                g.setForwardedAt(LocalDateTime.now());
                g.setLevelSince(LocalDateTime.now());
                java.util.List<PublicAuthority> cmho = g.getDistrict() == null ? java.util.List.of()
                        : authorities.findByLevelAndDistrictAndActiveTrue(EscalationLevel.DISTRICT, g.getDistrict());
                PublicAuthority target = cmho.isEmpty()
                        ? authorities.findByLevelAndActiveTrue(EscalationLevel.DISTRICT).stream().findFirst().orElse(null)
                        : cmho.get(0);
                if (target != null) g.setAuthority(target);
                afterCommit(() -> rules.findByLevel(EscalationLevel.DISTRICT).ifPresent(rule ->
                        notify.alertOfficer(g, rule, g.getAuthority(), rule.getEndDay() - rule.getStartDay() + 1, false)));
            }
            case "SMS", "EMAIL" -> afterCommit(() -> rules.findByLevel(g.getCurrentLevel()).ifPresent(rule -> {
                EscalationRule one = new EscalationRule();
                one.setLevel(rule.getLevel()); one.setNotifyRole(rule.getNotifyRole());
                one.setSmsTemplate(rule.getSmsTemplate()); one.setEmailTemplate(rule.getEmailTemplate());
                one.setNotifySms("SMS".equals(action)); one.setNotifyEmail("EMAIL".equals(action));
                notify.alertOfficer(g, one, g.getAuthority(), 0, true);
            }));
            case "CLOSE" -> {
                g.setStatus(GrievanceStatus.CLOSED);
                g.setResolvedAt(LocalDateTime.now());
                if (g.getResolutionRemarks() == null) g.setResolutionRemarks(g.getSupervisorRemarks());
                afterCommit(() -> notify.notifyCallerStatus(g));
            }
            default -> { if (g.getStatus() == GrievanceStatus.NEW) g.setStatus(GrievanceStatus.IN_PROGRESS); }
        }
        repo.save(g);
        history.save(new GrievanceHistory(g.getId(), action, from, g.getStatus().name(), g.getCurrentLevel().name(),
                firstNonBlank(r.supervisorRemarks(), r.cmhoRemarks(), action), CurrentUser.username()));
        audit.record("GRIEVANCE_" + action, "Grievance", g.getGrievanceNo(), null);
        return toResponse(g);
    }

    /** Attach a document (PDF/JPG/PNG, max 5 MB). Content type is checked by magic bytes, not trusted from client. */
    @Transactional
    public java.util.Map<String, Object> attach(Long id, String fileName, byte[] data) {
        Grievance g = find(id);
        if (data.length == 0 || data.length > MAX_ATTACHMENT_BYTES) throw ApiException.bad("File must be between 1 byte and 5 MB");
        String type = sniff(data);
        if (!ALLOWED_TYPES.contains(type)) throw ApiException.bad("Only PDF, JPG or PNG files are allowed");
        GrievanceAttachment a = new GrievanceAttachment();
        a.setGrievanceId(g.getId());
        a.setFileName(fileName == null ? "document" : fileName.replaceAll("[^A-Za-z0-9._ -]", "_"));
        a.setContentType(type);
        a.setSizeBytes(data.length);
        a.setData(data);
        a.setUploadedBy(CurrentUser.username());
        attachments.save(a);
        history.save(new GrievanceHistory(g.getId(), "ATTACHMENT", null, null, null, a.getFileName(), CurrentUser.username()));
        return java.util.Map.of("id", a.getId(), "fileName", a.getFileName());
    }

    public GrievanceAttachment attachment(Long grievanceId, Long attachmentId) {
        find(grievanceId);
        return attachments.findById(attachmentId).filter(a -> a.getGrievanceId().equals(grievanceId))
                .orElseThrow(() -> ApiException.notFound("Attachment"));
    }

    private static String sniff(byte[] d) {
        if (d.length > 4 && d[0] == '%' && d[1] == 'P' && d[2] == 'D' && d[3] == 'F') return "application/pdf";
        if (d.length > 3 && (d[0] & 0xFF) == 0xFF && (d[1] & 0xFF) == 0xD8) return "image/jpeg";
        if (d.length > 8 && (d[0] & 0xFF) == 0x89 && d[1] == 'P' && d[2] == 'N' && d[3] == 'G') return "image/png";
        return "unknown";
    }

    private static String firstNonBlank(String... s) {
        for (String x : s) if (x != null && !x.isBlank()) return x;
        return null;
    }

    public GrievanceDetail detail(Long id) {
        Grievance g = find(id);
        return new GrievanceDetail(toResponse(g), history.findByGrievanceIdOrderByCreatedAtAsc(id),
                notificationLogs.findByGrievanceNoOrderByTimestampDesc(g.getGrievanceNo()), attachments.listMeta(id));
    }

    /** Registers a grievance, generates its unique ID and triggers caller + officer alerts after commit. */
    @Transactional
    public Grievance create(GrievanceRequest r) {
        Grievance g = new Grievance();
        apply(g, r);
        g.setGrievanceNo(String.format("GRV-%d-%06d", Year.now().getValue(), repo.nextSequence()));
        g.setCreatedBy(CurrentUser.username());
        g.setCallerId(r.callerId());
        g.setCallId(r.callId());
        repo.save(g);
        history.save(new GrievanceHistory(g.getId(), "CREATED", null, g.getStatus().name(),
                g.getCurrentLevel().name(), "Grievance registered", CurrentUser.username()));
        audit.record("GRIEVANCE_CREATE", "Grievance", g.getGrievanceNo(), Map.of("category", g.getCategory()));

        // Fire notifications only once the DB transaction is committed.
        afterCommit(() -> {
            notify.acknowledgeCaller(g);
            rules.findByLevel(EscalationLevel.FACILITY).filter(EscalationRule::isActive).ifPresent(rule ->
                    notify.alertOfficer(g, rule, g.getAuthority(), rule.getEndDay(), false));
        });
        return g;
    }

    @Transactional
    public GrievanceResponse update(Long id, GrievanceRequest r, Long expectedVersion) {
        Grievance g = find(id);
        if (expectedVersion != null && !expectedVersion.equals(g.getVersion()))
            throw new ApiException(org.springframework.http.HttpStatus.CONFLICT, "This grievance was changed by someone else. Please reload.");
        if (!g.getStatus().isOpen()) throw ApiException.bad("Closed or resolved grievances cannot be edited");
        apply(g, r);
        repo.save(g);
        history.save(new GrievanceHistory(g.getId(), "UPDATED", null, null, g.getCurrentLevel().name(),
                "Details edited", CurrentUser.username()));
        audit.record("GRIEVANCE_UPDATE", "Grievance", g.getGrievanceNo(), null);
        return toResponse(g);
    }

    /** Soft delete: record stays in DB for audit but disappears from all lists. */
    @Transactional
    public void delete(Long id, String reason) {
        Grievance g = find(id);
        g.setDeleted(true);
        repo.save(g);
        history.save(new GrievanceHistory(g.getId(), "DELETED", g.getStatus().name(), null, null,
                reason == null ? "Deleted" : reason, CurrentUser.username()));
        audit.record("GRIEVANCE_DELETE", "Grievance", g.getGrievanceNo(), Map.of("reason", reason == null ? "" : reason));
    }

    /** Officer/supervisor action: mark in-progress, resolved, closed or rejected. */
    @Transactional
    public GrievanceResponse changeStatus(Long id, StatusUpdateRequest r) {
        Grievance g = find(id);
        GrievanceStatus from = g.getStatus();
        GrievanceStatus to = GrievanceStatus.valueOf(r.status());
        g.setStatus(to);
        if (to == GrievanceStatus.RESOLVED || to == GrievanceStatus.CLOSED || to == GrievanceStatus.REJECTED) {
            g.setResolvedAt(LocalDateTime.now());
            g.setResolutionRemarks(r.remarks());
        }
        if ("SUPERVISOR".equals(CurrentUser.role())) g.setSupervisorRemarks(r.remarks());
        repo.save(g);
        history.save(new GrievanceHistory(g.getId(), "STATUS_CHANGE", from.name(), to.name(),
                g.getCurrentLevel().name(), r.remarks(), CurrentUser.username()));
        audit.record("GRIEVANCE_STATUS", "Grievance", g.getGrievanceNo(), Map.of("from", from.name(), "to", to.name()));
        if (!to.isOpen()) afterCommit(() -> notify.notifyCallerStatus(g));
        return toResponse(g);
    }

    public Grievance find(Long id) {
        return repo.findByIdAndDeletedFalse(id).orElseThrow(() -> ApiException.notFound("Grievance"));
    }

    private void apply(Grievance g, GrievanceRequest r) {
        g.setPatientName(r.patientName().trim());
        g.setPatientAge(r.patientAge());
        g.setPatientSex(r.patientSex());
        g.setContactPhone(r.contactPhone());
        g.setContactEmail(r.contactEmail());
        g.setDistrict(r.district());
        g.setBlock(r.block());
        g.setVillage(r.village());
        g.setCategory(r.category());
        g.setComplaintAgainst(r.complaintAgainst());
        g.setDescription(r.description().trim());
        g.setPriority(r.priority() == null || r.priority().isBlank() ? "NORMAL" : r.priority());
        g.setEpidemic(r.epidemic());
        g.setPatientAgeUnit(r.patientAgeUnit() == null || r.patientAgeUnit().isBlank() ? "YEARS" : r.patientAgeUnit());
        g.setCommonComplaint(r.commonComplaint());
        g.setPandemic(r.pandemic());
        g.setPassTo(r.passTo() == null || r.passTo().isBlank() ? "SUPERVISOR" : r.passTo());
        if (r.authorityId() != null) {
            g.setAuthority(authorities.findById(r.authorityId()).orElseThrow(() -> ApiException.bad("Unknown public authority")));
        }
    }

    /** Converts entity to API response, including SLA fields (age and days left at current level). */
    public GrievanceResponse toResponse(Grievance g) {
        long ageDays = Duration.between(g.getCreatedAt(), LocalDateTime.now()).toDays();
        int daysLeft = rules.findByLevel(g.getCurrentLevel())
                .map(rule -> (int) (rule.getEndDay() - (ageDays + 1)) + 1).orElse(0);
        PublicAuthority a = g.getAuthority();
        return new GrievanceResponse(g.getId(), g.getGrievanceNo(), g.getPatientName(), g.getPatientAge(), g.getPatientSex(),
                g.getContactPhone(), g.getContactEmail(), g.getDistrict(), g.getBlock(), g.getVillage(), g.getCategory(),
                g.getComplaintAgainst(), a == null ? null : a.getId(), a == null ? null : a.getName(),
                a == null ? null : a.getOfficerName(), a == null ? null : a.getOfficerPhone(), g.getDescription(),
                g.getPriority(), g.isEpidemic(), g.getCurrentLevel().name(), g.getStatus().name(), g.getResolutionRemarks(),
                g.getSupervisorRemarks(), g.getCreatedBy(), g.getCreatedAt(), g.getUpdatedAt(), g.getLevelSince(),
                g.getResolvedAt(), ageDays, g.getStatus().isOpen() ? daysLeft : 0, g.getVersion(),
                g.getPatientAgeUnit(), g.isCommonComplaint(), g.isPandemic(), g.getPassTo(),
                g.getCmhoRemarks(), g.getForwardedAt());
    }

    static void afterCommit(Runnable r) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { r.run(); }
            });
        } else r.run();
    }
}
