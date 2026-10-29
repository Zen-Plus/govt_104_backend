package in.gov.health104.crm.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Agent console payloads. Flow (matches the UX deck):
 *   1. POST /calls/start            call lands  -> Call ID, Phone, Call Date
 *   2. POST /calls/{id}/caller      "Generate"  -> CallerID (caller registration)
 *   3. POST /patients               "Generate ID" -> Patient ID (patient registration)
 *   4. POST /calls/{id}/complete    call type screen -> Close / Register Complaint / Transfer
 */
public final class CallDtos {
    private CallDtos() {}

    public static final String CALL_TYPES = "^(MEDICAL_ADVICE|COUNSELLING|COMPLAINTS|GENERAL_INFORMATION|INVALID|TRANSFER)$";

    public record StartCallRequest(@NotBlank @Pattern(regexp = "^\\d{3,15}$", message = "Invalid phone") String phone) {}

    public record StartCallResponse(Long callId, String callRef, String phone, LocalDateTime callDate,
                                    List<?> previousCallers) {}

    public record CallerInfo(
            Long id,
            @NotBlank(message = "Caller name is required") @Size(max = 120) String name,
            @Min(0) @Max(120) Integer age,
            @Pattern(regexp = "^(Male|Female|Other)?$") String sex,
            @Pattern(regexp = "^(\\d{10})?$", message = "Enter a valid 10-digit mobile number") String phone,
            @Email @Size(max = 120) String email,
            @Size(max = 60) String district, @Size(max = 60) String block,
            @Size(max = 80) String village, @Size(max = 300) String address) {}

    public record PatientRequest(
            Long callerId,
            @NotBlank(message = "Patient name is required") @Size(max = 120) String name,
            @Min(0) @Max(150) Integer age,
            @Pattern(regexp = "^(YEARS|MONTHS|DAYS)?$") String ageUnit,
            @Pattern(regexp = "^(Male|Female|Other)?$") String gender) {}

    /**
     * Completes the call from the call-type screen.
     * action: CLOSE (close call), TRANSFER (to MEDICAL_OFFICER / COUNSELLOR / EMS / 102 / 108 / 112 / 1099),
     *         COMPLAINT (register grievance, then close).
     */
    public record CompleteCallRequest(
            @NotBlank @Pattern(regexp = CALL_TYPES) String callType,
            @Size(max = 60) String subCallType,
            Long patientId,
            @NotBlank @Pattern(regexp = "^(CLOSE|TRANSFER|COMPLAINT)$") String action,
            @Pattern(regexp = "^(MEDICAL_OFFICER|COUNSELLOR|EMS|102|108|112|1099)?$") String transferTo,
            @Pattern(regexp = "^(BLIND|CONFERENCE)?$") String transferMode,
            @Size(max = 40) String closeType,
            @Pattern(regexp = "^(YES|NO|NOT_GIVEN)?$") String cdiSatisfied,
            @Size(max = 300) String cdiReason,
            @Size(max = 60) String specialityGroup,
            @Size(max = 4000) String queryText,
            @Size(max = 4000) String adviceGiven,
            LocalDateTime followUpAt,
            @Size(max = 30) Map<String, Object> details,
            @Valid GrievanceDtos.GrievanceRequest grievance) {}

    public record CallResponse(Long callId, String callRef, Long callerId, String grievanceNo, String status) {}

    /** Doctor / counsellor closing a transferred call (MO & Counsellor screens). */
    public record CloseCallRequest(
            @Size(max = 4000) String adviceGiven,
            @Size(max = 40) String closeType,
            @Pattern(regexp = "^(YES|NO|NOT_GIVEN)?$") String cdiSatisfied,
            @Size(max = 300) String cdiReason,
            LocalDateTime followUpAt,
            @Pattern(regexp = "^(EMS)?$") String transferTo,
            @Size(max = 30) Map<String, Object> details) {}

    public record TransferRequest(@NotBlank @Size(max = 30) String target,
                                  @Pattern(regexp = "^(BLIND|CONFERENCE)$") String mode) {}

    public record AmbulanceRequest(@NotBlank String patientName, @NotBlank String phone,
                                   @NotBlank String location, Double latitude, Double longitude,
                                   @Size(max = 500) String condition) {}
}
