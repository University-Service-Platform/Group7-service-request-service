package com.usm.servicerequest.dto;

import com.usm.servicerequest.domain.RequestCategory;
import com.usm.servicerequest.domain.RequestPriority;
import com.usm.servicerequest.domain.RequestStatus;
import com.usm.servicerequest.domain.ServiceRequest;

import java.time.Instant;

/** Full detail representation - GET .../{id} and GET .../ list both return this shape (guide §4.1). */
public record ServiceRequestResponse(
        String requestId,
        String requesterId,
        RequestCategory category,
        String location,
        RequestPriority priority,
        String description,
        String attachmentReference,
        RequestStatus status,
        String responsibleServiceUnit,
        String rejectionReason,
        String confirmationFeedback,
        Instant reportedTime,
        Instant acknowledgedTime,
        Instant assignedTime,
        Instant resolvedTime,
        Instant closedTime
) {
    public static ServiceRequestResponse from(ServiceRequest entity) {
        return new ServiceRequestResponse(
                entity.getRequestId(),
                entity.getRequesterId(),
                entity.getCategory(),
                entity.getLocation(),
                entity.getPriority(),
                entity.getDescription(),
                entity.getAttachmentReference(),
                entity.getStatus(),
                entity.getResponsibleServiceUnit(),
                entity.getRejectionReason(),
                entity.getConfirmationFeedback(),
                entity.getReportedTime(),
                entity.getAcknowledgedTime(),
                entity.getAssignedTime(),
                entity.getResolvedTime(),
                entity.getClosedTime()
        );
    }
}
