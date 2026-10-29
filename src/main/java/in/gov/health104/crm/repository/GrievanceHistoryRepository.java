package in.gov.health104.crm.repository;

import in.gov.health104.crm.domain.GrievanceHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface GrievanceHistoryRepository extends JpaRepository<GrievanceHistory, Long> {
    List<GrievanceHistory> findByGrievanceIdOrderByCreatedAtAsc(Long grievanceId);
}
