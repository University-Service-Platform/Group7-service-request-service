package com.usm.servicerequest.repository;

import com.usm.servicerequest.domain.RequestCategory;
import com.usm.servicerequest.domain.RequestStatus;
import com.usm.servicerequest.domain.ServiceRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ServiceRequestRepository extends JpaRepository<ServiceRequest, String> {

    List<ServiceRequest> findByRequesterId(String requesterId);

    List<ServiceRequest> findByStatus(RequestStatus status);

    List<ServiceRequest> findByCategory(RequestCategory category);
}
