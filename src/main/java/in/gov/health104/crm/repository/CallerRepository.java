package in.gov.health104.crm.repository;

import in.gov.health104.crm.domain.Caller;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface CallerRepository extends JpaRepository<Caller, Long> {
    /** Used to pre-fill demographics for repeat callers. */
    List<Caller> findTop5ByPhoneOrderByCreatedAtDesc(String phone);
}
