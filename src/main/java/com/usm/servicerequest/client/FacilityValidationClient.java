package com.usm.servicerequest.client;

import com.usm.servicerequest.dto.FacilityValidationResponse;
import com.usm.servicerequest.dto.FacilityValidationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class FacilityValidationClient {

    private static final Logger log = LoggerFactory.getLogger(FacilityValidationClient.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;

    @Autowired
    public FacilityValidationClient(
            RestTemplateBuilder restTemplateBuilder,
            @Value("${group6.facility-service.base-url:http://localhost:8081}") String baseUrl) {
        this(restTemplateBuilder.build(), baseUrl);
    }

    public FacilityValidationClient(RestTemplate restTemplate, String baseUrl) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl == null ? "" : baseUrl.replaceAll("/+$", "");
    }

    public FacilityValidationResult validateByCode(String code) {
        if (code == null || code.isBlank()) {
            log.warn("Facility validation requested with blank code");
            return FacilityValidationResult.unreachable();
        }

        String url = baseUrl + "/api/resources/code/{code}/validate";
        try {
            ResponseEntity<FacilityValidationResponse> response = restTemplate.getForEntity(
                    url, FacilityValidationResponse.class, code.trim());

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null && response.getBody().data() != null) {
                return response.getBody().data();
            }

            log.warn("Facility validation service returned empty body for code '{}'", code);
            return FacilityValidationResult.unreachable();
        } catch (Exception ex) {
            log.warn("Failed to validate facility code '{}': {}", code, ex.getMessage());
            return FacilityValidationResult.unreachable();
        }
    }

    public String getBaseUrl() {
        return baseUrl;
    }
}
