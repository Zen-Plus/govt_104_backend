package in.gov.health104.crm.mongo;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.Instant;
import java.util.Map;

/**
 * Audit trail entry stored in MongoDB collection "audit_logs".
 * Records WHO did WHAT to WHICH entity, from WHERE, and WHEN.
 * Never store passwords, tokens or full secrets in {@code details}.
 */
@Document("audit_logs")
@Getter @Setter
public class AuditLog {
    @Id private String id;
    @Indexed private Instant timestamp = Instant.now();
    @Indexed private String username;
    private String role;
    private String action;       // e.g. GRIEVANCE_CREATE, LOGIN_FAILED, INTEGRATION_UPDATE
    private String entityType;
    private String entityId;
    private String ipAddress;
    private Map<String, Object> details;
}
