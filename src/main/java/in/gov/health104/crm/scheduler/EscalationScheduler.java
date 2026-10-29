package in.gov.health104.crm.scheduler;

import in.gov.health104.crm.service.EscalationService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Triggers the escalation engine on the cron in app.escalation.cron (default: top of every hour, IST).
 * NOTE: when running more than one backend instance, add a distributed lock (e.g. ShedLock)
 * so only one node escalates at a time.
 */
@Component
public class EscalationScheduler {
    private final EscalationService service;

    public EscalationScheduler(EscalationService service) { this.service = service; }

    @Scheduled(cron = "${app.escalation.cron}", zone = "Asia/Kolkata")
    public void run() { service.runOnce(); }
}
