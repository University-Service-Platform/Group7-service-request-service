package com.usm.servicerequest.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;


@Entity
@Table(name = "service_request")
public class ServiceRequest {

    @Id
    @Column(name = "request_id", length = 20, nullable = false, updatable = false)
    private String requestId;

    @Column(name = "requester_id", length = 64, nullable = false, updatable = false)
    private String requesterId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "category", length = 20, nullable = false)
    private RequestCategory category;

    @Column(name = "location", length = 255, nullable = false)
    private String location;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "priority", length = 20, nullable = false)
    private RequestPriority priority;

    @Column(name = "description", nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(name = "attachment_reference", length = 500)
    private String attachmentReference;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "status", length = 20, nullable = false)
    private RequestStatus status;

    @Column(name = "responsible_service_unit", length = 100)
    private String responsibleServiceUnit;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    @Column(name = "confirmation_feedback", length = 500)
    private String confirmationFeedback;

    @Column(name = "reported_time", nullable = false)
    private Instant reportedTime;

    @Column(name = "acknowledged_time")
    private Instant acknowledgedTime;

    @Column(name = "assigned_time")
    private Instant assignedTime;

    @Column(name = "resolved_time")
    private Instant resolvedTime;

    @Column(name = "closed_time")
    private Instant closedTime;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    protected ServiceRequest() {
        // JPA
    }

    public ServiceRequest(String requestId, String requesterId, RequestCategory category, String location,
                           RequestPriority priority, String description, String attachmentReference,
                           Instant reportedTime) {
        this.requestId = requestId;
        this.requesterId = requesterId;
        this.category = category;
        this.location = location;
        this.priority = priority;
        this.description = description;
        this.attachmentReference = attachmentReference;
        this.status = RequestStatus.NEW;
        this.reportedTime = reportedTime;
    }

    // --- getters ---

    public String getRequestId() {
        return requestId;
    }

    public String getRequesterId() {
        return requesterId;
    }

    public RequestCategory getCategory() {
        return category;
    }

    public String getLocation() {
        return location;
    }

    public RequestPriority getPriority() {
        return priority;
    }

    public String getDescription() {
        return description;
    }

    public String getAttachmentReference() {
        return attachmentReference;
    }

    public RequestStatus getStatus() {
        return status;
    }

    public String getResponsibleServiceUnit() {
        return responsibleServiceUnit;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public String getConfirmationFeedback() {
        return confirmationFeedback;
    }

    public Instant getReportedTime() {
        return reportedTime;
    }

    public Instant getAcknowledgedTime() {
        return acknowledgedTime;
    }

    public Instant getAssignedTime() {
        return assignedTime;
    }

    public Instant getResolvedTime() {
        return resolvedTime;
    }

    public Instant getClosedTime() {
        return closedTime;
    }

    public Long getVersion() {
        return version;
    }

    // --- mutators used by the service layer (BR-09: status + timestamp always change together) ---

    public void setCategory(RequestCategory category) {
        this.category = category;
    }

    public void setPriority(RequestPriority priority) {
        this.priority = priority;
    }

    public void setResponsibleServiceUnit(String responsibleServiceUnit) {
        this.responsibleServiceUnit = responsibleServiceUnit;
    }

    public void acknowledge(Instant when) {
        this.status = RequestStatus.ACKNOWLEDGED;
        this.acknowledgedTime = when;
    }

    /** Called back by work-order-service once a work order has been created for this request (BR-06/BR-09). */
    public void markAssigned(Instant when) {
        this.status = RequestStatus.ASSIGNED;
        this.assignedTime = when;
    }

    public void markInProgress() {
        this.status = RequestStatus.IN_PROGRESS;
    }

    /** Called back by work-order-service once the work order is resolved (BR-08/BR-09). */
    public void markResolved(Instant when) {
        this.status = RequestStatus.RESOLVED;
        this.resolvedTime = when;
    }

    public void reject(String reason, Instant when) {
        this.status = RequestStatus.REJECTED;
        this.rejectionReason = reason;
        this.closedTime = when;
    }

    public void escalate(String newResponsibleServiceUnit, Instant when) {
        this.status = RequestStatus.ESCALATED;
        this.responsibleServiceUnit = newResponsibleServiceUnit;
        this.assignedTime = this.assignedTime == null ? when : this.assignedTime;
    }

    public void confirmAndClose(String feedback, Instant when) {
        this.status = RequestStatus.CLOSED;
        this.confirmationFeedback = feedback;
        this.closedTime = when;
    }
}
