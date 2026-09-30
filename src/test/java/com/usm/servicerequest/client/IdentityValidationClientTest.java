package com.usm.servicerequest.client;

import com.usm.servicerequest.exception.ForbiddenOperationException;
import com.usm.servicerequest.security.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class IdentityValidationClientTest {

    private static final String BASE_URL = "http://localhost:8001";
    private static final String EXPECTED_URL = "http://localhost:8001/api/v1/validation/users/usr-001?require_active=true&required_role=SERVICE_DESK_OFFICER";
    private static final String SAMPLE_TOKEN = "eyJhbGciOiJSUzI1NiJ9.test-payload";

    private RestTemplate restTemplate;
    private MockRestServiceServer mockServer;
    private IdentityValidationClient client;

    @BeforeEach
    void setUp() {
        restTemplate = new RestTemplate();
        mockServer = MockRestServiceServer.createServer(restTemplate);
        client = new IdentityValidationClient(restTemplate, BASE_URL);
    }

    @Test
    void validateUser_validAndAuthorized_succeedsNormally() {
        String json = """
                {
                  "is_valid": true,
                  "is_authorized": true,
                  "message": "User is active and authorized"
                }
                """;

        mockServer.expect(requestTo(EXPECTED_URL))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer " + SAMPLE_TOKEN))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        assertThatCode(() -> client.validateUser("usr-001", Role.SERVICE_DESK_OFFICER, SAMPLE_TOKEN))
                .doesNotThrowAnyException();

        mockServer.verify();
    }

    @Test
    void validateUser_nestedDataPayload_succeedsNormally() {
        String json = """
                {
                  "success": true,
                  "data": {
                    "is_valid": true,
                    "is_authorized": true
                  }
                }
                """;

        mockServer.expect(requestTo(EXPECTED_URL))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer " + SAMPLE_TOKEN))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        assertThatCode(() -> client.validateUser("usr-001", Role.SERVICE_DESK_OFFICER, SAMPLE_TOKEN))
                .doesNotThrowAnyException();

        mockServer.verify();
    }

    @Test
    void validateUser_notAuthorizedInResponse_throwsForbiddenOperationException() {
        String json = """
                {
                  "is_valid": true,
                  "is_authorized": false,
                  "message": "User lacks required role"
                }
                """;

        mockServer.expect(requestTo(EXPECTED_URL))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.validateUser("usr-001", Role.SERVICE_DESK_OFFICER, SAMPLE_TOKEN))
                .isInstanceOf(ForbiddenOperationException.class)
                .hasMessageContaining("User lacks required role");

        mockServer.verify();
    }

    @Test
    void validateUser_accountInactive_returns403_throwsForbiddenOperationException() {
        mockServer.expect(requestTo(EXPECTED_URL))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.FORBIDDEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\": \"ACCOUNT_INACTIVE\"}"));

        assertThatThrownBy(() -> client.validateUser("usr-001", Role.SERVICE_DESK_OFFICER, SAMPLE_TOKEN))
                .isInstanceOf(ForbiddenOperationException.class)
                .hasMessageContaining("403");

        mockServer.verify();
    }

    @Test
    void validateUser_userNotFound_returns404_throwsForbiddenOperationException() {
        mockServer.expect(requestTo(EXPECTED_URL))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.NOT_FOUND)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\": \"USER_NOT_FOUND\"}"));

        assertThatThrownBy(() -> client.validateUser("usr-001", Role.SERVICE_DESK_OFFICER, SAMPLE_TOKEN))
                .isInstanceOf(ForbiddenOperationException.class)
                .hasMessageContaining("404");

        mockServer.verify();
    }

    @Test
    void validateUser_serviceError_returns500_throwsForbiddenOperationException() {
        mockServer.expect(requestTo(EXPECTED_URL))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatThrownBy(() -> client.validateUser("usr-001", Role.SERVICE_DESK_OFFICER, SAMPLE_TOKEN))
                .isInstanceOf(ForbiddenOperationException.class);

        mockServer.verify();
    }

    @Test
    void validateUser_missingToken_throwsForbiddenOperationExceptionWithoutNetworkCall() {
        assertThatThrownBy(() -> client.validateUser("usr-001", Role.SERVICE_DESK_OFFICER, null))
                .isInstanceOf(ForbiddenOperationException.class)
                .hasMessageContaining("Missing bearer token");

        assertThatThrownBy(() -> client.validateUser("usr-001", Role.SERVICE_DESK_OFFICER, "   "))
                .isInstanceOf(ForbiddenOperationException.class)
                .hasMessageContaining("Missing bearer token");
    }
}
