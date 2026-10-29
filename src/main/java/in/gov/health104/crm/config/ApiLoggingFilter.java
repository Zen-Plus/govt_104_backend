package in.gov.health104.crm.config;

import in.gov.health104.crm.mongo.ApiLog;
import in.gov.health104.crm.mongo.ApiLogRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Writes one ApiLog document to MongoDB per /api request (method, path, status, latency, user, IP).
 * Request/response bodies are deliberately NOT logged (they contain patient PII).
 * A Mongo outage must never break the API, so failures are only written to the app log.
 */
@Component
public class ApiLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(ApiLoggingFilter.class);
    private final ApiLogRepository repo;

    public ApiLoggingFilter(ApiLogRepository repo) { this.repo = repo; }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        long start = System.currentTimeMillis();
        try {
            chain.doFilter(req, res);
        } finally {
            try {
                ApiLog l = new ApiLog();
                l.setMethod(req.getMethod());
                l.setPath(req.getRequestURI());
                l.setStatus(res.getStatus());
                l.setDurationMs(System.currentTimeMillis() - start);
                var auth = SecurityContextHolder.getContext().getAuthentication();
                l.setUsername(auth == null ? "anonymous" : auth.getName());
                l.setIpAddress(ClientIp.of(req));
                repo.save(l);
            } catch (Exception e) {
                log.warn("Could not write API log to MongoDB: {}", e.getMessage());
            }
        }
    }
}
