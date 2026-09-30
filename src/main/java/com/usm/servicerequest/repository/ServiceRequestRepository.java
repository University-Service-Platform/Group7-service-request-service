package com.usm.servicerequest.repository;

import com.usm.servicerequest.domain.RequestCategory;
import com.usm.servicerequest.domain.RequestStatus;
import com.usm.servicerequest.domain.ServiceRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ServiceRequestRepository extends JpaRepository<ServiceRequest, String> {

    List<ServiceRequest> findByRequesterId(String requesterId);

    List<ServiceRequest> findByStatus(RequestStatus status);

    List<ServiceRequest> findByCategory(RequestCategory category);

    @Query("SELECT MAX(r.requestId) FROM ServiceRequest r WHERE r.requestId LIKE CONCAT('SR-', :year, '-%')")
    Optional<String> findMaxRequestIdForYear(@Param("year") int year);
}
