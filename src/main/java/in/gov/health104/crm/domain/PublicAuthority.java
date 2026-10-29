package in.gov.health104.crm.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** The office/officer responsible for redressing a grievance at a given level. */
@Entity
@Table(name = "public_authority")
@Getter @Setter
public class PublicAuthority {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String name;
    @Enumerated(EnumType.STRING)
    private EscalationLevel level;
    private String district;
    private String officerName;
    private String officerDesignation;
    private String officerPhone;
    private String officerEmail;
    private Long officerUserId;
    private boolean active = true;
}
