package com.usm.servicerequest.service;

import com.usm.servicerequest.client.FacilityValidationClient;
import com.usm.servicerequest.domain.RequestCategory;
import com.usm.servicerequest.domain.RequestStatus;
import com.usm.servicerequest.domain.ServiceRequest;
import com.usm.servicerequest.dto.ConfirmRequest;
import com.usm.servicerequest.dto.CreateServiceRequestRequest;
import com.usm.servicerequest.dto.EscalateRequest;
import com.usm.servicerequest.dto.FacilityValidationResult;
import com.usm.servicerequest.dto.InternalStatusUpdateRequest;
import com.usm.servicerequest.dto.RejectRequest;
import com.usm.servicerequest.dto.ServiceRequestResponse;
import com.usm.servicerequest.dto.SummaryResponse;
import com.usm.servicerequest.dto.TriageRequest;
import com.usm.servicerequest.exception.ForbiddenOperationException;
import com.usm.servicerequest.exception.InvalidRequestException;
import com.usm.servicerequest.exception.ResourceNotFoundException;
import com.usm.servicerequest.repository.ServiceRequestRepository;
import com.usm.servicerequest.security.AuthContext;
import com.usm.servicerequest.security.Role;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional
public class ServiceRequestServiceImpl implements ServiceRequestService {

    private static final Logger log = LoggerFactory.getLogger(ServiceRequestServiceImpl.class);

    /** Roles that may see every request, not just their own (BR-10). */
    private static final Set<Role> CAN_VIEW_ALL = EnumSet.of(Role.SERVICE_DESK_OFFICER, Role.SERVICE);

    /** Statuses a request can still be triaged from (guide §5 BR-03 scope). */
    private static final Set<RequestStatus> TRIAGEABLE = EnumSet.of(
            RequestStatus.NEW, RequestStatus.ACKNOWLEDGED, RequestStatus.ESCALATED);

    /** Statuses a request can still be rejected from - once work has started, reject no longer applies. */
    private static final Set<RequestStatus> REJECTABLE = EnumSet.of(
            RequestStatus.NEW, RequestStatus.ACKNOWLEDGED, RequestStatus.ESCALATED);

    /** Statuses a request can still be escalated from. */
    private static final Set<RequestStatus> ESCALATABLE = EnumSet.of(
            RequestStatus.NEW, RequestStatus.ACKNOWLEDGED, RequestStatus.ESCALATED);

    private final ServiceRequestRepository repository;
    private final RequestIdGenerator idGenerator;
    private final FacilityValidationClient facilityValidationClient;
    private final boolean enforceValidation;

    public ServiceRequestServiceImpl(ServiceRequestRepository repository, RequestIdGenerator idGenerator) {
        this(repository, idGenerator, null, false);
    }

    public ServiceRequestServiceImpl(
            ServiceRequestRepository repository,
            RequestIdGenerator idGenerator,
            FacilityValidationClient facilityValidationClient) {
        this(repository, idGenerator, facilityValidationClient, false);
    }

    @Autowired
    public ServiceRequestServiceImpl(
            ServiceRequestRepository repository,
            RequestIdGenerator idGenerator,
            FacilityValidationClient facilityValidationClient,
            @Value("${group6.facility-service.enforce-validation:false}") boolean enforceValidation) {
        this.repository = repository;
        this.idGenerator = idGenerator;
        this.facilityValidationClient = facilityValidationClient;
        this.enforceValidation = enforceValidation;
    }

    @Override
    public ServiceRequestResponse create(CreateServiceRequestRequest request, AuthContext caller) {

        boolean isFacilityOrEquipment = request.category() == RequestCategory.FACILITY
                || request.category() == RequestCategory.EQUIPMENT;
        boolean hasLocation = request.location() != null && !request.location().isBlank();

        if (isFacilityOrEquipment && hasLocation && facilityValidationClient != null) {
            FacilityValidationResult result = facilityValidationClient.validateByCode(request.location());
            if (result != null && !result.validForReservation()) {
                if (enforceValidation) {
                    throw new InvalidRequestException(result.message());
                } else {
                    log.warn("Facility validation warning for location '{}': {}", request.location(), result.message());
                }
            }
        }

        String requestId = idGenerator.nextId();
        ServiceRequest entity = new ServiceRequest(
                requestId,
                caller.getUserId(),
                request.category(),
                request.location(),
                request.priority(),
                request.description(),
                request.attachmentReference(),
                Instant.now()
        );
        repository.save(entity);
        return ServiceRequestResponse.from(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ServiceRequestResponse> list(String requesterIdFilter, RequestStatus statusFilter,
                                              RequestCategory categoryFilter, AuthContext caller) {
        List<ServiceRequest> base;

        if (CAN_VIEW_ALL.contains(caller.getRole())) {
            if (statusFilter != null) {
                base = repository.findByStatus(statusFilter);
            } else if (categoryFilter != null) {
                base = repository.findByCategory(categoryFilter);
            } else {
                base = repository.findAll();
            }
            // BR-10 is about limiting requesters to their own data, not about restricting staff -
            // an explicit requesterId filter from staff is just a normal query parameter.
            if (requesterIdFilter != null) {
                base = base.stream().filter(r -> r.getRequesterId().equals(requesterIdFilter)).toList();
            }
        } else {
            // BR-10: requesters only ever see their own requests, regardless of what filter they pass.
            base = repository.findByRequesterId(caller.getUserId());
            if (statusFilter != null) {
                base = base.stream().filter(r -> r.getStatus() == statusFilter).toList();
            }
            if (categoryFilter != null) {
                base = base.stream().filter(r -> r.getCategory() == categoryFilter).toList();
            }
        }

        return base.stream().map(ServiceRequestResponse::from).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ServiceRequestResponse getById(String requestId, AuthContext caller) {
        ServiceRequest entity = findOrThrow(requestId);
        assertCanView(entity, caller);
        return ServiceRequestResponse.from(entity);
    }

    @Override
    public ServiceRequestResponse triage(String requestId, TriageRequest request, AuthContext caller) {
        ServiceRequest entity = findOrThrow(requestId);

        if (!TRIAGEABLE.contains(entity.getStatus())) {
            throw new InvalidRequestException(
                    "Cannot triage a request in status " + entity.getStatus() + ".");
        }

        if (request.category() != null) {
            entity.setCategory(request.category());
        }
        if (request.priority() != null) {
            entity.setPriority(request.priority());
        }
        entity.setResponsibleServiceUnit(request.responsibleServiceUnit());

        if (entity.getStatus() == RequestStatus.NEW) {
            entity.acknowledge(Instant.now());
        }

        return ServiceRequestResponse.from(entity);
    }

    @Override
    public ServiceRequestResponse reject(String requestId, RejectRequest request, AuthContext caller) {
        ServiceRequest entity = findOrThrow(requestId);

        // BR-05: enforced again here (not just @NotBlank on the DTO) so this stays true even if
        // this method is ever called from somewhere other than the HTTP layer (e.g. a future batch job).
        if (request.rejectionReason() == null || request.rejectionReason().isBlank()) {
            throw new InvalidRequestException("rejectionReason is required to reject a request.");
        }

        if (!REJECTABLE.contains(entity.getStatus())) {
            throw new InvalidRequestException(
                    "Cannot reject a request in status " + entity.getStatus() + ".");
        }

        entity.reject(request.rejectionReason(), Instant.now());
        return ServiceRequestResponse.from(entity);
    }

    @Override
    public ServiceRequestResponse escalate(String requestId, EscalateRequest request, AuthContext caller) {
        ServiceRequest entity = findOrThrow(requestId);

        if (!ESCALATABLE.contains(entity.getStatus())) {
            throw new InvalidRequestException(
                    "Cannot escalate a request in status " + entity.getStatus() + ".");
        }

        entity.escalate(request.responsibleServiceUnit(), Instant.now());
        return ServiceRequestResponse.from(entity);
    }

    @Override
    public ServiceRequestResponse confirm(String requestId, ConfirmRequest request, AuthContext caller) {
        ServiceRequest entity = findOrThrow(requestId);

        // US-12: "Original requester only" - even Service Desk cannot confirm on the requester's behalf.
        if (!entity.getRequesterId().equals(caller.getUserId())) {
            throw new ForbiddenOperationException("Only the original requester can confirm this request.");
        }

        if (entity.getStatus() != RequestStatus.RESOLVED) {
            throw new InvalidRequestException(
                    "Cannot confirm a request in status " + entity.getStatus() + " - it must be Resolved first.");
        }

        entity.confirmAndClose(request.confirmationFeedback(), Instant.now());
        return ServiceRequestResponse.from(entity);
    }

    @Override
    public ServiceRequestResponse applyInternalStatusUpdate(String requestId, InternalStatusUpdateRequest request,
                                                              AuthContext caller) {
        // Controller-level @PreAuthorize already restricts this to Role.SERVICE, but a service-layer
        // check costs nothing and keeps this method safe if a future refactor forgets that annotation.
        if (!caller.isServiceCall()) {
            throw new ForbiddenOperationException("This endpoint is for service-to-service calls only.");
        }

        ServiceRequest entity = findOrThrow(requestId);
        Instant now = Instant.now();

        switch (request.status()) {
            case ASSIGNED -> {
                // BR-06: a work order (and therefore this callback) can only exist for a triaged request.
                Set<RequestStatus> eligibleForAssignment = EnumSet.of(
                        RequestStatus.ACKNOWLEDGED, RequestStatus.ESCALATED, RequestStatus.ASSIGNED);
                if (!eligibleForAssignment.contains(entity.getStatus())) {
                    throw new InvalidRequestException(
                            "Cannot mark Assigned - request " + requestId + " is in status "
                                    + entity.getStatus() + " and was never triaged.");
                }
                entity.markAssigned(now);
            }
            case IN_PROGRESS -> entity.markInProgress();
            case RESOLVED -> {
                Set<RequestStatus> resolvableFrom = EnumSet.of(RequestStatus.ASSIGNED, RequestStatus.IN_PROGRESS);
                if (!resolvableFrom.contains(entity.getStatus())) {
                    throw new InvalidRequestException(
                            "Cannot mark Resolved - request " + requestId + " is in status "
                                    + entity.getStatus() + ".");
                }
                entity.markResolved(now);
            }
            default -> throw new InvalidRequestException(
                    "The internal status endpoint only accepts Assigned, InProgress or Resolved, got "
                            + request.status() + ".");
        }

        return ServiceRequestResponse.from(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public SummaryResponse summary(String groupBy, AuthContext caller) {
        List<ServiceRequest> all = repository.findAll();

        Function<ServiceRequest, String> keyExtractor = switch (groupBy == null ? "status" : groupBy.toLowerCase()) {
            case "category" -> r -> r.getCategory().name();
            case "priority" -> r -> r.getPriority().name();
            case "location" -> ServiceRequest::getLocation;
            case "servicedeskofficer", "responsibleserviceunit", "unit" ->
                    r -> r.getResponsibleServiceUnit() == null ? "UNASSIGNED" : r.getResponsibleServiceUnit();
            case "status" -> r -> r.getStatus().name();
            default -> throw new InvalidRequestException(
                    "Unsupported groupBy '" + groupBy + "'. Use one of: status, category, priority, "
                            + "location, responsibleServiceUnit.");
        };

        Map<String, Long> counts = all.stream()
                .collect(Collectors.groupingBy(keyExtractor, Collectors.counting()));

        return new SummaryResponse(groupBy == null ? "status" : groupBy, counts);
    }

    private ServiceRequest findOrThrow(String requestId) {
        return repository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("No service request found with id " + requestId));
    }

    private void assertCanView(ServiceRequest entity, AuthContext caller) {
        boolean isOwner = entity.getRequesterId().equals(caller.getUserId());
        if (!isOwner && !CAN_VIEW_ALL.contains(caller.getRole())) {
            throw new ForbiddenOperationException("You can only view your own service requests.");
        }
    }
}
