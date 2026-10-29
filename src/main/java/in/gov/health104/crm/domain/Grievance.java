package in.gov.health104.crm.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

/**
 * A grievance registered through the 104 helpline (Pathway 1).
 * The escalation engine moves it FACILITY -> DISTRICT -> STATE -> MINISTER
 * when it stays unresolved beyond each level's day window.
 * Rows are never physically deleted (soft delete via {@code deleted}).
 */
@Entity
@Table(name = "grievance")
@Getter @Setter
public class Grievance {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String grievanceNo;
    private Long callerId;
    private Long callId;
    private String patientName;
    private Integer patientAge;
    private String patientSex;
    private String patientAgeUnit = "YEARS";
    private boolean commonComplaint;
    private boolean pandemic;
    private String passTo = "SUPERVISOR";
    private String cmhoRemarks;
    private LocalDateTime forwardedAt;
    private String contactPhone;
    private String contactEmail;
    private String district;
    private String block;
    private String village;
    private String category;
    private String complaintAgainst;
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "authority_id")
    private PublicAuthority authority;
    private String description;
    private String priority = "NORMAL";
    private boolean epidemic;
    @Enumerated(EnumType.STRING)
    private EscalationLevel currentLevel = EscalationLevel.FACILITY;
    @Enumerated(EnumType.STRING)
    private GrievanceStatus status = GrievanceStatus.NEW;
    private String resolutionRemarks;
    private String supervisorRemarks;
    private String createdBy;
    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime updatedAt = LocalDateTime.now();
    private LocalDateTime levelSince = LocalDateTime.now();
    private LocalDateTime lastReminderAt;
    private LocalDateTime resolvedAt;
    private boolean deleted;
    @Version
    private Long version;

    @PreUpdate
    void touch() { this.updatedAt = LocalDateTime.now(); }
}
