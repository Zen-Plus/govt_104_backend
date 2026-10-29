package in.gov.health104.crm.mongo;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.Instant;

/** One line per REST request (method, path, status, latency) in "api_logs". No bodies are stored. */
@Document("api_logs")
@Getter @Setter
public class ApiLog {
    @Id private String id;
    @Indexed private Instant timestamp = Instant.now();
    private String method;
    private String path;
    private int status;
    private long durationMs;
    private String username;
    private String ipAddress;
}
