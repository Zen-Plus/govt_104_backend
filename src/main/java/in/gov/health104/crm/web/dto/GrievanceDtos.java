package in.gov.health104.crm.web.dto;

import in.gov.health104.crm.domain.GrievanceHistory;
import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
import java.util.List;

/** Request/response bodies for grievance add / edit / delete / status update. */
public final class GrievanceDtos {
    private GrievanceDtos() {}

    /** Mobile number: Indian 10-digit starting 6-9. */
    public static final String MOBILE = "^[6-9]\\d{9}$";

    public record GrievanceRequest(
            @NotBlank(message = "Patient name is required") @Size(max = 120) String patientName,
            @Min(0) @Max(150) Integer patientAge,
            @Pattern(regexp = "^(Male|Female|Other)?$") String patientSex,
            @NotBlank(message = "Contact number is required") @Pattern(regexp = MOBILE, message = "Enter a valid 10-digit mobile number") String contactPhone,
            @Email(message = "Enter a valid email") @Size(max = 120) String contactEmail,
            @Size(max = 60) String district,
            @Size(max = 60) String block,
            @Size(max = 80) String village,
            @NotBlank(message = "Nature of complaint is required") @Size(max = 60) String category,
            @Size(max = 60) String complaintAgainst,
            Long authorityId,
            @NotBlank(message = "Description is required") @Size(max = 4000) String description,
            @Pattern(regexp = "^(LOW|NORMAL|HIGH|CRITICAL)?$") String priority,
            boolean epidemic,
            Long callerId,
            Long callId,
            @Pattern(regexp = "^(YEARS|MONTHS|DAYS)?$") String patientAgeUnit,
            boolean commonComplaint,
            boolean pandemic,
            @Pattern(regexp = "^(SUPERVISOR|FACILITY_OFFICER|DISTRICT_OFFICER)?$") String passTo) {}

    /**
     * Supervisor (SIO screen) actions: UPDATE remarks, FORWARD_CMHO, SMS / EMAIL (re-send alert), CLOSE.
     * District officer (CMHO) uses UPDATE with cmhoRemarks.
     */
    public record SupervisorActionRequest(
            @NotBlank @Pattern(regexp = "^(UPDATE|FORWARD_CMHO|SMS|EMAIL|CLOSE)$") String action,
            @Size(max = 4000) String supervisorRemarks,
            @Size(max = 4000) String cmhoRemarks) {}

    public record StatusUpdateRequest(
            @NotBlank @Pattern(regexp = "^(IN_PROGRESS|RESOLVED|CLOSED|REJECTED)$") String status,
            @NotBlank(message = "Remarks are required") @Size(max = 2000) String remarks) {}

    public record GrievanceResponse(
            Long id, String grievanceNo, String patientName, Integer patientAge, String patientSex,
            String contactPhone, String contactEmail, String district, String block, String village,
            String category, String complaintAgainst, Long authorityId, String authorityName,
            String officerName, String officerPhone, String description, String priority, boolean epidemic,
            String currentLevel, String status, String resolutionRemarks, String supervisorRemarks,
            String createdBy, LocalDateTime createdAt, LocalDateTime updatedAt, LocalDateTime levelSince,
            LocalDateTime resolvedAt, long ageDays, int daysLeftAtLevel, Long version,
            String patientAgeUnit, boolean commonComplaint, boolean pandemic, String passTo,
            String cmhoRemarks, LocalDateTime forwardedAt) {}

    public record GrievanceDetail(GrievanceResponse grievance, List<GrievanceHistory> history,
                                  List<?> notifications, List<?> attachments) {}
}
