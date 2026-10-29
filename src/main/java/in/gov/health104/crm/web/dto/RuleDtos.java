package in.gov.health104.crm.web.dto;

import jakarta.validation.constraints.*;

/** Escalation / alert follow-up rule configuration. */
public final class RuleDtos {
    private RuleDtos() {}
    public record EscalationRuleRequest(
            @NotBlank String notifyRole,
            @Min(1) @Max(365) int startDay,
            @Min(1) @Max(365) int endDay,
            boolean notifyPortal, boolean notifySms, boolean notifyEmail, boolean notifyCall,
            boolean notifyCallerSms,
            @Min(1) @Max(168) int reminderHours,
            @Size(max = 500) String smsTemplate,
            @Size(max = 4000) String emailTemplate,
            boolean active) {}
}
