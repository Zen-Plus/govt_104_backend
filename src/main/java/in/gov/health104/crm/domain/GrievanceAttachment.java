package in.gov.health104.crm.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

/** Document attached to a grievance on the SIO screen ("Attached Docs"). Stored in PostgreSQL. */
@Entity
@Table(name = "grievance_attachment")
@Getter @Setter
public class GrievanceAttachment {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long grievanceId;
    private String fileName;
    private String contentType;
    private long sizeBytes;
    @Basic(fetch = FetchType.LAZY)
    private byte[] data;
    private String uploadedBy;
    private LocalDateTime uploadedAt = LocalDateTime.now();
}
