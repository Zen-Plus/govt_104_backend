package in.gov.health104.crm.web;

import in.gov.health104.crm.config.ApiException;
import in.gov.health104.crm.domain.EscalationRule;
import in.gov.health104.crm.domain.Role;
import in.gov.health104.crm.repository.EscalationRuleRepository;
import in.gov.health104.crm.security.CurrentUser;
import in.gov.health104.crm.service.AuditService;
import in.gov.health104.crm.web.dto.RuleDtos.EscalationRuleRequest;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Alert follow-up / escalation configuration per level & role.
 *  GET /api/escalation-rules        (any logged-in user can view the ladder)
 *  PUT /api/escalation-rules/{id}   (Admin only) - validates windows don't overlap.
 */
@RestController
@RequestMapping("/api/escalation-rules")
public class EscalationRuleController {

    private final EscalationRuleRepository repo;
    private final AuditService audit;

    public EscalationRuleController(EscalationRuleRepository repo, AuditService audit) {
        this.repo = repo; this.audit = audit;
    }

    @GetMapping
    public List<EscalationRule> list() { return repo.findAllByOrderByLevelOrderAsc(); }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public EscalationRule update(@PathVariable Long id, @Valid @RequestBody EscalationRuleRequest r) {
        EscalationRule rule = repo.findById(id).orElseThrow(() -> ApiException.notFound("Rule"));
        if (r.endDay() < r.startDay()) throw ApiException.bad("End day must be on or after start day");
        Role role;
        try { role = Role.valueOf(r.notifyRole()); } catch (IllegalArgumentException e) { throw ApiException.bad("Unknown role"); }
        // Windows of other active levels must not overlap this one.
        for (EscalationRule other : repo.findAll()) {
            if (!other.getId().equals(id) && other.isActive() && r.active()
                    && r.startDay() <= other.getEndDay() && r.endDay() >= other.getStartDay()) {
                throw ApiException.bad("Day window overlaps with " + other.getLevel() + " level ("
                        + other.getStartDay() + "-" + other.getEndDay() + ")");
            }
        }
        rule.setNotifyRole(role);
        rule.setStartDay(r.startDay());
        rule.setEndDay(r.endDay());
        rule.setNotifyPortal(r.notifyPortal());
        rule.setNotifySms(r.notifySms());
        rule.setNotifyEmail(r.notifyEmail());
        rule.setNotifyCall(r.notifyCall());
        rule.setNotifyCallerSms(r.notifyCallerSms());
        rule.setReminderHours(r.reminderHours());
        rule.setSmsTemplate(r.smsTemplate());
        rule.setEmailTemplate(r.emailTemplate());
        rule.setActive(r.active());
        rule.setUpdatedBy(CurrentUser.username());
        rule.setUpdatedAt(LocalDateTime.now());
        repo.save(rule);
        audit.record("ESCALATION_RULE_UPDATE", "EscalationRule", rule.getLevel(),
                Map.of("window", r.startDay() + "-" + r.endDay(), "role", r.notifyRole()));
        return rule;
    }
}
