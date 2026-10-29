package in.gov.health104.crm.service;

import in.gov.health104.crm.domain.EscalationRule;
import in.gov.health104.crm.domain.Grievance;
import in.gov.health104.crm.domain.PublicAuthority;
import in.gov.health104.crm.integration.EmailGateway;
import in.gov.health104.crm.integration.HttpJsonClient;
import in.gov.health104.crm.integration.SmsGateway;
import in.gov.health104.crm.integration.TelecomGateway;
import in.gov.health104.crm.mongo.NotificationLog;
import in.gov.health104.crm.mongo.NotificationLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Sends all grievance alerts through the configured gateways and logs each attempt to MongoDB.
 * Methods are @Async so a slow SMS/SMTP provider never blocks the operator's screen.
 *
 * Template placeholders: {grievanceNo} {officerName} {officerPhone} {category} {daysLeft} {level} {callerName}
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    private final SmsGateway sms;
    private final EmailGateway email;
    private final TelecomGateway telecom;
    private final NotificationLogRepository logs;

    public NotificationService(SmsGateway sms, EmailGateway email, TelecomGateway telecom, NotificationLogRepository logs) {
        this.sms = sms; this.email = email; this.telecom = telecom; this.logs = logs;
    }

    /** Acknowledgement to the caller right after registration: grievance ID + redressing officer details. */
    @Async
    public void acknowledgeCaller(Grievance g) {
        PublicAuthority a = g.getAuthority();
        String officer = a == null ? "the concerned officer" : a.getOfficerName() + " (" + a.getOfficerDesignation() + ", " + a.getName() + ")";
        String text = "Your grievance is registered with 104. ID: " + g.getGrievanceNo()
                + ". Officer: " + officer + ". Track by calling 104.";
        smsAndLog(g, g.getContactPhone(), "CALLER", text);
        if (g.getContactEmail() != null && !g.getContactEmail().isBlank()) {
            emailAndLog(g, g.getContactEmail(), "CALLER", "104 Grievance Registered - " + g.getGrievanceNo(),
                    "<p>Dear " + esc(g.getPatientName()) + ",</p><p>Your grievance <b>" + g.getGrievanceNo()
                            + "</b> has been registered.</p><p>Redressing officer: " + esc(officer) + "</p><p>- 104 Helpline</p>");
        }
    }

    /** Alerts the officer who owns the grievance at the rule's level via the channels enabled on the rule. */
    @Async
    public void alertOfficer(Grievance g, EscalationRule rule, PublicAuthority officer, int daysLeft, boolean reminder) {
        Map<String, String> vars = vars(g, officer, daysLeft);
        String prefix = reminder ? "REMINDER: " : "";
        String role = rule.getNotifyRole().name();
        if (officer != null) {
            if (rule.isNotifySms() && officer.getOfficerPhone() != null)
                smsAndLog(g, officer.getOfficerPhone(), role, prefix + fill(rule.getSmsTemplate(), vars));
            if (rule.isNotifyEmail() && officer.getOfficerEmail() != null)
                emailAndLog(g, officer.getOfficerEmail(), role, prefix + "104 Grievance " + g.getGrievanceNo(), fill(rule.getEmailTemplate(), vars));
            if (rule.isNotifyCall() && officer.getOfficerPhone() != null) {
                HttpJsonClient.Result r = telecom.placeAlertCall(officer.getOfficerPhone(),
                        "This is 104 helpline. Grievance " + g.getGrievanceNo() + " requires your action.");
                save("CALL", Masking.phone(officer.getOfficerPhone()), role, g, "Voice alert", r);
            }
        }
        if (rule.isNotifyPortal()) {
            save("PORTAL", role, role, g, prefix + "Grievance in inbox at " + rule.getLevel() + " level",
                    new HttpJsonClient.Result(true, 200, "INBOX"));
        }
    }

    /** Tells the caller their grievance moved up a level. */
    @Async
    public void notifyCallerEscalated(Grievance g) {
        smsAndLog(g, g.getContactPhone(), "CALLER", "Your 104 grievance " + g.getGrievanceNo()
                + " has been escalated to " + g.getCurrentLevel() + " level for faster action.");
    }

    /** Status update to the caller when the grievance is resolved/closed. */
    @Async
    public void notifyCallerStatus(Grievance g) {
        smsAndLog(g, g.getContactPhone(), "CALLER", "Your 104 grievance " + g.getGrievanceNo()
                + " status: " + g.getStatus() + ". Remarks: " + trim(g.getResolutionRemarks(), 80));
    }

    // ----- helpers -----
    private void smsAndLog(Grievance g, String phone, String role, String text) {
        save("SMS", Masking.phone(phone), role, g, text, safe(() -> sms.send(phone, text)));
    }

    private void emailAndLog(Grievance g, String to, String role, String subject, String html) {
        save("EMAIL", Masking.email(to), role, g, subject, safe(() -> email.send(to, subject, html)));
    }

    private HttpJsonClient.Result safe(java.util.function.Supplier<HttpJsonClient.Result> s) {
        try { return s.get(); } catch (Exception e) { return new HttpJsonClient.Result(false, 0, e.getMessage()); }
    }

    private void save(String channel, String recipient, String role, Grievance g, String msg, HttpJsonClient.Result r) {
        try {
            NotificationLog l = new NotificationLog();
            l.setChannel(channel);
            l.setRecipient(recipient);
            l.setRecipientRole(role);
            l.setGrievanceNo(g.getGrievanceNo());
            l.setMessage(msg);
            l.setStatus("SKIPPED_DISABLED".equals(r.body()) ? "SKIPPED_DISABLED" : (r.ok() ? "SENT" : "FAILED"));
            l.setProviderResponse(r.body());
            logs.save(l);
        } catch (Exception e) {
            log.warn("Notification log write failed: {}", e.getMessage());
        }
    }

    private Map<String, String> vars(Grievance g, PublicAuthority a, int daysLeft) {
        return Map.of("grievanceNo", g.getGrievanceNo(),
                "officerName", a == null ? "Officer" : a.getOfficerName(),
                "officerPhone", a == null || a.getOfficerPhone() == null ? "" : a.getOfficerPhone(),
                "category", g.getCategory() == null ? "" : g.getCategory(),
                "daysLeft", String.valueOf(Math.max(daysLeft, 0)),
                "level", g.getCurrentLevel().name(),
                "callerName", g.getPatientName() == null ? "" : g.getPatientName());
    }

    static String fill(String template, Map<String, String> vars) {
        if (template == null) return "";
        String out = template;
        for (var e : vars.entrySet()) out = out.replace("{" + e.getKey() + "}", esc(e.getValue()));
        return out;
    }

    /** HTML-escape user-provided values placed into email bodies (prevents HTML injection). */
    static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private static String trim(String s, int n) { return s == null ? "" : (s.length() > n ? s.substring(0, n) + "..." : s); }
}
