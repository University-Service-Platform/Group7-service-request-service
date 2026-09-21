package com.usm.servicerequest.controller;

import com.usm.servicerequest.domain.RequestCategory;
import com.usm.servicerequest.domain.RequestStatus;
import com.usm.servicerequest.dto.ConfirmRequest;
import com.usm.servicerequest.dto.CreateServiceRequestRequest;
import com.usm.servicerequest.dto.EscalateRequest;
import com.usm.servicerequest.dto.InternalStatusUpdateRequest;
import com.usm.servicerequest.dto.RejectRequest;
import com.usm.servicerequest.dto.ServiceRequestResponse;
import com.usm.servicerequest.dto.SummaryResponse;
import com.usm.servicerequest.dto.TriageRequest;
import com.usm.servicerequest.security.AuthContextHolder;
import com.usm.servicerequest.service.ServiceRequestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Backend Dev 1 surface (guide §2/§4.1). The internal `/status` callback at
 * the bottom of this class is service-to-service only - see its own javadoc
 * and the README's "Service-to-service auth" section for why it stays on
 * this same base path instead of a separate controller.
 */
@RestController
@RequestMapping("/api/service-requests")
@Tag(name = "Service Requests", description = "Submit, triage, reject/escalate and confirm service requests.")
public class ServiceRequestController {

    private final ServiceRequestService service;

    public ServiceRequestController(ServiceRequestService service) {
        this.service = service;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('STUDENT','ACADEMIC_STAFF','ADMIN_STAFF')")
    @Operation(summary = "Submit a new service request (US-01, FR-01, API-01)")
    public ResponseEntity<ServiceRequestResponse> create(@Valid @RequestBody CreateServiceRequestRequest request) {
        ServiceRequestResponse created = service.create(request, AuthContextHolder.require());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping
    @Operation(summary = "List service requests - own requests for a requester, full queue for Service Desk "
            + "(US-02/03, FR-02)")
    public List<ServiceRequestResponse> list(
            @RequestParam(required = false) String requesterId,
            @RequestParam(required = false) RequestStatus status,
            @RequestParam(required = false) RequestCategory category) {
        return service.list(requesterId, status, category, AuthContextHolder.require());
    }

    @GetMapping("/summary")
    @PreAuthorize("hasAnyRole('SERVICE_DESK_OFFICER','ADMIN_STAFF')")
    @Operation(summary = "Category/status/priority/location/service-unit counts (US-13, FR-12)")
    public SummaryResponse summary(@RequestParam(required = false) String groupBy) {
        return service.summary(groupBy, AuthContextHolder.require());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Full detail for one request - owner or Service Desk only (US-02/03, API-02)")
    public ServiceRequestResponse getById(@PathVariable("id") String id) {
        return service.getById(id, AuthContextHolder.require());
    }

    @PatchMapping("/{id}/triage")
    @PreAuthorize("hasRole('SERVICE_DESK_OFFICER')")
    @Operation(summary = "Classify + set priority + set responsible unit (US-04/05, FR-03/04, API-03, BR-03)")
    public ServiceRequestResponse triage(@PathVariable("id") String id, @Valid @RequestBody TriageRequest request) {
        return service.triage(id, request, AuthContextHolder.require());
    }

    @PatchMapping("/{id}/reject")
    @PreAuthorize("hasRole('SERVICE_DESK_OFFICER')")
    @Operation(summary = "Reject with a mandatory reason (US-11, FR-10, BR-05)")
    public ServiceRequestResponse reject(@PathVariable("id") String id, @Valid @RequestBody RejectRequest request) {
        return service.reject(id, request, AuthContextHolder.require());
    }

    @PatchMapping("/{id}/escalate")
    @PreAuthorize("hasRole('SERVICE_DESK_OFFICER')")
    @Operation(summary = "Escalate to a different responsible unit (BR-04)")
    public ServiceRequestResponse escalate(@PathVariable("id") String id,
                                            @Valid @RequestBody EscalateRequest request) {
        return service.escalate(id, request, AuthContextHolder.require());
    }

    @PatchMapping("/{id}/confirm")
    @Operation(summary = "Requester confirms/feedback after resolution - closes the request "
            + "(US-12, FR-11) - original requester only")
    public ServiceRequestResponse confirm(@PathVariable("id") String id,
                                           @Valid @RequestBody ConfirmRequest request) {
        return service.confirm(id, request, AuthContextHolder.require());
    }

    /**
     * INTERNAL - service-to-service only (guide §4.1: "Called by work-order-service to push
     * Assigned/Resolved status + timestamp... not exposed to the frontend/gateway"). Same base
     * path as the rest of this controller by design, matching what the guide documents - keep
     * this path OUT of the API Gateway's public route table (README "Service-to-service auth").
     */
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('SERVICE')")
    @Operation(summary = "INTERNAL ONLY - work-order-service pushes Assigned/InProgress/Resolved here")
    public ServiceRequestResponse applyInternalStatusUpdate(@PathVariable("id") String id,
                                                             @Valid @RequestBody InternalStatusUpdateRequest request) {
        return service.applyInternalStatusUpdate(id, request, AuthContextHolder.require());
    }
}
