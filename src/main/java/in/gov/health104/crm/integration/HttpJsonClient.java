package in.gov.health104.crm.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.gov.health104.crm.domain.IntegrationConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;

/**
 * Minimal JSON-over-HTTPS client shared by all gateway adapters.
 * Applies the per-integration timeout and retry count, and Basic auth when username/secret are set.
 * Replace the payload mapping in each adapter with the vendor's exact API contract during onboarding.
 */
@Component
public class HttpJsonClient {

    private static final Logger log = LoggerFactory.getLogger(HttpJsonClient.class);
    private final ObjectMapper mapper;

    public HttpJsonClient(ObjectMapper mapper) { this.mapper = mapper; }

    public record Result(boolean ok, int status, String body) {}

    public Result post(IntegrationConfig cfg, String secret, String path, Map<String, ?> payload) {
        return send(cfg, secret, "POST", path, payload);
    }

    public Result get(IntegrationConfig cfg, String secret, String path) {
        return send(cfg, secret, "GET", path, null);
    }

    private Result send(IntegrationConfig cfg, String secret, String method, String path, Map<String, ?> payload) {
        if (cfg.getBaseUrl() == null || cfg.getBaseUrl().isBlank()) return new Result(false, 0, "Base URL not configured");
        String url = cfg.getBaseUrl().replaceAll("/+$", "") + (path == null ? "" : path);
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(cfg.getTimeoutMs())).build();
        int attempts = Math.max(1, cfg.getRetryCount() + 1);
        String lastError = "";
        for (int i = 1; i <= attempts; i++) {
            try {
                HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url))
                        .timeout(Duration.ofMillis(cfg.getTimeoutMs()))
                        .header("Content-Type", "application/json")
                        .header("Accept", "application/json");
                if (cfg.getUsername() != null && secret != null) {
                    String basic = Base64.getEncoder().encodeToString((cfg.getUsername() + ":" + secret).getBytes(StandardCharsets.UTF_8));
                    b.header("Authorization", "Basic " + basic);
                }
                if ("POST".equals(method)) b.POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload)));
                else b.GET();
                HttpResponse<String> r = client.send(b.build(), HttpResponse.BodyHandlers.ofString());
                boolean ok = r.statusCode() >= 200 && r.statusCode() < 300;
                if (ok || r.statusCode() < 500) return new Result(ok, r.statusCode(), truncate(r.body()));
                lastError = "HTTP " + r.statusCode();
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return new Result(false, 0, "Interrupted");
            } catch (Exception e) {
                lastError = e.getClass().getSimpleName() + ": " + e.getMessage();
            }
            log.warn("{} {} attempt {}/{} failed: {}", cfg.getCode(), method, i, attempts, lastError);
        }
        return new Result(false, 0, lastError);
    }

    private static String truncate(String s) { return s == null ? "" : (s.length() > 500 ? s.substring(0, 500) : s); }
}
