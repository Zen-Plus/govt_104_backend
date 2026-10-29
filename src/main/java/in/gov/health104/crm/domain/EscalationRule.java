package in.gov.health104.crm.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

/**
 * Admin-configurable alert & follow-up rule for one escalation level / role.
 * Defines the day window, which channels fire (portal, SMS, email, voice call),
 * how often reminders repeat, and the message templates.
 * Templates support placeholders: {grievanceNo} {officerName} {category} {daysLeft} {level} {callerName}
 */
@Entity
@Table(name = "escalation_rule")
@Getter @Setter
public class EscalationRule {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Enumerated(EnumType.STRING)
    private EscalationLevel level;
    private int levelOrder;
    @Enumerated(EnumType.STRING)
    private Role notifyRole;
    private int startDay;
    private int endDay;
    private boolean notifyPortal;
    private boolean notifySms;
    private boolean notifyEmail;
    private boolean notifyCall;
    private boolean notifyCallerSms;
    private int reminderHours;
    private String smsTemplate;
    private String emailTemplate;
    private boolean active = true;
    private String updatedBy;
    private LocalDateTime updatedAt = LocalDateTime.now();
}
