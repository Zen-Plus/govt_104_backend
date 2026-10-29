package in.gov.health104.crm.repository;

import in.gov.health104.crm.domain.IntegrationCode;
import in.gov.health104.crm.domain.IntegrationConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface IntegrationConfigRepository extends JpaRepository<IntegrationConfig, Long> {
    Optional<IntegrationConfig> findByCode(IntegrationCode code);
}
