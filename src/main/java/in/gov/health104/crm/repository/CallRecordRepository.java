package in.gov.health104.crm.repository;

import in.gov.health104.crm.domain.CallPathway;
import in.gov.health104.crm.domain.CallRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;

public interface CallRecordRepository extends JpaRepository<CallRecord, Long> {
    long countByCreatedAtAfter(LocalDateTime t);
    long countByPathwayAndCreatedAtAfter(CallPathway p, LocalDateTime t);
    Page<CallRecord> findByTransferredToAndStatus(String transferredTo, String status, Pageable p);
    Page<CallRecord> findAllByOrderByCreatedAtDesc(Pageable p);

    /** Follow-ups: calls with a follow-up date not yet completed, for the given agent. */
    @org.springframework.data.jpa.repository.Query("SELECT c FROM CallRecord c WHERE (c.agentId = :userId OR c.handledById = :userId) "
            + "AND c.followUpDone = false AND c.followUpAt IS NOT NULL ORDER BY c.followUpAt")
    java.util.List<CallRecord> openFollowUps(@org.springframework.data.repository.query.Param("userId") Long userId);

    /** Most recent completed calls of the same caller - "Last Advice Given" panel on the MO screen. */
    java.util.List<CallRecord> findTop5ByCaller_IdAndIdNotOrderByCreatedAtDesc(Long callerId, Long excludeId);
}
