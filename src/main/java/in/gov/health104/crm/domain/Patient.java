package in.gov.health104.crm.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

/**
 * Patient registered during a call ("Registration Details" panel -> Generate ID).
 * A caller may call on behalf of several patients (e.g. mother calling for a child).
 */
@Entity
@Table(name = "patient")
@Getter @Setter
public class Patient {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String patientCode;
    private Long callerId;
    private String name;
    private Integer age;
    private String ageUnit = "YEARS";
    private String gender;
    private LocalDateTime createdAt = LocalDateTime.now();
}
