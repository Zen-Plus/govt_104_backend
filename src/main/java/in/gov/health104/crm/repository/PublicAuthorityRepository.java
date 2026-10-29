package in.gov.health104.crm.repository;

import in.gov.health104.crm.domain.EscalationLevel;
import in.gov.health104.crm.domain.PublicAuthority;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface PublicAuthorityRepository extends JpaRepository<PublicAuthority, Long> {
    List<PublicAuthority> findByActiveTrueOrderByName();
    List<PublicAuthority> findByLevelAndDistrictAndActiveTrue(EscalationLevel level, String district);
    List<PublicAuthority> findByLevelAndActiveTrue(EscalationLevel level);
}
