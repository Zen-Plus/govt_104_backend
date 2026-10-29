package in.gov.health104.crm.mongo;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.Instant;

/** Every outbound SMS / Email / voice call / portal alert, with delivery status ("notification_logs"). */
@Document("notification_logs")
@Getter @Setter
public class NotificationLog {
    @Id private String id;
    @Indexed private Instant timestamp = Instant.now();
    private String channel;       // SMS, EMAIL, CALL, PORTAL
    private String recipient;     // masked phone/email
    private String recipientRole;
    @Indexed private String grievanceNo;
    private String message;
    private String status;        // SENT, FAILED, SKIPPED_DISABLED
    private String providerResponse;
}
