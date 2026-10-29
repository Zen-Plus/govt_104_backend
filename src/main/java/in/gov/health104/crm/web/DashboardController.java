package in.gov.health104.crm.web;

import in.gov.health104.crm.domain.CallPathway;
import in.gov.health104.crm.domain.EscalationLevel;
import in.gov.health104.crm.domain.GrievanceStatus;
import in.gov.health104.crm.mongo.AuditLog;
import in.gov.health104.crm.mongo.AuditLogRepository;
import in.gov.health104.crm.repository.CallRecordRepository;
import in.gov.health104.crm.repository.GrievanceRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;

/** KPIs for dashboards (CMHO / Nodal / Admin) and the audit log viewer. */
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final GrievanceRepository grievances;
    private final CallRecordRepository calls;
    private final AuditLogRepository auditLogs;

    public DashboardController(GrievanceRepository grievances, CallRecordRepository calls, AuditLogRepository auditLogs) {
        this.grievances = grievances; this.calls = calls; this.auditLogs = auditLogs;
    }

    @GetMapping("/summary")
    public Map<String, Object> summary() {
        var today = LocalDate.now().atStartOfDay();
        var open = EnumSet.of(GrievanceStatus.NEW, GrievanceStatus.IN_PROGRESS, GrievanceStatus.ESCALATED);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("callsToday", calls.countByCreatedAtAfter(today));
        Map<String, Long> byPathway = new LinkedHashMap<>();
        for (CallPathway p : CallPathway.values()) byPathway.put(p.name(), calls.countByPathwayAndCreatedAtAfter(p, today));
        m.put("callsByPathway", byPathway);
        Map<String, Long> byStatus = new LinkedHashMap<>();
        for (GrievanceStatus s : GrievanceStatus.values()) byStatus.put(s.name(), grievances.countByDeletedFalseAndStatus(s));
        m.put("grievancesByStatus", byStatus);
        Map<String, Long> byLevel = new LinkedHashMap<>();
        for (EscalationLevel l : EscalationLevel.values()) byLevel.put(l.name(), grievances.countByDeletedFalseAndCurrentLevelAndStatusIn(l, open));
        m.put("openByLevel", byLevel);
        return m;
    }

    @GetMapping("/audit")
    @PreAuthorize("hasRole('ADMIN')")
    public Page<AuditLog> audit(@RequestParam(defaultValue = "0") int page) {
        return auditLogs.findAllByOrderByTimestampDesc(PageRequest.of(Math.max(page, 0), 50));
    }
}
