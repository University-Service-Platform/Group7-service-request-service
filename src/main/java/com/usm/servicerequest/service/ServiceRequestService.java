package com.usm.servicerequest.service;

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
import com.usm.servicerequest.security.AuthContext;

import java.util.List;

public interface ServiceRequestService {

    ServiceRequestResponse create(CreateServiceRequestRequest request, AuthContext caller);

    List<ServiceRequestResponse> list(String requesterIdFilter, RequestStatus statusFilter,
                                       RequestCategory categoryFilter, AuthContext caller);

    ServiceRequestResponse getById(String requestId, AuthContext caller);

    ServiceRequestResponse triage(String requestId, TriageRequest request, AuthContext caller);

    ServiceRequestResponse reject(String requestId, RejectRequest request, AuthContext caller);

    ServiceRequestResponse escalate(String requestId, EscalateRequest request, AuthContext caller);

    ServiceRequestResponse confirm(String requestId, ConfirmRequest request, AuthContext caller);

    ServiceRequestResponse applyInternalStatusUpdate(String requestId, InternalStatusUpdateRequest request,
                                                       AuthContext caller);

    SummaryResponse summary(String groupBy, AuthContext caller);
}
