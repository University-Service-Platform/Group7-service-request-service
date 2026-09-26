package com.usm.servicerequest.client;

import com.usm.servicerequest.dto.FacilityValidationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class FacilityValidationClientTest {

    private static final String BASE_URL = "http://localhost:8081";

    private RestTemplate restTemplate;
    private MockRestServiceServer mockServer;
    private FacilityValidationClient client;

    @BeforeEach
    void setUp() {
        restTemplate = new RestTemplate();
        mockServer = MockRestServiceServer.createServer(restTemplate);
        client = new FacilityValidationClient(restTemplate, BASE_URL);
    }

    @Test
    void validateByCode_validAndAvailableResource_returnsValidForReservationTrue() {
        String json = """
                {
                  "success": true,
                  "message": "Group 8 resource validation completed",
                  "data": {
                    "resourceId": 1,
                    "resourceCode": "LAB-101",
                    "facilityId": 1,
                    "exists": true,
                    "active": true,
                    "available": true,
                    "capacity": 30,
                    "approvalRequired": false,
                    "operatingHoursStart": "08:00:00",
                    "operatingHoursEnd": "20:00:00",
                    "validForReservation": true,
                    "message": "Resource is valid and available for reservation"
                  },
                  "timestamp": "2026-09-25T19:22:00"
                }
                """;

        mockServer.expect(requestTo("http://localhost:8081/api/resources/code/LAB-101/validate"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        FacilityValidationResult result = client.validateByCode("LAB-101");

        mockServer.verify();
        assertThat(result).isNotNull();
        assertThat(result.validForReservation()).isTrue();
        assertThat(result.exists()).isTrue();
        assertThat(result.active()).isTrue();
        assertThat(result.available()).isTrue();
        assertThat(result.resourceId()).isEqualTo(1L);
        assertThat(result.resourceCode()).isEqualTo("LAB-101");
        assertThat(result.facilityId()).isEqualTo(1L);
        assertThat(result.capacity()).isEqualTo(30);
        assertThat(result.approvalRequired()).isFalse();
        assertThat(result.operatingHoursStart()).isEqualTo("08:00:00");
        assertThat(result.operatingHoursEnd()).isEqualTo("20:00:00");
        assertThat(result.message()).isEqualTo("Resource is valid and available for reservation");
    }

    @Test
    void validateByCode_notFoundResource_returnsExistsFalseAndValidForReservationFalse() {
        String json = """
                {
                  "success": true,
                  "message": "Group 8 resource validation completed",
                  "data": {
                    "resourceId": 999, "resourceCode": null, "facilityId": null,
                    "exists": false, "active": false, "available": false,
                    "capacity": null, "approvalRequired": false,
                    "operatingHoursStart": null, "operatingHoursEnd": null,
                    "validForReservation": false,
                    "message": "Resource with ID 999 does not exist"
                  },
                  "timestamp": "2026-09-25T19:22:00"
                }
                """;

        mockServer.expect(requestTo("http://localhost:8081/api/resources/code/UNKNOWN-999/validate"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        FacilityValidationResult result = client.validateByCode("UNKNOWN-999");

        mockServer.verify();
        assertThat(result).isNotNull();
        assertThat(result.exists()).isFalse();
        assertThat(result.active()).isFalse();
        assertThat(result.available()).isFalse();
        assertThat(result.validForReservation()).isFalse();
        assertThat(result.resourceId()).isEqualTo(999L);
        assertThat(result.resourceCode()).isNull();
        assertThat(result.facilityId()).isNull();
        assertThat(result.capacity()).isNull();
        assertThat(result.approvalRequired()).isFalse();
        assertThat(result.operatingHoursStart()).isNull();
        assertThat(result.operatingHoursEnd()).isNull();
        assertThat(result.message()).isEqualTo("Resource with ID 999 does not exist");
    }

    @Test
    void validateByCode_inactiveOrUnavailableResource_returnsValidForReservationFalse() {
        String json = """
                {
                  "success": true,
                  "message": "Group 8 resource validation completed",
                  "data": {
                    "resourceId": 1, "resourceCode": "LAB-101", "facilityId": 1,
                    "exists": true, "active": true, "available": false,
                    "capacity": 30, "approvalRequired": false,
                    "operatingHoursStart": "08:00:00", "operatingHoursEnd": "20:00:00",
                    "validForReservation": false,
                    "message": "Resource is currently marked unavailable"
                  },
                  "timestamp": "2026-09-25T19:22:00"
                }
                """;

        mockServer.expect(requestTo("http://localhost:8081/api/resources/code/LAB-101/validate"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        FacilityValidationResult result = client.validateByCode("LAB-101");

        mockServer.verify();
        assertThat(result).isNotNull();
        assertThat(result.exists()).isTrue();
        assertThat(result.active()).isTrue();
        assertThat(result.available()).isFalse();
        assertThat(result.validForReservation()).isFalse();
        assertThat(result.resourceId()).isEqualTo(1L);
        assertThat(result.resourceCode()).isEqualTo("LAB-101");
        assertThat(result.facilityId()).isEqualTo(1L);
        assertThat(result.capacity()).isEqualTo(30);
        assertThat(result.approvalRequired()).isFalse();
        assertThat(result.operatingHoursStart()).isEqualTo("08:00:00");
        assertThat(result.operatingHoursEnd()).isEqualTo("20:00:00");
        assertThat(result.message()).isEqualTo("Resource is currently marked unavailable");
    }

    @Test
    void validateByCode_serverError500_returnsSafeFallbackResult() {
        mockServer.expect(requestTo("http://localhost:8081/api/resources/code/LAB-101/validate"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withServerError());

        FacilityValidationResult result = client.validateByCode("LAB-101");

        mockServer.verify();
        assertThat(result).isNotNull();
        assertThat(result.exists()).isFalse();
        assertThat(result.validForReservation()).isFalse();
        assertThat(result.message()).isEqualTo("Facility validation service unreachable");
    }

    @Test
    void validateByCode_connectionRefusedOrTimeout_returnsSafeFallbackResult() {
        RestTemplate mockRestTemplate = mock(RestTemplate.class);
        when(mockRestTemplate.getForEntity(anyString(), any(), anyString()))
                .thenThrow(new ResourceAccessException("Connection refused: connect"));

        FacilityValidationClient exceptionClient = new FacilityValidationClient(mockRestTemplate, BASE_URL);
        FacilityValidationResult result = exceptionClient.validateByCode("LAB-101");

        assertThat(result).isNotNull();
        assertThat(result.exists()).isFalse();
        assertThat(result.validForReservation()).isFalse();
        assertThat(result.message()).isEqualTo("Facility validation service unreachable");
    }

    @Test
    void validateByCode_blankOrNullCode_returnsSafeFallbackResult() {
        FacilityValidationResult resultBlank = client.validateByCode("   ");
        assertThat(resultBlank.exists()).isFalse();
        assertThat(resultBlank.validForReservation()).isFalse();
        assertThat(resultBlank.message()).isEqualTo("Facility validation service unreachable");

        FacilityValidationResult resultNull = client.validateByCode(null);
        assertThat(resultNull.exists()).isFalse();
        assertThat(resultNull.validForReservation()).isFalse();
        assertThat(resultNull.message()).isEqualTo("Facility validation service unreachable");
    }
}
