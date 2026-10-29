package in.gov.health104.crm.service;

import in.gov.health104.crm.config.ClientIp;
import in.gov.health104.crm.mongo.AuditLog;
import in.gov.health104.crm.mongo.AuditLogRepository;
import in.gov.health104.crm.security.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Map;

/** Writes business audit events (who changed what) to MongoDB "audit_logs". */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);
    private final AuditLogRepository repo;

    public AuditService(AuditLogRepository repo) { this.repo = repo; }

    public void record(String action, String entityType, Object entityId, Map<String, Object> details) {
        record(CurrentUser.username(), action, entityType, entityId, details);
    }

    public void record(String username, String action, String entityType, Object entityId, Map<String, Object> details) {
        try {
            AuditLog a = new AuditLog();
            a.setUsername(username);
            a.setRole(CurrentUser.role());
            a.setAction(action);
            a.setEntityType(entityType);
            a.setEntityId(entityId == null ? null : String.valueOf(entityId));
            a.setDetails(details);
            var attrs = RequestContextHolder.getRequestAttributes();
            if (attrs instanceof ServletRequestAttributes sra) {
                HttpServletRequest req = sra.getRequest();
                a.setIpAddress(ClientIp.of(req));
            }
            repo.save(a);
        } catch (Exception e) {
            // Never fail a business transaction because the log store is down.
            log.warn("Audit write failed for {}: {}", action, e.getMessage());
        }
    }
}
