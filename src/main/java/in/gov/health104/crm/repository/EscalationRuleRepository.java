package in.gov.health104.crm.repository;

import in.gov.health104.crm.domain.EscalationLevel;
import in.gov.health104.crm.domain.EscalationRule;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface EscalationRuleRepository extends JpaRepository<EscalationRule, Long> {
    List<EscalationRule> findAllByOrderByLevelOrderAsc();
    Optional<EscalationRule> findByLevel(EscalationLevel level);
}
