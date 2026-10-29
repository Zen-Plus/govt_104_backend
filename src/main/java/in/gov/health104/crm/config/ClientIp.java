package in.gov.health104.crm.config;

import jakarta.servlet.http.HttpServletRequest;

/** Resolves the client IP, honouring X-Forwarded-For set by the reverse proxy / load balancer. */
public final class ClientIp {
    private ClientIp() {}
    public static String of(HttpServletRequest req) {
        String xff = req.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) return xff.split(",")[0].trim();
        return req.getRemoteAddr();
    }
}
