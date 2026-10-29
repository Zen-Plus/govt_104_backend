package in.gov.health104.crm.integration;

import in.gov.health104.crm.domain.IntegrationCode;
import in.gov.health104.crm.domain.IntegrationConfig;
import jakarta.mail.internet.MimeMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Properties;

/**
 * SMTP email adapter. The mail sender is built from the DB configuration on each send so that
 * changes on the "Email Gateway" admin page apply immediately without a restart.
 */
@Component
public class EmailGateway {
    private final IntegrationService integrations;

    public EmailGateway(IntegrationService integrations) { this.integrations = integrations; }

    public HttpJsonClient.Result send(String to, String subject, String html) {
        IntegrationConfig c = integrations.entity(IntegrationCode.EMAIL);
        if (!c.isEnabled()) return new HttpJsonClient.Result(false, 0, "SKIPPED_DISABLED");
        Map<String, String> s = integrations.settings(c);
        try {
            JavaMailSenderImpl sender = sender(c, s);
            MimeMessage msg = sender.createMimeMessage();
            MimeMessageHelper h = new MimeMessageHelper(msg, "UTF-8");
            h.setFrom(s.getOrDefault("fromAddress", "noreply@localhost"), s.getOrDefault("fromName", "104 Helpline"));
            h.setTo(to);
            h.setSubject(subject);
            h.setText(html, true);
            sender.send(msg);
            return new HttpJsonClient.Result(true, 250, "SENT");
        } catch (Exception e) {
            return new HttpJsonClient.Result(false, 0, e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    /** Connection test used by the admin page. */
    public HttpJsonClient.Result test() {
        IntegrationConfig c = integrations.entity(IntegrationCode.EMAIL);
        try {
            sender(c, integrations.settings(c)).testConnection();
            return new HttpJsonClient.Result(true, 250, "SMTP connection OK");
        } catch (Exception e) {
            return new HttpJsonClient.Result(false, 0, "SMTP failed: " + e.getMessage());
        }
    }

    private JavaMailSenderImpl sender(IntegrationConfig c, Map<String, String> s) {
        JavaMailSenderImpl m = new JavaMailSenderImpl();
        m.setHost(s.getOrDefault("smtpHost", ""));
        m.setPort(Integer.parseInt(s.getOrDefault("smtpPort", "587")));
        m.setUsername(c.getUsername());
        m.setPassword(integrations.secret(c));
        Properties p = m.getJavaMailProperties();
        p.put("mail.smtp.auth", String.valueOf(c.getUsername() != null && !c.getUsername().isBlank()));
        p.put("mail.smtp.starttls.enable", s.getOrDefault("tls", "true"));
        p.put("mail.smtp.connectiontimeout", String.valueOf(c.getTimeoutMs()));
        p.put("mail.smtp.timeout", String.valueOf(c.getTimeoutMs()));
        return m;
    }
}
