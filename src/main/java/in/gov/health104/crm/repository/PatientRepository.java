package in.gov.health104.crm.repository;

import in.gov.health104.crm.domain.Patient;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;

public interface PatientRepository extends JpaRepository<Patient, Long> {
    List<Patient> findByCallerIdOrderByCreatedAtDesc(Long callerId);
    Optional<Patient> findByPatientCode(String code);

    @Query(value = "SELECT nextval('patient_code_seq')", nativeQuery = true)
    long nextSequence();
}
