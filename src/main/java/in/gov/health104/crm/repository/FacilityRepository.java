package in.gov.health104.crm.repository;

import in.gov.health104.crm.domain.Facility;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface FacilityRepository extends JpaRepository<Facility, Long> {
    @Query("""
        SELECT f FROM Facility f WHERE f.active = true
          AND (:type IS NULL OR f.type = :type)
          AND (:district IS NULL OR f.district = :district)
          AND (:q IS NULL OR LOWER(f.name) LIKE LOWER(CONCAT('%', :q, '%')))
        ORDER BY f.name
        """)
    List<Facility> search(@Param("type") String type, @Param("district") String district, @Param("q") String q);
}
