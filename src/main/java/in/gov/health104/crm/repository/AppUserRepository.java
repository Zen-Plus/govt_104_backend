package in.gov.health104.crm.repository;

import in.gov.health104.crm.domain.AppUser;
import in.gov.health104.crm.domain.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

/** Data access for users. Spring Data generates parameterised SQL (no SQL injection). */
public interface AppUserRepository extends JpaRepository<AppUser, Long> {
    Optional<AppUser> findByUsername(String username);
    List<AppUser> findByRoleAndActiveTrue(Role role);
    List<AppUser> findByRoleAndDistrictAndActiveTrue(Role role, String district);
    List<AppUser> findAllByOrderByRoleAscUsernameAsc();
    boolean existsByUsernameIgnoreCase(String username);
    boolean existsByExtensionNoAndActiveTrueAndIdNot(String extensionNo, Long id);
}
