package in.gov.health104.crm.repository;

import in.gov.health104.crm.domain.EscalationLevel;
import in.gov.health104.crm.domain.Grievance;
import in.gov.health104.crm.domain.GrievanceStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface GrievanceRepository extends JpaRepository<Grievance, Long> {

    Optional<Grievance> findByIdAndDeletedFalse(Long id);

    /** Filtered, paginated search for the grievance list page. Null filters are ignored. */
    @Query("""
        SELECT g FROM Grievance g
        WHERE g.deleted = false
          AND (:status IS NULL OR g.status = :status)
          AND (:level IS NULL OR g.currentLevel = :level)
          AND (:district IS NULL OR g.district = :district)
          AND g.createdAt >= :from AND g.createdAt < :to
          AND (
              :q IS NULL OR
              LOWER(g.grievanceNo) LIKE LOWER(:pattern) OR
              LOWER(g.patientName) LIKE LOWER(:pattern) OR
              LOWER(g.contactPhone) LIKE LOWER(:pattern)
          )
        ORDER BY g.createdAt DESC
        """)
    Page<Grievance> search(@Param("status") GrievanceStatus status,
                           @Param("level") EscalationLevel level,
                           @Param("district") String district,
                           @Param("q") String q,
                           @Param("pattern") String pattern,
                           @Param("from") java.time.LocalDateTime from,
                           @Param("to") java.time.LocalDateTime to,
                           Pageable pageable);

    /** Open grievances the escalation engine must evaluate. */
    List<Grievance> findByDeletedFalseAndStatusIn(Collection<GrievanceStatus> statuses);

    long countByDeletedFalseAndStatus(GrievanceStatus status);
    long countByDeletedFalseAndCurrentLevelAndStatusIn(EscalationLevel level, Collection<GrievanceStatus> s);

    @Query(value = "SELECT nextval('grievance_no_seq')", nativeQuery = true)
    long nextSequence();
}
