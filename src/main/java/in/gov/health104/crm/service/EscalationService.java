package in.gov.health104.crm.service;

import in.gov.health104.crm.domain.*;
import in.gov.health104.crm.repository.EscalationRuleRepository;
import in.gov.health104.crm.repository.GrievanceHistoryRepository;
import in.gov.health104.crm.repository.GrievanceRepository;
import in.gov.health104.crm.repository.PublicAuthorityRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

/**
 * THE ESCALATION ENGINE.
 *
 * For every open grievance:
 *   dayNo = days since registration + 1  (registration day = day 1)
 *   targetRule = active rule whose [startDay..endDay] contains dayNo
 *                (after the last window, the highest level is kept)
 *   if targetRule.level is higher than current level  -> ESCALATE
 *        - move grievance, reassign to that level's authority, alert via rule channels,
 *          optionally SMS the caller
 *   else if last alert older than rule.reminderHours   -> REMINDER to the current owner
 *
 * Default ladder (editable on "Escalation & Alerts" page):
 *   FACILITY 1-7, DISTRICT 8-14, STATE 15-21, MINISTER 22-28.
 */
@Service
public class EscalationService {

    private static final Logger log = LoggerFactory.getLogger(EscalationService.class);

    private final GrievanceRepository grievances;
    private final GrievanceHistoryRepository history;
    private final EscalationRuleRepository rules;
    private final PublicAuthorityRepository authorities;
    private final NotificationService notify;

    public EscalationService(GrievanceRepository grievances, GrievanceHistoryRepository history,
                             EscalationRuleRepository rules, PublicAuthorityRepository authorities,
                             NotificationService notify) {
        this.grievances = grievances; this.history = history; this.rules = rules;
        this.authorities = authorities; this.notify = notify;
    }

    /** @return number of grievances escalated in this run. */
    @Transactional
    public int runOnce() {
        List<EscalationRule> active = rules.findAllByOrderByLevelOrderAsc().stream().filter(EscalationRule::isActive).toList();
        if (active.isEmpty()) return 0;
        int escalated = 0;
        LocalDateTime now = LocalDateTime.now();
        for (Grievance g : grievances.findByDeletedFalseAndStatusIn(
                EnumSet.of(GrievanceStatus.NEW, GrievanceStatus.IN_PROGRESS, GrievanceStatus.ESCALATED))) {
            try {
                int dayNo = (int) Duration.between(g.getCreatedAt(), now).toDays() + 1;
                EscalationRule target = ruleForDay(active, dayNo);
                EscalationRule current = active.stream().filter(r -> r.getLevel() == g.getCurrentLevel()).findFirst().orElse(target);

                if (target.getLevelOrder() > current.getLevelOrder()) {
                    escalate(g, target, dayNo);
                    escalated++;
                } else if (dueForReminder(g, current, now)) {
                    g.setLastReminderAt(now);
                    grievances.save(g);
                    notify.alertOfficer(g, current, g.getAuthority(), current.getEndDay() - dayNo + 1, true);
                }
            } catch (Exception e) {
                log.error("Escalation failed for {}: {}", g.getGrievanceNo(), e.getMessage());
            }
        }
        if (escalated > 0) log.info("Escalation run: {} grievance(s) escalated", escalated);
        return escalated;
    }

    private void escalate(Grievance g, EscalationRule target, int dayNo) {
        EscalationLevel from = g.getCurrentLevel();
        g.setCurrentLevel(target.getLevel());
        g.setStatus(GrievanceStatus.ESCALATED);
        g.setLevelSince(LocalDateTime.now());
        g.setLastReminderAt(LocalDateTime.now());
        authorityFor(target.getLevel(), g.getDistrict()).ifPresent(g::setAuthority);
        grievances.save(g);
        history.save(new GrievanceHistory(g.getId(), "ESCALATED", from.name(), GrievanceStatus.ESCALATED.name(),
                target.getLevel().name(), "Auto-escalated on day " + dayNo + " (no action at " + from + " level)", "system"));
        notify.alertOfficer(g, target, g.getAuthority(), target.getEndDay() - dayNo + 1, false);
        if (target.isNotifyCallerSms()) notify.notifyCallerEscalated(g);
    }

    static EscalationRule ruleForDay(List<EscalationRule> active, int dayNo) {
        return active.stream()
                .filter(r -> dayNo >= r.getStartDay() && dayNo <= r.getEndDay())
                .findFirst()
                .orElseGet(() -> dayNo < active.get(0).getStartDay() ? active.get(0)
                        : active.stream().max(Comparator.comparingInt(EscalationRule::getLevelOrder)).orElseThrow());
    }

    private boolean dueForReminder(Grievance g, EscalationRule rule, LocalDateTime now) {
        LocalDateTime last = g.getLastReminderAt() != null ? g.getLastReminderAt() : g.getLevelSince();
        return Duration.between(last, now).toHours() >= rule.getReminderHours();
    }

    /** District-level offices are chosen by the grievance's district; state/minister are single offices. */
    private Optional<PublicAuthority> authorityFor(EscalationLevel level, String district) {
        if (district != null && (level == EscalationLevel.DISTRICT || level == EscalationLevel.FACILITY)) {
            var local = authorities.findByLevelAndDistrictAndActiveTrue(level, district);
            if (!local.isEmpty()) return Optional.of(local.get(0));
        }
        return authorities.findByLevelAndActiveTrue(level).stream().findFirst();
    }
}
