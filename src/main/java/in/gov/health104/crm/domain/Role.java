package in.gov.health104.crm.domain;

/** All system roles. Used by Spring Security (as ROLE_xxx) and by escalation rules. */
public enum Role {
    ADMIN,              // system configuration, integrations, users
    AGENT,              // call-centre operator (first point of contact)
    MEDICAL_OFFICER,    // doctor receiving transferred clinical calls
    COUNSELLOR,         // well-being / counselling calls
    SUPERVISOR,         // quality check, forwards/updates grievances
    FACILITY_OFFICER,   // level 1 redressal (day 1-7)
    DISTRICT_OFFICER,   // level 2 redressal (day 8-14), e.g. CMHO
    STATE_OFFICER,      // level 3 redressal (day 15-21)
    MINISTER_OFFICE     // level 4 redressal (day 22-28)
}
