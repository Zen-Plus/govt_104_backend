package in.gov.health104.crm.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.LocalDateTime;

/** Immutable timeline entry for a grievance (created, updated, escalated, resolved...). */
@Entity
@Table(name = "grievance_history")
@Getter @Setter @NoArgsConstructor
public class GrievanceHistory {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long grievanceId;
    private String action;
    private String fromStatus;
    private String toStatus;
    private String level;
    private String remarks;
    private String actor;
    private LocalDateTime createdAt = LocalDateTime.now();

    public GrievanceHistory(Long grievanceId, String action, String fromStatus, String toStatus,
                            String level, String remarks, String actor) {
        this.grievanceId = grievanceId; this.action = action; this.fromStatus = fromStatus;
        this.toStatus = toStatus; this.level = level; this.remarks = remarks; this.actor = actor;
    }
}
