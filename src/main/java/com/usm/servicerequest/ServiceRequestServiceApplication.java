package com.usm.servicerequest;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * USM-G7 Sprint 1 - service-request-service.
 *
 * Owns the ServiceRequest entity end to end: submission, triage,
 * reject/escalate, and requester confirmation/close.
 *
 * See the project README for how this fits into Group 7's two-service split,
 * and the "Group 7 Sprint 1 - Backend Developer Guide" for the full spec this
 * service was built from (sections 3, 4, 5, 6 and 7 in particular).
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class ServiceRequestServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ServiceRequestServiceApplication.class, args);
    }
}
