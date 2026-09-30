package com.usm.servicerequest.service;

import com.usm.servicerequest.domain.RequestCategory;
import com.usm.servicerequest.domain.RequestPriority;
import com.usm.servicerequest.domain.RequestStatus;
import com.usm.servicerequest.domain.ServiceRequest;
import com.usm.servicerequest.dto.ConfirmRequest;
import com.usm.servicerequest.dto.CreateServiceRequestRequest;
import com.usm.servicerequest.dto.EscalateRequest;
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
import com.usm.servicerequest.client.FacilityValidationClient;
import com.usm.servicerequest.dto.FacilityValidationResult;
import com.usm.servicerequest.security.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * First-draft unit tests for the business rules in guide §5, written the way
 * the guide's §12 describes using AI: a fast first pass you then run, read,
 * and extend yourself with the acceptance-criteria cases your BA report
 * actually calls out.
 */
@ExtendWith(MockitoExtension.class)
class ServiceRequestServiceImplTest {

    @Mock
    private ServiceRequestRepository repository;

    @Mock
    private RequestIdGenerator idGenerator;

    private ServiceRequestServiceImpl service;

    private static final AuthContext STUDENT = new AuthContext("student-1", Role.STUDENT, "Faculty of Science");
    private static final AuthContext OFFICER = new AuthContext("officer-1", Role.SERVICE_DESK_OFFICER, "IT Services");
    private static final AuthContext SERVICE_CALL = new AuthContext("work-order-service", Role.SERVICE, "SYSTEM");

    @BeforeEach
    void setUp() {
        service = new ServiceRequestServiceImpl(repository, idGenerator);
    }

    private ServiceRequest newRequest(RequestStatus status) {
        ServiceRequest entity = new ServiceRequest("SR-2026-0001", STUDENT.getUserId(), RequestCategory.IT,
                "Library Room 12", RequestPriority.MEDIUM, "Projector not working", null, Instant.now());
        if (status != RequestStatus.NEW) {
            entity.acknowledge(Instant.now());
        }
        if (status == RequestStatus.ASSIGNED || status == RequestStatus.IN_PROGRESS
                || status == RequestStatus.RESOLVED || status == RequestStatus.CLOSED) {
            entity.markAssigned(Instant.now());
        }
        if (status == RequestStatus.IN_PROGRESS) {
            entity.markInProgress();
        }
        if (status == RequestStatus.RESOLVED || status == RequestStatus.CLOSED) {
            entity.markResolved(Instant.now());
        }
        if (status == RequestStatus.CLOSED) {
            entity.confirmAndClose("thanks", Instant.now());
        }
        if (status == RequestStatus.REJECTED) {
            entity.reject("duplicate", Instant.now());
        }
        if (status == RequestStatus.ESCALATED) {
            entity.escalate("Facilities Management", Instant.now());
        }
        return entity;
    }

    @Test
    void create_setsStatusNewAndOwnerFromCaller() {
        when(idGenerator.nextId()).thenReturn("SR-2026-0007");

        CreateServiceRequestRequest request = new CreateServiceRequestRequest(
                RequestCategory.IT, "Lab 3", RequestPriority.HIGH, "Network is down", null);

        ServiceRequestResponse response = service.create(request, STUDENT);

        assertThat(response.requestId()).isEqualTo("SR-2026-0007");
        assertThat(response.requesterId()).isEqualTo(STUDENT.getUserId());
        assertThat(response.status()).isEqualTo(RequestStatus.NEW);
        assertThat(response.reportedTime()).isNotNull();
    }

    @Test
    void reject_withoutReason_isRejectedWithInvalidRequestException() {
        // Bypasses bean validation on purpose - this is the defensive, service-layer half of BR-05.
        ServiceRequest entity = newRequest(RequestStatus.NEW);
        when(repository.findById("SR-2026-0001")).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> service.reject("SR-2026-0001", new RejectRequest("  "), OFFICER))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("rejectionReason");
    }

    @Test
    void reject_afterAssigned_isRejectedAsInvalidState() {
        ServiceRequest entity = newRequest(RequestStatus.ASSIGNED);
        when(repository.findById("SR-2026-0001")).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> service.reject("SR-2026-0001", new RejectRequest("too late"), OFFICER))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void triage_fromNew_movesToAcknowledgedAndSetsResponsibleUnit() {
        ServiceRequest entity = newRequest(RequestStatus.NEW);
        when(repository.findById("SR-2026-0001")).thenReturn(Optional.of(entity));

        ServiceRequestResponse response = service.triage("SR-2026-0001",
                new TriageRequest(RequestCategory.FACILITY, RequestPriority.HIGH, "Facilities Management"),
                OFFICER);

        assertThat(response.status()).isEqualTo(RequestStatus.ACKNOWLEDGED);
        assertThat(response.responsibleServiceUnit()).isEqualTo("Facilities Management");
        assertThat(response.acknowledgedTime()).isNotNull();
    }

    @Test
    void confirm_byNonOwner_isForbidden() {
        ServiceRequest entity = newRequest(RequestStatus.RESOLVED);
        when(repository.findById("SR-2026-0001")).thenReturn(Optional.of(entity));

        AuthContext someoneElse = new AuthContext("student-2", Role.STUDENT, "Faculty of Arts");

        assertThatThrownBy(() -> service.confirm("SR-2026-0001", new ConfirmRequest("looks good"), someoneElse))
                .isInstanceOf(ForbiddenOperationException.class);
    }

    @Test
    void confirm_beforeResolved_isRejectedAsInvalidState() {
        ServiceRequest entity = newRequest(RequestStatus.ACKNOWLEDGED);
        when(repository.findById("SR-2026-0001")).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> service.confirm("SR-2026-0001", new ConfirmRequest("too soon"), STUDENT))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void confirm_afterResolved_byOwner_closesTheRequest() {
        ServiceRequest entity = newRequest(RequestStatus.RESOLVED);
        when(repository.findById("SR-2026-0001")).thenReturn(Optional.of(entity));

        ServiceRequestResponse response = service.confirm("SR-2026-0001",
                new ConfirmRequest("Fixed, thank you"), STUDENT);

        assertThat(response.status()).isEqualTo(RequestStatus.CLOSED);
        assertThat(response.confirmationFeedback()).isEqualTo("Fixed, thank you");
        assertThat(response.closedTime()).isNotNull();
    }

    @Test
    void applyInternalStatusUpdate_assignedOnUntriaged_isRejected_BR06() {
        ServiceRequest entity = newRequest(RequestStatus.NEW);
        when(repository.findById("SR-2026-0001")).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> service.applyInternalStatusUpdate("SR-2026-0001",
                new InternalStatusUpdateRequest(RequestStatus.ASSIGNED), SERVICE_CALL))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("never triaged");
    }

    @Test
    void applyInternalStatusUpdate_assignedOnAcknowledged_succeeds() {
        ServiceRequest entity = newRequest(RequestStatus.ACKNOWLEDGED);
        when(repository.findById("SR-2026-0001")).thenReturn(Optional.of(entity));

        ServiceRequestResponse response = service.applyInternalStatusUpdate("SR-2026-0001",
                new InternalStatusUpdateRequest(RequestStatus.ASSIGNED), SERVICE_CALL);

        assertThat(response.status()).isEqualTo(RequestStatus.ASSIGNED);
        assertThat(response.assignedTime()).isNotNull();
    }

    @Test
    void applyInternalStatusUpdate_byNonServiceCaller_isForbidden() {
        assertThatThrownBy(() -> service.applyInternalStatusUpdate("SR-2026-0001",
                new InternalStatusUpdateRequest(RequestStatus.ASSIGNED), OFFICER))
                .isInstanceOf(ForbiddenOperationException.class);
    }

    @Test
    void getById_unknownId_isNotFound() {
        when(repository.findById("SR-2026-9999")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById("SR-2026-9999", STUDENT))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getById_otherStudentsRequest_isForbidden_BR10() {
        ServiceRequest entity = newRequest(RequestStatus.NEW);
        when(repository.findById("SR-2026-0001")).thenReturn(Optional.of(entity));

        AuthContext someoneElse = new AuthContext("student-2", Role.STUDENT, "Faculty of Arts");

        assertThatThrownBy(() -> service.getById("SR-2026-0001", someoneElse))
                .isInstanceOf(ForbiddenOperationException.class);
    }

    @Test
    void escalate_setsStatusEscalatedAndNewResponsibleUnit() {
        ServiceRequest entity = newRequest(RequestStatus.ACKNOWLEDGED);
        when(repository.findById("SR-2026-0001")).thenReturn(Optional.of(entity));

        ServiceRequestResponse response = service.escalate("SR-2026-0001",
                new EscalateRequest("Deputy Director"), OFFICER);

        assertThat(response.status()).isEqualTo(RequestStatus.ESCALATED);
        assertThat(response.responsibleServiceUnit()).isEqualTo("Deputy Director");
    }

    @Test
    void reject_withNullReason_isRejectedWithInvalidRequestException_BR05() {
        ServiceRequest entity = newRequest(RequestStatus.NEW);
        when(repository.findById("SR-2026-0001")).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> service.reject("SR-2026-0001", new RejectRequest(null), OFFICER))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("rejectionReason");
    }

    @Test
    void reject_fromNew_setsStatusRejectedAndClosedTime_BR05_BR09() {
        ServiceRequest entity = newRequest(RequestStatus.NEW);
        when(repository.findById("SR-2026-0001")).thenReturn(Optional.of(entity));

        ServiceRequestResponse response = service.reject("SR-2026-0001",
                new RejectRequest("Duplicate request"), OFFICER);

        assertThat(response.status()).isEqualTo(RequestStatus.REJECTED);
        assertThat(response.rejectionReason()).isEqualTo("Duplicate request");
        assertThat(response.closedTime()).isNotNull();
    }

    @Test
    void triage_onNonTriageableStatus_throwsInvalidRequestException() {
        ServiceRequest entity = newRequest(RequestStatus.RESOLVED);
        when(repository.findById("SR-2026-0001")).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> service.triage("SR-2026-0001",
                new TriageRequest(RequestCategory.IT, RequestPriority.LOW, "IT Desk"), OFFICER))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("Cannot triage");
    }

    @Test
    void escalate_onNonEscalatableStatus_throwsInvalidRequestException() {
        ServiceRequest entity = newRequest(RequestStatus.RESOLVED);
        when(repository.findById("SR-2026-0001")).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> service.escalate("SR-2026-0001",
                new EscalateRequest("Dean Office"), OFFICER))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("Cannot escalate");
    }

    @Test
    void applyInternalStatusUpdate_assignedOnEscalated_succeeds_BR06() {
        ServiceRequest entity = newRequest(RequestStatus.ESCALATED);
        when(repository.findById("SR-2026-0001")).thenReturn(Optional.of(entity));

        ServiceRequestResponse response = service.applyInternalStatusUpdate("SR-2026-0001",
                new InternalStatusUpdateRequest(RequestStatus.ASSIGNED), SERVICE_CALL);

        assertThat(response.status()).isEqualTo(RequestStatus.ASSIGNED);
        assertThat(response.assignedTime()).isNotNull();
    }

    @Test
    void applyInternalStatusUpdate_inProgress_fromAssigned_succeeds() {
        ServiceRequest entity = newRequest(RequestStatus.ASSIGNED);
        when(repository.findById("SR-2026-0001")).thenReturn(Optional.of(entity));

        ServiceRequestResponse response = service.applyInternalStatusUpdate("SR-2026-0001",
                new InternalStatusUpdateRequest(RequestStatus.IN_PROGRESS), SERVICE_CALL);

        assertThat(response.status()).isEqualTo(RequestStatus.IN_PROGRESS);
    }

    @Test
    void applyInternalStatusUpdate_resolved_fromAssigned_succeeds_BR09() {
        ServiceRequest entity = newRequest(RequestStatus.ASSIGNED);
        when(repository.findById("SR-2026-0001")).thenReturn(Optional.of(entity));

        ServiceRequestResponse response = service.applyInternalStatusUpdate("SR-2026-0001",
                new InternalStatusUpdateRequest(RequestStatus.RESOLVED), SERVICE_CALL);

        assertThat(response.status()).isEqualTo(RequestStatus.RESOLVED);
        assertThat(response.resolvedTime()).isNotNull();
    }

    @Test
    void applyInternalStatusUpdate_resolved_fromUntriagedOrNew_isRejected() {
        ServiceRequest entity = newRequest(RequestStatus.NEW);
        when(repository.findById("SR-2026-0001")).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> service.applyInternalStatusUpdate("SR-2026-0001",
                new InternalStatusUpdateRequest(RequestStatus.RESOLVED), SERVICE_CALL))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("Cannot mark Resolved");
    }

    @Test
    void applyInternalStatusUpdate_unsupportedStatus_throwsInvalidRequestException() {
        ServiceRequest entity = newRequest(RequestStatus.ACKNOWLEDGED);
        when(repository.findById("SR-2026-0001")).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> service.applyInternalStatusUpdate("SR-2026-0001",
                new InternalStatusUpdateRequest(RequestStatus.CLOSED), SERVICE_CALL))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("only accepts Assigned, InProgress or Resolved");
    }

    @Test
    void getById_byOwner_succeeds() {
        ServiceRequest entity = newRequest(RequestStatus.NEW);
        when(repository.findById("SR-2026-0001")).thenReturn(Optional.of(entity));

        ServiceRequestResponse response = service.getById("SR-2026-0001", STUDENT);

        assertThat(response.requestId()).isEqualTo("SR-2026-0001");
        assertThat(response.requesterId()).isEqualTo(STUDENT.getUserId());
    }

    @Test
    void getById_byServiceDeskOfficer_canViewOtherUsersRequest_BR10() {
        ServiceRequest entity = newRequest(RequestStatus.NEW);
        when(repository.findById("SR-2026-0001")).thenReturn(Optional.of(entity));

        ServiceRequestResponse response = service.getById("SR-2026-0001", OFFICER);

        assertThat(response.requestId()).isEqualTo("SR-2026-0001");
        assertThat(response.requesterId()).isEqualTo(STUDENT.getUserId());
    }

    @Test
    void list_forRequester_returnsOnlyOwnRequests_BR10() {
        ServiceRequest req1 = newRequest(RequestStatus.NEW);
        when(repository.findByRequesterId(STUDENT.getUserId())).thenReturn(List.of(req1));

        List<ServiceRequestResponse> results = service.list("someone-else", null, null, STUDENT);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).requesterId()).isEqualTo(STUDENT.getUserId());
    }

    @Test
    void list_forOfficer_returnsAllRequests_andFiltersByStatus() {
        ServiceRequest req1 = newRequest(RequestStatus.ACKNOWLEDGED);
        when(repository.findByStatus(RequestStatus.ACKNOWLEDGED)).thenReturn(List.of(req1));

        List<ServiceRequestResponse> results = service.list(null, RequestStatus.ACKNOWLEDGED, null, OFFICER);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).status()).isEqualTo(RequestStatus.ACKNOWLEDGED);
    }

    @Test
    void summary_groupByStatus_calculatesCounts() {
        ServiceRequest req1 = newRequest(RequestStatus.NEW);
        ServiceRequest req2 = newRequest(RequestStatus.ACKNOWLEDGED);
        when(repository.findAll()).thenReturn(List.of(req1, req2));

        SummaryResponse response = service.summary("status", OFFICER);

        assertThat(response.groupBy()).isEqualTo("status");
        assertThat(response.counts()).containsEntry("NEW", 1L).containsEntry("ACKNOWLEDGED", 1L);
    }

    @Test
    void list_forRequester_filtersByStatusAndCategory() {
        ServiceRequest req1 = newRequest(RequestStatus.NEW); // IT category
        ServiceRequest req2 = new ServiceRequest("SR-2026-0002", STUDENT.getUserId(), RequestCategory.FACILITY,
                "Room 1", RequestPriority.LOW, "Desk broken", null, Instant.now());
        when(repository.findByRequesterId(STUDENT.getUserId())).thenReturn(List.of(req1, req2));

        List<ServiceRequestResponse> results = service.list(null, RequestStatus.NEW, RequestCategory.IT, STUDENT);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).requestId()).isEqualTo("SR-2026-0001");
    }

    @Test
    void list_forOfficer_filtersByCategory() {
        ServiceRequest req1 = newRequest(RequestStatus.NEW); // IT category
        when(repository.findByCategory(RequestCategory.IT)).thenReturn(List.of(req1));

        List<ServiceRequestResponse> results = service.list(null, null, RequestCategory.IT, OFFICER);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).category()).isEqualTo(RequestCategory.IT);
    }

    @Test
    void list_forOfficer_filtersByRequesterId() {
        ServiceRequest req1 = newRequest(RequestStatus.NEW); // owner: student-1
        ServiceRequest req2 = new ServiceRequest("SR-2026-0002", "student-2", RequestCategory.IT,
                "Room 1", RequestPriority.LOW, "Desk broken", null, Instant.now());
        when(repository.findAll()).thenReturn(List.of(req1, req2));

        List<ServiceRequestResponse> results = service.list("student-2", null, null, OFFICER);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).requesterId()).isEqualTo("student-2");
    }

    @Test
    void summary_groupByCategory_calculatesCounts() {
        ServiceRequest req1 = newRequest(RequestStatus.NEW); // IT
        ServiceRequest req2 = new ServiceRequest("SR-2026-0002", STUDENT.getUserId(), RequestCategory.FACILITY,
                "Room 1", RequestPriority.LOW, "Desk broken", null, Instant.now());
        when(repository.findAll()).thenReturn(List.of(req1, req2));

        SummaryResponse response = service.summary("category", OFFICER);

        assertThat(response.groupBy()).isEqualTo("category");
        assertThat(response.counts()).containsEntry("IT", 1L).containsEntry("FACILITY", 1L);
    }

    @Test
    void summary_withUnsupportedGroupBy_throwsInvalidRequestException() {
        when(repository.findAll()).thenReturn(List.of());

        assertThatThrownBy(() -> service.summary("invalid_group", OFFICER))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("Unsupported groupBy");
    }

    @Test
    void create_facilityCategory_whenEnforceValidationFalse_proceedsWhenValidationFails() {
        FacilityValidationClient mockClient = mock(FacilityValidationClient.class);
        when(mockClient.validateByCode("LAB-101"))
                .thenReturn(new FacilityValidationResult(1L, "LAB-101", 1L, true, true, false, 30, false, null, null, false, "Resource is currently marked unavailable"));
        when(idGenerator.nextId()).thenReturn("SR-2026-0099");

        ServiceRequestServiceImpl customService = new ServiceRequestServiceImpl(repository, idGenerator, mockClient, false);

        CreateServiceRequestRequest request = new CreateServiceRequestRequest(
                RequestCategory.FACILITY, "LAB-101", RequestPriority.HIGH, "Need room", null);

        ServiceRequestResponse response = customService.create(request, STUDENT);

        assertThat(response.requestId()).isEqualTo("SR-2026-0099");
        assertThat(response.category()).isEqualTo(RequestCategory.FACILITY);
        verify(mockClient).validateByCode("LAB-101");
    }

    @Test
    void create_facilityCategory_whenEnforceValidationTrue_throwsWhenValidationFails() {
        FacilityValidationClient mockClient = mock(FacilityValidationClient.class);
        when(mockClient.validateByCode("LAB-101"))
                .thenReturn(new FacilityValidationResult(1L, "LAB-101", 1L, true, true, false, 30, false, null, null, false, "Resource is currently marked unavailable"));

        ServiceRequestServiceImpl customService = new ServiceRequestServiceImpl(repository, idGenerator, mockClient, true);

        CreateServiceRequestRequest request = new CreateServiceRequestRequest(
                RequestCategory.FACILITY, "LAB-101", RequestPriority.HIGH, "Need room", null);

        assertThatThrownBy(() -> customService.create(request, STUDENT))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Resource is currently marked unavailable");
        verify(mockClient).validateByCode("LAB-101");
    }

    @Test
    void create_facilityCategory_whenEnforceValidationTrue_succeedsWhenValid() {
        FacilityValidationClient mockClient = mock(FacilityValidationClient.class);
        when(mockClient.validateByCode("LAB-101"))
                .thenReturn(new FacilityValidationResult(1L, "LAB-101", 1L, true, true, true, 30, false, null, null, true, "Resource is valid and available for reservation"));
        when(idGenerator.nextId()).thenReturn("SR-2026-0100");

        ServiceRequestServiceImpl customService = new ServiceRequestServiceImpl(repository, idGenerator, mockClient, true);

        CreateServiceRequestRequest request = new CreateServiceRequestRequest(
                RequestCategory.FACILITY, "LAB-101", RequestPriority.HIGH, "Need room", null);

        ServiceRequestResponse response = customService.create(request, STUDENT);

        assertThat(response.requestId()).isEqualTo("SR-2026-0100");
        verify(mockClient).validateByCode("LAB-101");
    }

    @Test
    void create_itCategory_doesNotCallFacilityValidation() {
        FacilityValidationClient mockClient = mock(FacilityValidationClient.class);
        when(idGenerator.nextId()).thenReturn("SR-2026-0101");

        ServiceRequestServiceImpl customService = new ServiceRequestServiceImpl(repository, idGenerator, mockClient, true);

        CreateServiceRequestRequest request = new CreateServiceRequestRequest(
                RequestCategory.IT, "LAB-101", RequestPriority.HIGH, "Network down", null);

        ServiceRequestResponse response = customService.create(request, STUDENT);

        assertThat(response.requestId()).isEqualTo("SR-2026-0101");
        verifyNoInteractions(mockClient);
    }
}
