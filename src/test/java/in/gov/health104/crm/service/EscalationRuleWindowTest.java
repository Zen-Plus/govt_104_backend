package in.gov.health104.crm.service;

import in.gov.health104.crm.domain.EscalationLevel;
import in.gov.health104.crm.domain.EscalationRule;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Verifies the default 7-day escalation ladder picks the right level for each day. */
class EscalationRuleWindowTest {

    private static EscalationRule rule(EscalationLevel l, int order, int from, int to) {
        EscalationRule r = new EscalationRule();
        r.setLevel(l); r.setLevelOrder(order); r.setStartDay(from); r.setEndDay(to); r.setActive(true);
        return r;
    }

    private final List<EscalationRule> ladder = List.of(
            rule(EscalationLevel.FACILITY, 1, 1, 7),
            rule(EscalationLevel.DISTRICT, 2, 8, 14),
            rule(EscalationLevel.STATE, 3, 15, 21),
            rule(EscalationLevel.MINISTER, 4, 22, 28));

    @Test
    void picksLevelByDay() {
        assertEquals(EscalationLevel.FACILITY, EscalationService.ruleForDay(ladder, 1).getLevel());
        assertEquals(EscalationLevel.FACILITY, EscalationService.ruleForDay(ladder, 7).getLevel());
        assertEquals(EscalationLevel.DISTRICT, EscalationService.ruleForDay(ladder, 8).getLevel());
        assertEquals(EscalationLevel.STATE, EscalationService.ruleForDay(ladder, 21).getLevel());
        assertEquals(EscalationLevel.MINISTER, EscalationService.ruleForDay(ladder, 22).getLevel());
    }

    @Test
    void staysAtTopLevelAfterLastWindow() {
        assertEquals(EscalationLevel.MINISTER, EscalationService.ruleForDay(ladder, 40).getLevel());
    }

    @Test
    void templatePlaceholdersAreFilledAndEscaped() {
        String out = NotificationService.fill("ID {grievanceNo} by {callerName}",
                java.util.Map.of("grievanceNo", "GRV-1", "callerName", "<b>x</b>"));
        assertEquals("ID GRV-1 by &lt;b&gt;x&lt;/b&gt;", out);
    }
}
