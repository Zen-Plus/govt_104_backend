package in.gov.health104.crm.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** Small helper to read the logged-in username/role anywhere in the service layer. */
public final class CurrentUser {
    private CurrentUser() {}

    public static String username() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        return a == null ? "system" : a.getName();
    }

    public static String role() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a == null || a.getAuthorities().isEmpty()) return "SYSTEM";
        return a.getAuthorities().iterator().next().getAuthority().replace("ROLE_", "");
    }
}
