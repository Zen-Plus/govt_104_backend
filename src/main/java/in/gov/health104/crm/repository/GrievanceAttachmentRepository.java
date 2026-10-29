package in.gov.health104.crm.repository;

import in.gov.health104.crm.domain.GrievanceAttachment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface GrievanceAttachmentRepository extends JpaRepository<GrievanceAttachment, Long> {
    /** Metadata only (no file bytes) for listing. */
    @Query("SELECT new map(a.id as id, a.fileName as fileName, a.contentType as contentType, a.sizeBytes as sizeBytes, a.uploadedBy as uploadedBy, a.uploadedAt as uploadedAt) FROM GrievanceAttachment a WHERE a.grievanceId = :gid ORDER BY a.uploadedAt")
    List<java.util.Map<String, Object>> listMeta(@Param("gid") Long grievanceId);
}
