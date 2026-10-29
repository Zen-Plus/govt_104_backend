package in.gov.health104.crm.config;

import in.gov.health104.crm.domain.AppUser;
import in.gov.health104.crm.domain.Role;
import in.gov.health104.crm.repository.AppUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * On first start (empty user table) creates one demo user per role so the system can be explored.
 * Password comes from env SEED_PASSWORD. CHANGE / DISABLE these accounts before go-live
 * (set SEED_USERS=false in production).
 */
@Component
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);
    private final AppUserRepository users;
    private final PasswordEncoder encoder;
    @Value("${SEED_USERS:true}") private boolean seed;
    @Value("${SEED_PASSWORD:Crm@104Demo}") private String seedPassword;

    public DataSeeder(AppUserRepository users, PasswordEncoder encoder) {
        this.users = users; this.encoder = encoder;
    }

    @Override
    public void run(String... args) {
        if (!seed || users.count() > 0) return;
        create("admin", "System Administrator", Role.ADMIN, null, null);
        create("agent01", "Arjun Y (Agent)", Role.AGENT, "Raipur", "1010");
        create("doctor01", "Dr. Meera (Medical Officer)", Role.MEDICAL_OFFICER, "Raipur", "2001");
        create("counsel01", "Kavita (Counsellor)", Role.COUNSELLOR, "Raipur", "3001");
        create("supervisor01", "Supervisor 01", Role.SUPERVISOR, "Raipur", null);
        create("facility01", "Civil Surgeon Balod", Role.FACILITY_OFFICER, "Balod", null);
        create("cmho01", "CMHO Raipur", Role.DISTRICT_OFFICER, "Raipur", null);
        create("state01", "Director Health Services", Role.STATE_OFFICER, null, null);
        create("minister01", "Minister Office OSD", Role.MINISTER_OFFICE, null, null);
        log.warn("Demo users created. Change passwords / set SEED_USERS=false before production!");
    }

    private void create(String u, String name, Role r, String district, String ext) {
        AppUser a = new AppUser();
        a.setUsername(u); a.setFullName(name); a.setRole(r);
        a.setDistrict(district); a.setExtensionNo(ext);
        a.setPasswordHash(encoder.encode(seedPassword));
        users.save(a);
    }
}
