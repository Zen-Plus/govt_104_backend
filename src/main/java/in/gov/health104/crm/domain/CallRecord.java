package in.gov.health104.crm.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * One inbound call. Created the moment the call lands ("Call ID"), then completed by the agent
 * with the call type, the screen-specific fields and a close type, or transferred to MO/Counsellor/EMS.
 * {@code details} (JSONB) keeps fields that differ per screen, e.g.
 *   Medical Advice: patientType, complaintProblem, notAvailableProblem, otherComplaint
 *   Counselling   : category, subCategory, complaint, typeOfCall, typeOfCounselling
 *   RCH           : anm, asha, completeInformation, relative
 *   Hospital/Blood: selectedFacilityIds
 *   Govt. Schemes : scheme, otherData
 *   Doctor        : prescription
 */
@Entity
@Table(name = "call_record")
@Getter @Setter
public class CallRecord {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String callRef;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "caller_id")
    private Caller caller;
    @Enumerated(EnumType.STRING)
    private CallPathway pathway = CallPathway.INFORMATION;
    private String callType;
    private String queryText;
    private String adviceGiven;
    private String algorithmCode;
    private String transferredTo;
    private String status = "OPEN";           // OPEN, TRANSFERRED, CLOSED
    private String phone;                     // incoming number (ANI)
    private Long patientId;
    private String subCallType;
    private String closeType;
    private String cdiSatisfied;              // YES / NO / NOT_GIVEN
    private String cdiReason;
    private String specialityGroup;
    private LocalDateTime followUpAt;
    private boolean followUpDone;
    private String transferMode;              // BLIND / CONFERENCE
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> details;
    private Long agentId;
    private Long handledById;
    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime closedAt;
}
