package in.gov.health104.crm.service;

/** Masks PII before it is written to logs (e.g. 98XXXXXX21, a***@gmail.com). */
public final class Masking {
    private Masking() {}
    public static String phone(String p) {
        if (p == null || p.length() < 4) return p;
        return p.substring(0, 2) + "X".repeat(p.length() - 4) + p.substring(p.length() - 2);
    }
    public static String email(String e) {
        if (e == null || !e.contains("@")) return e;
        int at = e.indexOf('@');
        return e.charAt(0) + "***" + e.substring(at);
    }
}
