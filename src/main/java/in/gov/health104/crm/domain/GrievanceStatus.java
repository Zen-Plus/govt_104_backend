package in.gov.health104.crm.domain;

/** Lifecycle of a grievance. */
public enum GrievanceStatus { NEW, IN_PROGRESS, ESCALATED, RESOLVED, CLOSED, REJECTED;
    /** Open = still subject to SLA timers and escalation. */
    public boolean isOpen() { return this == NEW || this == IN_PROGRESS || this == ESCALATED; }
}
